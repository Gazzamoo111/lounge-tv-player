package tv.own.owntv.ui.components

/** A collection narrows the existing paged catalogue, never the stored library. */
internal data class LoungeBrowseFilter(
    val ids: Set<Long>? = null,
    val releaseYears: IntRange? = null,
) {
    fun accepts(id: Long, year: Int?): Boolean =
        (ids == null || id in ids) && (releaseYears == null || (year != null && year in releaseYears))
}

internal object LoungeBrowseRules {
    const val PREVIEW_LIMIT = 16
    fun columns(usableDesignWidth: Float): Int = when {
        usableDesignWidth >= 1400f -> 6
        usableDesignWidth >= 1100f -> 5
        else -> 4
    }
    fun restoreIndex(ids: List<Long?>, id: Long?, previousIndex: Int): Int =
        ids.indexOf(id).takeIf { id != null && it >= 0 }
            ?: previousIndex.coerceIn(0, (ids.size - 1).coerceAtLeast(0))
}
