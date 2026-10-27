package tv.own.owntv.ui.components

import tv.own.owntv.core.metadata.TitleNormalizer

/**
 * Read-only, deterministic presentation guardrails for the user-facing VOD
 * catalogue, global Search, Home, and automatic editorial highlights.
 * Suppressed items are hidden in the UI after every import, while provider
 * streams, metadata rows and customer playback state remain unchanged.
 */
internal object LoungeVodEditorialQuality {
    // Only EXPLICIT provider markers. Words in genuine film titles (e.g. nationality
    // names) are not language evidence, and unknown metadata is not treated as foreign.
    private val foreignPrefix = Regex(
        """(?i)^\s*(?:\[(?:ES|ESP|PT|POR|FR|FRA|DE|GER|IT|ITA|RU|RUS)\]\s*|(?:ES|ESP|PT|POR|FR|FRA|DE|GER|IT|ITA|RU|RUS)\s*[-:|]\s*)"""
    )
    private val foreignSuffix = Regex(
        """(?i)\s*[\[(](?:ES|ESP|PT|POR|FR|FRA|DE|GER|IT|ITA|RU|RUS)[\])]\s*$"""
    )
    private val foreignLanguage = Regex(
        """(?i)^(?:es|spa|spanish|pt|por|portuguese|fr|fra|french|de|ger|german|it|ita|italian|ru|rus|russian)$"""
    )
    // Persistently suppress explicit exploitation, adult-marketed and poor-copy records
    // by normalized title, not ephemeral provider/Room IDs. This still applies after a
    // BEST playlist reimport, profile refresh or reordered category.
    // Avoid generic words alone (e.g. "Naked Gun", "Sex Education", "Sexy Beast")
    // so legitimate mainstream titles are not accidentally removed.
    private val explicitAdultTitle = Regex(
        """(?i)\b(?:xxx|porn(?:o|ography)?|erotic(?:a)?|softcore|hardcore|hentai|fetish|orgy|onlyfans|sexploitation|striptease|stripper|strippers|playboy|playmate|escort|bondage|sex\s+(?:tape|party|scenes|compilation|collection|videos)|sexy\s+(?:movies|girls|babes)|nude\s+(?:models|women|girls|videos)|naked\s+(?:women|girls|babes)|adult\s+(?:film|movies|videos))\b"""
    )
    private val badProviderPrint = Regex(
        """(?i)(?:\[(?:CAM|CAMRIP|HDTS|TS|WORKPRINT)\]|\b(?:CAMRIP|TELESYNC|WORKPRINT)\b)"""
    )

    /** Global browse/search ban for obviously unwanted or very poorly rated content.
     * Unknown scores remain eligible: old over-strict allowlists removed most VOD.
     * No provider rows, stream URLs, or user state are ever deleted.
     */
    fun publicBrowseEligible(
        providerName: String,
        canonicalTitle: String,
        providerLanguage: String?,
        rating: Double?,
    ): Boolean {
        if (providerName.isBlank() || !LoungeVodCatalogueCleanup.visibleEnglish(providerName, providerLanguage)) return false
        if (explicitAdultTitle.containsMatchIn(providerName) ||
            explicitAdultTitle.containsMatchIn(canonicalTitle) ||
            badProviderPrint.containsMatchIn(providerName)) return false
        // Ratings of zero are generally provider placeholders, not measured votes.
        if (rating != null && rating > 0.0 && rating < 5.0) return false
        return true
    }

    private val keyPunctuation = Regex("""[^\p{L}\p{N}]+""")
    private val multiSpace = Regex("""\s+""")

    fun eligibleForHighlights(
        providerName: String,
        canonicalTitle: String,
        providerLanguage: String?,
        rating: Double?,
    ): Boolean {
        if (providerName.isBlank()) return false
        if (!publicBrowseEligible(providerName, canonicalTitle, providerLanguage, rating)) return false
        if (foreignPrefix.containsMatchIn(providerName) || foreignSuffix.containsMatchIn(providerName)) return false
        if (providerLanguage?.trim()?.let(foreignLanguage::matches) == true) return false
        // Editorial shelves must be demonstrably well rated: no anonymous/unrated
        // titles or low-rated C-list titles immediately after Featured/New Releases.
        if (rating == null || !rating.isFinite() || rating < 6.5) return false
        return key(canonicalTitle.ifBlank { providerName }).isNotBlank()
    }

    fun key(raw: String): String = TitleNormalizer.normalize(loungeVodTitle(raw)).query
        .lowercase()
        .replace(keyPunctuation, " ")
        .trim()
        .replace(multiSpace, " ")

    fun duplicateKey(raw: String, year: Int?): String =
        key(raw) + (year?.takeIf { it in 1900..2099 }?.let { " " + it } ?: "")
}
