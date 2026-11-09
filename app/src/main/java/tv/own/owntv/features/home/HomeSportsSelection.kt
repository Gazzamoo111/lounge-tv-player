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
}
