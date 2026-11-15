package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class OnDemandVerifiedBadgesTest {
    @Test fun selected1080StreamDoesNotInheritAdvertised4k() {
        val rows = listOf(
            StreamInfoRow(StreamInfoLabel.VIDEO, StreamInfoValue.Video(codec = "h264", width = 1920, height = 1080)),
        )
        assertEquals(listOf("1080p"), verifiedPlaybackBadges(rows))
    }

    @Test fun pqDoesNotClaimHdr10PlusOrDolbyVision() {
        val rows = listOf(
            StreamInfoRow(StreamInfoLabel.VIDEO, StreamInfoValue.Video(codec = "hevc", width = 3840, height = 2160)),
            StreamInfoRow(StreamInfoLabel.HDR, StreamInfoValue.Hdr(StreamHdrMode.HDR10_PQ)),
        )
        assertEquals(listOf("4K", "HDR PQ"), verifiedPlaybackBadges(rows))
    }

    @Test fun dtsHdMaIsNotInferredFromGerman() {
        val rows = listOf(
            StreamInfoRow(StreamInfoLabel.AUDIO, StreamInfoValue.Audio(codec = "German DTS-HD HRA", channelCount = 6)),
        )
        assertEquals(listOf("DTS", "5.1 source"), verifiedPlaybackBadges(rows))
    }

    @Test fun exactJocSelectedTrackIndicatesAtmosStreamNotHardwareOutput() {
        val rows = listOf(
            StreamInfoRow(StreamInfoLabel.AUDIO, StreamInfoValue.Audio(codec = "eac3-joc", channelCount = 8)),
        )
        assertEquals(listOf("Atmos stream", "7.1 source"), verifiedPlaybackBadges(rows))
    }

    @Test fun unknownStreamProducesNoBadge() {
        assertEquals(emptyList<String>(), verifiedPlaybackBadges(emptyList()))
    }
}
