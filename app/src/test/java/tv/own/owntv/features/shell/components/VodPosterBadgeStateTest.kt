package tv.own.owntv.features.shell.components

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.model.MediaType

class VodPosterBadgeStateTest {
    private fun progress(position: Long, duration: Long) = PlaybackProgressEntity(
        profileId = 1, mediaType = MediaType.MOVIE, itemId = 1,
        positionMs = position, durationMs = duration,
    )

    @Test fun unratedUnwatchedTitlesDoNotInventBadges() {
        assertEquals(VodPosterBadgeState(), vodMoviePosterBadges(null, null))
        assertNull(vodMoviePosterBadges(0.0, null).rating)
        assertNull(vodMoviePosterBadges(-1.0, null).rating)
    }

    @Test fun ratingUsesTheExistingVodFormatter() {
        assertEquals(vodRating(8.4), vodMoviePosterBadges(8.4, null).rating)
    }

    @Test fun inProgressAndWatchedBadgesUseTheVodCompletionThreshold() {
        val underway = vodMoviePosterBadges(7.5, progress(50, 100))
        assertEquals(0.5f, requireNotNull(underway.progress), 0f)
        assertFalse(underway.watched)
        assertFalse(vodMoviePosterBadges(null, progress(94, 100)).watched)
        assertTrue(vodMoviePosterBadges(null, progress(95, 100)).watched)
        assertTrue(vodMoviePosterBadges(null, progress(1, 1)).watched)
        assertNull(vodMoviePosterBadges(null, progress(100, 0)).progress)
    }
}
