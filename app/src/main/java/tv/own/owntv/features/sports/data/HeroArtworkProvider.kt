package tv.own.owntv.features.sports.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.sports.model.Match

/** Exact-event artwork and logo enrichment only; anything unverified falls back locally. */
internal data class HeroEventAssets(
    val heroArtUrl: String? = null,
    val teamLogoUrls: Map<String, String> = emptyMap(),
    val competitionLogoUrl: String? = null,
)

internal fun heroLogoKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

internal object HeroArtworkParser {
    private val trustedHosts = setOf(
        "www.thesportsdb.com", "thesportsdb.com", "r2.thesportsdb.com"
    )

    fun parse(body: String): Map<String, HeroEventAssets>? = runCatching {
        val root = JSONObject(body)
        if (root.optString("status") != "ok") return@runCatching null
        val rows = root.optJSONArray("arts") ?: return@runCatching null
        buildMap<String, HeroEventAssets> {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                if (item.optString("source") != "thesportsdb" ||
                    item.optString("confidence") != "exact") continue
                val key = item.optString("key").takeIf(String::isNotBlank) ?: continue
                val hero = item.optString("url").takeIf(::trustedEventArtworkUrl)
                val teamLogos = buildMap<String, String> {
                    val logos = item.optJSONArray("teamLogos") ?: JSONArray()
                    for (logoIndex in 0 until logos.length()) {
                        val row = logos.optJSONObject(logoIndex) ?: continue
                        val name = row.optString("name").takeIf(String::isNotBlank) ?: continue
                        val url = trustedOriginalBadgeUrl(row.optString("url"), "team") ?: continue
                        put(heroLogoKey(name), url)
                    }
                }
                val competitionLogo =
                    trustedOriginalBadgeUrl(item.optString("competitionLogoUrl"), "league")
                if (hero != null || teamLogos.isNotEmpty() || competitionLogo != null) {
                    put(key, HeroEventAssets(hero, teamLogos, competitionLogo))
                }
            }
        }
    }.getOrNull()

    private fun trustedEventArtworkUrl(raw: String): Boolean = runCatching {
        val url = URI(raw)
        url.scheme == "https" &&
            url.host?.lowercase(Locale.ROOT) in trustedHosts &&
            url.path.startsWith("/images/media/event/") &&
            (url.path.contains("/thumb/") || url.path.contains("/fanart/"))
    }.getOrDefault(false)

    private fun trustedOriginalBadgeUrl(raw: String, kind: String): String? = runCatching {
        val url = URI(raw)
        val host = url.host?.lowercase(Locale.ROOT)
        if (url.scheme != "https" || host !in trustedHosts) return@runCatching null
        var path = url.path
        for (suffix in listOf("/medium", "/small", "/tiny")) {
            if (path.endsWith(suffix)) path = path.removeSuffix(suffix)
        }
        if (!path.startsWith("/images/media/$kind/badge/") ||
            !path.endsWith(".png", ignoreCase = true)) return@runCatching null
        URI("https", host, path, null).toString()
    }.getOrNull()
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
            .filterKeys { it in keys }
            .mapValues { (_, assets) ->
                SportEventEditorial(
                    heroArtUrl = assets.heroArtUrl,
                    teamLogoUrls = assets.teamLogoUrls,
                    competitionLogoUrl = assets.competitionLogoUrl,
                )
            }
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
            verified.filterKeys { it in currentKeys }.mapValues { (_, assets) ->
                SportEventEditorial(
                    heroArtUrl = assets.heroArtUrl,
                    teamLogoUrls = assets.teamLogoUrls,
                    competitionLogoUrl = assets.competitionLogoUrl,
                )
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
