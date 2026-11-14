package tv.own.owntv.features.shell.components

import tv.own.owntv.core.database.entity.PlaybackProgressEntity

/** The badge values consumed by the existing StagePoster, shared by VOD and Home. */
internal data class VodPosterBadgeState(
    val rating: String? = null,
    val progress: Float? = null,
    val watched: Boolean = false,
)

internal fun vodMovieCompleted(progress: PlaybackProgressEntity): Boolean =
    progress.durationMs > 0 && progress.positionMs >= (progress.durationMs * 0.95f).toLong()

internal fun vodMoviePosterBadges(rating: Double?, progress: PlaybackProgressEntity?): VodPosterBadgeState =
    VodPosterBadgeState(
        rating = rating?.takeIf { it > 0 }?.let(::vodRating),
        progress = progress?.takeIf { it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs },
        watched = progress?.let(::vodMovieCompleted) == true,
    )

internal fun vodSeriesPosterBadges(rating: Double?, progress: Float?): VodPosterBadgeState =
    VodPosterBadgeState(rating = rating?.takeIf { it > 0 }?.let(::vodRating), progress = progress)
