package tv.own.owntv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoungeVodCatalogueCleanupTest {
    private val policy = LoungeVodCatalogueCleanup
    private fun folder(id: Long, name: String, count: Int?, source: Long = 1L) =
        LoungeVodCatalogueCleanup.Folder(id, source, "${source}:${id}", name, count)

    private fun series(seed: List<LoungeVodCatalogueCleanup.Folder> = emptyList()) = listOf(
        folder(10, "|EN| ENGLISH SERIES", 998),
        folder(11, "|EN| LATEST RELEASES", 537),
        folder(12, "|EN| GANGSTER & MAFIA", 116),
        folder(13, "|EN| REALITY SHOWS", 526),
        folder(14, "|EN| DOCUMENTARIES", 612),
        folder(15, "|EN| CLASSIC SERIES", 115),
    ) + seed

    private fun movies(seed: List<LoungeVodCatalogueCleanup.Folder> = emptyList()) = listOf(
        folder(41, "|EN| DRAMA", 756),
        folder(48, "|EN| NEW RELEASED", 957),
        folder(37, "|EN| CLASSIC MOVIES COLLECTION", 1652),
        folder(53, "|EN| SCIENCE FICTION/FANTASTY", 386),
        folder(64, "|EN| ACTORS & DIRECTORS", 1650),
        folder(57, "|EN| DRAMA/CRIME", 481),
    ) + seed

    @Test fun `Turkish and Asian series are hidden even with more than ten entries`() {
        val plan = policy.plan(LoungeVodCatalogueCleanup.Kind.SERIES, series(listOf(
            folder(16, "|EN| TURKISH SERIES SUB", 89),
            folder(36, "|EN| ASIAN SERIES", 96),
        )))
        assertTrue(16L in plan.hiddenIds)
        assertTrue(36L in plan.hiddenIds)
        assertFalse(10L in plan.hiddenIds)
    }

    @Test fun `all MULTI series categories are hidden while audited English categories remain`() {
        val plan = policy.plan(LoungeVodCatalogueCleanup.Kind.SERIES, series(listOf(
            folder(1, "|MULTI| NETFLIX SERIES 4K", 89),
            folder(2, "|MULTI| NETFLIX SERIES", 1147),
            folder(3, "|MULTI| NETFLIX ASIA", 363),
            folder(4, "|MULTI| NETFLIX KIDS", 246),
        )))
        assertTrue(plan.hiddenIds.containsAll(listOf(1L, 2L, 3L, 4L)))
        assertFalse(plan.hiddenIds.contains(11L))
    }

    @Test fun `audited non-English movie folders and tiny categories are hidden`() {
        val plan = policy.plan(LoungeVodCatalogueCleanup.Kind.MOVIE, movies(listOf(
            folder(40, "|EN| FRENCH MOVIES / ENG SUB", 961),
            folder(42, "|EN| ITALIAN MOVIES / ENG SUB", 201),
            folder(43, "|EN| FOREIGN MOVIES", 414),
            folder(52, "|EN| PRE-RELEASES/SD CAM", 6),
            folder(78, "|EN| BIBLICAL/RELIGIOUS", 62),
            folder(83, "|EN| 4K CHILDREN", 3),
            folder(86, "NETFLIX MOVIES", 2489),
            folder(87, "NETFLIX ASIA", 144),
        )))
        assertTrue(plan.hiddenIds.containsAll(listOf(40L, 42L, 43L, 52L, 78L, 83L, 87L)))
        assertFalse(plan.hiddenIds.contains(86L))
        assertEquals(tv.own.owntv.R.string.lounge_movies_new_releases.toString(), plan.names["1:48"])
        assertEquals(tv.own.owntv.R.string.catalogue_sci_fi_fantasy.toString(), plan.names["1:53"])
    }

    @Test fun `BEST SD CAM pre releases remain hidden even when provider adds more titles`() {
        val plan = policy.plan(LoungeVodCatalogueCleanup.Kind.MOVIE, movies(listOf(
            folder(52, "|EN| PRE-RELEASES/SD CAM", 500),
        )))
        assertTrue(52L in plan.hiddenIds)
        assertFalse(48L in plan.hiddenIds)
    }

    @Test fun `nine entries hide and ten entries stay visible after playlist refresh`() {
        val current = policy.plan(LoungeVodCatalogueCleanup.Kind.MOVIE, movies(listOf(folder(95, "|EN| COMEDY", 9))))
        val refreshed = policy.plan(LoungeVodCatalogueCleanup.Kind.MOVIE, movies(listOf(folder(95, "|EN| COMEDY", 10))))
        assertTrue(95L in current.hiddenIds)
        assertFalse(95L in refreshed.hiddenIds)
        val unknown = policy.plan(LoungeVodCatalogueCleanup.Kind.MOVIE, movies(listOf(folder(95, "|EN| COMEDY", null))))
        assertFalse(95L in unknown.hiddenIds)
    }

    @Test fun `do not apply BEST audit rules to unrelated playlists`() {
        val other = folder(999, "|EN| TURKISH SERIES SUB", 89, source = 43L)
        val plan = policy.plan(LoungeVodCatalogueCleanup.Kind.SERIES, series(listOf(other)))
        assertFalse(999L in plan.hiddenIds)
        assertFalse(plan.names.containsKey("43:999"))
    }

    @Test fun `provider language decides ambiguous English-labelled folder content`() {
        assertFalse(policy.visibleEnglish("Some Show", "tr"))
        assertFalse(policy.visibleEnglish("Some Show", "Turkish"))
        assertFalse(policy.visibleEnglish("Some Show", "fr-FR"))
        assertFalse(policy.visibleEnglish("Some Show", "multi"))
        assertTrue(policy.visibleEnglish("Some Show", "en-US"))
        assertTrue(policy.visibleEnglish("Some Show", "English"))
        assertTrue(policy.visibleEnglish("The Italian Job", null))
    }

    @Test fun `explicit language markers do not misclassify ordinary titles`() {
        assertTrue(policy.explicitlyNonEnglishTitle("[TR] Foreign Movie"))
        assertTrue(policy.explicitlyNonEnglishTitle("KO - Some Series"))
        assertFalse(policy.explicitlyNonEnglishTitle("French Connection"))
        assertFalse(policy.explicitlyNonEnglishTitle("Italian Job"))
        assertFalse(policy.explicitlyNonEnglishTitle("Korean War"))
    }
}
