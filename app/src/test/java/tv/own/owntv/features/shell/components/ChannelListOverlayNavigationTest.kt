package tv.own.owntv.features.shell.components

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class ChannelListOverlayNavigationTest {
    private val channels = (1L..200L).map { id ->
        ChannelEntity(
            id = id,
            sourceId = 1L,
            categoryId = 1L,
            name = "Channel $id",
            remoteId = "$id",
            streamUrl = "http://test.invalid/$id",
        )
    }

    @Test fun categoryReopensAtLastBrowsedChannelWithoutSelectingIt() {
        assertEquals(183, channelOverlayInitialIndex(channels, 2, false, 184L))
    }

    @Test fun categoryWithoutCurrentPlaybackRestoresItsRememberedRow() {
        assertEquals(174, channelOverlayInitialIndex(channels, -1, false, 175L))
    }

    @Test fun unknownPreviousRowFallsBackToPlayingChannel() {
        assertEquals(2, channelOverlayInitialIndex(channels, 2, false, 800L))
    }

    @Test fun categoryWithNoPlayingOrRememberedChannelStartsAtFirstRow() {
        assertEquals(0, channelOverlayInitialIndex(channels, -1, false, null))
    }

    @Test fun historyStartsOneRowBelowCurrentChannel() {
        assertEquals(1, channelOverlayInitialIndex(channels, 0, true, null))
    }

    @Test fun historyWithOnlyOneChannelStartsAtFirstRow() {
        assertEquals(0, channelOverlayInitialIndex(channels.take(1), 0, true, null))
    }
}
