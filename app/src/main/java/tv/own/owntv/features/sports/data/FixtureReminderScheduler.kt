package tv.own.owntv.features.sports.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class FixtureReminder(val profileId: Long, val fixtureId: String, val title: String, val startMs: Long, val leadMinutes: Int, val deliveredStartMs: Long? = null) {
    val dueMs get() = startMs - leadMinutes.coerceAtLeast(0) * 60_000L
}
data class FixtureSchedule(val fixtureId: String, val title: String, val startMs: Long, val active: Boolean = true)
interface FixtureReminderPersistence { fun load(): List<FixtureReminder>; fun save(rows: List<FixtureReminder>) }

class SharedPreferencesFixtureReminderPersistence(context: Context) : FixtureReminderPersistence {
    private val prefs = context.getSharedPreferences("sport_fixture_reminders_v1", Context.MODE_PRIVATE)
    override fun load() = runCatching {
        val a = JSONArray(prefs.getString("rows", "[]")); (0 until a.length()).mapNotNull { i -> runCatching { a.getJSONObject(i).let { o ->
            FixtureReminder(o.getLong("profile"), o.getString("fixture"), o.getString("title"), o.getLong("start"), o.getInt("lead"), o.optLong("delivered").takeIf { o.has("delivered") && !o.isNull("delivered") })
        } }.getOrNull() }
    }.getOrDefault(emptyList())
    override fun save(rows: List<FixtureReminder>) {
        val a = JSONArray().apply { rows.forEach { r -> put(JSONObject().apply {
            put("profile", r.profileId); put("fixture", r.fixtureId); put("title", r.title); put("start", r.startMs); put("lead", r.leadMinutes); put("delivered", r.deliveredStartMs ?: JSONObject.NULL)
        }) } }; prefs.edit().putString("rows", a.toString()).apply()
    }
}

/** Channel-independent, restart-safe in-app fixture reminder scheduler. */
class FixtureReminderScheduler(private val persistence: FixtureReminderPersistence) {
    private val state = MutableStateFlow(persistence.load().associateBy(::key))
    fun observe(profileId: Long): Flow<List<FixtureReminder>> = state.map { it.values.filter { r -> r.profileId == profileId }.sortedBy(FixtureReminder::startMs) }
    @Synchronized fun set(profileId: Long, fixture: FixtureSchedule, leadMinutes: Int) {
        val k = key(profileId, fixture.fixtureId); val old = state.value[k]
        update(state.value + (k to FixtureReminder(profileId, fixture.fixtureId, fixture.title, fixture.startMs, leadMinutes.coerceAtLeast(0), old?.deliveredStartMs?.takeIf { old.startMs == fixture.startMs })))
    }
    @Synchronized fun remove(profileId: Long, fixtureId: String): Boolean {
        val k = key(profileId, fixtureId); if (k !in state.value) return false; update(state.value - k); return true
    }
    @Synchronized fun reconcile(profileId: Long, fixtures: List<FixtureSchedule>) {
        val found = fixtures.associateBy(FixtureSchedule::fixtureId); var changed = false
        val next = state.value.mapNotNull { (key, r) ->
            val f = found[r.fixtureId]
            when {
                r.profileId != profileId || f == null -> key to r
                !f.active -> { changed = true; null }
                f.startMs == r.startMs && f.title == r.title -> key to r
                else -> { changed = true; key to r.copy(title = f.title, startMs = f.startMs, deliveredStartMs = r.deliveredStartMs?.takeIf { r.startMs == f.startMs }) }
            }
        }.toMap()
        if (changed) update(next)
    }
    @Synchronized fun takeDue(profileId: Long, nowMs: Long): List<FixtureReminder> {
        val due = state.value.values.filter { it.profileId == profileId && it.deliveredStartMs != it.startMs && nowMs >= it.dueMs && nowMs < it.startMs + 21_600_000L }
        if (due.isNotEmpty()) update(state.value + due.associate { key(it) to it.copy(deliveredStartMs = it.startMs) }); return due.sortedBy(FixtureReminder::dueMs)
    }
    private fun update(next: Map<String, FixtureReminder>) { state.value = next; persistence.save(next.values.toList()) }
    private companion object { fun key(profile: Long, fixture: String) = "$profile|$fixture"; fun key(r: FixtureReminder) = key(r.profileId, r.fixtureId) }
}
