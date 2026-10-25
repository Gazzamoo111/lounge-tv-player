package tv.own.owntv.features.settings

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
}
