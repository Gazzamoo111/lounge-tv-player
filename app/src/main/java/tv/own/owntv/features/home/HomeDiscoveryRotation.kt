package tv.own.owntv.features.home

/** Six-hour cache windows; only local catalogue reads, never a network refresh. */
internal object HomeDiscoveryRotation {
    const val WINDOW_MS = 6 * 60 * 60 * 1000L
    fun window(nowMs: Long): Long = Math.floorDiv(nowMs, WINDOW_MS)
    fun expired(loadedAt: Long?, nowMs: Long): Boolean = loadedAt == null || window(loadedAt) != window(nowMs)
    fun <T> rotate(items: List<T>, profileId: Long, nowMs: Long): List<T> {
        if (items.size < 2) return items
        val start = Math.floorMod(window(nowMs) * 7 + profileId, items.size.toLong()).toInt()
        return items.drop(start) + items.take(start)
    }
}
