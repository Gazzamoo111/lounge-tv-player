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
    private val loopPrefix = Regex("^(?:24\\s*/\\s*7\\s*[·•|:–-]?\\s*)+", RegexOption.IGNORE_CASE)
    private val generic = setOf("news", "live", "movie", "film", "to be announced", "tba", "no information", "no programme information", "off air", "sport", "sports")

    fun normalize(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    fun query(title: String, year: Int? = null, type: MetadataType? = null): Query? {
        val cleaned = title.trim().replace(prefix, "").replace(loopPrefix, "")
        val taggedYear = yearTag.find(cleaned)?.groupValues?.get(1)?.toIntOrNull()
        val episodic = episodeTag.containsMatchIn(cleaned)
        val name = cleaned.replace(yearTag, "").replace(episodeTag, "").trim()
        if (normalize(name).length < 3 || normalize(name) in generic) return null
        return Query(name, year ?: taggedYear, type ?: if (episodic) MetadataType.TV else null)
    }

    /** Channel-themed poster when a 24/7 channel's EPG doesn't identify a real current film.
     * This is never evidence of the exact content being broadcast. */
    fun channelThemeTitle(channelName: String?): String? {
        val title = channelName?.trim() ?: return null
        if (!loopPrefix.containsMatchIn(title)) return null
        val cleaned = title.replace(loopPrefix, "").trim()
        val normalized = normalize(cleaned)
        if (normalized.length < 3 || normalized in generic) return null
        return cleaned
    }

    fun select(query: Query, candidates: List<MetadataSearchResult>): MetadataSearchResult? {
        val key = normalize(query.title)
        val matches = candidates.filter {
            (query.type == null || it.type == query.type) &&
                (query.year == null || query.year == it.year) &&
                (normalize(it.title) == key || it.originalTitle?.let(::normalize) == key)
        }.distinctBy { it.type to it.tmdbId }
        // Popularity cannot identify which remake or same-named series the EPG means.
        return matches.singleOrNull()?.takeIf { !it.posterPath.isNullOrBlank() }
    }
}
