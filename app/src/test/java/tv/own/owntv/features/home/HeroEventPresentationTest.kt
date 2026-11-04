package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport

class HeroEventPresentationTest {
    private fun event(
        sport: Sport,
        league: String,
        title: String = league,
        subtitle: String? = null,
        feed: String = "test",
    ) = Match(
        id = "presentation-test",
        sport = sport,
        feed = feed,
        leagueSlug = league.lowercase().replace(" ", "-"),
        leagueName = league,
        startMs = 1_800_000_000_000L,
        state = MatchState.PRE,
        statusText = "Upcoming",
        title = title,
        subtitle = subtitle,
    )

    @Test fun rugbyLeagueWorldCupSeparatesWomenAndMen() {
        assertEquals(
            HeroDivision.WOMEN,
            heroDivision(event(Sport.RUGBY_LEAGUE, "Women's Rugby League World Cup", "Women's Semi-Final")),
        )
        assertEquals(
            HeroDivision.MEN,
            heroDivision(event(Sport.RUGBY_LEAGUE, "Rugby League World Cup", "Semi-Final")),
        )
    }

    @Test fun tennisAndGolfUseProviderGenderSignals() {
        assertEquals(HeroDivision.WOMEN, heroDivision(event(Sport.TENNIS, "WTA Finals")))
        assertEquals(HeroDivision.MEN, heroDivision(event(Sport.TENNIS, "ATP Masters")))
        assertEquals(HeroDivision.WOMEN, heroDivision(event(Sport.GOLF, "LPGA Tour")))
        assertNull(heroDivision(event(Sport.GOLF, "The Open Championship")))
    }

    @Test fun individualSportsUseCompetitionIdentityInsteadOfParticipantBadges() {
        assertTrue(usesCompetitionIdentityOnly(event(Sport.TENNIS, "Wimbledon")))
        assertTrue(usesCompetitionIdentityOnly(event(Sport.GOLF, "The Masters")))
        assertTrue(usesCompetitionIdentityOnly(event(Sport.RACING, "Formula 1")))
        assertTrue(usesCompetitionIdentityOnly(event(Sport.MMA, "UFC 335")))
        assertFalse(usesCompetitionIdentityOnly(event(Sport.RUGBY_LEAGUE, "Rugby League World Cup")))
        assertFalse(usesCompetitionIdentityOnly(event(Sport.BASKETBALL, "NBA")))
    }
}
