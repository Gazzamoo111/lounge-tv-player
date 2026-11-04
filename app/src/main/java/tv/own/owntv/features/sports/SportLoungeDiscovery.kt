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
import tv.own.owntv.features.sports.data.SportEventProvider
import tv.own.owntv.features.sports.data.SportEventQuery
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException

data class SportLoungeDiscoverySnapshot(
    val matches: List<Match> = emptyList(),
    val updatedAt: Long? = null,
    val statusObservedAt: Map<String, Long> = emptyMap(),
    val failedFeeds: Int = 0,
    val heroEditorialByMatchKey: Map<String, tv.own.owntv.features.sports.data.SportEventEditorial> = emptyMap(),
)

/** Locked, previously validated feed coverage for the seven-day Sport Lounge window. */
internal val SPORT_LOUNGE_DISCOVERY_FEEDS = listOf(
    "football/nfl",
    "football/college-football",
    "rugby-league/3",
    "rugby/180659", "rugby/164205", "rugby/244293", "rugby/271937",
    "rugby/267979", "rugby/270559", "rugby/270557", "rugby/242041",
    "rugby/17567", "rugby/289234", "rugby/272073", "rugby/268565", "rugby/289237",
    "cricket/all",
    "basketball/nbl", "basketball/nba", "basketball/wnba", "basketball/mens-college-basketball",
    "hockey/nhl", "baseball/mlb",
    "soccer/aus.1", "soccer/eng.1", "soccer/uefa.champions",
    "racing/f1", "mma/ufc", "golf/pga", "tennis/atp", "tennis/wta",
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

class SportLoungeDiscovery(
    private val source: SportsSource,
    private val cache: SportEventCache,
    private val supplemental: SportEventProvider? = null,
) {
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
            statusObservedAt = entries.mapNotNull { e -> e.toMatch()?.let { it.key to e.lastSuccessfulUpdateMs } }.toMap(),
            heroEditorialByMatchKey = editorialByKey(entries),
        )
    }

    suspend fun load(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.of("Pacific/Auckland"),
    ): SportLoungeDiscoverySnapshot = coroutineScope {
        val days = sportLoungeDays(nowMs, zoneId)
        val months = days.map(YearMonth::from).distinct()
        val leagues = SPORT_LOUNGE_DISCOVERY_FEEDS.mapNotNull(::discoveryLeague)
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
        val firstDay = days.first()
        val lastDay = days.last()
        val freshMatches = results.filterNot { it.third }.flatMap { it.second }
            .filter { match ->
                val localDate = Instant.ofEpochMilli(match.startMs).atZone(zoneId).toLocalDate()
                localDate in firstDay..lastDay ||
                    (match.isLive && match.startMs in (nowMs - 24 * 60 * 60_000L)..nowMs)
            }

        val failedFeeds = results.filter { it.third }.mapTo(hashSetOf()) { it.first }
        val supplementalBatch = supplemental?.let { provider ->
            runCatching {
                provider.events(
                    SportEventQuery(
                        fromUtcMs = nowMs - 6 * 60 * 60_000L,
                        toUtcMs = nowMs + 7 * 24 * 60 * 60_000L,
                    ),
                )
            }.onFailure { error ->
                Log.w(TAG, "supplemental sports calendar failed: ${error.javaClass.simpleName}")
                failedFeeds += BOXING_FEED
            }.getOrNull()
        }
        supplementalBatch?.failedFeedIds?.let(failedFeeds::addAll)

        val freshEvents = freshMatches.map { SportEvent.fromMatch(it, SOURCE_ESPN, nowMs) } +
            supplementalBatch?.events.orEmpty()
        val events = SportEventMerge.withSafeStale(freshEvents, cache.read(), failedFeeds, nowMs)
        cache.write(events)

        val freshByStableId = freshMatches.associateBy { "$SOURCE_ESPN:${it.key}" }
        val supplementalByStableId = supplementalBatch?.events.orEmpty()
            .mapNotNull { event -> event.toMatch()?.let { event.stableId to it } }
            .toMap()
        val matches = events.mapNotNull { event ->
            freshByStableId[event.stableId] ?: supplementalByStableId[event.stableId] ?: event.toMatch()
        }
        val failures = results.count { it.third } + if (BOXING_FEED in failedFeeds) 1 else 0
        SportLoungeDiscoverySnapshot(
            matches = SportLoungeRules.dedupe(matches),
            statusObservedAt = events.mapNotNull { e -> e.toMatch()?.let { it.key to e.lastSuccessfulUpdateMs } }.toMap(),
            updatedAt = events.maxOfOrNull { it.lastSuccessfulUpdateMs }
                ?: nowMs.takeIf { failures < jobs.size + if (supplemental != null) 1 else 0 },
            failedFeeds = failures,
            heroEditorialByMatchKey = editorialByKey(events),
        )
    }

    /** Refresh only live or imminently starting feeds. One caller owns this loop while Home is visible. */
    suspend fun refreshVisible(previous: SportLoungeDiscoverySnapshot, now: Long): SportLoungeDiscoverySnapshot = coroutineScope {
        val feeds = previous.matches.filter { it.isLive || it.startMs in now..(now + 60_000L) }.map { it.feed }.distinct()
        val gate = Semaphore(MAX_PARALLEL)
        val updates = feeds.mapNotNull(::discoveryLeague).map { league ->
            async {
                try {
                    gate.withPermit { source.scoreboard(league, Instant.ofEpochMilli(now).atZone(ZoneId.of("Pacific/Auckland")).toLocalDate()) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            }
        }.awaitAll().flatten()
        val byKey = updates.associateBy { it.key }
        previous.copy(
            matches = SportLoungeRules.dedupe(previous.matches.filterNot { it.key in byKey } + updates),
            statusObservedAt = previous.statusObservedAt + updates.associate { it.key to now },
            updatedAt = if (updates.isNotEmpty()) now else previous.updatedAt,
        )
    }

    private fun editorialByKey(events: List<SportEvent>) = events.mapNotNull { event ->
        val details = event.toEditorial() ?: return@mapNotNull null
        val key = event.toMatch()?.key ?: return@mapNotNull null
        key to details
    }.toMap()

    private companion object {
        const val TAG = "LoungeTV-SportLounge"
        const val SOURCE_ESPN = "espn"
        const val BOXING_FEED = "mma/boxing"
        const val MAX_PARALLEL = 3
        fun discoveryLeague(key: String): League? = SportsCatalog.league(key) ?: when (key) {
            "basketball/nbl" -> League(Sport.BASKETBALL, "nbl", "National Basketball League", "NBL", "AU")
            "soccer/aus.1" -> League(Sport.SOCCER, "aus.1", "Australian A-League Men", "A-League", "AU")
            else -> null
        }
    }
}
