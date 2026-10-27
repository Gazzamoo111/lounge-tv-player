package tv.own.owntv.ui.components

/**
 * High-confidence artwork correction for a known title/year collision.
 * TMDb's Titanic (1997) is movie 597; do not apply the 1997 artwork to
 * Titanic (1953), unrelated films, or an ambiguous title without a year.
 *
 * This only replaces presentation artwork, never TMDb IDs, source streams,
 * content rows, playback, or the original provider metadata.
 */
internal object LoungeVodPosterCorrections {
    const val TITANIC_1997_POSTER = "https://image.tmdb.org/t/p/w500/9xjZS2rlVxm8SFx8kPC3aIGCOYQ.jpg"

    private val yearSuffix = Regex("""\s*[\[(]1997[\])]\s*$""")
    private val platformPrefix = Regex("""(?i)^(?:(?:NF|D\+|AMZN|APTV|HBO|NETFLIX|DISNEY\+|PRIME)\s*[:|\-]?\s*)+""")

    fun moviePoster(
        providerTitle: String,
        canonicalTitle: String,
        releaseYear: Int?,
        existingPoster: String?,
    ): String? {
        if (releaseYear != 1997) return existingPoster
        val title = canonicalTitle.ifBlank { providerTitle }
            .trim()
            .replace(platformPrefix, "")
            .replace(yearSuffix, "")
            .trim()
        return if (title.equals("Titanic", ignoreCase = true)) TITANIC_1997_POSTER else existingPoster
    }
}
