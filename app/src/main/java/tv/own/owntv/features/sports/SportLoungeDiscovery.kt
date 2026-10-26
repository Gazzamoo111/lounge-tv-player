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
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException

data class SportLoungeDiscoverySnapshot(
    val matches: List<Match> = emptyList(),
    val updatedAt: Long? = null,
    val failedFeeds: Int = 0,
)

/**
 * Small, profile-independent discovery set for a new TV. It uses the already configured fixture
 * source and verified catalogue entries; it neither touches BEST nor invents sample fixtures.
 */
/** The seven-day rolling fixture window, always aligned to New Zealand's calendar. */
internal fun sportLoungeDays(nowMs: Long, zoneId: ZoneId): List<LocalDate> {
    val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
    return (0 until 7).map { today.plusDays(it.toLong()) }
}

class SportLoungeDiscovery(private val source: SportsSource, private val cache: SportEventCache) {
    /**
     * Seven NZ calendar dates: today plus the following six dates. Month-level feeds
     * fetch complete basketball/NFL/rugby/football/F1 schedules without 7 network
     * calls per sport. Cricket needs day-level calls because ESPN has no month endpoint.
     */
    fun cached(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.of("Pacific/Auckland")): SportLoungeDiscoverySnapshot {
        val days = sportLoungeDays(nowMs, zoneId)
        val entries = cache.read().filter { event ->
            val local = Instant.ofEpochMilli(event.startUtcMs).atZone(zoneId).toLocalDate()
            (local in days || (event.state == tv.own.owntv.features.sports.data.SportEventState.LIVE &&
                event.startUtcMs in (nowMs - 24 * 60 * 60_000L)..nowMs)) &&
                (event.state != tv.own.owntv.features.sports.data.SportEventState.LIVE ||
                    nowMs - event.lastSuccessfulUpdateMs < 15 * 60_000L)
        }
        return SportLoungeDiscoverySnapshot(
            matches = SportLoungeRules.dedupe(entries.mapNotNull { it.toMatch() }),
            updatedAt = entries.maxOfOrNull { it.lastSuccessfulUpdateMs },
        )
    }

    suspend fun load(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.of("Pacific/Auckland"),
    ): SportLoungeDiscoverySnapshot = coroutineScope {
        val days = sportLoungeDays(nowMs, zoneId)
        val months = days.map(YearMonth::from).distinct()
        val leagues = DISCOVERY_FEEDS.mapNotNull(::discoveryLeague)
        val gate = Semaphore(MAX_PARALLEL)

        suspend fun readFeed(key: String, request: suspend () -> List<Match>): Triple<String, List<Match>, Boolean> =
            try {
                Triple(key, request(), false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "discovery feed failed $key: ${error.javaClass.simpleName}")
                Triple(key, emptyList(), true)
            }

        val jobs = leagues.flatMap { league ->
            if (league.sport == Sport.CRICKET) {
                days.map { day ->
                    async { gate.withPermit { readFeed(league.key) { source.scoreboard(league, day) } } }
                }
            } else {
                months.map { month ->
                    async { gate.withPermit { readFeed(league.key) { source.month(league, month).matches } } }
                }
            }
        }
        val results = jobs.awaitAll()
        val failures = results.count { it.third }
        val failedFeeds = results.filter { it.third }.mapTo(hashSetOf()) { it.first }
        val firstDay = days.first()
        val lastDay = days.last()
        val freshMatches = results.filterNot { it.third }.flatMap { it.second }
            .filter { match ->
                val localDate = Instant.ofEpochMilli(match.startMs).atZone(zoneId).toLocalDate()
                localDate in firstDay..lastDay ||
                    (match.isLive && match.startMs in (nowMs - 24 * 60 * 60_000L)..nowMs)
            }
        val freshEvents = freshMatches.map { SportEvent.fromMatch(it, SOURCE_ESPN, nowMs) }
        val events = SportEventMerge.withSafeStale(freshEvents, cache.read(), failedFeeds, nowMs)
        cache.write(events)
        val freshByStableId = freshMatches.associateBy { "$SOURCE_ESPN:${it.key}" }
        val matches = events.mapNotNull { event -> freshByStableId[event.stableId] ?: event.toMatch() }
        SportLoungeDiscoverySnapshot(
            matches = SportLoungeRules.dedupe(matches),
            updatedAt = events.maxOfOrNull { it.lastSuccessfulUpdateMs }
                ?: nowMs.takeIf { failures < jobs.size },
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
