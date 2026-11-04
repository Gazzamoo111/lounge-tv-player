package tv.own.owntv.features.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.Match
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal const val BACKEND_CONFIDENCE_PREFIX = "__LTV_BACKEND__:"

internal class BackendSportsCandidateProvider(
    private val endpoint: String = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-sports-candidates-qa",
    private val playbackEndpoint: String = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-xtream",
) {
    suspend fun options(match: Match, source: SourceEntity): List<WatchOption> = withContext(Dispatchers.IO) {
        val username = source.username?.takeIf(String::isNotBlank) ?: return@withContext emptyList()
        val password = source.password?.takeIf(String::isNotBlank) ?: return@withContext emptyList()
        val home = match.home?.team?.name.orEmpty()
        val away = match.away?.team?.name.orEmpty()
        val params = linkedMapOf(
            "contractVersion" to "2",
            "eventId" to match.key,
            "sportId" to match.sport.path,
            "competitionId" to match.feed,
            "scheduledStartUtc" to Instant.ofEpochMilli(match.startMs).toString(),
            "status" to match.state.name,
            "sport" to match.sport.path,
            "league" to match.leagueName,
            "title" to match.title,
            "home" to home,
            "away" to away,
            "broadcasters" to match.broadcasts.joinToString("|") { it.name },
            "limit" to "6",
        )
        val query = params.entries.joinToString("&") { (key, value) -> q(key) + "=" + q(value) }
        val connection = (URL(endpoint + "?" + query).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val response = JSONObject(body)
            val version = response.optInt("contractVersion", 1)
            val now = System.currentTimeMillis()
            if (version !in 1..2) return@withContext emptyList()
            if (version == 2 && (response.optString("eventId") != match.key ||
                parseUtc(response.optString("expiresAtUtc")) <= now ||
                response.optBoolean("catalogueStale", true))) return@withContext emptyList()
            val rows = response.optJSONArray("candidates") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val providerItemId = row.optLong("providerItemId", 0L).takeIf { it > 0L } ?: continue
                    val name = row.optString("displayName", row.optString("name")).takeIf(String::isNotBlank) ?: continue
                    val evidenceValid = version == 2 && candidateEvidenceValid(row, match, now) && !response.optBoolean("catalogueStale", true)
                    if (tv.own.owntv.features.live.SportsChannelPolicy.excludedBest(providerItemId.toString(), name)) continue
                    // Keep unverified catalogue discovery in the secondary alternatives only.
                    // Do not let a name/fixture guess masquerade as programme evidence.
                    val tierName = if (evidenceValid) row.optString("tier") else "unverified"
                    if (tierName !in setOf("verified", "plausible", "unverified")) continue
                    val streamUrl = playbackEndpoint + "/sport/_/_/" + providerItemId + ".ts" +
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
                            tier = validatedBackendTier(version, tierName, evidenceValid),
                            guideTitle = BACKEND_CONFIDENCE_PREFIX + tierName,
                        ),
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun q(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

/** Merge strongest evidence first; backend presence never discards local alternatives. */
internal fun mergeBackendSportsOptions(backend: List<WatchOption>, local: List<WatchOption>): List<WatchOption> =
    (backend + local)
        .sortedBy { it.tier.ordinal }
        .distinctBy { it.channel.sourceId to (it.channel.remoteId?.removePrefix("sport-backend:") ?: it.channel.id.toString()) }
        .take(8)

internal fun mergeBackendSportsOptions(match: Match, backend: List<WatchOption>, local: List<WatchOption>): List<WatchOption> {
    val soccer = match.sport.path.contains("soccer", ignoreCase = true) ||
        match.leagueName.contains("football", ignoreCase = true) ||
        match.leagueName.contains("champions league", ignoreCase = true) ||
        match.leagueName.contains("premier league", ignoreCase = true)
    val noisySoccerNetwork = Regex("\\b(?:STAN\\s+SPORT|PARAMOUNT(?:\\s*\\+|\\s+PLUS)?)\\b", RegexOption.IGNORE_CASE)
    val cleanLocal = if (!soccer) local else local.filterNot { noisySoccerNetwork.containsMatchIn(it.channel.name) }
    return mergeBackendSportsOptions(backend, cleanLocal)
}

internal fun validatedBackendTier(version: Int, tier: String, evidenceValid: Boolean): WatchTier = when {
    version == 2 && evidenceValid && tier == "verified" -> WatchTier.GUIDE
    version == 2 && evidenceValid && tier == "plausible" -> WatchTier.EVENT
    else -> WatchTier.LIKELY
}

private fun parseUtc(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(Long.MIN_VALUE)

/** Unknown intervals/identity fail closed; labels never establish trust. */
private fun candidateEvidenceValid(row: JSONObject, match: Match, now: Long): Boolean {
    if (row.optString("sourceId") != "best" || row.optString("serviceMarket").isBlank() ||
        !row.optBoolean("identityReviewed") || parseUtc(row.optString("expiresAtUtc")) <= now) return false
    val evidence = row.optJSONArray("evidence") ?: return false
    return (0 until evidence.length()).any { i ->
        val e = evidence.optJSONObject(i) ?: return@any false
        val observed = parseUtc(e.optString("observedAtUtc"))
        val start = parseUtc(e.optString("startUtc"))
        val end = parseUtc(e.optString("endUtc"))
        val ageLimit = if (match.startMs > now) 86_400_000L else 300_000L
        val evidenceType = e.optString("type")
        val claimTier = row.optString("tier")
        val typeSupportsClaim = when (claimTier) {
            "verified" -> evidenceType in setOf("programme", "event-feed")
            "plausible" -> evidenceType in setOf("programme", "event-feed", "assignment")
            else -> false
        }
        e.optString("eventId") == match.key && e.optString("source").isNotBlank() &&
            now - observed in 0..ageLimit && start < end &&
            start <= match.startMs + 900_000L && end > match.startMs &&
            e.optString("sportId") == match.sport.path && e.optString("competitionId") == match.feed &&
            e.optString("serviceId") == row.optString("logicalServiceId") &&
            e.optString("market") == row.optString("serviceMarket") &&
            !e.optBoolean("contentMismatch") && typeSupportsClaim
    }
}
