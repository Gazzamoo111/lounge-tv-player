package tv.own.owntv.features.live

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.metadata.MetadataProvider
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.settings.SettingsRepository

/** One bounded queue and disk cache. Never tunes a stream or requires a provider VOD catalogue. */
internal class ProgrammeArtworkResolver(
    context: Context,
    private val provider: MetadataProvider,
    private val settings: SettingsRepository,
) {
    private data class Entry(val poster: String?, val expires: Long)
    private val mutex = Mutex()
    private val file = AtomicFile(File(context.cacheDir, "lounge-programme-artwork-v1.json"))
    private val cache = LinkedHashMap<String, Entry>(256, 0.75f, true)
    private var loaded = false

    suspend fun resolve(title: String, year: Int? = null, type: MetadataType? = null): String? = withContext(Dispatchers.IO) {
        val query = ProgrammeArtworkMatch.query(title, year, type) ?: return@withContext null
        val config = settings.metadataConfig()
        if (!config.enabled) return@withContext null
        // Hash configuration: cache cannot leak API keys or reuse results from another language/server.
        val rawKey = listOf(ProgrammeArtworkMatch.normalize(query.title), query.year, query.type,
            config.resolvedLanguage, config.customServerUrl, config.tmdbApiKey).joinToString("|")
        val key = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }
        mutex.withLock {
            if (!loaded) { readCache(); loaded = true }
            val now = System.currentTimeMillis()
            cache[key]?.takeIf { it.expires > now }?.let { return@withContext it.poster }
            // One selected programme, at most two searches. Timeout/transport failure is not a miss.
            val results = withTimeoutOrNull(8_000) {
                try {
                    val movies = if (query.type != MetadataType.TV) provider.searchMovie(query.title, query.year) else emptyList()
                    val shows = if (query.type != MetadataType.MOVIE) provider.searchTv(query.title, query.year) else emptyList()
                    if (movies == null || shows == null) null else movies + shows
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { null }
            } ?: return@withContext null
            val poster = ProgrammeArtworkMatch.select(query, results)?.posterPath?.let { MetadataImages.poster(it, "w342") }
            cache[key] = Entry(poster, now + if (poster == null) 6 * 60 * 60_000L else 7 * 24 * 60 * 60_000L)
            cache.entries.removeAll { it.value.expires <= now }
            while (cache.size > 256) cache.remove(cache.keys.first())
            writeCache()
            poster
        }
    }

    private fun readCache() {
        try {
            val rows = JSONArray(file.openRead().bufferedReader().use { it.readText() })
            for (i in 0 until minOf(rows.length(), 256)) {
                val row = rows.getJSONObject(i)
                val key = row.getString("key")
                val poster = if (row.isNull("poster")) null else row.getString("poster")
                if (key.matches(Regex("[a-f0-9]{64}")) && (poster == null || poster.startsWith("https://image.tmdb.org/"))) {
                    cache[key] = Entry(poster, row.getLong("expires"))
                }
            }
        } catch (_: Exception) { cache.clear() }
    }

    private fun writeCache() {
        var stream: java.io.FileOutputStream? = null
        try {
            val rows = JSONArray()
            cache.forEach { (key, entry) -> rows.put(JSONObject().put("key", key).put("poster", entry.poster ?: JSONObject.NULL).put("expires", entry.expires)) }
            stream = file.startWrite()
            stream.write(rows.toString().toByteArray())
            file.finishWrite(stream)
        } catch (_: Exception) { stream?.let { file.failWrite(it) } }
    }
}
