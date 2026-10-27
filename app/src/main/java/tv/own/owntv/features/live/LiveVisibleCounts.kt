package tv.own.owntv.features.live

import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.isChannelVisible

/** Counts produced from the same post-filter channel set rendered by Live TV. */
internal data class VisibleLiveCounts(
    val all: Int,
    val folders: Map<Long, Int>,
)

/**
 * Apply the customer and Lounge TV presentation rules before counting.
 *
 * Provider database totals are intentionally not used directly: hidden channels, moved-out rows,
 * duplicate backup streams and source-specific curation are removed by the paging pipeline, so counting
 * them would make the category badge disagree with the list the customer can actually open.
 */
internal fun visibleLiveCounts(
    channels: List<ChannelEntity>,
    customizations: SectionCustomizations,
    hiddenCategoryIds: Set<Long>,
    duplicateIds: Set<Long>,
    folderContextKeys: Map<Long, String>,
    allowedByLoungePolicy: (ChannelEntity) -> Boolean,
): VisibleLiveCounts {
    var all = 0
    // A fully filtered folder is still a completed result: publish zero so callers never fall back
    // to its unfiltered database total.
    val folders = folderContextKeys.keys.associateWith { 0 }.toMutableMap()
    for (channel in channels) {
        if (!isChannelVisible(channel, customizations, hiddenCategoryIds, LiveKey.All)) continue
        if (channel.id in duplicateIds || !allowedByLoungePolicy(channel)) continue
        all++

        val categoryId = channel.categoryId ?: continue
        val movedFrom = customizations.movedFromOrigin[CustomizeKeys.channel(channel)]
        if (movedFrom != null && movedFrom == folderContextKeys[categoryId]) continue
        folders[categoryId] = (folders[categoryId] ?: 0) + 1
    }
    return VisibleLiveCounts(all = all, folders = folders)
}

/** Apply the identical row predicates used by a fixed/custom Live category before showing its badge. */
internal fun visibleLiveCountForKey(
    channels: List<ChannelEntity>,
    key: LiveKey,
    customizations: SectionCustomizations,
    hiddenCategoryIds: Set<Long>,
    duplicateIds: Set<Long>,
    allowedByLoungePolicy: (ChannelEntity) -> Boolean,
): Int {
    val keepsExplicitBackup = key == LiveKey.Favorites || key == LiveKey.History || key is LiveKey.Custom
    return channels.count { channel ->
        isChannelVisible(channel, customizations, hiddenCategoryIds, key) &&
            (keepsExplicitBackup || channel.id !in duplicateIds) &&
            allowedByLoungePolicy(channel)
    }
}
