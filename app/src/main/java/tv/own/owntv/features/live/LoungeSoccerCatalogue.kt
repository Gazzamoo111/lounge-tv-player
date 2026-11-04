package tv.own.owntv.features.live

import tv.own.owntv.core.database.entity.ChannelEntity

/** Display-only projection. No folder unhide or saved-reference mutation. */
internal object LoungeSoccerCatalogue {
    const val FOLDER_ID = -8_400_000_001L
    data class EventEvidence(val channelId: Long, val soccer: Boolean, val startMs: Long, val endMs: Long, val observedAt: Long)
    private val reviewed = mapOf("646977" to "au:bein:1", "646976" to "au:bein:2", "646975" to "au:bein:3")
    private fun reviewedService(channel: ChannelEntity): String? = reviewed[channel.remoteId] ?:
        Regex("^AU\\s*[|:]\\s*BEIN SPORTS?\\s*([123])(?:\\s+(?:HD|FHD|SD|4K|UHD|RAW))*$", RegexOption.IGNORE_CASE)
            .matchEntire(channel.name.trim())?.groupValues?.get(1)?.let { "au:bein:$it" }
    fun project(rows: List<ChannelEntity>, bestSources: Set<Long>, evidence: List<EventEvidence>, now: Long): List<ChannelEntity> {
        val active = evidence.filter { it.soccer && now-it.observedAt in 0..300_000L && now >= it.startMs-1_800_000L && now < it.endMs }.mapTo(hashSetOf()) { it.channelId }
        val admitted = rows.filter { ch -> ch.sourceId in bestSources &&
            !SportsChannelPolicy.excludedBest(ch.remoteId, ch.name) &&
            !Regex("NO EVENT|REPLAY|HIGHLIGHTS|OFFLINE",RegexOption.IGNORE_CASE).containsMatchIn(ch.name) &&
            (reviewedService(ch) != null || ch.id in active) }
        return admitted.sortedWith(compareBy({ reviewedService(it) ?: "event:${it.id}" }, { it.id }))
            .distinctBy { it.sourceId to (reviewedService(it) ?: "event:${it.id}") }
    }
}
