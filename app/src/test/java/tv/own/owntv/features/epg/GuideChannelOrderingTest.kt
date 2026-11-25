package tv.own.owntv.features.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.settings.SettingsRepository

class GuideChannelOrderingTest {
    private fun channel(
        id: Long,
        name: String,
        source: Long = 1,
        providerPosition: Int = id.toInt(),
        catchup: Boolean = false,
    ) = ChannelEntity(
        id = id,
        sourceId = source,
        name = name,
        streamUrl = "https://example.invalid/$id",
        sortOrder = providerPosition,
        catchup = catchup,
    )

    private val rows = listOf(
        channel(1, "Zulu", providerPosition = 0),
        channel(2, "Alpha", providerPosition = 1, catchup = true),
        channel(3, "Bravo", providerPosition = 2),
    )

    @Test fun `live guide follows the exact selected rail rank including manual moves`() {
        val ordered = GuideChannelOrdering.order(
            rows,
            SettingsRepository.GuideSort.LIVE_TV,
            SettingsRepository.SortMode.ALPHA,
            emptySet(),
            liveOrderIds = listOf(3, 2, 1),
        )
        assertEquals(listOf(3L, 2L, 1L), ordered.map { it.id })
    }

    @Test fun `live guide uses the live alpha setting when the rail has no manual rank`() {
        val ordered = GuideChannelOrdering.order(
            rows,
            SettingsRepository.GuideSort.LIVE_TV,
            SettingsRepository.SortMode.ALPHA,
            emptySet(),
        )
        assertEquals(listOf("Alpha", "Bravo", "Zulu"), ordered.map { it.name })
    }

    @Test fun `provider and alpha guide choices remain explicit and deterministic`() {
        val provider = GuideChannelOrdering.order(
            rows.reversed(), SettingsRepository.GuideSort.PROVIDER,
            SettingsRepository.SortMode.ALPHA, emptySet(),
        )
        val alpha = GuideChannelOrdering.order(
            rows, SettingsRepository.GuideSort.ALPHA,
            SettingsRepository.SortMode.PLAYLIST, emptySet(),
        )
        assertEquals(listOf(1L, 2L, 3L), provider.map { it.id })
        assertEquals(listOf(2L, 3L, 1L), alpha.map { it.id })
    }

    @Test fun `catchup and favorites float only their matching channels`() {
        val catchup = GuideChannelOrdering.order(
            rows, SettingsRepository.GuideSort.CATCHUP,
            SettingsRepository.SortMode.PLAYLIST, emptySet(),
        )
        val favorites = GuideChannelOrdering.order(
            rows, SettingsRepository.GuideSort.FAVORITES,
            SettingsRepository.SortMode.PLAYLIST, setOf(3),
        )
        assertEquals(2L, catchup.first().id)
        assertEquals(3L, favorites.first().id)
    }

    @Test fun `channels absent from a bounded rail snapshot follow the selected fallback`() {
        val ordered = GuideChannelOrdering.order(
            rows,
            SettingsRepository.GuideSort.LIVE_TV,
            SettingsRepository.SortMode.ALPHA,
            emptySet(),
            liveOrderIds = listOf(3),
        )
        assertEquals(listOf(3L, 2L, 1L), ordered.map { it.id })
    }
    @Test fun `guide initially selects the currently playing channel when present`() {
        assertEquals(2, GuideChannelOrdering.initialRow(rows, 3L))
        assertEquals(0, GuideChannelOrdering.initialRow(rows, 99L))
        assertEquals(0, GuideChannelOrdering.initialRow(emptyList(), 3L))
    }

    @Test fun `return follows playback owner after surfing away from last Guide tune`() {
        assertEquals(2, GuideChannelOrdering.initialRow(rows, 3L, lastTunedChannelId = 1L))
        assertEquals(0, GuideChannelOrdering.initialRow(rows, 99L, lastTunedChannelId = 3L))
        assertEquals(1, GuideChannelOrdering.initialRow(rows, null, lastTunedChannelId = 2L))
    }

    @Test fun `exact Live SQLite order wins over a different case folded alpha order`() {
        val mixedCase = listOf(channel(1, "alpha"), channel(2, "Bravo"), channel(3, "Zulu"))
        assertEquals(listOf(2L, 3L, 1L), GuideChannelOrdering.order(
            mixedCase, SettingsRepository.GuideSort.LIVE_TV, SettingsRepository.SortMode.ALPHA,
            emptySet(), liveOrderIds = listOf(2L, 3L, 1L),
        ).map { it.id })
    }


    @Test fun `cached rows from the previous category cannot consume initial focus`() {
        val previous = tv.own.owntv.core.live.LiveKey.Folder(1)
        val requested = tv.own.owntv.core.live.LiveKey.Folder(2)
        assertFalse(GuideChannelOrdering.loadedRowsMatchCategory(requested, previous, true, true))
        assertTrue(GuideChannelOrdering.loadedRowsMatchCategory(requested, requested, true, true))
        // The general Guide and Live Guide may share a category while using different channel rules.
        assertFalse(GuideChannelOrdering.loadedRowsMatchCategory(requested, requested, true, false))
        assertTrue(GuideChannelOrdering.loadedRowsMatchCategory(null, null, false, false))
    }
}
