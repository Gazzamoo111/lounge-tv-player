package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LoungeLiveSectionsTest {
    @Test fun sportsAcrossCountriesShareOneSection() {
        assertEquals(LoungeLiveSection.SPORTS, loungeLiveSectionOf("NZ | Sky Sport RAW"))
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
        assertEquals(LoungeLiveSection.NEWS, loungeLiveSectionOf("US | CNN News"))
        assertEquals(LoungeLiveSection.KIDS, loungeLiveSectionOf("UK | Kids Disney"))
        assertEquals(LoungeLiveSection.MOVIES, loungeLiveSectionOf("US | MGM Movies"))
        assertEquals(LoungeLiveSection.DOCUMENTARIES, loungeLiveSectionOf("UK | Discovery"))
    }
}
