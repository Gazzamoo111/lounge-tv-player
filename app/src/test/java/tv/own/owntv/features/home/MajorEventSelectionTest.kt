package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport

class MajorEventSelectionTest {
    private val now = 1_790_000_000_000L

    private fun fixture(id: String, sport: Sport, league: String, start: Long = now + 86_400_000L, state: MatchState = MatchState.PRE) =
        Match(id = id, sport = sport, feed = "test", leagueSlug = "test", leagueName = league,
            startMs = start, state = state, statusText = "", title = league, competitors = emptyList())

    @Test fun headlineSportsGetVariety() {
        val events = (1..10).map { fixture("nba$it", Sport.BASKETBALL, "NBA") } +
            fixture("ufc", Sport.MMA, "UFC championship") +
            fixture("rugby", Sport.RUGBY, "All Blacks international")
        val chosen = MajorEventSelection.select(events, now, 4)
        assertEquals(4, chosen.size)
        assertTrue(chosen.any { it.sport == Sport.MMA })
        assertTrue(chosen.any { it.sport == Sport.RUGBY })
        assertEquals(2, chosen.count { it.sport == Sport.BASKETBALL })
    }

    @Test fun eventsBeyondSevenDaysAreNotFeatured() {
        val tomorrow = fixture("tomorrow", Sport.RUGBY, "All Blacks Test", now + 86_400_000L)
        val beyond = fixture("beyond", Sport.MMA, "UFC 335", now + 8 * 86_400_000L)
        assertEquals(listOf("tomorrow"), MajorEventSelection.select(listOf(tomorrow, beyond), now).map { it.id })
    }

    @Test fun noPastOrPostponedEvents() {
        val events = listOf(
            fixture("past", Sport.RUGBY, "Test", now - 1_000L),
            fixture("cancelled", Sport.MMA, "UFC", state = MatchState.CANCELLED),
            fixture("future", Sport.CRICKET, "World Cup")
        )
        assertEquals(listOf("future"), MajorEventSelection.select(events, now).map { it.id })
    }
    @Test fun fillsAtLeastTenWhenHeadlinePoolHasTen() {
        val events = (1..14).map { fixture("nba$it", Sport.BASKETBALL, "NBA") }
        val chosen = MajorEventSelection.select(events, now, 12)
        assertEquals(12, chosen.size)
        assertTrue(chosen.all { it.sport == Sport.BASKETBALL })
    }

    @Test fun regionalAndSecondaryForeignFixturesStayOffHome() {
        val events = listOf(
            fixture("regional-cricket", Sport.CRICKET, "Quaid-e-Azam Trophy Gold 2026/27"),
            fixture("secondary-ucl", Sport.SOCCER, "UEFA Champions League Sporting CP at Shakhtar Donetsk"),
            fixture("nba", Sport.BASKETBALL, "NBA"),
            fixture("boxing", Sport.MMA, "Gallen title fight")
        )
        val ids = MajorEventSelection.select(events, now, 12).map { it.id }
        assertTrue("regional cricket leaked into Home", "regional-cricket" !in ids)
        assertTrue("secondary foreign UCL game leaked into Home", "secondary-ucl" !in ids)
        assertTrue("NBA should remain eligible", "nba" in ids)
        assertTrue("headline boxing should remain eligible", "boxing" in ids)
    }

}
