package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoungeLiveSectionsTest {
    @Test fun sportsAcrossCountriesShareOneSection() {
        assertEquals(LoungeLiveSection.NEW_ZEALAND, loungeLiveSectionOf("NZ | Sky Sport RAW"))
        assertEquals(LoungeLiveSection.SPORTS, loungeLiveSectionOf("AU | Kayo Sports"))
        assertEquals(LoungeLiveSection.SPORTS, loungeLiveSectionOf("UK | TNT Sports"))
        assertEquals(LoungeLiveSection.SPORTS, loungeLiveSectionOf("US | ESPN"))
        assertEquals(LoungeLiveSection.SPORTS, loungeLiveSectionOf("US | NFL Network"))
    }

    @Test fun seasonalCategoriesRemainVisibleAndSeparate() {
        assertEquals(LoungeLiveSection.SEASONAL, loungeLiveSectionOf("US | Christmas"))
        assertEquals(LoungeLiveSection.SEASONAL, loungeLiveSectionOf("UK | Holiday Movies 24/7"))
    }

    @Test fun editorialGroupsAreDistinct() {
        assertEquals(LoungeLiveSection.INTERNATIONAL, loungeLiveSectionOf("US | CNN News"))
        assertEquals(LoungeLiveSection.KIDS, loungeLiveSectionOf("UK | Kids Disney"))
        assertEquals(LoungeLiveSection.MOVIES, loungeLiveSectionOf("US | MGM Movies"))
        assertEquals(LoungeLiveSection.DOCUMENTARIES, loungeLiveSectionOf("UK | Discovery"))
    }
    @Test fun regionHeadingsComeFirstAndCatchExistingSources() {
        assertEquals(LoungeLiveSection.NEW_ZEALAND, loungeLiveSectionOf("Nz"))
        assertEquals(LoungeLiveSection.NEW_ZEALAND, loungeLiveSectionOf("NZ| SKY SPORTS NZ"))
        assertEquals(LoungeLiveSection.AUSTRALIA, loungeLiveSectionOf("AU| AUSTRALIA"))
        assertEquals(LoungeLiveSection.AUSTRALIA, loungeLiveSectionOf("AU| 9NOW ᴿᴬᵂ"))
        assertTrue(LoungeLiveSection.NEW_ZEALAND.ordinal < LoungeLiveSection.SPORTS.ordinal)
        assertTrue(LoungeLiveSection.AUSTRALIA.ordinal < LoungeLiveSection.SPORTS.ordinal)
    }

    @Test fun namedOtherFoldersRouteToEditorialSections() {
        val expected = mapOf(
            "UK| SKYMIX SERIES" to LoungeLiveSection.ENTERTAINMENT,
            "UK| SKYMIX DOCS" to LoungeLiveSection.DOCUMENTARIES,
            "UK| SKY STORE" to LoungeLiveSection.MOVIES,
            "UK| BBCIPLAYER SERIES" to LoungeLiveSection.ENTERTAINMENT,
            "UK| PRIME ᴿᴬᵂ ⁶⁰ᶠᵖˢ" to LoungeLiveSection.ENTERTAINMENT,
            "UK| FA PLAYER" to LoungeLiveSection.SPORTS,
            "UK| AMAZON PRIME +" to LoungeLiveSection.PPV,
            "UK| UEFA REPLAY" to LoungeLiveSection.SPORTS,
            "UK| CHAMPIONSHIP" to LoungeLiveSection.SPORTS,
            "UK| PRIME VIDEO SERIES" to LoungeLiveSection.ENTERTAINMENT,
            "UK| ITV X VIP" to LoungeLiveSection.ENTERTAINMENT,
            "UK| MC VIDEO ᴴᴰ" to LoungeLiveSection.MUSIC,
            "US| NCAAB PACKAGE" to LoungeLiveSection.SPORTS,
            "US| NCAAF PACKAGE" to LoungeLiveSection.SPORTS,
            "US| TUBI ᴿᴬᵂ" to LoungeLiveSection.STREAMING,
            "US| TV ᴿᴬᵂ ⁶⁰ᶠᵖˢ" to LoungeLiveSection.US_UK,
            "US| TV ᶜᶦᵗʸ ᴿᴬᵂ ⁶⁰ᶠᵖˢ" to LoungeLiveSection.US_UK,
            "US| PRIME" to LoungeLiveSection.US_UK,
            "US| NETFLIX ON AIR ᴿᴬᵂ ⁶⁰ᶠᵖˢ" to LoungeLiveSection.STREAMING,
            "UK| APPLE TV+ SERIES" to LoungeLiveSection.STREAMING,
            "US| HULU NETWORK" to LoungeLiveSection.STREAMING,
            "US| PARAMOUNT+" to LoungeLiveSection.STREAMING,
            "US| DISNEY+ NETWORK" to LoungeLiveSection.STREAMING,
        )
        expected.forEach { (name, section) ->
            assertEquals(name, section, loungeLiveSectionOf(name))
        }
    }

}
