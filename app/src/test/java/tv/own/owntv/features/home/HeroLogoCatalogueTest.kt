package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport

class HeroLogoCatalogueTest {
    private fun event(sport: Sport, league: String, feed: String = "test") = Match(
        id = "logo-test",
        sport = sport,
        feed = feed,
        leagueSlug = league.lowercase().replace(" ", "-"),
        leagueName = league,
        startMs = 1_800_000_000_000L,
        state = MatchState.PRE,
        statusText = "Upcoming",
        title = league,
    )

    @Test fun rugbyLeagueWorldCupTeamsResolveByNameNotEspnId() {
        val e = event(Sport.RUGBY_LEAGUE, "Rugby League World Cup")
        assertTrue(HeroLogoCatalogue.resolve(e, "Australia", "Australia", "", "", null)!!.contains("h0dp9w1768825327"))
        assertTrue(HeroLogoCatalogue.resolve(e, "New Zealand", "Kiwis", "", "", null)!!.contains("t1svbv1768825413"))
        assertTrue(HeroLogoCatalogue.resolve(e, "Fiji Bati", "Fiji", "", "", null)!!.contains("mclqci1768825350"))
        assertTrue(HeroLogoCatalogue.resolve(e, "Toa Samoa", "Samoa", "", "", null)!!.contains("dcgnql1768825433"))
        assertTrue(HeroLogoCatalogue.resolve(e, "Mate Ma'a Tonga", "Tonga", "", "", null)!!.contains("lcu2zg1768825449"))
    }

    @Test fun uploadedManifestAliasesResolveNbaFixtures() {
        val e = event(Sport.BASKETBALL, "National Basketball Association")
        assertEquals(
            "https://a.espncdn.com/i/teamlogos/nba/500/gs.png",
            HeroLogoCatalogue.resolve(e, "Golden State Warriors", "Warriors", "GS", "", null),
        )
        assertEquals(
            "https://a.espncdn.com/i/teamlogos/nba/500/mia.png",
            HeroLogoCatalogue.resolve(e, "Miami Heat", "Heat", "MIA", "", null),
        )
    }

    @Test fun rugbyLeagueWorldCupGetsCompetitionBadgeFallback() {
        val e = event(Sport.RUGBY_LEAGUE, "Rugby League World Cup")
        assertEquals(
            "https://r2.thesportsdb.com/images/media/league/badge/sxoc6h1768739073.png",
            HeroLogoCatalogue.competition(e, null),
        )
    }

    @Test fun nrlClubStillHasStable500pxFallback() {
        val e = event(Sport.RUGBY_LEAGUE, "NRL")
        assertEquals(
            "https://a.espncdn.com/i/teamlogos/rugby/teams/500/289195.png",
            HeroLogoCatalogue.resolve(e, "Brisbane Broncos", "Broncos", "BRI", "289195", null),
        )
    }
}
