package tv.own.owntv.features.sports

import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.Sport

/**
 * NBA fixtures only: a broadcaster brand, NBA TV, generic numbered slot or
 * remembered feed does not prove this fixture is on that stream.
 * Keep exact fixture labels and programme-guide evidence; the rest must not
 * be displayed as watchable game options.
 */
internal fun nbaEventCandidateAllowed(match: Match, option: WatchOption): Boolean {
    if (match.sport != Sport.BASKETBALL || !match.leagueSlug.equals("nba", ignoreCase = true)) return true
    return option.tier == WatchTier.GUIDE || option.tier == WatchTier.EVENT
}
