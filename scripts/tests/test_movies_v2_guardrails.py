"""Read-only Movie highlights must never truncate or rewrite the full catalogue."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MOVIES = ROOT / "app/src/main/java/tv/own/owntv/features/movies"


class MoviesHighlightsGuardrailsTest(unittest.TestCase):
    def test_highlights_are_independent_of_full_catalogue_paging(self):
        screen = (MOVIES / "MoviesScreen.kt").read_text()
        model = (MOVIES / "MovieViewModel.kt").read_text()
        self.assertIn("vm.movies.collectAsLazyPagingItems()", screen)
        self.assertGreaterEqual(screen.count("items(count = movies.itemCount"), 3)
        self.assertIn("Pager(PagingConfig(pageSize = 60", model)
        self.assertIn(".cachedIn(viewModelScope)", model)
        self.assertNotIn("collectAsLazyPagingItems().itemSnapshotList", (MOVIES / "MovieCategoryHighlights.kt").read_text())

    def test_both_rows_are_scoped_and_scroll_away(self):
        screen = (MOVIES / "MoviesScreen.kt").read_text()
        self.assertIn("it.categoryKey == selectedKey", screen)
        self.assertIn("displayFeatured", screen)
        self.assertIn("displayNewReleases", screen)
        self.assertEqual(screen.count("item(key = Long.MIN_VALUE + 0L,"), 2)
        self.assertEqual(screen.count("item(key = Long.MIN_VALUE + 1L,"), 2)
        self.assertEqual(screen.count("item(key = Long.MIN_VALUE + 2L,"), 3)
        self.assertIn("val showCatalogueHeading = editorialRowCount > 0 && movies.itemCount > 0", screen)
        self.assertIn("text = stringResource(R.string.content_category_all_movies)", screen)
        self.assertLess(screen.index("item(key = Long.MIN_VALUE + 2L,"), screen.index("items(count = movies.itemCount"))
        self.assertIn("gridHeaderOffset", screen)
        self.assertIn("listHeaderOffset", screen)
        self.assertIn("scrollToItem(i + listHeaderOffset)", screen)
        self.assertIn("scrollToItem(i + gridHeaderOffset)", screen)
        self.assertIn("it - gridHeaderOffset else it - listHeaderOffset", screen)

    def test_one_vertical_six_column_movie_grid(self):
        tile = (MOVIES / "LoungeMoviesFeaturedRail.kt").read_text()
        screen = (MOVIES / "MoviesScreen.kt").read_text()
        self.assertNotIn("LazyRow(", tile)
        self.assertNotIn("LoungeMoviesFeaturedRail(", screen)
        self.assertIn("LoungeMoviesEditorialPoster(", screen)
        self.assertIn("width = posterW", screen)
        self.assertIn("height = width * 1.5f", tile)
        self.assertIn("val columns = 6", screen)
        self.assertIn("val featuredPreview = if (displayFeatured) allMoviesEditorial.take(6)", screen)
        self.assertIn("val newPreview = if (displayNewReleases) newMovies.take(6)", screen)
        self.assertIn("1 + featuredPreview.size", screen)
        self.assertIn("1 + newPreview.size", screen)
        self.assertIn("contentType = { \"featured-poster\" }", screen)
        self.assertIn("contentType = { \"new-poster\" }", screen)
        self.assertIn("posterUrl = posterOf(movie)", screen)
        self.assertIn("VodPosterArt(posterUrl, OwnTVIcon.MOVIES)", tile)
        self.assertEqual(screen.count("onLeftToCategories ="), 4)
        self.assertIn("val heroH = if (showcaseActive) minOf(maxHeight * 0.325f, 350.mpx)", screen)
        self.assertIn("LoungeBrowseSpotlight(it, location)", screen)

    def test_series_has_matching_full_catalogue_and_highlight_rows(self):
        series = ROOT / "app/src/main/java/tv/own/owntv/features/series"
        screen = (series / "SeriesScreen.kt").read_text()
        model = (series / "SeriesViewModel.kt").read_text()
        rail = (series / "LoungeSeriesFeaturedRail.kt").read_text()
        reader = (series / "SeriesCategoryHighlights.kt").read_text()
        self.assertIn("vm.series.collectAsLazyPagingItems()", screen)
        self.assertEqual(screen.count("items(count = series.itemCount"), 3)
        self.assertEqual(screen.count("item(key = Long.MIN_VALUE + 0L,"), 2)
        self.assertEqual(screen.count("item(key = Long.MIN_VALUE + 1L,"), 2)
        self.assertEqual(screen.count("item(key = Long.MIN_VALUE + 2L,"), 3)
        self.assertIn("val showCatalogueHeading = editorialRowCount > 0 && series.itemCount > 0", screen)
        self.assertIn("text = stringResource(R.string.content_category_all_series)", screen)
        self.assertLess(screen.index("item(key = Long.MIN_VALUE + 2L,"), screen.index("items(count = series.itemCount"))
        self.assertEqual(screen.count("onLeftToCategories ="), 4)
        self.assertIn("LoungeBrowseSpotlight(it, location)", screen)
        self.assertIn("val heroH = if (showcaseActive) minOf(maxHeight * 0.325f, 350.mpx)", screen)
        self.assertIn("categoryHighlights.categoryKey != selectedKey", screen)
        self.assertIn("effectiveGridState.scrollToItem(0)", screen)
        self.assertIn("effectiveListState.scrollToItem(0)", screen)
        self.assertIn("scrollToItem(i + gridHeaderOffset)", screen)
        self.assertIn("scrollToItem(i + listHeaderOffset)", screen)
        self.assertIn("VodPosterArt(posterUrl, OwnTVIcon.SERIES)", rail)
        self.assertNotIn("LazyRow(", rail)
        self.assertNotIn("LoungeSeriesFeaturedRail(", screen)
        self.assertIn("LoungeSeriesEditorialPoster(", screen)
        self.assertIn("val columns = 6", screen)
        self.assertIn("val featuredPreview = if (displayFeatured) featuredShows.take(6)", screen)
        self.assertIn("val newPreview = if (displayNewReleases) newShows.take(6)", screen)
        self.assertIn("1 + featuredPreview.size", screen)
        self.assertIn("1 + newPreview.size", screen)
        self.assertIn("getSeriesByIds(candidates)", reader)
        self.assertIn("COALESCE(m.year, m.parsedYear, 0) BETWEEN ? AND ?", reader)
        self.assertIn("shuffled(Random(", reader)
        self.assertIn("MAX_ROW_ITEMS = 20", reader)
        self.assertNotIn("UPDATE series", reader)
        self.assertNotIn("DELETE FROM series", reader)
        self.assertIn("SeriesCategoryHighlightsReader(", model)


    def test_larger_hero_preserves_readable_title_and_synopsis(self):
        spotlight = (ROOT / "app/src/main/java/tv/own/owntv/features/shell/components/LoungeBrowseSpotlight.kt").read_text()
        self.assertIn("Modifier.width(720.mpx).height(126.mpx)", spotlight)
        self.assertIn("stageText(68, 800)", spotlight)
        self.assertIn("size = 21", spotlight)
        self.assertIn("stageText(23, 400)", spotlight)
        self.assertIn("maxLines = 3", spotlight)
        self.assertIn("onFailed = { logoFailed = true }", spotlight)

    def test_editorial_tiles_remain_accessible_from_sidebar(self):
        tile = (MOVIES / "LoungeMoviesFeaturedRail.kt").read_text()
        self.assertIn("onLeftToCategories()", tile)
        self.assertIn("firstInSection", tile)
        self.assertIn("focusRequester(focusRequester)", tile)
        self.assertIn("atLeftEdge", tile)

    def test_sidebar_category_entry_returns_to_highlight_rows(self):
        screen = (MOVIES / "MoviesScreen.kt").read_text()
        self.assertIn("pendingCategoryFocus = item.key", screen)
        self.assertIn("categoryHighlights.categoryKey != selectedKey", screen)
        self.assertIn("effectiveGridState.scrollToItem(0)", screen)
        self.assertIn("effectiveListState.scrollToItem(0)", screen)
        self.assertIn("firstFeaturedFocus", screen)
        self.assertIn("firstNewReleaseFocus", screen)
        self.assertEqual(screen.count("onLeftFromFirstCard ="), 6)

    def test_guide_explains_hide_and_restore(self):
        guide = (ROOT / "extras/USER_GUIDE.md").read_text()
        self.assertIn("Settings → Customise", guide)
        self.assertIn("restore or unhide", guide)

    def test_editorial_rotation_and_recent_years_are_read_only(self):
        reader = (MOVIES / "MovieCategoryHighlights.kt").read_text()
        model = (MOVIES / "MovieViewModel.kt").read_text()
        self.assertIn("getByIds(candidates)", reader)
        self.assertIn("COALESCE(m.year, m.parsedYear, 0) BETWEEN ? AND ?", reader)
        self.assertIn("shuffled(Random(", reader)
        self.assertIn("MAX_ROW_ITEMS = 20", reader)
        self.assertIn("useReaderConnection", reader)
        self.assertNotIn("DELETE FROM movies", reader)
        self.assertNotIn("UPDATE movies", reader)
        self.assertIn("delay(3_600_000L)", model)


if __name__ == "__main__":
    unittest.main()
