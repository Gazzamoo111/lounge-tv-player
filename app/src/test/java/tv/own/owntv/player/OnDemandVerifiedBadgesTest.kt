package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.R

class OnDemandVerifiedBadgesTest {
    @Test fun selected1080StreamDoesNotInheritAdvertised4k() {
        val rows = listOf(
            StreamInfoRow(StreamInfoLabel.VIDEO, StreamInfoValue.Video(codec = "h264", width = 1920, height = 1080)),
        )
        assertEquals(listOf(R.string.ondemand_badge_1080p), verifiedPlaybackBadges(rows))
    }

    @Test fun pqDoesNotClaimHdr10PlusOrDolbyVision() {
        val rows = listOf(
            StreamInfoRow(StreamInfoLabel.VIDEO, StreamInfoValue.Video(codec = "hevc", width = 3840, height = 2160)),
            StreamInfoRow(StreamInfoLabel.HDR, StreamInfoValue.Hdr(StreamHdrMode.HDR10_PQ)),
        )
        assertEquals(listOf(R.string.ondemand_badge_4k, R.string.ondemand_badge_hdr_pq), verifiedPlaybackBadges(rows))
    }

    @Test fun dtsHdMaIsNotInferredFromGerman() {
        val rows = listOf(
            StreamInfoRow(StreamInfoLabel.AUDIO, StreamInfoValue.Audio(codec = "German DTS-HD HRA", channelCount = 6)),
        )
        assertEquals(listOf(R.string.ondemand_badge_dts, R.string.ondemand_badge_5_1_source), verifiedPlaybackBadges(rows))
    }

    @Test fun exactJocSelectedTrackIndicatesAtmosStreamNotHardwareOutput() {
        val rows = listOf(
            StreamInfoRow(StreamInfoLabel.AUDIO, StreamInfoValue.Audio(codec = "eac3-joc", channelCount = 8)),
        )
        assertEquals(listOf(R.string.ondemand_badge_atmos_stream, R.string.ondemand_badge_7_1_source), verifiedPlaybackBadges(rows))
    }

    @Test fun unknownStreamProducesNoBadge() {
        assertEquals(emptyList<Int>(), verifiedPlaybackBadges(emptyList()))
    }
}
