package tv.own.owntv.features.sports

import java.util.Locale
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.Sport

/** Only the fixture's NBA team feeds, plus unverified numbered NBA fallbacks. */
internal fun nbaEventCandidateAllowed(match: Match, option: WatchOption): Boolean {
    if (match.sport != Sport.BASKETBALL || !match.leagueSlug.equals("nba", ignoreCase = true)) return true
    if (option.tier == WatchTier.GUIDE || option.tier == WatchTier.EVENT) return true
    val name = option.channel.name.trim().uppercase(Locale.ROOT)
    if (name.startsWith("NBA FEED")) return true
    if (!name.startsWith("NBA TEAM")) return false
    return listOfNotNull(match.home?.team, match.away?.team).any { team ->
        sequenceOf(team.name, team.shortName)
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.length >= 4 }
            .any { alias -> name.contains(alias) }
    }
}
