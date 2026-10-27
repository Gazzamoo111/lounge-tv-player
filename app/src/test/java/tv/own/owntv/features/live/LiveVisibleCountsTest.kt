package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.LiveKey

class LiveVisibleCountsTest {
    private fun channel(id: Long, categoryId: Long? = 10L, sourceId: Long = 1L) = ChannelEntity(
        id = id,
        sourceId = sourceId,
        categoryId = categoryId,
        name = "Channel $id",
        streamUrl = "http://counts.invalid/$id.ts",
    )

    @Test
    fun `folder and all counts use the displayed filtered deduplicated rows`() {
        val hidden = channel(2)
        val moved = channel(3)
        val customizations = SectionCustomizations(
            hiddenItems = mapOf(CustomizeKeys.channel(hidden) to hidden.name),
            movedFromOrigin = mapOf(CustomizeKeys.channel(moved) to "folder-10"),
        )
        val result = visibleLiveCounts(
            channels = listOf(
                channel(1), hidden, moved, channel(4), channel(5),
                channel(6, categoryId = 20), channel(7, categoryId = null),
            ),
            customizations = customizations,
            hiddenCategoryIds = setOf(20),
            duplicateIds = setOf(4),
            folderContextKeys = mapOf(10L to "folder-10", 20L to "folder-20"),
            allowedByLoungePolicy = { it.id != 5L },
        )

        assertEquals(3, result.all) // visible folder row, moved row in All, uncategorized row
        assertEquals(mapOf(10L to 1, 20L to 0), result.folders)
    }

    @Test
    fun `source replacement and resync recompute without mutating customer customizations`() {
        val oldHidden = channel(2)
        val customizations = SectionCustomizations(
            hiddenItems = mapOf(CustomizeKeys.channel(oldHidden) to oldHidden.name),
        )
        val before = visibleLiveCounts(
            listOf(channel(1), oldHidden), customizations, emptySet(), emptySet(),
            mapOf(10L to "folder-10"),
        ) { true }
        val afterRestartAndResync = visibleLiveCounts(
            listOf(channel(2), channel(8), channel(9, categoryId = 30, sourceId = 2)),
            customizations,
            emptySet(),
            emptySet(),
            mapOf(10L to "folder-10", 30L to "folder-30"),
        ) { true }

        assertEquals(1, before.all)
        assertEquals(2, afterRestartAndResync.all)
        assertEquals(mapOf(10L to 1, 30L to 1), afterRestartAndResync.folders)
        assertEquals(setOf(CustomizeKeys.channel(oldHidden)), customizations.hiddenItems.keys)
    }

    @Test
    fun `fixed category badges use the same visibility rules as their displayed rows`() {
        val hidden = channel(2)
        val customizations = SectionCustomizations(
            hiddenItems = mapOf(CustomizeKeys.channel(hidden) to hidden.name),
        )
        val rows = listOf(channel(1), hidden, channel(3), channel(4, categoryId = 20))

        assertEquals(1, visibleLiveCountForKey(
            rows, LiveKey.Catchup, customizations, setOf(20), setOf(3),
        ) { true })
        // A customer explicitly favouriting a backup keeps it visible; Lounge policy still applies.
        assertEquals(2, visibleLiveCountForKey(
            rows, LiveKey.Favorites, customizations, setOf(20), setOf(3),
        ) { it.id != 4L })
    }
}
