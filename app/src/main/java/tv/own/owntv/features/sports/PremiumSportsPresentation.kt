package tv.own.owntv.features.sports

import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class SportsDestination { MY_SPORTS, ALL_EVENTS, TABLES, NETWORKS }

/** Ordering/filtering only actual board/discovery rows. No synthetic fixtures or scores. */
internal object PremiumSportsPresentation {
    val zone: ZoneId = ZoneId.of("Pacific/Auckland")
    fun follows(m: Match, c: SportsConfig): Boolean {
        val teams = c.teams.mapTo(HashSet()) { it.key }
        return m.feed in c.followedLeagues() || m.competitors.any { FollowedTeam(m.sport, it.team.id, it.team.name).key in teams }
    }
    fun ranked(matches: List<Match>, c: SportsConfig, now: Long): List<Match> =
        SportLoungeRules.dedupe(matches).filter { it.isLive || it.state == MatchState.PRE && it.startMs >= now }
            .sortedWith(compareBy<Match>({ !it.isLive }, { !follows(it, c) },
                { c.rankOf(it.sport, it.feed).first }, { c.rankOf(it.sport, it.feed).second }, { it.startMs }, { it.key }))
    fun onDay(matches: List<Match>, date: LocalDate, sport: Sport?, leagues: Set<String>): List<Match> =
        matches.filter { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == date &&
            (sport == null || it.sport == sport) && (leagues.isEmpty() || it.feed in leagues) }
            .sortedWith(compareBy<Match>({ when(it.state) { MatchState.IN -> 0; MatchState.PRE -> 1; MatchState.POST -> 2; else -> 3 } }, { it.startMs }, { it.key }))
    fun carouselIndex(current: Int, delta: Int, size: Int): Int = if (size == 0) 0 else (current + delta).mod(size)
    fun <T> move(items: List<T>, from: Int, delta: Int): List<T> {
        val to = from + delta
        if (from !in items.indices || to !in items.indices) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }
    // A competition preference is not verification of a fixture's video.
    fun previewMatches(session: SportsSession?, selected: Match?, playingId: Long?): Boolean =
        selected != null && session?.match?.key == selected.key && session.match.startMs == selected.startMs &&
            session.options.any { it.channel.id == playingId }
}
