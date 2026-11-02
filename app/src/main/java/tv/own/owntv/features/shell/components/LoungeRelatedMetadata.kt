package tv.own.owntv.features.shell.components

import org.json.JSONArray
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.ui.components.LoungeDiscoveryRules

internal data class LoungeRelatedSelection<T>(val items: List<T> = emptyList(), val genreMatched: Boolean = false, val ownerId: Long? = null)

/** At most 40 already-visible candidates; two batch cache reads, no network or catalogue scan. */
internal suspend fun <T> cachedRelatedTitles(
    database: OwnTVDatabase,
    target: LoungeDiscoveryRules.Candidate,
    candidates: List<T>,
    signal: (T) -> LoungeDiscoveryRules.Candidate,
    localKey: (T) -> String,
    series: Boolean,
    language: String,
    kids: Boolean,
    metadataEnabled: Boolean,
): LoungeRelatedSelection<T> {
    val bounded = candidates.distinctBy { signal(it).id }.take(40)
    val keys = bounded.associateBy { localKey(it) + if (kids) ":kids" else "" }
    val dao = database.metadataDao()
    val matches = if (metadataEnabled) dao.getMatches(keys.keys.toList()) else emptyList()
    fun cacheKey(id: Int) = if (series) MetadataRepository.tvCacheKey(id, language) else MetadataRepository.cacheKey(id, language)
    val cacheKeys = matches.mapNotNull { it.tmdbId?.let(::cacheKey) }.distinct()
    val caches = if (cacheKeys.isEmpty()) emptyMap() else dao.getCaches(cacheKeys).associateBy { it.key }
    val byId = matches.mapNotNull { match ->
        val item = keys[match.localKey] ?: return@mapNotNull null
        val cache = match.tmdbId?.let { caches[cacheKey(it)] } ?: return@mapNotNull null
        signal(item).id to cache
    }.toMap()
    val ranked = LoungeDiscoveryRules.related(target, bounded.map { item ->
        val base = signal(item)
        base.copy(genres = genreNames(byId[base.id]), rating = byId[base.id]?.rating ?: base.rating)
    })
    val itemsById = bounded.associateBy { signal(it).id }
    return LoungeRelatedSelection(ranked.ids.mapNotNull(itemsById::get), ranked.genreMatched, target.id)
}

internal fun genreNames(meta: MetadataCacheEntity?): Set<String> = runCatching {
    val array = JSONArray(meta?.genresJson ?: "[]")
    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
}.getOrDefault(emptySet())
