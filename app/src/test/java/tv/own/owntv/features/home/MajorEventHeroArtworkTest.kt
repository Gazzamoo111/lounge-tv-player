package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport

class MajorEventHeroArtworkTest {
    private fun event(sport: Sport, league: String, title: String = league, feed: String = "test") =
        Match(
            id = "1", sport = sport, feed = feed, leagueSlug = league.lowercase().replace(" ", "-"),
            leagueName = league, startMs = 1_800_000_000_000L, state = MatchState.PRE,
            statusText = "Upcoming", title = title,
        )

    @Test fun boxingGetsDedicatedArtwork() {
        assertEquals(HeroArtworkKind.BOXING,
            heroArtworkKind(event(Sport.MMA, "Boxing", "Paul Gallen vs Nelson Asofa-Solomona", "mma/boxing")))
    }

    @Test fun ufcAndBareknuckleStayOnMmaArtwork() {
        assertEquals(HeroArtworkKind.MMA, heroArtworkKind(event(Sport.MMA, "UFC 335")))
        assertEquals(HeroArtworkKind.MMA, heroArtworkKind(event(Sport.MMA, "BKFC", "Bare Knuckle Main Event")))
    }

    @Test fun wweGetsDedicatedArtwork() {
        assertEquals(HeroArtworkKind.WWE,
            heroArtworkKind(event(Sport.MMA, "WWE", "WWE Survivor Series")))
    }

    @Test fun cricketAndRugbyCodesRemainDistinct() {
        assertEquals(HeroArtworkKind.CRICKET, heroArtworkKind(event(Sport.CRICKET, "ICC Cricket")))
        assertEquals(HeroArtworkKind.RUGBY, heroArtworkKind(event(Sport.RUGBY, "International Rugby")))
        assertEquals(HeroArtworkKind.RUGBY_LEAGUE, heroArtworkKind(event(Sport.RUGBY_LEAGUE, "Pacific Championships")))
        assertEquals(HeroArtworkKind.TENNIS, heroArtworkKind(event(Sport.TENNIS, "ATP Masters")))
        assertEquals(HeroArtworkKind.GOLF, heroArtworkKind(event(Sport.GOLF, "PGA Tour")))
        assertEquals(HeroArtworkKind.BASEBALL, heroArtworkKind(event(Sport.BASEBALL, "MLB")))
        assertEquals(HeroArtworkKind.HOCKEY, heroArtworkKind(event(Sport.HOCKEY, "NHL")))
    }
}
