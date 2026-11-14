package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import tv.own.owntv.core.model.HomeRow
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRulesTest {
    @Test fun homeViewportShowsSixOfEightWithoutDiscardingTheOtherTitles() = assertEquals(6, loungeHomeVisiblePosterCount(8))
    @Test fun homeViewportHandlesShortRows() = assertEquals(2, loungeHomeVisiblePosterCount(2))
    @Test fun curatedHomeHasOnlyRequestedRowsInOrder() = assertEquals(
        listOf(LoungeHomeRow.SPORTS_HERO, HomeRow.HERO, LoungeHomeRow.NEW_RELEASES, HomeRow.TRENDING,
            LoungeHomeRow.FEATURED_MOVIES, LoungeHomeRow.POPULAR_SERIES, LoungeHomeRow.LIVE_NOW,
            HomeRow.FAVORITE_CHANNELS, HomeRow.FAVORITE_MOVIES, HomeRow.FAVORITE_SERIES, LoungeHomeRow.CUSTOMIZE),
        loungeCuratedHomeRows(emptySet()),
    )

    @Test fun curatedHomeHonoursHiddenKeepWatchingAndTrending() = assertEquals(
        listOf(LoungeHomeRow.SPORTS_HERO, LoungeHomeRow.NEW_RELEASES, LoungeHomeRow.FEATURED_MOVIES,
            LoungeHomeRow.POPULAR_SERIES, LoungeHomeRow.LIVE_NOW, HomeRow.FAVORITE_CHANNELS,
            HomeRow.FAVORITE_MOVIES, HomeRow.FAVORITE_SERIES, LoungeHomeRow.CUSTOMIZE),
        loungeCuratedHomeRows(setOf(HomeRow.HERO, HomeRow.TRENDING)),
    )

    @Test fun profileCustomizationReordersAndHidesShelvesWithoutChangingTheHero() = assertEquals(
        listOf(LoungeHomeRow.SPORTS_HERO, LoungeHomeRow.LIVE_NOW, HomeRow.HERO, LoungeHomeRow.CUSTOMIZE),
        loungeCuratedHomeRows(
            emptySet(),
            LoungeHomePreferences(
                shelfOrder = listOf(LoungeHomeShelf.LIVE, LoungeHomeShelf.NEW, LoungeHomeShelf.KEEP),
                hiddenShelves = setOf(LoungeHomeShelf.NEW),
            ),
        ),
    )

    @Test fun profileShelfMoveDoesNotMutateTheOriginalOrder() {
        val old = LoungeHomePreferences()
        val changed = old.move(LoungeHomeShelf.NEW, -1)
        assertEquals(LoungeHomeShelf.NEW, changed.shelfOrder.first())
        assertEquals(LoungeHomeShelf.KEEP, old.shelfOrder.first())
    }

    // --- Keep watching stills: TMDB backdrop → provider backdrop → poster → channel logo ---

    @Test
    fun tmdbBackdropWins() = assertEquals(HomeStill.Picture("tmdb"), homeStill("tmdb", "prov", "poster", null))

    @Test
    fun providerBackdropWhenNoTmdb() = assertEquals(HomeStill.Picture("prov"), homeStill(null, "prov", "poster", null))

    @Test
    fun posterIsCroppedWhenNoBackdrop() = assertEquals(HomeStill.Picture("poster"), homeStill(" ", null, "poster", null))

    @Test
    fun channelShowsItsLogo() = assertEquals(HomeStill.Logo("logo"), homeStill(null, null, null, "logo"))

    @Test
    fun nothingAtAll() = assertEquals(HomeStill.None, homeStill(null, "", null, ""))

    // --- the Trending pager ---

    @Test
    fun rightGoesToNext() = assertEquals(3, trendingPagerTarget(2, 6, 1))

    @Test
    fun rightOnLastWraps() = assertEquals(0, trendingPagerTarget(5, 6, 1))

    @Test
    fun leftGoesToPrevious() = assertEquals(1, trendingPagerTarget(2, 6, -1))

    @Test
    fun leftOnFirstIsNotThePagers() = assertNull(trendingPagerTarget(0, 6, -1))

    @Test
    fun singleTitleNeverPages() = assertNull(trendingPagerTarget(0, 1, 1))
}
