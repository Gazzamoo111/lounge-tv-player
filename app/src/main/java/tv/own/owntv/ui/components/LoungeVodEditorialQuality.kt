package tv.own.owntv.ui.components

import tv.own.owntv.core.metadata.TitleNormalizer

/**
 * Presentation-only guardrails for automatic Movie and Series highlights.
 * Never removes, mutates, syncs or hides a provider catalogue item. Explicit
 * language markers and weak provider ratings prevent those items from being
 * prominently recommended; they remain searchable and playable in All.
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
    private val keyPunctuation = Regex("""[^\p{L}\p{N}]+""")
    private val multiSpace = Regex("""\s+""")

    fun eligibleForHighlights(
        providerName: String,
        canonicalTitle: String,
        providerLanguage: String?,
        rating: Double?,
    ): Boolean {
        if (providerName.isBlank()) return false
        if (foreignPrefix.containsMatchIn(providerName) || foreignSuffix.containsMatchIn(providerName)) return false
        if (providerLanguage?.trim()?.let(foreignLanguage::matches) == true) return false
        // Keep unrated titles eligible where the provider has no rating data.
        // Ratings known to be poor should not populate the first screen.
        if (rating != null && rating in 0.1..5.49) return false
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
