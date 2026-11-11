package tv.own.owntv.features.sports

internal object SportsCarouselPolicy {
    const val IDLE_MS = 20_000L
    fun canRotate(now: Long, lastInput: Long, count: Int, previewFocused: Boolean): Boolean =
        count > 1 && !previewFocused && now - lastInput >= IDLE_MS
}
