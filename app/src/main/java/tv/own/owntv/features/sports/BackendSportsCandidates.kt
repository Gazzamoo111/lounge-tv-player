package tv.own.owntv.features.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.Match
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

internal const val BACKEND_CONFIDENCE_PREFIX = "__LTV_BACKEND__:"

internal class BackendSportsCandidateProvider(
    private val endpoint: String = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-sports-candidates",
    private val playbackEndpoint: String = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-xtream",
    private val qaEndpoint: String = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-sports-candidates-qa",
    private val qaPlaybackEndpoint: String = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-xtream-qa-next",
    private val loungeTv2: Boolean = tv.own.owntv.BuildConfig.LOUNGE_TV_2,
) {
    suspend fun options(match: Match, source: SourceEntity): List<WatchOption> = withContext(Dispatchers.IO) {
        val username = source.username?.takeIf(String::isNotBlank) ?: return@withContext emptyList()
        val password = source.password?.takeIf(String::isNotBlank) ?: return@withContext emptyList()
        val home = match.home?.team?.name.orEmpty()
        val away = match.away?.team?.name.orEmpty()
        // QA tennis uses the independently evidenced contract-v2 matcher. The legacy
        // endpoint has no tennis-specific network branch and can return no usable channels.
        // This is QA-only and never changes the production candidate service or catalogue.
        val qaTennis = loungeTv2 &&
            match.sport == tv.own.owntv.core.sports.model.Sport.TENNIS
        val params = if (qaTennis) linkedMapOf(
            "contractVersion" to "2",
            "eventId" to match.key,
            "sportId" to match.sport.name,
            "competitionId" to match.leagueSlug.ifBlank { match.feed },
            "scheduledStartUtc" to Instant.ofEpochMilli(match.startMs).toString(),
            "status" to match.state.name,
            "title" to match.title,
            "home" to home,
            "away" to away,
            "limit" to "12",
        ) else linkedMapOf(
            "sport" to match.sport.path,
            "league" to match.leagueName,
            "title" to match.title,
            "home" to home,
            "away" to away,
            "broadcasters" to match.broadcasts.joinToString("|") { it.name },
        )
        val query = params.entries.joinToString("&") { (key, value) -> q(key) + "=" + q(value) }
        val target = if (qaTennis) qaEndpoint else endpoint
        val connection = (URL(target + "?" + query).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val rows = JSONObject(body).optJSONArray("candidates") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val providerItemId = row.optLong("providerItemId", 0L).takeIf { it > 0L } ?: continue
                    val name = row.optString(if (qaTennis) "displayName" else "name").takeIf(String::isNotBlank) ?: continue
                    if (loungeTv2 && isRetrospectiveSportsFeed(name)) continue
                    // The QA matcher reports candidates as plausible, not playback-confirmed.
                    // Local fixture/EPG evidence may promote them after inspection.
                    val confidence = if (qaTennis) "maybe" else row.optString("confidence").lowercase()
                    if (confidence !in setOf("definite", "likely", "maybe")) continue
                    // QA tennis must use the isolated repaired gateway, rather than the unchanged
                    // production route that still rejects these otherwise valid candidate streams.
                    val streamUrl = (if (qaTennis) qaPlaybackEndpoint else playbackEndpoint) + "/sport/_/_/" + providerItemId + ".ts" +
                        "?username=" + q(username) + "&password=" + q(password)
                    val channel = ChannelEntity(
                        id = -(8_000_000_000L + providerItemId),
                        sourceId = source.id,
                        name = name,
                        logoUrl = row.optString("logoUrl").takeIf { !row.isNull("logoUrl") && it.isNotBlank() },
                        streamUrl = streamUrl,
                        epgChannelId = row.optString("epgId").takeIf { !row.isNull("epgId") && it.isNotBlank() },
                        remoteId = "sport-backend:" + providerItemId,
                        sortOrder = index,
                    )
                    add(
                        WatchOption(
                            channel = channel,
                            tier = when (confidence) {
                                "definite" -> WatchTier.GUIDE
                                "likely" -> WatchTier.EVENT
                                else -> WatchTier.LIKELY
                            },
                            guideTitle = BACKEND_CONFIDENCE_PREFIX + confidence,
                        ),
                    )
                }
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun q(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

/**
 * Backend Sport Lounge candidates are authoritative. When they exist, weak local LIKELY matches are
 * deliberately discarded so a team word such as "Jazz" cannot promote an unrelated channel.
 * Guide/event/remembered local hits still supplement the backend after its ranked candidates.
 */
internal fun mergeBackendSportsOptions(
    backend: List<WatchOption>,
    local: List<WatchOption>,
): List<WatchOption> {
    if (backend.isEmpty()) return local
    val out = LinkedHashMap<String, WatchOption>()
    fun add(option: WatchOption) {
        val key = option.channel.remoteId?.takeIf(String::isNotBlank)
            ?: option.facets.portableKey
        if (key !in out) out[key] = option
    }
    backend.forEach(::add)
    local.asSequence()
        .filter { it.tier != WatchTier.LIKELY }
        .forEach(::add)
    return out.values.toList()
}
