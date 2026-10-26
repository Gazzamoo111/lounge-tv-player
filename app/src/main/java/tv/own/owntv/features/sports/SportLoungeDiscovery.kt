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
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.features.sports.data.SportEvent
import tv.own.owntv.features.sports.data.SportEventCache
import tv.own.owntv.features.sports.data.SportEventMerge
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
class SportLoungeDiscovery(private val source: SportsSource, private val cache: SportEventCache) {
    suspend fun load(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): SportLoungeDiscoverySnapshot = coroutineScope {
        val days = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().let { listOf(it, it.plusDays(1)) }
        val leagues = DISCOVERY_FEEDS.mapNotNull(::discoveryLeague)
        val gate = Semaphore(MAX_PARALLEL)
        val results = leagues.flatMap { league ->
            days.map { day ->
                async {
                    gate.withPermit {
                        runCatching { Triple(league.key, source.scoreboard(league, day), false) }
                            .getOrElse { error ->
                                Log.w(TAG, "discovery feed failed ${league.key}: ${error.javaClass.simpleName}")
                                Triple(league.key, emptyList<Match>(), true)
                            }
                    }
                }
            }
        }.awaitAll()
        val failures = results.count { it.third }
        val failedFeeds = results.filter { it.third }.mapTo(hashSetOf()) { it.first }
        val freshMatches = results.filterNot { it.third }.flatMap { it.second }
        val freshEvents = freshMatches.map { SportEvent.fromMatch(it, SOURCE_ESPN, nowMs) }
        val events = SportEventMerge.withSafeStale(freshEvents, cache.read(), failedFeeds, nowMs)
        cache.write(events)
        val freshByStableId = freshMatches.associateBy { "$SOURCE_ESPN:${it.key}" }
        val matches = events.mapNotNull { event -> freshByStableId[event.stableId] ?: event.toMatch() }
        SportLoungeDiscoverySnapshot(
            matches = SportLoungeRules.dedupe(matches),
            updatedAt = events.maxOfOrNull { it.lastSuccessfulUpdateMs }
                ?: nowMs.takeIf { failures < leagues.size * days.size },
            failedFeeds = failures,
        )
    }

    private companion object {
        const val TAG = "LoungeTV-SportLounge"
        const val SOURCE_ESPN = "espn"
        const val MAX_PARALLEL = 3
        val DISCOVERY_FEEDS = listOf(
            "football/nfl",
            "rugby-league/3",
            "rugby/289234",
            "cricket/all",
            "basketball/nbl",
            "basketball/nba",
            "soccer/aus.1",
            "soccer/eng.1",
            "soccer/uefa.champions",
            "racing/f1",
            "mma/ufc",
        )

        fun discoveryLeague(key: String): League? = SportsCatalog.league(key) ?: when (key) {
            "basketball/nbl" -> League(Sport.BASKETBALL, "nbl", "National Basketball League", "NBL", "AU")
            "soccer/aus.1" -> League(Sport.SOCCER, "aus.1", "Australian A-League Men", "A-League", "AU")
            else -> null
        }
    }
}
