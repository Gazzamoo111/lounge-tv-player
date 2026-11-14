package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.ui.lounge.LoungePremiumTokens

class LoungeCinematicSportsHeroTest {
    private fun fixture(state: MatchState, startMs: Long, subtitle: String? = null) = Match(
        id = "event-1",
        sport = Sport.RUGBY,
        feed = "rugby/super-rugby",
        leagueSlug = "super-rugby",
        leagueName = "Super Rugby",
        startMs = startMs,
        state = state,
        statusText = state.name,
        title = "Team A v Team B",
        subtitle = subtitle,
    )

    @Test fun aFutureFixtureNeverClaimsLiveEvenIfTheProviderStateIsIn() {
        assertFalse(canShowHomeSportLive(fixture(MatchState.IN, 2_000L), 1_000L))
    }

    @Test fun onlyAnActualInPlayFixtureIsLive() {
        assertTrue(canShowHomeSportLive(fixture(MatchState.IN, 1_000L), 2_000L))
        assertFalse(canShowHomeSportLive(fixture(MatchState.PRE, 1_000L), 2_000L))
        assertFalse(canShowHomeSportLive(fixture(MatchState.POST, 1_000L), 2_000L))
    }

    @Test fun upcomingIsNotConfusedWithAnOverdueScheduledFixture() {
        assertTrue(canShowHomeSportUpcoming(fixture(MatchState.PRE, 2_000L), 1_000L))
        assertFalse(canShowHomeSportUpcoming(fixture(MatchState.PRE, 1_000L), 2_000L))
    }

    @Test fun theSynopsisIsNeverInvented() {
        assertNull(verifiedHomeSportSynopsis(fixture(MatchState.PRE, 2_000L)))
        assertNull(verifiedHomeSportSynopsis(fixture(MatchState.PRE, 2_000L, "Team A v Team B")))
        assertEquals("Round 12", verifiedHomeSportSynopsis(fixture(MatchState.PRE, 2_000L, " Round 12 ")))
    }

    @Test fun sportsGetDistinctFallbackPalettesWithoutASecondNetworkRequest() {
        assertFalse(homeSportBackdropColor(Sport.RUGBY) == homeSportBackdropColor(Sport.BASKETBALL))
        assertEquals(LoungePremiumTokens.Elevated, homeSportBackdropColor(Sport.LACROSSE))
    }
}
