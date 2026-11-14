package tv.own.owntv.ui.components

/** Provider dates only: unknown and implausible timestamps are never recent additions. */
internal object LoungeVodRecentlyAdded {
    private const val DAY_MS = 86_400_000L
    private const val WINDOW_MS = 90L * DAY_MS
    fun since(nowMs: Long): Long = (nowMs - WINDOW_MS).coerceAtLeast(0L)
    fun latestAcceptable(nowMs: Long): Long = nowMs + DAY_MS
    fun isRecent(addedAt: Long?, nowMs: Long): Boolean =
        addedAt != null && addedAt in since(nowMs)..latestAcceptable(nowMs)
}
