package tv.own.owntv.features.live

import java.text.Normalizer
import java.util.Locale
import tv.own.owntv.core.metadata.MetadataSearchResult
import tv.own.owntv.core.metadata.MetadataType

/** Strict identification, independent of playback and VOD activation. Ambiguity means channel art. */
internal object ProgrammeArtworkMatch {
    data class Query(val title: String, val year: Int?, val type: MetadataType?)
    private val yearTag = Regex("\\s*\\(((?:19|20)\\d{2})\\)\\s*$")
    private val episodeTag = Regex("\\s+S\\d{1,2}\\s*E\\d{1,3}.*$", RegexOption.IGNORE_CASE)
    private val prefix = Regex("^(?:new|live|movie|film):\\s*", RegexOption.IGNORE_CASE)
    private val providerPrefix = Regex("^(?:(?:NZ|AU|AUS|UK|US|USA|CA|EN)\\s*[|:•]\\s*)+", RegexOption.IGNORE_CASE)
    private val loopPrefix = Regex("^(?:24\\s*/\\s*7\\s*[·•|:–-]?\\s*)+", RegexOption.IGNORE_CASE)
    private val generic = setOf("news", "live", "movie", "film", "to be announced", "tba", "no information", "no programme information", "off air", "sport", "sports")

    fun normalize(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    fun query(title: String, year: Int? = null, type: MetadataType? = null): Query? {
        val cleaned = cleanTitle(title)
        val withoutEpisode = cleaned.replace(episodeTag, "")
        val taggedYear = yearTag.find(withoutEpisode)?.groupValues?.get(1)?.toIntOrNull()
        val episodic = episodeTag.containsMatchIn(cleaned)
        val name = withoutEpisode.replace(yearTag, "").trim()
        if (normalize(name).length < 3 || normalize(name) in generic) return null
        return Query(name, year ?: taggedYear, type ?: if (episodic) MetadataType.TV else null)
    }

    fun isSynthetic(title: String?): Boolean = title != null && loopPrefix.containsMatchIn(title.trim().replace(providerPrefix, ""))

    private fun cleanTitle(title: String): String = title.trim().replace(providerPrefix, "")
        .replace(prefix, "").replace(loopPrefix, "").replace(providerPrefix, "").replace(prefix, "")

    /** Channel-themed poster when a 24/7 channel's EPG doesn't identify a real current film.
     * This is never evidence of the exact content being broadcast. */
    fun channelThemeTitle(channelName: String?, synthetic247Programme: Boolean = false): String? {
        val title = channelName?.trim()?.replace(providerPrefix, "") ?: return null
        if (!synthetic247Programme && !loopPrefix.containsMatchIn(title)) return null
        val cleaned = title.replace(loopPrefix, "").trim()
        val normalized = normalize(cleaned)
        if (normalized.length < 3 || normalized in generic) return null
        return cleaned
    }

    /** Accept a missing English article only when a single real metadata identity survives.
     * Never use popularity to guess between films, seasons, remakes or same-title results. */
    private fun withoutArticles(text: String): String = normalize(text)
        .split(" ").let { words -> if (words.firstOrNull() in setOf("the", "a", "an")) words.drop(1) else words }.joinToString(" ")

    fun select(query: Query, candidates: List<MetadataSearchResult>): MetadataSearchResult? {
        val key = normalize(query.title)
        val relaxedKey = withoutArticles(query.title)
        val allowed = candidates.filter {
            (query.type == null || it.type == query.type) && (query.year == null || query.year == it.year)
        }.distinctBy { it.type to it.tmdbId }
        // An exact title takes precedence; only try article-insensitive identity when none exists.
        val exact = allowed.filter { normalize(it.title) == key || it.originalTitle?.let(::normalize) == key }
        val matches = if (exact.isNotEmpty()) exact else allowed.filter {
            withoutArticles(it.title) == relaxedKey || it.originalTitle?.let(::withoutArticles) == relaxedKey
        }
        // Popularity cannot identify which remake or same-named series the EPG means.
        return matches.singleOrNull()?.takeIf { !it.posterPath.isNullOrBlank() }
    }
}
