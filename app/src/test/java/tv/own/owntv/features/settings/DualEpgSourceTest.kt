package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.model.SourceType

class DualEpgSourceTest {
    private val bestGuide = "https://example.test/xmltv.php?username=u&password=p"

    @Test fun `xtream imports provider first and lounge second`() {
        assertEquals(
            listOf(bestGuide, LOUNGE_EPG_URL),
            guideUrlsWithLounge(SourceType.XTREAM, listOf(bestGuide)),
        )
    }

    @Test fun `lounge fills a missing provider guide`() {
        assertEquals(
            listOf(LOUNGE_EPG_URL),
            guideUrlsWithLounge(SourceType.XTREAM, emptyList()),
        )
    }

    @Test fun `duplicate url is never fetched twice`() {
        assertEquals(
            listOf(bestGuide, LOUNGE_EPG_URL),
            guideUrlsWithLounge(SourceType.XTREAM, listOf(bestGuide, LOUNGE_EPG_URL)),
        )
    }

    @Test fun `other playlist types retain their configured EPGs`() {
        assertEquals(
            listOf(bestGuide),
            guideUrlsWithLounge(SourceType.M3U, listOf(bestGuide)),
        )
    }
}
