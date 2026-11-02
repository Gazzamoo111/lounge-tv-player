package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.epg.EpgSource

class AutomaticEpgTest {
    @Test fun `new guide is queued for sync`() {
        assertTrue(shouldPrimeGuide(null))
    }

    @Test fun `guide with no successful sync is queued for recovery`() {
        val source = EpgSource(
            id = -1L, name = "Example", url = "https://example.test/guide.xml",
            lastSyncAt = null, lastError = "Temporary network error",
        )
        assertTrue(shouldPrimeGuide(source))
    }

    @Test fun `guide already synced remains untouched`() {
        val source = EpgSource(
            id = -2L, name = "Example", url = "https://example.test/guide.xml",
            lastSyncAt = 1_792_910_000_000L,
        )
        assertFalse(shouldPrimeGuide(source))
    }
    @Test fun `canonical gateway does not duplicate Lounge guide`() {
        val urls = guideUrlsWithLounge(
            tv.own.owntv.core.model.SourceType.XTREAM,
            listOf("https://connect.loungetv.nz/xmltv.php?username=u&password=p"),
        )
        assertEquals(1, urls.size)
        assertTrue(urls.single().startsWith("https://connect.loungetv.nz/xmltv.php"))
    }

    @Test fun `legacy Xtream source receives canonical Lounge supplement`() {
        val urls = guideUrlsWithLounge(
            tv.own.owntv.core.model.SourceType.XTREAM,
            listOf("http://provider.example/xmltv.php?username=u&password=p"),
        )
        assertEquals(2, urls.size)
        assertTrue(LOUNGE_EPG_URL in urls)
    }

}
