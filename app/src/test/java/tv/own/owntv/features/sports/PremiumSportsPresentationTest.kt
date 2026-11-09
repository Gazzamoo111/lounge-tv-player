package tv.own.owntv.features.sports

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import java.time.Instant
import java.time.LocalDate

class PremiumSportsPresentationTest {
    private val now = Instant.parse("2026-11-08T08:00:00Z").toEpochMilli()
    private fun match(id: String, state: MatchState = MatchState.PRE, start: Long = now + 60_000, feed: String = "soccer/eng.1") =
        Match(id, Sport.SOCCER, feed, feed.substringAfter('/'), "Competition", start, state, "", "Fixture")
    @Test fun `live precedes followed upcoming and cancelled or completed fixtures cannot become hero`() {
        val live = match("live", MatchState.IN, now - 60_000)
        val preferred = match("preferred", feed = "soccer/esp.1")
        val other = match("other")
        val results = PremiumSportsPresentation.ranked(listOf(other, match("post", MatchState.POST), preferred,
            match("cancelled", MatchState.CANCELLED), live, match("past", start = now - 1)), SportsConfig(leagues = listOf("soccer/esp.1")), now)
        assertEquals(listOf(live, preferred, other), results)
    }
    @Test fun `followed team changes hero ranking without changing source fixtures`() {
        val a = match("a")
        val b = match("b").copy(competitors = listOf(Competitor(Team("123", "Team"), true, null, null)))
        val config = SportsConfig(teams = listOf(FollowedTeam(Sport.SOCCER, "123", "Team")))
        assertEquals(b, PremiumSportsPresentation.ranked(listOf(a,b), config, now).first())
        assertFalse(PremiumSportsPresentation.follows(b, SportsConfig()))
    }
    @Test fun `calendar uses NZ day including daylight saving rather than UTC date`() {
        val lateUtc = match("nz", start = Instant.parse("2026-11-08T12:30:00Z").toEpochMilli())
        assertEquals(listOf(lateUtc), PremiumSportsPresentation.onDay(listOf(lateUtc), LocalDate.parse("2026-11-09"), null, emptySet()))
        assertTrue(PremiumSportsPresentation.onDay(listOf(lateUtc), LocalDate.parse("2026-11-08"), null, emptySet()).isEmpty())
    }
    @Test fun `NZ kickoff display ignores device US timezone and handles daylight saving`() {
        val november = Instant.parse("2026-11-10T00:00:00Z").toEpochMilli()
        val july = Instant.parse("2026-07-10T00:00:00Z").toEpochMilli()
        assertEquals("1:00 PM NZ", PremiumSportsPresentation.nzKickoffTime(november))
        assertEquals("12:00 PM NZ", PremiumSportsPresentation.nzKickoffTime(july))
        assertEquals("Tue 10 Nov", PremiumSportsPresentation.nzKickoffDay(november))
        assertEquals("Tue 10 Nov · 1:00 PM NZ", PremiumSportsPresentation.nzKickoffSummary(november))
    }
    @Test fun `sport and league filters cannot leak excluded events`() {
        val m = match("one")
        assertTrue(PremiumSportsPresentation.onDay(listOf(m), LocalDate.parse("2026-11-08"), Sport.CRICKET, emptySet()).isEmpty())
        assertTrue(PremiumSportsPresentation.onDay(listOf(m), LocalDate.parse("2026-11-08"), null, setOf("soccer/esp.1")).isEmpty())
    }
    @Test fun `carousel wraps safely when empty or reduced`() {
        assertEquals(0, PremiumSportsPresentation.carouselIndex(8, 1, 0))
        assertEquals(2, PremiumSportsPresentation.carouselIndex(0, -1, 3))
        assertEquals(0, PremiumSportsPresentation.carouselIndex(2, 1, 3))
    }
    @Test fun `moving an item preserves membership and respects boundaries`() {
        assertEquals(listOf("b", "a", "c"), PremiumSportsPresentation.move(listOf("a","b","c"), 1, -1))
        assertEquals(listOf("a"), PremiumSportsPresentation.move(listOf("a"), 0, -1))
        assertEquals(emptyList<String>(), PremiumSportsPresentation.move(emptyList<String>(), -1, 1))
    }
    @Test fun `competition preference does not assert selected fixture is playing`() {
        val a = match("a")
        val b = match("b")
        val channel = tv.own.owntv.core.database.entity.ChannelEntity(id = 1, sourceId = 1, name = "Channel", streamUrl = "https://example.invalid/live")
        val session = SportsSession(a, listOf(WatchOption(channel, WatchTier.REMEMBERED)))
        assertTrue(PremiumSportsPresentation.previewMatches(session, a, 1))
        assertFalse(PremiumSportsPresentation.previewMatches(session, b, 1))
        assertFalse(PremiumSportsPresentation.previewMatches(session, a.copy(startMs = a.startMs + 1), 1))
        assertFalse(PremiumSportsPresentation.previewMatches(session, a, 2))
        assertFalse(PremiumSportsPresentation.previewMatches(null, a, 1))
    }
    @Test fun `sports preference order and alert scope survive serialization separately for profiles`() {
        val one = SportsConfig(leagues = listOf("soccer/eng.1", "basketball/nba"), teams = listOf(FollowedTeam(Sport.SOCCER,"1","Team")), alerts = GoalAlerts(enabled = true))
        val moved = one.copy(leagues = PremiumSportsPresentation.move(one.leagues, 1, -1))
        assertEquals(moved, SportsConfig.fromJson(moved.toJson().toString()))
        assertEquals(listOf("soccer/eng.1", "basketball/nba"), one.leagues)
        assertEquals(SportsConfig(), SportsConfig.fromJson(null))
    }
    @Test fun `supplemental fixture cache keeps source team colours and accepts older records`() {
        val team = Team("1", "TEST team", color = "E31837")
        val m = match("colour").copy(competitors = listOf(Competitor(team, true, null, null)))
        val event = tv.own.owntv.features.sports.data.SportEvent.fromMatch(m, "test", now)
        val json = event.toJson()
        val reloaded = tv.own.owntv.features.sports.data.SportEvent.fromJson(json)!!.toMatch()!!
        assertEquals(team.color, reloaded.competitors.single().team.color)
        json.getJSONArray("competitors").getJSONObject(0).remove("color")
        assertNull(tv.own.owntv.features.sports.data.SportEvent.fromJson(json)!!.toMatch()!!.competitors.single().team.color)
    }

    @Test fun `direct preview requires exactly one fixture-specific candidate`() {
        val channel = tv.own.owntv.core.database.entity.ChannelEntity(id = 1, sourceId = 1, name = "Channel", streamUrl = "https://example.invalid/live")
        val guide = WatchOption(channel, WatchTier.GUIDE)
        val event = WatchOption(channel, WatchTier.EVENT)
        assertEquals(guide, directSportsPreviewChoice(listOf(guide)))
        assertEquals(event, directSportsPreviewChoice(listOf(event)))
        assertNull(directSportsPreviewChoice(emptyList()))
        assertNull(directSportsPreviewChoice(listOf(guide, event.copy(channel = channel.copy(id = 2)))))
        assertNull(directSportsPreviewChoice(listOf(guide.copy(tier = WatchTier.REMEMBERED))))
        assertNull(directSportsPreviewChoice(listOf(guide.copy(tier = WatchTier.LIKELY))))
    }

}
