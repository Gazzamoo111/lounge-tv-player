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
    private fun match(sport: Sport = Sport.BASKETBALL, league: String = "nba") = Match(
        id = "test", sport = sport, feed = "basketball/$league",
        leagueSlug = league, leagueName = league, startMs = 1L,
        state = MatchState.IN, statusText = "In progress", title = "Lakers vs Warriors",
    )
    private fun option(name: String, tier: WatchTier = WatchTier.LIKELY) = WatchOption(
        ChannelEntity(id = 42L, sourceId = 1L, name = name, streamUrl = "https://example.invalid/live.ts"),
        tier,
    )

    @Test fun originalNbaTeamFeedsRemainSelectable() {
        assertTrue(nbaEventCandidateAllowed(match(), option("NBA TEAM | LOS ANGELES LAKERS")))
        assertTrue(nbaEventCandidateAllowed(match(), option("NBA TEAM | GOLDEN STATE WARRIORS")))
        assertTrue(nbaEventCandidateAllowed(match(), option("NBA Feed 08")))
        assertTrue(nbaEventCandidateAllowed(match(), option("NBA Feed 15")))
        assertTrue(nbaEventCandidateAllowed(match(), option("NBA TV")))
    }

    @Test fun unrelatedPrimeAndGenericChannelsDoNotReturnAsNbaGames() {
        assertFalse(nbaEventCandidateAllowed(match(), option("Prime Video 1")))
        assertFalse(nbaEventCandidateAllowed(match(), option("PRIME | NBA TV RAW")))
        assertFalse(nbaEventCandidateAllowed(match(), option("FOX Sports News")))
        assertFalse(nbaEventCandidateAllowed(match(), option("Prime Video 2", WatchTier.REMEMBERED)))
    }

    @Test fun guideAndEventEvidenceRemainVisible() {
        assertTrue(nbaEventCandidateAllowed(match(), option("Actual matchup", WatchTier.EVENT)))
        assertTrue(nbaEventCandidateAllowed(match(), option("NBA fixture feed", WatchTier.GUIDE)))
    }

    @Test fun otherSportsUntouched() {
        assertTrue(nbaEventCandidateAllowed(match(Sport.FOOTBALL, "nfl"), option("Prime Video 1")))
        assertTrue(nbaEventCandidateAllowed(match(Sport.BASKETBALL, "wnba"), option("Prime Video 1")))
    }
}
