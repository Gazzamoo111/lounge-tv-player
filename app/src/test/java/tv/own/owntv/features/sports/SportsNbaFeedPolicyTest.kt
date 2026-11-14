package tv.own.owntv.features.sports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport

class SportsNbaFeedPolicyTest {
    private fun match(sport: Sport, league: String) = Match(
        id = "test", sport = sport, feed = "basketball/$league",
        leagueSlug = league, leagueName = league, startMs = 1L,
        state = MatchState.IN, statusText = "In progress", title = "Lakers vs Warriors",
    )
    private fun option(tier: WatchTier) = WatchOption(
        ChannelEntity(id = 42L, sourceId = 1L, name = "Prime Video 1", streamUrl = "https://example.invalid/live.ts"),
        tier,
    )

    @Test fun nbaFixtureRejectsPrimeBrandAndGenericChannels() {
        assertFalse(nbaEventCandidateAllowed(match(Sport.BASKETBALL, "nba"), option(WatchTier.LIKELY)))
        assertFalse(nbaEventCandidateAllowed(match(Sport.BASKETBALL, "nba"), option(WatchTier.REMEMBERED)))
    }

    @Test fun nbaFixtureKeepsExactGameAndGuideVerifiedOptions() {
        assertTrue(nbaEventCandidateAllowed(match(Sport.BASKETBALL, "nba"), option(WatchTier.EVENT)))
        assertTrue(nbaEventCandidateAllowed(match(Sport.BASKETBALL, "nba"), option(WatchTier.GUIDE)))
    }

    @Test fun otherCompetitionsUnchanged() {
        assertTrue(nbaEventCandidateAllowed(match(Sport.FOOTBALL, "nfl"), option(WatchTier.LIKELY)))
        assertTrue(nbaEventCandidateAllowed(match(Sport.BASKETBALL, "wnba"), option(WatchTier.LIKELY)))
    }
}
