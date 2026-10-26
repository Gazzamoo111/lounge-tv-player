package tv.own.owntv.features.sports

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsSource
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import java.time.Instant
import java.time.ZoneId

data class SportLoungeDiscoverySnapshot(
    val matches: List<Match> = emptyList(),
    val updatedAt: Long? = null,
    val failedFeeds: Int = 0,
)

/**
 * Small, profile-independent discovery set for a new TV. It uses the already configured fixture
 * source and verified catalogue entries; it neither touches BEST nor invents sample fixtures.
 */
class SportLoungeDiscovery(private val source: SportsSource) {
    suspend fun load(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): SportLoungeDiscoverySnapshot = coroutineScope {
        val days = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().let { listOf(it, it.plusDays(1)) }
        val leagues = DISCOVERY_FEEDS.mapNotNull(SportsCatalog::league)
        val gate = Semaphore(MAX_PARALLEL)
        val results = leagues.flatMap { league ->
            days.map { day ->
                async {
                    gate.withPermit {
                        runCatching { source.scoreboard(league, day) to false }
                            .getOrElse { error ->
                                Log.w(TAG, "discovery feed failed ${league.key}: ${error.javaClass.simpleName}")
                                emptyList<Match>() to true
                            }
                    }
                }
            }
        }.awaitAll()
        val failures = results.count { it.second }
        val matches = results.flatMap { it.first }
        SportLoungeDiscoverySnapshot(
            matches = SportLoungeRules.dedupe(matches),
            updatedAt = nowMs.takeIf { matches.isNotEmpty() || failures < leagues.size * days.size },
            failedFeeds = failures,
        )
    }

    private companion object {
        const val TAG = "LoungeTV-SportLounge"
        const val MAX_PARALLEL = 3
        val DISCOVERY_FEEDS = listOf(
            "football/nfl",
            "rugby-league/3",
            "rugby/289234",
            "cricket/all",
            "basketball/nba",
            "racing/f1",
            "mma/ufc",
        )
    }
}
