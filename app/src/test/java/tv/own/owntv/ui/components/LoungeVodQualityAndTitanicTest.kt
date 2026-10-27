package tv.own.owntv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoungeVodQualityAndTitanicTest {
    @Test fun `adult and sexually explicit VOD titles never reappear in browse`() {
        for (title in listOf("XXX Night", "Nude Models", "Erotica Collection",
            "Sex Tape", "OnlyFans Specials", "Sexy Movies", "The Stripper")) {
            assertFalse(title, LoungeVodEditorialQuality.publicBrowseEligible(title, title, "en", 8.0))
        }
    }

    @Test fun `poor provider scores and bootleg print variants are excluded`() {
        assertFalse(LoungeVodEditorialQuality.publicBrowseEligible("Poor Movie", "Poor Movie", "en", 3.2))
        assertFalse(LoungeVodEditorialQuality.publicBrowseEligible("Movie [CAM]", "Movie", "en", 9.2))
        assertFalse(LoungeVodEditorialQuality.publicBrowseEligible("[DE] Film", "Film", null, 9.0))
    }

    @Test fun `mainstream and unknown rating movies survive conservative curation`() {
        assertTrue(LoungeVodEditorialQuality.publicBrowseEligible("Titanic (1997)", "Titanic", "en", 7.8))
        assertTrue(LoungeVodEditorialQuality.publicBrowseEligible("The Naked Gun", "The Naked Gun", "en", 7.3))
        assertTrue(LoungeVodEditorialQuality.publicBrowseEligible("Sex Education", "Sex Education", "en", 8.2))
        assertTrue(LoungeVodEditorialQuality.publicBrowseEligible("Sexy Beast", "Sexy Beast", "en", 7.4))
        assertTrue(LoungeVodEditorialQuality.publicBrowseEligible("The French Connection", "The French Connection", null, null))
        assertTrue(LoungeVodEditorialQuality.publicBrowseEligible("Italian Job", "The Italian Job", "English", 6.8))
    }

    @Test fun `editorial shelves no longer contain unknown or middling quality`() {
        assertFalse(LoungeVodEditorialQuality.eligibleForHighlights("Mystery B Movie", "Mystery B Movie", "en", null))
        assertFalse(LoungeVodEditorialQuality.eligibleForHighlights("Below Standard", "Below Standard", "en", 6.3))
        assertTrue(LoungeVodEditorialQuality.eligibleForHighlights("Titanic", "Titanic", "en", 7.9))
    }

    @Test fun `Titanic artwork pins 1997 only and does not corrupt older films`() {
        val wrong = "https://example.com/poster-titanic-1953.jpg"
        val fixed = LoungeVodPosterCorrections.moviePoster("Titanic (1997)", "Titanic", 1997, wrong)
        assertEquals(LoungeVodPosterCorrections.TITANIC_1997_POSTER, fixed)
        // A stale external lookup may supply 1953, or no year at all, even
        // though the provider's actual record explicitly identifies 1997.
        assertEquals(LoungeVodPosterCorrections.TITANIC_1997_POSTER,
            LoungeVodPosterCorrections.moviePoster("Titanic (1997)", "Titanic", null, wrong))
        assertEquals(LoungeVodPosterCorrections.TITANIC_1997_POSTER,
            LoungeVodPosterCorrections.moviePoster("Titanic (1997)", "Titanic", 1953, wrong))
        assertEquals(wrong, LoungeVodPosterCorrections.moviePoster("Titanic", "Titanic", 1953, wrong))
        assertEquals(wrong, LoungeVodPosterCorrections.moviePoster("Titanic", "Titanic", null, wrong))
        assertEquals(wrong, LoungeVodPosterCorrections.moviePoster("Raise the Titanic", "Raise the Titanic", 1997, wrong))
    }
}
