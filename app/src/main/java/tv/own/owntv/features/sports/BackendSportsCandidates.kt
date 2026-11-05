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

internal const val QA_SPORTS_IMPLEMENTATION = "qa-event-streams-v4"

internal const val BACKEND_CONFIDENCE_PREFIX = "__LTV_BACKEND__:"

data class SportsStreamCoverage(
    val broadcastConfirmed: Int = 0,
    val playbackVerified: Int = 0,
    val coverageGap: Boolean = true,
    val reasons: List<String> = emptyList(),
)

data class EventStreamResolution(val options: List<WatchOption>, val coverage: SportsStreamCoverage)

internal class BackendSportsCandidateProvider(
    private val endpoint: String = tv.own.owntv.BuildConfig.SPORTS_CANDIDATES_URL,
    private val playbackEndpoint: String = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-xtream",
) {
    suspend fun options(match: Match, source: SourceEntity): List<WatchOption> = resolve(match, source).options

    suspend fun resolve(match: Match, source: SourceEntity): EventStreamResolution = withContext(Dispatchers.IO) {
        fun unavailable(reason: String) = EventStreamResolution(emptyList(), SportsStreamCoverage(reasons = listOf(reason)))
        if (endpoint.isBlank()) return@withContext unavailable("qa-endpoint-not-configured")
        val username = source.username?.takeIf(String::isNotBlank) ?: return@withContext unavailable("source-not-configured")
        val password = source.password?.takeIf(String::isNotBlank) ?: return@withContext unavailable("source-not-configured")
        val params = linkedMapOf(
            "contractVersion" to "2", "eventId" to match.key, "sportId" to match.sport.path,
            "competitionId" to match.feed, "scheduledStartUtc" to Instant.ofEpochMilli(match.startMs).toString(),
            "status" to match.state.name, "limit" to "12",
        )
        val query = params.entries.joinToString("&") { (k,v) -> q(k)+"="+q(v) }
        val connection = (URL(endpoint+"?"+query).openConnection() as HttpURLConnection).apply {
            connectTimeout=8_000; readTimeout=12_000; requestMethod="GET"; setRequestProperty("Accept","application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext unavailable("event-evidence-unavailable")
            val response=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val now=System.currentTimeMillis()
            if (response.optString("implementationId") != QA_SPORTS_IMPLEMENTATION || response.optInt("contractVersion") != 2 ||
                response.optString("eventId") != match.key || parseUtc(response.optString("expiresAtUtc")) <= now ||
                response.optBoolean("catalogueStale",true)) return@withContext unavailable("backend-contract-or-freshness-failed")
            val rows=response.optJSONArray("candidates") ?: return@withContext unavailable("malformed-candidates")
            val services=hashSetOf<String>()
            val options=buildList {
                for (index in 0 until minOf(rows.length(),12)) {
                    val row=rows.optJSONObject(index) ?: continue
                    val id=row.optLong("providerItemId",0).takeIf { it>0 } ?: continue
                    val name=row.optString("displayName").takeIf(String::isNotBlank) ?: continue
                    val tier=row.optString("tier")
                    if (tier !in setOf("verified","plausible") || !candidateEvidenceValid(row,match,now) || !candidatePlaybackValid(row,now,playbackEndpoint)) continue
                    if (tv.own.owntv.features.live.SportsChannelPolicy.excludedBest(id.toString(),name)) continue
                    if (!services.add(row.optString("logicalServiceId"))) continue
                    val streamUrl=playbackEndpoint+"/sport/_/_/"+id+".ts?username="+q(username)+"&password="+q(password)
                    add(WatchOption(channel=ChannelEntity(id=-(8_000_000_000L+id),sourceId=source.id,name=name,
                        streamUrl=streamUrl,epgChannelId=row.optString("epgId").takeIf { !row.isNull("epgId")&&it.isNotBlank() },
                        remoteId="sport-backend:"+id,sortOrder=index),tier=validatedBackendTier(2,tier,true),
                        guideTitle=BACKEND_CONFIDENCE_PREFIX+tier))
                }
            }
            val c=response.optJSONObject("coverage")
            val reasons=c?.optJSONArray("reasons")
            EventStreamResolution(options,SportsStreamCoverage(
                broadcastConfirmed=c?.optInt("broadcastConfirmed",0) ?: 0,
                playbackVerified=options.size,
                coverageGap=options.size<3,
                reasons=if(reasons==null) emptyList() else (0 until reasons.length()).map { reasons.optString(it) },
            ))
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { unavailable("backend-unavailable")
        } finally { connection.disconnect() }
    }
    private fun q(value:String):String=URLEncoder.encode(value,Charsets.UTF_8.name())
}

internal fun candidatePlaybackValid(row:JSONObject,now:Long,playbackEndpoint:String):Boolean {
    val origin=runCatching { java.net.URI(playbackEndpoint) }.getOrNull() ?: return false
    val host=origin.host ?: return false
    val path=origin.path.trimEnd('/')+"/sport/_/_/"+row.optString("providerItemId")+".ts"
    val identity=host+"|"+path+"|"+row.optString("providerItemId")+"|"+row.optString("logicalServiceId")
    val expected=java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val h=row.optJSONObject("playbackObservation") ?: return false
    val at=parseUtc(h.optString("observedAtUtc"))
    return h.optString("status")=="verified" && h.optBoolean("httpAccessible") && h.optInt("decodedFrames",0)>0 &&
        now-at in 0..300_000L && h.optString("providerItemId")==row.optString("providerItemId") &&
        h.optString("serviceId")==row.optString("logicalServiceId") &&
        expected == h.optString("streamFingerprint")
}

/** Merge strongest evidence first; backend presence never discards local alternatives. */
internal fun mergeBackendSportsOptions(backend: List<WatchOption>, local: List<WatchOption>): List<WatchOption> =
    (backend + local)
        .sortedBy { it.tier.ordinal }
        .distinctBy { it.channel.sourceId to (tv.own.owntv.features.live.LoungeSoccerCatalogue.reviewedService(it.channel)
            ?: (it.facets.country.orEmpty() + ":" + it.facets.portableKey)) }
        .take(8)

internal fun mergeBackendSportsOptions(match: Match, backend: List<WatchOption>, local: List<WatchOption>): List<WatchOption> {
    val soccer = match.sport.path.contains("soccer", ignoreCase = true) ||
        match.leagueName.contains("football", ignoreCase = true) ||
        match.leagueName.contains("champions league", ignoreCase = true) ||
        match.leagueName.contains("premier league", ignoreCase = true)
    val keys = tv.own.owntv.core.sports.WatchMatcher.Keys(match)
    val cleanLocal = local.filterNot {
        tv.own.owntv.features.live.SportsChannelPolicy.excludedBest(it.channel.remoteId, it.channel.name)
    }.mapNotNull { option ->
        val guideMatches = option.tier == WatchTier.GUIDE &&
            !tv.own.owntv.features.live.SportsChannelPolicy.guideUntrusted(option.channel.remoteId, option.channel.name) &&
            tv.own.owntv.core.sports.WatchMatcher.isEventChannel(keys, option.guideTitle.orEmpty())
        when {
            guideMatches -> option
            soccer && tv.own.owntv.features.live.LoungeSoccerCatalogue.reviewedService(option.channel) != null ->
                option.copy(tier = WatchTier.LIKELY, guideTitle = null)
            !soccer -> option.copy(tier = WatchTier.LIKELY, guideTitle = null)
            else -> null
        }
    }
    return mergeBackendSportsOptions(backend, cleanLocal)
}

internal fun validatedBackendTier(version: Int, tier: String, evidenceValid: Boolean): WatchTier = when {
    version == 2 && evidenceValid && tier == "verified" -> WatchTier.GUIDE
    version == 2 && evidenceValid && tier == "plausible" -> WatchTier.EVENT
    else -> WatchTier.LIKELY
}

/** An assignment alone can only make a channel plausible, never fixture-verified. */
internal fun evidenceSupportsClaim(tier: String, evidenceType: String): Boolean = when (tier) {
    "verified" -> evidenceType in setOf("programme", "event-feed")
    "plausible" -> evidenceType in setOf("programme", "event-feed", "assignment")
    else -> false
}

private fun parseUtc(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(Long.MIN_VALUE)

/** Unknown intervals/identity fail closed; labels never establish trust. */
private fun candidateEvidenceValid(row: JSONObject, match: Match, now: Long): Boolean {
    if (row.optString("sourceId") != "best" || row.optString("serviceMarket").isBlank() ||
        !row.optBoolean("identityReviewed") || parseUtc(row.optString("expiresAtUtc")) <= now) return false
    val evidence = row.optJSONArray("evidence") ?: return false
    if ((0 until evidence.length()).any { evidence.optJSONObject(it)?.optBoolean("contentMismatch") == true }) return false
    return (0 until evidence.length()).any { i ->
        val e = evidence.optJSONObject(i) ?: return@any false
        val observed = parseUtc(e.optString("observedAtUtc"))
        val start = parseUtc(e.optString("startUtc"))
        val end = parseUtc(e.optString("endUtc"))
        val ageLimit = if (match.startMs > now) 86_400_000L else 300_000L
        val tournamentInterval = match.sport.path == "golf" &&
            parseUtc(e.optString("fixtureStartUtc")) == match.startMs &&
            parseUtc(e.optString("fixtureEndUtc")) > start && start >= match.startMs
        val intervalMatches = tournamentInterval || (start <= match.startMs + 900_000L && end > match.startMs)
        val typeSupportsClaim = evidenceSupportsClaim(row.optString("tier"), e.optString("type"))
        e.optString("eventId") == match.key && e.optString("source").isNotBlank() &&
            now - observed in 0..ageLimit && start < end &&
            intervalMatches && end > now &&
            e.optString("sportId") == match.sport.path && e.optString("competitionId") == match.feed &&
            e.optString("serviceId") == row.optString("logicalServiceId") &&
            e.optString("market") == row.optString("serviceMarket") &&
            !e.optBoolean("contentMismatch") && typeSupportsClaim
    }
}
