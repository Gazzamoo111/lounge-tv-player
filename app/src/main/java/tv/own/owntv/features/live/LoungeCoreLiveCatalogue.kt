package tv.own.owntv.features.live

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.own.owntv.core.database.entity.ChannelEntity
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Read-only Lounge Core catalogue used by QA builds.
 *
 * The endpoint deliberately contains no BEST credentials or stream URLs. It only returns the
 * provider stream ids already present in the customer's synced Xtream database, plus Lounge's
 * canonical display identity and source priority.
 */
internal data class LoungeCoreLiveSource(
    val streamId: Long,
    val priority: Int,
    val role: String,
    val qualityHint: String?,
    val qualityScore: Double,
    val healthScore: Double,
    val runtimeHealth: String,
)

internal data class LoungeCoreLiveChannel(
    val key: String,
    val name: String,
    val region: String,
    val category: String,
    val subcategory: String?,
    val epgId: String?,
    val order: Int,
    val is24x7: Boolean,
    val sources: List<LoungeCoreLiveSource>,
)

internal data class LoungeCoreLiveSnapshot(
    val schemaVersion: Int,
    val generatedAt: String?,
    val channels: List<LoungeCoreLiveChannel>,
)

internal class LoungeCoreLiveCatalogueClient(
    private val endpoint: String =
        "https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-live-catalogue",
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetch(): LoungeCoreLiveSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", "LoungeTV-CoreQA/1")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                parse(body)
            }
        }.getOrNull()
    }

    internal fun parse(raw: String): LoungeCoreLiveSnapshot {
        val root = JSONObject(raw)
        val channelsJson = root.optJSONArray("channels")
        val channels = buildList {
            if (channelsJson == null) return@buildList
            for (i in 0 until channelsJson.length()) {
                val channel = channelsJson.optJSONObject(i) ?: continue
                val sourcesJson = channel.optJSONArray("sources")
                val sources = buildList {
                    if (sourcesJson == null) return@buildList
                    for (j in 0 until sourcesJson.length()) {
                        val source = sourcesJson.optJSONObject(j) ?: continue
                        val streamId = source.optLong("stream_id", -1L)
                        if (streamId <= 0L) continue
                        add(
                            LoungeCoreLiveSource(
                                streamId = streamId,
                                priority = source.optInt("priority", Int.MAX_VALUE),
                                role = source.optString("role", "candidate"),
                                qualityHint = source.optString("quality_hint")
                                    .takeIf { it.isNotBlank() && it != "null" },
                                qualityScore = source.optDouble("quality_score", 0.0),
                                healthScore = source.optDouble("health_score", 0.0),
                                runtimeHealth = source.optString("runtime_health", "unknown"),
                            ),
                        )
                    }
                }.sortedWith(
                    compareBy<LoungeCoreLiveSource> { it.priority }
                        .thenByDescending { it.healthScore }
                        .thenByDescending { it.qualityScore },
                )

                if (sources.isEmpty()) continue
                val key = channel.optString("key").trim()
                val name = channel.optString("name").trim()
                if (key.isEmpty() || name.isEmpty()) continue

                add(
                    LoungeCoreLiveChannel(
                        key = key,
                        name = name,
                        region = channel.optString("region").trim(),
                        category = channel.optString("category").trim(),
                        subcategory = channel.optString("subcategory")
                            .takeIf { it.isNotBlank() && it != "null" },
                        epgId = channel.optString("epg_id")
                            .takeIf { it.isNotBlank() && it != "null" },
                        order = channel.optInt("order", 1000),
                        is24x7 = channel.optBoolean("is_24_7", false),
                        sources = sources,
                    ),
                )
            }
        }

        return LoungeCoreLiveSnapshot(
            schemaVersion = root.optInt("schema_version", 0),
            generatedAt = root.optString("generated_at")
                .takeIf { it.isNotBlank() && it != "null" },
            channels = channels,
        )
    }
}

/**
 * Converts the raw BEST rows already synced into Room into Lounge's one-channel / hidden-backup model.
 *
 * Independent M3U or other sources are not touched. For the audited BEST source:
 * - anything absent from Lounge Core is hidden from normal browsing;
 * - exactly one locally available row per canonical channel is visible;
 * - all other mapped rows are retained as hidden backups;
 * - the visible row gets Lounge's canonical name, EPG id and ordering in memory only.
 *
 * No provider rows are deleted or rewritten.
 */
internal object LoungeCoreLiveMapper {
    data class CanonicalPresentation(
        val key: String,
        val name: String,
        val region: String,
        val category: String,
        val subcategory: String?,
        val epgId: String?,
        val order: Int,
        val source: LoungeCoreLiveSource,
    )

    data class Category(
        val key: String,
        val title: String,
        val order: Int,
        val channelIds: List<Long>,
    )

    data class Plan(
        val hiddenIds: Set<Long> = emptySet(),
        val presentationByChannelId: Map<Long, CanonicalPresentation> = emptyMap(),
        val backupIdsByPrimaryId: Map<Long, List<Long>> = emptyMap(),
        val canonicalKeyByChannelId: Map<Long, String> = emptyMap(),
        val categories: List<Category> = emptyList(),
        val mappedBestCount: Int = 0,
        val unmappedBestCount: Int = 0,
    ) {
        fun present(channel: ChannelEntity): ChannelEntity? {
            if (channel.id in hiddenIds) return null
            val canonical = presentationByChannelId[channel.id] ?: return channel
            return channel.copy(
                name = canonical.name,
                epgChannelId = canonical.epgId ?: channel.epgChannelId,
                sortOrder = canonical.order,
            )
        }

        fun backupsFor(channelId: Long): List<Long> =
            backupIdsByPrimaryId[channelId].orEmpty()

        fun categoryFor(customId: String): Category? =
            categories.firstOrNull { it.key == customId }

        fun categoryCount(customId: String): Int =
            categoryFor(customId)?.channelIds?.size ?: 0
    }

    private fun categoryKey(title: String): String {
        val slug = title.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return CATEGORY_KEY_PREFIX + slug
    }

    fun isCategoryKey(customId: String): Boolean =
        customId.startsWith(CATEGORY_KEY_PREFIX)

    private data class Matched(
        val local: ChannelEntity,
        val canonical: LoungeCoreLiveChannel,
        val source: LoungeCoreLiveSource,
    )

    fun plan(
        channels: List<ChannelEntity>,
        auditedSourceIds: Set<Long>,
        snapshot: LoungeCoreLiveSnapshot?,
    ): Plan {
        if (auditedSourceIds.isEmpty() || snapshot == null || snapshot.schemaVersion != 1) {
            return Plan()
        }

        val sourceIndex = HashMap<Long, Pair<LoungeCoreLiveChannel, LoungeCoreLiveSource>>()
        snapshot.channels.forEach { canonical ->
            canonical.sources.forEach { source ->
                val existing = sourceIndex[source.streamId]
                if (existing == null || source.priority < existing.second.priority) {
                    sourceIndex[source.streamId] = canonical to source
                }
            }
        }

        val hidden = HashSet<Long>()
        val canonicalKeyByLocal = HashMap<Long, String>()
        val matched = ArrayList<Matched>()
        var unmapped = 0

        channels.forEach { channel ->
            if (channel.sourceId !in auditedSourceIds) return@forEach

            val streamId = channel.remoteId?.toLongOrNull()
            val mapping = streamId?.let(sourceIndex::get)
            if (mapping == null) {
                hidden += channel.id
                unmapped++
                return@forEach
            }

            val (canonical, source) = mapping
            canonicalKeyByLocal[channel.id] = canonical.key
            matched += Matched(channel, canonical, source)
        }

        val presentation = HashMap<Long, CanonicalPresentation>()
        val backups = HashMap<Long, List<Long>>()

        matched.groupBy { it.canonical.key }.values.forEach { group ->
            val sorted = group.sortedWith(
                compareBy<Matched> { it.source.priority }
                    .thenByDescending { it.source.runtimeHealth == "good" }
                    .thenByDescending { it.source.healthScore }
                    .thenByDescending { it.source.qualityScore }
                    .thenBy { it.local.id },
            )

            val primary = sorted.first()
            presentation[primary.local.id] = CanonicalPresentation(
                key = primary.canonical.key,
                name = primary.canonical.name,
                region = primary.canonical.region,
                category = primary.canonical.category,
                subcategory = primary.canonical.subcategory,
                epgId = primary.canonical.epgId,
                order = primary.canonical.order,
                source = primary.source,
            )

            val backupIds = sorted.drop(1).map { it.local.id }
            hidden += backupIds
            if (backupIds.isNotEmpty()) backups[primary.local.id] = backupIds
        }

        val categories = presentation.entries
            .groupBy { it.value.category.trim() }
            .filterKeys { it.isNotEmpty() }
            .map { (title, entries) ->
                Category(
                    key = categoryKey(title),
                    title = title,
                    order = entries.minOf { it.value.order },
                    channelIds = entries
                        .sortedWith(
                            compareBy<Map.Entry<Long, CanonicalPresentation>> { it.value.order }
                                .thenBy { it.value.name },
                        )
                        .map { it.key },
                )
            }
            .sortedWith(compareBy<Category> { it.order }.thenBy { it.title })

        return Plan(
            hiddenIds = hidden,
            presentationByChannelId = presentation,
            backupIdsByPrimaryId = backups,
            canonicalKeyByChannelId = canonicalKeyByLocal,
            categories = categories,
            mappedBestCount = matched.size,
            unmappedBestCount = unmapped,
        )
    }

    private const val CATEGORY_KEY_PREFIX = "lounge-core:"
}

/**
 * Small in-memory pager for canonical Lounge Core categories.
 *
 * The loader returns already-filtered local Room rows. This class only pages them; it never writes
 * provider data or creates fake Room categories.
 */
internal class LoungeCoreCategoryPagingSource(
    private val loader: suspend () -> List<ChannelEntity>,
) : PagingSource<Int, ChannelEntity>() {
    override fun getRefreshKey(state: PagingState<Int, ChannelEntity>): Int? = state.anchorPosition

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChannelEntity> =
        try {
            val rows = loader()
            val offset = (params.key ?: 0).coerceIn(0, rows.size)
            val end = (offset + params.loadSize).coerceAtMost(rows.size)
            LoadResult.Page(
                data = rows.subList(offset, end),
                prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                nextKey = if (end == rows.size) null else end,
                itemsBefore = offset,
                itemsAfter = rows.size - end,
            )
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
}
