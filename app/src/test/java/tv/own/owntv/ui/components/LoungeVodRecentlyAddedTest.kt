package tv.own.owntv.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoungeVodRecentlyAddedTest {
    private val now = 1_800_000_000_000L
    @Test fun recentProviderDateIsIncluded() {
        assertTrue(LoungeVodRecentlyAdded.isRecent(now - 7L * 86_400_000L, now))
    }
    @Test fun unknownAndOldDatesCannotMasqueradeAsNew() {
        assertFalse(LoungeVodRecentlyAdded.isRecent(null, now))
        assertFalse(LoungeVodRecentlyAdded.isRecent(0L, now))
        assertFalse(LoungeVodRecentlyAdded.isRecent(now - 91L * 86_400_000L, now))
    }
    @Test fun impossibleFutureDateIsExcluded() {
        assertFalse(LoungeVodRecentlyAdded.isRecent(now + 2L * 86_400_000L, now))
    }
}
