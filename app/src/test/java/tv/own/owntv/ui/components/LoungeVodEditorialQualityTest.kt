package tv.own.owntv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoungeVodEditorialQualityTest {
    @Test fun rejectsOnlyExplicitForeignLanguageVariants() {
        assertFalse(LoungeVodEditorialQuality.eligibleForHighlights(
            providerName = "ES | Dune: Part Two", canonicalTitle = "Dune: Part Two",
            providerLanguage = null, rating = 8.0,
        ))
        assertFalse(LoungeVodEditorialQuality.eligibleForHighlights(
            providerName = "[PT] Oppenheimer", canonicalTitle = "Oppenheimer",
            providerLanguage = null, rating = 8.0,
        ))
        assertFalse(LoungeVodEditorialQuality.eligibleForHighlights(
            providerName = "The Batman [DE]", canonicalTitle = "The Batman",
            providerLanguage = null, rating = 8.0,
        ))
        assertFalse(LoungeVodEditorialQuality.eligibleForHighlights(
            providerName = "The Batman", canonicalTitle = "The Batman",
            providerLanguage = "spa", rating = 8.0,
        ))
    }

    @Test fun allowsEnglishOrUnknownAndDoesNotInferLanguageFromOrdinaryWords() {
        assertTrue(LoungeVodEditorialQuality.eligibleForHighlights(
            providerName = "The French Connection", canonicalTitle = "",
            providerLanguage = null, rating = 7.0,
        ))
        assertTrue(LoungeVodEditorialQuality.eligibleForHighlights(
            providerName = "Italian Job", canonicalTitle = "",
            providerLanguage = "en", rating = 6.0,
        ))
        assertFalse(LoungeVodEditorialQuality.eligibleForHighlights(
            providerName = "D+ Bug Juice", canonicalTitle = "",
            providerLanguage = null, rating = null,
        ))
    }

    @Test fun excludesPoorAndUnratedTitlesFromEditorialShelves() {
        assertFalse(LoungeVodEditorialQuality.eligibleForHighlights(
            providerName = "Low Quality Film", canonicalTitle = "",
            providerLanguage = null, rating = 4.0,
        ))
        assertFalse(LoungeVodEditorialQuality.eligibleForHighlights(
            providerName = "Free Willy 2", canonicalTitle = "",
            providerLanguage = null, rating = 5.9,
        ))
        assertFalse(LoungeVodEditorialQuality.eligibleForHighlights(
            providerName = "Unrated Classic", canonicalTitle = "",
            providerLanguage = null, rating = null,
        ))
    }

    @Test fun stripsProviderNoiseAndKeepsRemakesSeparate() {
        assertEquals("bug juice", LoungeVodEditorialQuality.key("D+ Bug Juice"))
        assertEquals(
            LoungeVodEditorialQuality.duplicateKey("NF| Dune (2021) [4K]", 2021),
            LoungeVodEditorialQuality.duplicateKey("Dune", 2021),
        )
        assertFalse(
            LoungeVodEditorialQuality.duplicateKey("Dune", 1984) ==
                LoungeVodEditorialQuality.duplicateKey("Dune", 2021),
        )
    }
}
