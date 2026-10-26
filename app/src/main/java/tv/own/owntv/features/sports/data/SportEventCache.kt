package tv.own.owntv.features.sports.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface SportEventCache {
    fun read(): List<SportEvent>
    fun write(events: List<SportEvent>)
}

class FileSportEventCache(private val file: File) : SportEventCache {
    override fun read(): List<SportEvent> = runCatching {
        val rows = JSONObject(file.takeIf(File::isFile)?.readText().orEmpty()).optJSONArray("events") ?: JSONArray()
        (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.let(SportEvent::fromJson) }
    }.getOrDefault(emptyList())

    override fun write(events: List<SportEvent>) {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(JSONObject().put("version", 1).put("events", JSONArray().apply { events.forEach { put(it.toJson()) } }).toString())
            runCatching {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

object SportEventMerge {
    private const val KEEP_PAST_MS = 12 * 60 * 60_000L
    private const val KEEP_AHEAD_MS = 72 * 60 * 60_000L

    /** Fresh rows replace exact stable IDs; stale rows survive only for provider feeds that failed. */
    fun withSafeStale(
        fresh: List<SportEvent>,
        cached: List<SportEvent>,
        failedFeedIds: Set<String>,
        nowMs: Long,
    ): List<SportEvent> {
        val freshIds = fresh.mapTo(hashSetOf()) { it.stableId }
        val stale = cached.filter {
            it.feedId in failedFeedIds && it.stableId !in freshIds &&
                it.startUtcMs in (nowMs - KEEP_PAST_MS)..(nowMs + KEEP_AHEAD_MS)
        }
        return dedupe(fresh + stale)
    }

    /** Exact provider ID only: never fuzzy-merge different fixtures with similar team names. */
    fun dedupe(events: List<SportEvent>): List<SportEvent> = events
        .groupBy { it.stableId }
        .values
        .map { copies -> copies.reduce(::newerChronology) }
        .sortedWith(compareBy({ it.startUtcMs }, { it.stableId }))

    private fun newerChronology(old: SportEvent, next: SportEvent): SportEvent {
        val oldTerminal = old.state.isTerminal()
        val nextTerminal = next.state.isTerminal()
        return when {
            oldTerminal && !nextTerminal -> old
            !oldTerminal && nextTerminal -> next
            !oldTerminal && !nextTerminal && stateRank(next.state) > stateRank(old.state) -> next
            !oldTerminal && !nextTerminal && stateRank(next.state) < stateRank(old.state) -> old
            next.sourceUpdatedAtMs > old.sourceUpdatedAtMs -> next
            else -> old
        }
    }

    private fun stateRank(state: SportEventState): Int = when (state) {
        SportEventState.SCHEDULED -> 0
        SportEventState.LIVE -> 1
        SportEventState.FINISHED -> 2
        SportEventState.POSTPONED -> 2
        SportEventState.CANCELLED -> 2
    }

    private fun SportEventState.isTerminal(): Boolean =
        this == SportEventState.FINISHED || this == SportEventState.POSTPONED || this == SportEventState.CANCELLED
}
