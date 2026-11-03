package tv.own.owntv.features.sports.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tv.own.owntv.core.sports.model.Match
import java.net.HttpURLConnection
import java.net.URL

enum class SportEventCoverage { OK, PARTIAL, STALE, UNSUPPORTED }

data class SportEventQuery(val fromUtcMs: Long, val toUtcMs: Long, val sport: String? = null)

data class SportEventBatch(
    val events: List<SportEvent>,
    val fetchedAtMs: Long,
    val failedFeedIds: Set<String> = emptySet(),
    val coverage: SportEventCoverage = SportEventCoverage.OK,
)

/** Contract implemented by direct adapters today and by the central read-only gateway when deployed. */
fun interface SportEventProvider {
    suspend fun events(query: SportEventQuery): SportEventBatch
}

class CoreMatchSportEventAdapter(private val sourceId: String) {
    fun adapt(matches: List<Match>, updatedAtMs: Long): List<SportEvent> =
        matches.map { SportEvent.fromMatch(it, sourceId, updatedAtMs) }
}


class GatewaySportEventProvider(
    private val endpoint: String,
) : SportEventProvider {
    override suspend fun events(query: SportEventQuery): SportEventBatch = withContext(Dispatchers.IO) {
        val separator = if ('?' in endpoint) '&' else '?'
        val sport = query.sport?.let { "&sport=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
        val url = URL(
            endpoint + separator +
                "from=" + query.fromUtcMs +
                "&to=" + query.toUtcMs +
                sport,
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("Sport Lounge gateway HTTP $status")
            GatewaySportEventParser.parse(body) ?: error("Sport Lounge gateway returned invalid data")
        } finally {
            connection.disconnect()
        }
    }
}

/** Strict parser for the central read-only gateway; it has no BEST/channel semantics. */
object GatewaySportEventParser {
    fun parse(body: String): SportEventBatch? = runCatching {
        val root = JSONObject(body)
        val rows = root.getJSONArray("events")
        val diagnostics = root.optJSONObject("diagnostics")
        val failed = diagnostics?.optJSONArray("failedFeeds")
        val fetchedAt = root.optString("lastUpdated")
            .takeIf(String::isNotBlank)
            ?.let(java.time.Instant::parse)
            ?.toEpochMilli()
            ?: 0L
        SportEventBatch(
            events = (0 until rows.length()).mapNotNull { index ->
                rows.optJSONObject(index)?.let(SportEvent::fromJson)
            },
            fetchedAtMs = fetchedAt,
            failedFeedIds = if (failed == null) emptySet() else (0 until failed.length()).mapNotNullTo(hashSetOf()) { index ->
                failed.optJSONObject(index)?.optString("feed")?.takeIf(String::isNotBlank)
            },
            coverage = when (root.optString("status")) {
                "partial" -> SportEventCoverage.PARTIAL
                "stale" -> SportEventCoverage.STALE
                "unsupported" -> SportEventCoverage.UNSUPPORTED
                else -> SportEventCoverage.OK
            },
        )
    }.getOrNull()
}
