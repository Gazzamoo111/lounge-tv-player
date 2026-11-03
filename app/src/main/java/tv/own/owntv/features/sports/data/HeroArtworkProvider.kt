package tv.own.owntv.features.sports.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.sports.model.Match

/** Exact-event artwork only; anything else uses the TV's local crest hero. */
internal object HeroArtworkParser {
    fun parse(body: String): Map<String, String>? = runCatching {
        val root = JSONObject(body)
        if (root.optString("status") != "ok") return@runCatching null
        val rows = root.optJSONArray("arts") ?: return@runCatching null
        buildMap<String, String> {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                if (item.optString("source") != "thesportsdb" ||
                    item.optString("confidence") != "exact") continue
                val key = item.optString("key").takeIf(String::isNotBlank) ?: continue
                val url = item.optString("url")
                if (!trustedEventArtworkUrl(url)) continue
                put(key, url)
            }
        }
    }.getOrNull()

    private fun trustedEventArtworkUrl(raw: String): Boolean = runCatching {
        val url = URI(raw)
        url.scheme == "https" &&
            url.host?.lowercase(Locale.ROOT) in setOf(
                "www.thesportsdb.com", "thesportsdb.com", "r2.thesportsdb.com"
            ) &&
            url.path.startsWith("/images/media/event/") &&
            (url.path.contains("/thumb/") || url.path.contains("/fanart/"))
    }.getOrDefault(false)
}

/** Passive cache for a read-only QA artwork scan; never blocks fixture discovery or Home. */
internal class HeroArtworkProvider(
    private val file: File,
    private val endpoint: String,
) {
    private val refreshMs = 6 * 60 * 60_000L
    private val maxStaleMs = 24 * 60 * 60_000L

    fun cached(matches: List<Match>, nowMs: Long = System.currentTimeMillis()): Map<String, SportEventEditorial> {
        val saved = read() ?: return emptyMap()
        val age = nowMs - saved.optLong("checkedAtMs", 0L)
        if (age !in 0..maxStaleMs) return emptyMap()
        val keys = matches.map { it.key }.toSet()
        return HeroArtworkParser.parse(saved.toString()).orEmpty()
            .filterKeys { it in keys }.mapValues { SportEventEditorial(heroArtUrl = it.value) }
    }

    suspend fun refresh(matches: List<Match>): Map<String, SportEventEditorial> = withContext(Dispatchers.IO) {
        val fixtures = matches.take(12)
        if (fixtures.isEmpty()) return@withContext emptyMap()
        val requested = fixtures.map(::signature)
        val old = read()
        val nowMs = System.currentTimeMillis()
        if (old != null && nowMs - old.optLong("checkedAtMs", 0L) in 0..refreshMs &&
            old.optJSONArray("requested")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            } == requested) return@withContext cached(fixtures, nowMs)

        var connection: HttpURLConnection? = null
        try {
            val request = JSONObject().put("events", JSONArray().apply {
                fixtures.forEach { match ->
                    put(JSONObject()
                        .put("key", match.key)
                        .put("title", match.title)
                        .put("sport", match.sport.path)
                        .put("feed", match.feed)
                        .put("startUtcMs", match.startMs)
                        .put("teams", JSONArray().apply {
                            match.competitors.take(2).forEach { side ->
                                put(JSONObject()
                                    .put("name", side.team.name)
                                    .put("shortName", side.team.shortName))
                            }
                        }))
                }
            })
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 18_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@withContext cached(fixtures, nowMs)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val verified = HeroArtworkParser.parse(body) ?: return@withContext cached(fixtures, nowMs)
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(JSONObject(body)
                    .put("checkedAtMs", nowMs)
                    .put("requested", JSONArray(requested)).toString())
            }
            val currentKeys = fixtures.map { it.key }.toSet()
            verified.filterKeys { it in currentKeys }.mapValues {
                SportEventEditorial(heroArtUrl = it.value)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            cached(fixtures, nowMs)
        } finally {
            connection?.disconnect()
        }
    }

    private fun read(): JSONObject? = runCatching {
        if (!file.isFile || file.length() > 100_000L) null else JSONObject(file.readText())
    }.getOrNull()

    private fun signature(match: Match): String =
        match.key + "|" + match.startMs + "|" + match.title
}
