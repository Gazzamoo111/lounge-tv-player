package tv.own.owntv.features.live

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cache hits never queue behind network work for a different programme. */
internal class ProgrammeArtworkCache(
    private val read: () -> List<Entry>,
    private val write: (List<Entry>) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val capacity: Int = 256,
) {
    data class Entry(val key: String, val poster: String?, val expires: Long)
    data class Fetch(val completed: Boolean, val poster: String?)
    private val entries = LinkedHashMap<String, Entry>(capacity, 0.75f, true)
    private val lookup = Mutex()
    private var loaded = false

    private fun cached(key: String): Entry? = synchronized(entries) {
        if (!loaded) {
            read().takeLast(capacity).filter { it.expires > now() }.forEach { entries[it.key] = it }
            loaded = true
        }
        entries[key]?.takeIf { it.expires > now() }
    }

    suspend fun resolve(key: String, fetch: suspend () -> Fetch): String? {
        cached(key)?.let { return it.poster }
        return lookup.withLock {
            // Two visible artwork slots may ask for the same title. Only the first searches.
            cached(key)?.let { return@withLock it.poster }
            val result = fetch() // Cancellation/failure never poisons the persisted cache.
            if (!result.completed) return@withLock null
            val at = now()
            val snapshot = synchronized(entries) {
                entries[key] = Entry(key, result.poster,
                    at + if (result.poster == null) 6 * 60 * 60_000L else 7 * 24 * 60 * 60_000L)
                entries.entries.removeAll { it.value.expires <= at }
                while (entries.size > capacity) entries.remove(entries.keys.first())
                entries.values.toList()
            }
            write(snapshot)
            result.poster
        }
    }
}
