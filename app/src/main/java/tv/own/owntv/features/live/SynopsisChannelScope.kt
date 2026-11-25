package tv.own.owntv.features.live

import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * Synopsis browsing is independent of the previous playback rail. If a guide refresh,
 * channel deletion or list limit leaves the currently playing row unanchored,
 * do not jump into another category or pick a first row implicitly.
 */
internal fun synopsisChannelScope(
    channel: ChannelEntity,
    categoryChannels: List<ChannelEntity>,
    playbackContext: List<ChannelEntity>,
): List<ChannelEntity> {
    if (channel.id < 0L) {
        return playbackContext.takeIf { rows -> rows.any { it.id == channel.id } }
            ?: listOf(channel)
    }
    val ownCategory = categoryChannels.filter {
        it.sourceId == channel.sourceId && it.categoryId == channel.categoryId
    }
    return ownCategory.takeIf { rows -> rows.any { it.id == channel.id } }
        ?: listOf(channel)
}
