package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.features.sports.data.SportEventEditorial
import java.time.Instant

class MajorEventEditorialTest {
    private fun boxing(title: String, date: String) = Match(
        id = "b1", sport = Sport.MMA, feed = "mma/boxing",
        leagueSlug = "boxing", leagueName = "Boxing",
        startMs = Instant.parse(date).toEpochMilli(),
        state = MatchState.PRE, statusText = "Upcoming", title = title,
    )

    @Test fun exactFightGetsRealArt() {
        val copy = MajorEventEditorial.forMatch(boxing(
            "Paul Gallen vs Nelson Asofa-Solomona", "2026-11-06T06:00:00Z"))
        assertTrue(copy.artworkUrl.orEmpty().contains("f47084b2b38a13ae8c43df3dc681570a69c96740"))
    }

    @Test fun otherDatesDoNotReuseFightArt() {
        assertNull(MajorEventEditorial.forMatch(boxing(
            "Nelson Asofa-Solomona vs Paul Gallen", "2027-11-06T06:00:00Z")).artworkUrl)
    }

    @Test fun unknownFightUsesOfflineFallback() {
        val copy = MajorEventEditorial.forMatch(boxing(
            "Unknown vs Unknown", "2026-11-06T06:00:00Z"))
        assertNull(copy.artworkUrl)
        assertNull(copy.synopsis)
    }

    @Test fun backendDataIsReadWithoutChangingRouting() {
        val match = boxing("Known Fight", "2026-11-06T06:00:00Z")
        val copy = MajorEventEditorial.forMatch(match, SportEventEditorial(
            heroArtUrl = "https://example.org/verified.jpg",
            synopsis = "Named opponents meet in a bout.",
            editorialTitle = "Known Headline",
            importanceTag = "FEATURED",
        ))
        assertEquals("Known Headline", copy.title)
        assertEquals("https://example.org/verified.jpg", copy.artworkUrl)
        assertEquals("Named opponents meet in a bout.", copy.synopsis)
        assertEquals(match.key, match.key)
    }

    @Test fun everyHeadlineSportHasPhotographicBackdrop() {
        val sports = listOf(
            Sport.MMA, Sport.RUGBY_LEAGUE, Sport.RUGBY, Sport.CRICKET,
            Sport.BASKETBALL, Sport.FOOTBALL, Sport.SOCCER, Sport.RACING,
        )
        val urls = sports.map { sport ->
            MajorEventEditorial.sportBackdrop(boxing("Major fixture", "2026-11-06T06:00:00Z")
                .copy(sport = sport))
        }
        assertEquals(8, urls.size)
        assertTrue(urls.all { it.startsWith("https://images.pexels.com/photos/") })
        assertTrue(urls.distinct().size >= 6)
    }

    @Test fun backdropIsStableForSameFixture() {
        val match = boxing("Main event", "2026-11-06T06:00:00Z")
        assertEquals(MajorEventEditorial.sportBackdrop(match), MajorEventEditorial.sportBackdrop(match))
    }

    @Test fun httpArtworkIsRejected() {
        assertNull(MajorEventEditorial.forMatch(
            boxing("Other Bout", "2026-11-06T06:00:00Z"),
            SportEventEditorial(heroArtUrl = "http://example.org/pic.jpg")
        ).artworkUrl)
    }
}
