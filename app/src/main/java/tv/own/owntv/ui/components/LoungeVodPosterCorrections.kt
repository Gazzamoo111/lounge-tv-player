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
        // Provider titles explicitly marked (1997) are stronger evidence than a
        // stale or incorrect cached TMDb release year. An unqualified "Titanic"
        // must still have its resolved 1997 year to receive this correction.
        val providerSays1997 = yearSuffix.containsMatchIn(providerTitle)
        if (releaseYear != 1997 && !providerSays1997) return existingPoster
        val titles = if (providerSays1997) listOf(providerTitle) else listOf(canonicalTitle.ifBlank { providerTitle })
        return if (titles.any { raw ->
                raw.trim().replace(platformPrefix, "").replace(yearSuffix, "")
                    .trim().equals("Titanic", ignoreCase = true)
            }) TITANIC_1997_POSTER else existingPoster
    }
}
