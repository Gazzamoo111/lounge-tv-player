"""Regression checks for the first read-only Movies v2 shelf.

The featured shelf must never replace, limit, or filter the provider's full catalogue.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MOVIES = ROOT / "app/src/main/java/tv/own/owntv/features/movies"


class MoviesV2GuardrailsTest(unittest.TestCase):
    def test_featured_shelf_reuses_home_editorial_not_loaded_page(self):
        screen = (MOVIES / "MoviesScreen.kt").read_text()
        self.assertIn("movies = homeEditorial.featuredMovies", screen)
        self.assertIn("selectedKey == LiveKey.All && searchQuery.isBlank()", screen)
        self.assertIn("lockedKey == null", screen)
        self.assertIn("homeVm.refresh()", screen)
        self.assertIn("span = { GridItemSpan(maxLineSpan) }", screen)
        self.assertIn("i + cinematicHeaderOffset", screen)
        self.assertIn("it - cinematicHeaderOffset", screen)
        shelf = (MOVIES / "LoungeMoviesFeaturedRail.kt").read_text()
        self.assertIn("LazyRow(", shelf)
        self.assertNotIn("itemSnapshotList", shelf)
        self.assertNotIn("movieDao", shelf)

    def test_existing_full_catalogue_pager_remains(self):
        screen = (MOVIES / "MoviesScreen.kt").read_text()
        model = (MOVIES / "MovieViewModel.kt").read_text()
        self.assertIn("vm.movies.collectAsLazyPagingItems()", screen)
        self.assertGreaterEqual(screen.count("items(count = movies.itemCount"), 3)
        self.assertIn("Pager(PagingConfig(", model)
        self.assertIn(".cachedIn(viewModelScope)", model)


if __name__ == "__main__":
    unittest.main()
