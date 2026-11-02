package tv.own.owntv.ui.components

import org.junit.Assert.*
import org.junit.Test

class LoungeDiscoveryRulesTest {
    private val target = LoungeDiscoveryRules.Candidate(1, "The Example", 2024, 7, setOf("Comedy", "Family"))
    @Test fun excludesCurrentTitleAndDuplicateVersions() {
        val result = LoungeDiscoveryRules.related(target, listOf(
            target, target.copy(id = 2),
            target.copy(id = 3, title = "Another Film"), target.copy(id = 4, title = "Another Film"),
        ))
        assertEquals(listOf(3L), result.ids)
    }
    @Test fun sharedGenresOutrankSingleGenreAndProviderRating() {
        val result = LoungeDiscoveryRules.related(target, listOf(
            target.copy(id = 2, title = "Two", genres = setOf("comedy"), rating = 10.0),
            target.copy(id = 3, title = "Three", genres = setOf("Family", "Comedy"), rating = 6.0),
            target.copy(id = 4, title = "Four", genres = setOf("Horror"), rating = 9.0),
        ))
        assertTrue(result.genreMatched)
        assertEquals(listOf(3L, 2L), result.ids)
    }
    @Test fun missingMetadataFallsBackOnlyToSameNonNullCategory() {
        val result = LoungeDiscoveryRules.related(target.copy(genres = emptySet()), listOf(
            target.copy(id = 2, title = "Two", categoryId = 9),
            target.copy(id = 3, title = "Three"),
        ))
        assertFalse(result.genreMatched)
        assertEquals(listOf(3L), result.ids)
        assertTrue(LoungeDiscoveryRules.related(target.copy(categoryId = null, genres = emptySet()), listOf(target.copy(id = 4, title = "Four", categoryId = null))).ids.isEmpty())
    }
    @Test fun respectsDisplayBoundAndStableOrdering() {
        val candidates = (2L..50L).map { target.copy(id = it, title = "Film $it") }
        assertEquals((2L..13L).toList(), LoungeDiscoveryRules.related(target, candidates).ids)
        assertTrue(LoungeDiscoveryRules.related(target, candidates, 0).ids.isEmpty())
    }
}
