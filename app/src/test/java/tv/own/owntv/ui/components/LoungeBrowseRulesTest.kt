package tv.own.owntv.ui.components

import org.junit.Assert.*
import org.junit.Test

class LoungeBrowseRulesTest {
    @Test fun previewLimitDoesNotConstrainFullCollection() {
        val filter = LoungeBrowseFilter()
        assertEquals(16, LoungeBrowseRules.PREVIEW_LIMIT)
        assertTrue((1L..10000L).all { filter.accepts(it, null) })
    }
    @Test fun curatedAndReleaseCollectionsHaveDifferentEligibility() {
        val curated = LoungeBrowseFilter(ids = setOf(4, 9))
        assertTrue(curated.accepts(9, null)); assertFalse(curated.accepts(8, 2026))
        val recent = LoungeBrowseFilter(releaseYears = 2024..2026)
        assertTrue(recent.accepts(100, 2024)); assertTrue(recent.accepts(101, 2026))
        assertFalse(recent.accepts(102, 2023)); assertFalse(recent.accepts(103, null))
        assertFalse(recent.accepts(104, 2027))
    }
    @Test fun focusFollowsStableIdAfterReorderingAndFallsBackAfterRemoval() {
        assertEquals(2, LoungeBrowseRules.restoreIndex(listOf(8L, 9L, 4L), 4, 0))
        assertEquals(1, LoungeBrowseRules.restoreIndex(listOf(8L, 9L), 4, 8))
        assertEquals(0, LoungeBrowseRules.restoreIndex(emptyList(), 4, 8))
        assertEquals(0, LoungeBrowseRules.restoreIndex(listOf(null, 9L), null, 0))
    }
    @Test fun columnsAdaptWithoutClipping() {
        assertEquals(6, LoungeBrowseRules.columns(1700f))
        assertEquals(5, LoungeBrowseRules.columns(1200f))
        assertEquals(4, LoungeBrowseRules.columns(950f))
    }
}
