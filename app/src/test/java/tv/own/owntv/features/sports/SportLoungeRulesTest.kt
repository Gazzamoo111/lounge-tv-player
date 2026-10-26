package tv.own.owntv.features.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import java.time.Instant
import java.time.ZoneId

class SportLoungeRulesTest {
    private val nz = ZoneId.of("Pacific/Auckland")
    private val now = Instant.parse("2026-10-25T22:30:00Z").toEpochMilli() // NZ Monday 11:30

    @Test fun `american sunday fixture is Monday in New Zealand`() {
        val sundayUs = match("nfl", Sport.FOOTBALL, "2026-10-25T23:00:00Z")
        assertEquals("2026-10-26", SportLoungeRules.localDate(sundayUs.startMs, nz).toString())
        assertTrue(SportLoungeRules.belongsTo(sundayUs, SportLoungeTimeFilter.TODAY, now, nz))
    }

    @Test fun `DST transition uses local calendar day`() {
        val beforeJump = Instant.parse("2026-09-26T13:30:00Z").toEpochMilli()
        val afterJump = Instant.parse("2026-09-26T14:30:00Z").toEpochMilli()
        assertEquals("2026-09-27", SportLoungeRules.localDate(beforeJump, nz).toString())
        assertEquals("2026-09-27", SportLoungeRules.localDate(afterJump, nz).toString())
    }

    @Test fun `filters are mutually exclusive and counts use displayed data`() {
        val live = match("live", Sport.RUGBY, "2026-10-25T22:00:00Z", MatchState.IN)
        val today = match("today", Sport.CRICKET, "2026-10-26T05:00:00Z")
        val tomorrow = match("tomorrow", Sport.BASKETBALL, "2026-10-26T22:00:00Z")
        val counts = SportLoungeRules.counts(listOf(live, today, tomorrow), now, nz)
        assertEquals(SportLoungeCounts(1, 1, 1), counts)
    }

    @Test fun `dedupe keeps advanced state and does not fuzzy merge names`() {
        val pre = match("same", Sport.RUGBY, "2026-10-26T05:00:00Z")
        val live = pre.copy(state = MatchState.IN)
        val other = match("other", Sport.RUGBY, "2026-10-26T05:00:00Z").copy(title = pre.title)
        val result = SportLoungeRules.dedupe(listOf(pre, live, other))
        assertEquals(2, result.size)
        assertEquals(MatchState.IN, result.first { it.id == "same" }.state)
    }

    @Test fun `sport category and my teams filters are functional`() {
        val nfl = match("nfl", Sport.FOOTBALL, "2026-10-26T05:00:00Z")
        val rugby = match("rugby", Sport.RUGBY, "2026-10-26T06:00:00Z")
        val nflOnly = SportLoungeRules.select(listOf(nfl, rugby), SportLoungeTimeFilter.TODAY, SportLoungeCategory.NFL, now, nz)
        assertEquals(listOf("nfl"), nflOnly.matches.map { it.id })
        val noneMine = SportLoungeRules.select(listOf(nfl), SportLoungeTimeFilter.TODAY, SportLoungeCategory.ALL, now, nz, myTeamsOnly = true)
        assertTrue(noneMine.matches.isEmpty())
        assertFalse(nflOnly.categories.isEmpty())
    }

    @Test fun `sort favours live then NZ relevance then kickoff`() {
        val generic = match("generic", Sport.BASKETBALL, "2026-10-26T04:00:00Z")
        val nz = match("nz", Sport.RUGBY, "2026-10-26T06:00:00Z")
        val live = match("live", Sport.FOOTBALL, "2026-10-26T07:00:00Z", MatchState.IN)
        assertEquals(listOf("live", "nz", "generic"), listOf(generic, nz, live).sortedWith(SportLoungeRules.matchComparator(now)).map { it.id })
    }

    private fun match(id: String, sport: Sport, start: String, state: MatchState = MatchState.PRE) = Match(
        id = id,
        sport = sport,
        feed = "${sport.path}/test",
        leagueSlug = "test",
        leagueName = "Test League",
        startMs = Instant.parse(start).toEpochMilli(),
        state = state,
        statusText = state.name,
        title = "Team A v Team B",
    )
}
