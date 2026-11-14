package tv.own.owntv.features.sports

import java.util.Locale
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.Sport

/**
 * Preserve the original playable NBA TEAM package feeds and numbered NBA
 * sources. Those feeds are useful manual alternatives even when the provider's
 * schedule/EPG is unavailable. Never describe them as a verified matchup.
 * Exclude unrelated Prime/entertainment sources unless the fixture is confirmed.
 */
internal fun nbaEventCandidateAllowed(match: Match, option: WatchOption): Boolean {
    if (match.sport != Sport.BASKETBALL || !match.leagueSlug.equals("nba", ignoreCase = true)) return true
    if (option.tier == WatchTier.GUIDE || option.tier == WatchTier.EVENT) return true
    val name = option.channel.name.trim().uppercase(Locale.ROOT)
    return name.startsWith("NBA TEAM") ||
        name.startsWith("NBA FEED") ||
        name == "NBA TV" ||
        name.startsWith("NBA FAST CHANNEL") ||
        name.startsWith("SPORTSNET LA LAKERS")
}
