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
        self.assertEqual(screen.count("item(key = Long.MIN_VALUE,"), 3)
        self.assertEqual(screen.count("item(key = Long.MIN_VALUE + 1L,"), 3)
        self.assertIn("gridHeaderOffset", screen)
        self.assertIn("listHeaderOffset", screen)
        self.assertIn("scrollToItem(i + listHeaderOffset)", screen)
        self.assertIn("scrollToItem(i + gridHeaderOffset)", screen)
        self.assertIn("it - gridHeaderOffset else it - listHeaderOffset", screen)

    def test_showcase_uses_prominent_portrait_posters_and_visible_headings(self):
        shelf = (MOVIES / "LoungeMoviesFeaturedRail.kt").read_text()
        self.assertIn("val cardWidth = minOf(maxOf((maxWidth - 78.mpx) / 4.8f, 196.mpx), 284.mpx)", shelf)
        self.assertIn("height = cardWidth * 1.5f", shelf)
        self.assertIn("Text(heading, style = stageText(26, 800)", shelf)
        self.assertLess(shelf.index("Text(heading"), shelf.index("LazyRow("))
        self.assertIn("VodPosterArt(posterUrl(movie), OwnTVIcon.MOVIES)", shelf)
        self.assertNotIn("movie.backdropUrl", shelf)
        self.assertNotIn("ContentScale.Fit", shelf)
        screen = (MOVIES / "MoviesScreen.kt").read_text()
        self.assertIn("val showcaseActive = displayFeatured || displayNewReleases", screen)
        self.assertIn("val heroH = if (showcaseActive) 226.mpx", screen)
        self.assertIn("LoungeBrowseSpotlight(it, location)", screen)
        self.assertEqual(screen.count("heading = stringResource(R.string.lounge_home_featured_movies)"), 3)
        self.assertEqual(screen.count("heading = stringResource(R.string.lounge_movies_new_releases)"), 3)

    def test_series_has_matching_full_catalogue_and_highlight_rows(self):
        series = ROOT / "app/src/main/java/tv/own/owntv/features/series"
        screen = (series / "SeriesScreen.kt").read_text()
        model = (series / "SeriesViewModel.kt").read_text()
        rail = (series / "LoungeSeriesFeaturedRail.kt").read_text()
        reader = (series / "SeriesCategoryHighlights.kt").read_text()
        self.assertIn("vm.series.collectAsLazyPagingItems()", screen)
        self.assertEqual(screen.count("items(count = series.itemCount"), 3)
        self.assertEqual(screen.count("item(key = Long.MIN_VALUE,"), 3)
        self.assertEqual(screen.count("item(key = Long.MIN_VALUE + 1L,"), 3)
        self.assertEqual(screen.count("onLeftFromFirstCard ="), 6)
        self.assertIn("LoungeBrowseSpotlight(it, location)", screen)
        self.assertIn("val heroH = if (showcaseActive) 226.mpx", screen)
        self.assertIn("categoryHighlights.categoryKey != selectedKey", screen)
        self.assertIn("effectiveGridState.scrollToItem(0)", screen)
        self.assertIn("effectiveListState.scrollToItem(0)", screen)
        self.assertIn("scrollToItem(i + gridHeaderOffset)", screen)
        self.assertIn("scrollToItem(i + listHeaderOffset)", screen)
        self.assertIn("VodPosterArt(posterUrl(movie), OwnTVIcon.SERIES)", rail)
        self.assertIn("getSeriesByIds(candidates)", reader)
        self.assertIn("COALESCE(m.year, m.parsedYear, 0) BETWEEN ? AND ?", reader)
        self.assertIn("shuffled(Random(", reader)
        self.assertIn("MAX_ROW_ITEMS = 20", reader)
        self.assertNotIn("UPDATE series", reader)
        self.assertNotIn("DELETE FROM series", reader)
        self.assertIn("SeriesCategoryHighlightsReader(", model)

    def test_existing_sidebar_behaviour_is_retained_in_showcase(self):
        shelf = (MOVIES / "LoungeMoviesFeaturedRail.kt").read_text()
        self.assertIn("onLeftFromFirstCard()", shelf)
        self.assertIn("firstCardFocusRequester", shelf)

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
