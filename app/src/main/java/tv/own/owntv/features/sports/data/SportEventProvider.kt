package tv.own.owntv.features.sports.data

import tv.own.owntv.core.sports.model.Match

data class SportEventQuery(val fromUtcMs: Long, val toUtcMs: Long, val sport: String? = null)

data class SportEventBatch(
    val events: List<SportEvent>,
    val fetchedAtMs: Long,
    val failedFeedIds: Set<String> = emptySet(),
)

/** Contract implemented by direct adapters today and by the central read-only gateway when deployed. */
fun interface SportEventProvider {
    suspend fun events(query: SportEventQuery): SportEventBatch
}

class CoreMatchSportEventAdapter(private val sourceId: String) {
    fun adapt(matches: List<Match>, updatedAtMs: Long): List<SportEvent> =
        matches.map { SportEvent.fromMatch(it, sourceId, updatedAtMs) }
}
