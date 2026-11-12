package tv.own.owntv.features.home

import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.features.sports.SportLoungeRules
import tv.own.owntv.features.sports.PremiumSportsPresentation

/** Fixture-only ranking. Inclusion is never evidence of an available stream. */
internal object HomeSportsSelection {
    private val major = setOf("nfl", "nba", "nhl", "mlb", "uefa.champions", "eng.1", "ufc", "f1", "nrl", "super-rugby", "rugby-world-cup")
    private fun important(match: Match): Boolean = match.sport in setOf(Sport.RUGBY, Sport.RUGBY_LEAGUE, Sport.CRICKET, Sport.MMA, Sport.RACING) || match.leagueSlug.lowercase() in major ||
        match.feed.substringAfter('/').lowercase() in major
    fun ranked(matches: List<Match>, config: SportsConfig, now: Long): List<Match> =
        SportLoungeRules.dedupe(matches).filter {
            (it.isLive && it.startMs in (now - 12 * 60 * 60_000L)..now) ||
                (it.state == MatchState.PRE && it.startMs in now..(now + 72 * 60 * 60_000L))
        }.sortedWith(compareBy<Match>({ !it.isLive }, { !important(it) },
            { !PremiumSportsPresentation.follows(it, config) }, { it.startMs }, { it.key })).take(12)

    /**
     * Home's live rail is fixtures only, never guessed from channel titles.
     * Mix sports in a stable round-robin order so one league cannot occupy every tile.
     * A tile resolves its actual available feed on OK; never silently opens Sport Lounge.
     */
    fun liveNow(matches: List<Match>, now: Long, limit: Int = 10): List<Match> {
        if (limit <= 0) return emptyList()
        val groups = SportLoungeRules.dedupe(matches)
            .filter { it.isLive && it.startMs <= now && it.startMs >= now - 18 * 60 * 60_000L }
            .groupBy { it.sport }
            .values
            .map { group -> group.sortedWith(compareBy<Match>({ it.startMs }, { it.key })) }
            .sortedWith(compareBy({ it.first().startMs }, { it.first().sport.name }))
        val mixed = ArrayList<Match>(limit)
        var index = 0
        while (mixed.size < limit && groups.any { index < it.size }) {
            for (group in groups) {
                group.getOrNull(index)?.let { mixed.add(it) }
                if (mixed.size == limit) break
            }
            index++
        }
        return mixed
    }
}
