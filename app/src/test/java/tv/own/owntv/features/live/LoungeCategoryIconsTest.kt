package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.ui.components.OwnTVIcon

/** Preserve the owner-approved QA #667 category marks. Subcategories intentionally have no marks. */
class LoungeCategoryIconsTest {
    @Test fun topLevelMarksMatchTheOriginalSystem() {
        assertEquals(OwnTVIcon.NZ_FERN, LoungeEditorialOrder.icon(LoungeLiveSection.NEW_ZEALAND))
        assertEquals(OwnTVIcon.AU_ROO, LoungeEditorialOrder.icon(LoungeLiveSection.AUSTRALIA))
        assertEquals(OwnTVIcon.SPORTS, LoungeEditorialOrder.icon(LoungeLiveSection.SPORTS))
        assertEquals(OwnTVIcon.MOVIES, LoungeEditorialOrder.icon(LoungeLiveSection.MOVIES))
        assertEquals(OwnTVIcon.PERSON, LoungeEditorialOrder.icon(LoungeLiveSection.KIDS))
        assertEquals(OwnTVIcon.LANGUAGE, LoungeEditorialOrder.icon(LoungeLiveSection.DOCUMENTARIES))
        assertEquals(OwnTVIcon.CLOCK, LoungeEditorialOrder.icon(LoungeLiveSection.SEASONAL))
    }

    @Test fun allProviderAndCustomSubcategoriesAreTextOnly() {
        assertNull(liveCategoryEntryIcon(tv.own.owntv.core.live.LiveKey.Folder(1)))
        assertNull(liveCategoryEntryIcon(tv.own.owntv.core.live.LiveKey.Folder(2)))
        assertNull(liveCategoryEntryIcon(tv.own.owntv.core.live.LiveKey.Custom("family")))
        assertEquals(OwnTVIcon.LIVE_TV, liveCategoryEntryIcon(tv.own.owntv.core.live.LiveKey.All))
        assertEquals(OwnTVIcon.FAVORITE, liveCategoryEntryIcon(tv.own.owntv.core.live.LiveKey.Favorites))
    }
}
