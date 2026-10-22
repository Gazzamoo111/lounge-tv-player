package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HomeRulesTest {

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

    // --- fresh-catalogue hero fallback ---

    @Test
    fun `backdrop wins before poster and provider order`() {
        val posterOnly = candidate(CatalogueFeatureKind.MOVIE, 1, "First", 0, artwork = true)
        val backdrop = candidate(CatalogueFeatureKind.SERIES, 2, "Second", 99, backdrop = true)

        assertSame(backdrop, chooseCatalogueFeatureCandidate(listOf(posterOnly, backdrop)))
    }

    @Test
    fun `poster wins before an unillustrated live channel`() {
        val live = candidate(CatalogueFeatureKind.LIVE, 1, "Channel", 0)
        val movie = candidate(CatalogueFeatureKind.MOVIE, 2, "Movie", 99, artwork = true)

        assertSame(movie, chooseCatalogueFeatureCandidate(listOf(live, movie)))
    }

    @Test
    fun `provider order gives deterministic no-metadata fallback`() {
        val later = candidate(CatalogueFeatureKind.SERIES, 2, "A title", 20)
        val earlier = candidate(CatalogueFeatureKind.LIVE, 1, "Z channel", 5)

        assertSame(earlier, chooseCatalogueFeatureCandidate(listOf(later, earlier)))
    }

    @Test
    fun `name and id make equal provider order deterministic`() {
        val zed = candidate(CatalogueFeatureKind.MOVIE, 1, "Zulu", 4)
        val alphaHighId = candidate(CatalogueFeatureKind.MOVIE, 9, "Alpha", 4)
        val alphaLowId = candidate(CatalogueFeatureKind.MOVIE, 3, "alpha", 4)

        assertSame(alphaLowId, chooseCatalogueFeatureCandidate(listOf(zed, alphaHighId, alphaLowId)))
    }

    @Test
    fun `empty catalogue has no fallback`() = assertNull(chooseCatalogueFeatureCandidate(emptyList()))

    @Test
    fun `recent quality title outranks provider-first halloween title`() {
        val halloween = candidate(
            CatalogueFeatureKind.MOVIE, 1, "Halloween marathon", 0,
            backdrop = true, artwork = true, year = 2025, rating = 8.0, category = "Halloween",
        )
        val recent = candidate(
            CatalogueFeatureKind.MOVIE, 2, "Current release", 900,
            backdrop = true, artwork = true, year = 2026, rating = 7.5, category = "New releases",
        )

        assertSame(recent, rankCatalogueFeatureCandidates(listOf(halloween, recent), 1, currentYear = 2026).single())
    }

    @Test
    fun `balanced row caps a single provider category`() {
        val action = (1L..5L).map { candidate(CatalogueFeatureKind.MOVIE, it, "Action $it", it.toInt(), category = "Action") }
        val comedy = candidate(CatalogueFeatureKind.MOVIE, 9, "Comedy", 99, category = "Comedy")

        val ranked = rankCatalogueFeatureCandidates(action + comedy, limit = 4, currentYear = 2026, maxPerCategory = 2)

        assertEquals(3, ranked.size)
        assertEquals(2, ranked.count { it.categoryName == "Action" })
        assertEquals(1, ranked.count { it.categoryName == "Comedy" })
    }

    @Test
    fun `broken New Zealand live folder is retained but not promoted`() {
        val nz = candidate(CatalogueFeatureKind.LIVE, 1, "NZ One", 0, artwork = true, category = "NZ|New Zealand")
        val sport = candidate(CatalogueFeatureKind.LIVE, 2, "Sport One", 50, artwork = true, category = "Sport")

        assertSame(sport, rankCatalogueFeatureCandidates(listOf(nz, sport), 1, currentYear = 2026).single())
    }

    private fun candidate(
        kind: CatalogueFeatureKind,
        id: Long,
        name: String,
        order: Int,
        backdrop: Boolean = false,
        artwork: Boolean = false,
        year: Int? = null,
        rating: Double? = null,
        category: String? = null,
    ) = CatalogueFeatureCandidate(
        kind, id, name, order, backdrop, artwork,
        year = year, rating = rating, categoryName = category,
    )
}
