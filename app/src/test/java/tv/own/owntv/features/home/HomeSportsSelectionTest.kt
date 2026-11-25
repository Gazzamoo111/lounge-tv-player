package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.model.*

class HomeSportsSelectionTest {
    private val now = 1800000000000L
    private fun event(id: String, state: MatchState = MatchState.PRE, start: Long = now + 60000, slug: String = "nba") =
        Match(id, Sport.BASKETBALL, "basketball/$slug", slug, "TEST competition", start, state, "", "TEST $id")
    @Test fun `real live and upcoming sport precedes fallback and ended or stale fixtures cannot enter hero`() {
        val live = event("live", MatchState.IN, now - 60000)
        val upcoming = event("soon")
        val ranked = HomeSportsSelection.ranked(listOf(upcoming, live, live,
            event("post", MatchState.POST), event("cancelled", MatchState.CANCELLED),
            event("past", start = now - 1), event("stale-live", MatchState.IN, now - 13 * 3600000),
            event("distant", start = now + 73 * 3600000)), SportsConfig(), now)
        assertEquals(listOf(live, upcoming), ranked)
        assertTrue(HomeSportsSelection.ranked(emptyList(), SportsConfig(), now).isEmpty())
    }
    @Test fun `major events then followed competitions are preferred without inventing feed availability`() {
        val other = event("other", slug = "minor")
        val followed = event("followed", slug = "wnba")
        val major = event("major")
        assertEquals(listOf(major, followed, other), HomeSportsSelection.ranked(listOf(other, followed, major),
            SportsConfig(leagues = listOf("basketball/wnba")), now))
    }
    @Test fun `live Home shelf excludes upcoming and ended events and mixes sports`() {
        val nba1 = event("nba1", MatchState.IN, now - 120000)
        val nba2 = event("nba2", MatchState.IN, now - 180000)
        val rugby = Match("rugby", Sport.RUGBY, "rugby/super-rugby", "super-rugby", "Rugby",
            now - 60000, MatchState.IN, "", "Test rugby")
        val result = HomeSportsSelection.liveNow(listOf(nba1, nba2, rugby, nba1,
            event("soon"), event("ended", MatchState.POST), event("old", MatchState.IN, now - 19 * 3600000L)), now)
        assertEquals(listOf(nba2, rugby, nba1).toSet(), result.toSet())
        assertEquals(3, result.size)
        assertNotEquals(result[0].sport, result[1].sport)
        assertEquals(2, HomeSportsSelection.liveNow(result, now, limit = 2).size)
        assertTrue(HomeSportsSelection.liveNow(emptyList(), now).isEmpty())
    }
}
