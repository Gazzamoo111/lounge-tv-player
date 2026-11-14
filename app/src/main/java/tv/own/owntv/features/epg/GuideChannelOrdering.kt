package tv.own.owntv.features.epg

import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.settings.SettingsRepository
import java.util.Locale

/** Deterministic Guide ordering, including the exact rank exported by the selected Live rail. */
internal object GuideChannelOrdering {
    /** Cached rows must not consume the initial-focus request for a newly selected category. */
    fun loadedRowsMatchCategory(
        requestedCategory: tv.own.owntv.core.live.LiveKey?,
        loadedCategory: tv.own.owntv.core.live.LiveKey?,
        requestedLiveMode: Boolean,
        loadedLiveMode: Boolean,
    ): Boolean = requestedCategory == loadedCategory && requestedLiveMode == loadedLiveMode

    fun initialRow(
        channels: List<ChannelEntity>,
        currentChannelId: Long?,
        lastTunedChannelId: Long? = null,
    ): Int {
        if (channels.isEmpty()) return 0
        return (currentChannelId ?: lastTunedChannelId)
            ?.let { id -> channels.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
    }

    fun order(
        channels: List<ChannelEntity>,
        guideSort: SettingsRepository.GuideSort,
        liveSort: SettingsRepository.SortMode,
        favoriteIds: Set<Long>,
        liveOrderIds: List<Long> = emptyList(),
    ): List<ChannelEntity> {
        val byProvider = compareBy<ChannelEntity>(
            { it.sourceId },
            { it.sortOrder },
            { it.name.lowercase(Locale.ROOT) },
            { it.id },
        )
        val byAlpha = compareBy<ChannelEntity>(
            { it.name.lowercase(Locale.ROOT) },
            { it.sourceId },
            { it.sortOrder },
            { it.id },
        )
        return when (guideSort) {
            SettingsRepository.GuideSort.ALPHA -> channels.sortedWith(byAlpha)
            SettingsRepository.GuideSort.PROVIDER -> channels.sortedWith(byProvider)
            SettingsRepository.GuideSort.CATCHUP ->
                channels.sortedWith(compareByDescending<ChannelEntity> { it.catchup }.then(byProvider))
            SettingsRepository.GuideSort.FAVORITES ->
                channels.sortedWith(compareByDescending<ChannelEntity> { it.id in favoriteIds }.then(byProvider))
            SettingsRepository.GuideSort.LIVE_TV -> {
                val fallback = if (liveSort == SettingsRepository.SortMode.ALPHA) byAlpha else byProvider
                if (liveOrderIds.isEmpty()) {
                    channels.sortedWith(fallback)
                } else {
                    val rank = liveOrderIds.distinct().withIndex().associate { (index, id) -> id to index }
                    channels.sortedWith(
                        compareBy<ChannelEntity>({ rank[it.id] == null }, { rank[it.id] ?: Int.MAX_VALUE })
                            .then(fallback),
                    )
                }
            }
        }
    }
}
