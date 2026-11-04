package tv.own.owntv.features.live

import tv.own.owntv.core.database.entity.ChannelEntity

/** Display-only projection. No folder unhide or saved-reference mutation. */
internal object LoungeSoccerCatalogue {
    const val FOLDER_ID = -8_400_000_001L
    data class EventEvidence(val channelId: Long, val soccer: Boolean, val startMs: Long, val endMs: Long, val observedAt: Long)
    private val reviewed = mapOf("646977" to "au:bein:1", "646976" to "au:bein:2", "646975" to "au:bein:3")
    fun project(rows: List<ChannelEntity>, bestSources: Set<Long>, evidence: List<EventEvidence>, now: Long): List<ChannelEntity> {
        val active = evidence.filter { it.soccer && now-it.observedAt in 0..300_000L && now >= it.startMs-1_800_000L && now < it.endMs }.mapTo(hashSetOf()) { it.channelId }
        val admitted = rows.filter { ch -> ch.sourceId in bestSources &&
            !SportsChannelPolicy.excludedBest(ch.remoteId, ch.name) &&
            !Regex("NO EVENT|REPLAY|HIGHLIGHTS|OFFLINE",RegexOption.IGNORE_CASE).containsMatchIn(ch.name) &&
            (ch.remoteId in reviewed || ch.id in active) }
        return admitted.sortedWith(compareBy({ reviewed[it.remoteId] ?: "event:${it.id}" }, { it.id }))
            .distinctBy { it.sourceId to (reviewed[it.remoteId] ?: "event:${it.id}") }
    }
}
