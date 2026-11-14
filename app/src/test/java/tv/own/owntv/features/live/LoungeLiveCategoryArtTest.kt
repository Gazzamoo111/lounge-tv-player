package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LoungeLiveCategoryArtTest {
    @Test fun mainSectionsRemainVisuallyDistinct() {
        val visible = listOf(
            LoungeLiveSection.NEW_ZEALAND,
            LoungeLiveSection.AUSTRALIA,
            LoungeLiveSection.SPORTS,
            LoungeLiveSection.ENTERTAINMENT,
            LoungeLiveSection.MOVIES,
            LoungeLiveSection.DOCUMENTARIES,
            LoungeLiveSection.KIDS,
            LoungeLiveSection.SEASONAL,
            LoungeLiveSection.STREAMING,
        )
        assertEquals(visible.size, visible.map(::loungeLiveSectionColour).distinct().size)
        assertNotEquals(loungeLiveSectionColour(LoungeLiveSection.SPORTS),
            loungeLiveSectionColour(LoungeLiveSection.KIDS))
    }
}
