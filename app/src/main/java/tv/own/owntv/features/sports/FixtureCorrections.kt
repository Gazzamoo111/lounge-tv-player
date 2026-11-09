package tv.own.owntv.features.sports

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal interface FixtureCorrectionPersistence {
    fun read(): String?
    fun write(value: String): Boolean
}

internal class PreferenceFixtureCorrections(context: Context) : FixtureCorrectionPersistence {
    private val prefs = context.getSharedPreferences("lounge_fixture_corrections_v1", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString("corrections", null)
    override fun write(value: String): Boolean = prefs.edit().putString("corrections", value).commit()
}

/** Feed choices and negative reports belong to one profile and fixture occurrence. */
internal class FixtureCorrections(
    private val persistence: FixtureCorrectionPersistence,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val expires: Long, val channels: Set<String>, val selected: String? = null)
    private val entries = LinkedHashMap<String, Entry>()
    private var loaded = false

    @Synchronized fun selected(profile: Long, fixture: String, startMs: Long): String? {
        load()
        return entries[key(profile, fixture, startMs)]?.takeIf { it.expires > clock() }?.selected
    }

    @Synchronized fun remember(profile: Long, fixture: String, startMs: Long, channelKey: String): Boolean {
        require(profile >= 0)
        load()
        val before = LinkedHashMap(entries)
        val now = clock()
        entries.entries.removeAll { it.value.expires <= now }
        val key = key(profile, fixture, startMs)
        val previous = entries[key]
        if (channelKey in previous?.channels.orEmpty()) return false
        entries.remove(key)
        entries[key] = Entry(maxOf(startMs + 36 * 60 * 60_000L, now + 6 * 60 * 60_000L),
            previous?.channels.orEmpty(), channelKey)
        while (entries.size > 64) entries.remove(entries.keys.first())
        return saveOrRollback(before)
    }

    @Synchronized fun rejected(profile: Long, fixture: String, startMs: Long): Set<String> {
        load()
        return entries[key(profile, fixture, startMs)]?.takeIf { it.expires > clock() }?.channels.orEmpty()
    }

    @Synchronized fun selected(profile: Long, fixture: String, startMs: Long): String? {
        load()
        return entries[key(profile, fixture, startMs)]?.takeIf { it.expires > clock() }?.selected
    }

    @Synchronized fun remember(profile: Long, fixture: String, startMs: Long, channelKey: String): Boolean {
        require(profile >= 0)
        load()
        val before = LinkedHashMap(entries)
        val now = clock()
        entries.entries.removeAll { it.value.expires <= now }
        val key = key(profile, fixture, startMs)
        val previous = entries[key]
        if (channelKey in previous?.channels.orEmpty()) return false
        entries.remove(key)
        entries[key] = Entry(maxOf(startMs + 36 * 60 * 60_000L, now + 6 * 60 * 60_000L),
            previous?.channels.orEmpty(), channelKey)
        while (entries.size > 64) entries.remove(entries.keys.first())
        return saveOrRollback(before)
    }

    @Synchronized fun reject(profile: Long, fixture: String, startMs: Long, channelKey: String): Boolean {
        require(profile >= 0)
        load()
        val before = LinkedHashMap(entries)
        val now = clock()
        entries.entries.removeAll { it.value.expires <= now }
        val key = key(profile, fixture, startMs)
        val previous = entries.remove(key)
        entries[key] = Entry(maxOf(startMs + 36 * 60 * 60_000L, now + 6 * 60 * 60_000L),
            (previous?.channels.orEmpty() + channelKey).takeLastSet(64), previous?.selected?.takeUnless { it == channelKey })
        while (entries.size > 64) entries.remove(entries.keys.first())
        return saveOrRollback(before)
    }

    @Synchronized fun restore(profile: Long, fixture: String, startMs: Long): Boolean {
        load()
        val before = LinkedHashMap(entries)
        entries.remove(key(profile, fixture, startMs))
        return saveOrRollback(before)
    }

    private fun load() {
        if (loaded) return
        loaded = true
        try {
            val rows = JSONArray(persistence.read() ?: "[]")
            for (i in 0 until minOf(rows.length(), 64)) {
                val row = rows.getJSONObject(i)
                val channels = row.getJSONArray("channels")
                entries[row.getString("fixture")] = Entry(row.getLong("expires"),
                    (0 until minOf(channels.length(), 64)).map { channels.getString(it) }.toSet(),
                    if (row.isNull("selected")) null else row.getString("selected"))
            }
        } catch (_: Exception) { entries.clear() }
    }

    private fun saveOrRollback(before: Map<String, Entry>): Boolean {
        val rows = JSONArray()
        entries.forEach { (fixture, entry) -> rows.put(JSONObject().put("fixture", fixture)
            .put("expires", entry.expires).put("channels", JSONArray(entry.channels.toList()))
            .put("selected", entry.selected ?: JSONObject.NULL)) }
        val saved = try { persistence.write(rows.toString()) } catch (_: Exception) { false }
        if (!saved) { entries.clear(); entries.putAll(before) }
        return saved
    }

    private fun key(profile: Long, fixture: String, startMs: Long) = digest("$profile|$fixture|$startMs")
    private fun Set<String>.takeLastSet(limit: Int) = toList().takeLast(limit).toSet()

    companion object {
        fun channelKey(sourceId: Long, remoteId: String?, name: String): String =
            digest("$sourceId|" + (remoteId?.removePrefix("sport-backend:")?.takeIf { it.isNotBlank() }
                ?: FixtureEvidence.normalized(name)))
        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
