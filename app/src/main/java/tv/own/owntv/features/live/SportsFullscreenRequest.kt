package tv.own.owntv.features.live

import tv.own.owntv.core.database.entity.ChannelEntity

/** Backend sports candidates carry playable source data but need not be rows in the local catalogue. */
internal suspend fun requestSportsFullscreen(
    channel: ChannelEntity,
    candidates: List<ChannelEntity>,
    visibleToProfile: suspend (ChannelEntity) -> Boolean,
    watch: suspend (ChannelEntity, List<ChannelEntity>) -> Unit,
): Boolean {
    if (!visibleToProfile(channel)) return false
    // Keep the trusted candidate list as the channel-surf context, including an unpersisted selection.
    val context = if (candidates.any { it.id == channel.id }) candidates else listOf(channel) + candidates
    watch(channel, context)
    return true
}
