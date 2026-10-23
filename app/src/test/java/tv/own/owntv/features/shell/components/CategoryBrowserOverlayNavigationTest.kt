package tv.own.owntv.features.shell.components

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.live.LiveKey

class CategoryBrowserOverlayNavigationTest {
    private val keys = (1L..60L).map { LiveKey.Folder(it) }

    @Test fun returnsToDisneyInsteadOfPlayingEspnAfterBrowsingFortyCategories() {
        // ESPN 2 stays tuned in category 1 while a user opens category 41,
        // then presses Back. The category rail must resume at 41.
        assertEquals(40, categoryOverlayInitialIndex(keys, LiveKey.Folder(1), LiveKey.Folder(41)))
    }

    @Test fun remembersTheLastHighlightedCategoryEvenWithoutPlayingItsChannel() {
        assertEquals(48, categoryOverlayInitialIndex(keys, LiveKey.Folder(2), LiveKey.Folder(49)))
    }

    @Test fun usesPlayingCategoryOnlyBeforeAnyBrowsing() {
        assertEquals(1, categoryOverlayInitialIndex(keys, LiveKey.Folder(2), null))
    }

    @Test fun deletedOrHiddenBrowsedCategoryFallsBackToPlayingCategory() {
        assertEquals(0, categoryOverlayInitialIndex(keys, LiveKey.Folder(1), LiveKey.Folder(70)))
    }

    @Test fun missingCategoriesSafelyFallBackToFirstEntry() {
        assertEquals(0, categoryOverlayInitialIndex(keys, LiveKey.Folder(80), LiveKey.Folder(70)))
        assertEquals(0, categoryOverlayInitialIndex(emptyList(), null, null))
    }
}
