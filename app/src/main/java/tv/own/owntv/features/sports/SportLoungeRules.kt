package tv.own.owntv.features.sports

import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Mutually exclusive time views used by Sport Lounge. */
enum class SportLoungeTimeFilter { LIVE_NOW, TODAY, UPCOMING }

/**
 * Customer-facing categories. American football and association football intentionally have
 * different categories: calling both "football" made the old hub ambiguous in New Zealand.
 */
enum class SportLoungeCategory(val sports: Set<Sport>) {
    ALL(Sport.entries.toSet()),
    NFL(setOf(Sport.FOOTBALL)),
    LEAGUE(setOf(Sport.RUGBY_LEAGUE)),
    RUGBY(setOf(Sport.RUGBY)),
    CRICKET(setOf(Sport.CRICKET)),
    BASKETBALL(setOf(Sport.BASKETBALL)),
    FOOTBALL(setOf(Sport.SOCCER)),
    COMBAT(setOf(Sport.MMA)),
    MOTORSPORT(setOf(Sport.RACING)),
    TENNIS(setOf(Sport.TENNIS)),
    HOCKEY(setOf(Sport.HOCKEY)),
    BASEBALL(setOf(Sport.BASEBALL)),
    GOLF(setOf(Sport.GOLF)),
    AFL(setOf(Sport.AUSSIE_RULES)),
    VOLLEYBALL(setOf(Sport.VOLLEYBALL)),
    LACROSSE(setOf(Sport.LACROSSE)),
}

data class SportLoungeCounts(val liveNow: Int, val today: Int, val upcoming: Int)

data class SportLoungeSelection(
    val matches: List<Match>,
    val counts: SportLoungeCounts,
    val categories: List<SportLoungeCategory>,
)

/** Pure, clock-injected selection rules so long-running TV screens never freeze their status. */
object SportLoungeRules {
    private val primaryCategories = listOf(
        SportLoungeCategory.ALL,
        SportLoungeCategory.NFL,
        SportLoungeCategory.LEAGUE,
        SportLoungeCategory.RUGBY,
        SportLoungeCategory.CRICKET,
        SportLoungeCategory.BASKETBALL,
        SportLoungeCategory.FOOTBALL,
        SportLoungeCategory.COMBAT,
        SportLoungeCategory.MOTORSPORT,
    )

    fun select(
        source: List<Match>,
        timeFilter: SportLoungeTimeFilter,
        category: SportLoungeCategory,
        nowMs: Long,
        zoneId: ZoneId,
        followedTeamKeys: Set<String> = emptySet(),
        myTeamsOnly: Boolean = false,
    ): SportLoungeSelection {
        val unique = dedupe(source)
        val actualSports = unique.mapTo(linkedSetOf()) { it.sport }
        val additional = SportLoungeCategory.entries
            .filter { it !in primaryCategories && it.sports.any(actualSports::contains) }
        val categoryMatches = unique.asSequence()
            .filter { category == SportLoungeCategory.ALL || it.sport in category.sports }
            .filter { !myTeamsOnly || it.competitors.any { side -> "${it.sport.path}/${side.team.id}" in followedTeamKeys } }
            .toList()
        val counts = counts(categoryMatches, nowMs, zoneId)
        val filtered = categoryMatches.asSequence()
            .filter { belongsTo(it, timeFilter, nowMs, zoneId) }
            .sortedWith(matchComparator(nowMs))
            .toList()
        return SportLoungeSelection(filtered, counts, primaryCategories + additional)
    }

    fun counts(matches: List<Match>, nowMs: Long, zoneId: ZoneId): SportLoungeCounts {
        val unique = dedupe(matches)
        return SportLoungeCounts(
            liveNow = unique.count { belongsTo(it, SportLoungeTimeFilter.LIVE_NOW, nowMs, zoneId) },
            today = unique.count { belongsTo(it, SportLoungeTimeFilter.TODAY, nowMs, zoneId) },
            upcoming = unique.count { belongsTo(it, SportLoungeTimeFilter.UPCOMING, nowMs, zoneId) },
        )
    }

    fun belongsTo(match: Match, filter: SportLoungeTimeFilter, nowMs: Long, zoneId: ZoneId): Boolean {
        val localToday = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val localStart = localDate(match.startMs, zoneId)
        return when (filter) {
            SportLoungeTimeFilter.LIVE_NOW -> match.state == MatchState.IN
            SportLoungeTimeFilter.TODAY -> match.state != MatchState.IN && localStart == localToday
            SportLoungeTimeFilter.UPCOMING ->
                match.state == MatchState.PRE && match.startMs > nowMs && localStart.isAfter(localToday)
        }
    }

    fun localDate(epochMs: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()

    /**
     * Match keys are provider-stable. A later lifecycle state wins an out-of-order duplicate; ties
     * prefer the richer copy rather than fuzzy-merging similarly named, potentially different games.
     */
    fun dedupe(matches: List<Match>): List<Match> = matches
        .groupBy { it.key }
        .values
        .map { copies ->
            copies.maxWithOrNull(
                compareBy<Match>({ stateRank(it.state) }, { it.events.size }, { it.competitors.count { side -> side.score != null } }),
            )!!
        }

    fun matchComparator(nowMs: Long): Comparator<Match> = compareBy<Match>(
        { if (it.state == MatchState.IN) 0 else 1 },
        { if (isNzAuPriority(it)) 0 else 1 },
        { if (it.startMs >= nowMs) it.startMs else Long.MAX_VALUE - it.startMs },
        { it.key },
    )

    private fun stateRank(state: MatchState): Int = when (state) {
        MatchState.PRE -> 0
        MatchState.IN -> 1
        MatchState.POST -> 2
        MatchState.POSTPONED -> 3
        MatchState.CANCELLED -> 4
    }

    private fun isNzAuPriority(match: Match): Boolean {
        val text = buildString {
            append(match.feed); append(' '); append(match.leagueName); append(' '); append(match.title)
            match.competitors.forEach { append(' '); append(it.team.name); append(' '); append(it.team.location.orEmpty()) }
        }.lowercase()
        return match.sport in setOf(Sport.RUGBY, Sport.RUGBY_LEAGUE, Sport.AUSSIE_RULES) ||
            NZ_AU_HINTS.any(text::contains)
    }

    private val NZ_AU_HINTS = listOf(
        "new zealand", " nz ", "nrl", "super rugby", "australia", "australian",
        "breakers", "black caps", "silver ferns",
    )
}
