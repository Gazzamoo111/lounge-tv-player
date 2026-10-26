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
