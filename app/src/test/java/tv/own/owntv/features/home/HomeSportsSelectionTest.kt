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
}
