package tv.own.owntv.player

import java.util.Locale
import tv.own.owntv.R

/**
 * A decoder-evidence-only technical badge set, not an advertised source badge.
 * No filename, title, playlist name, provider quality rank, or unselected track
 * is used here. These describe the active stream, not device output capability.
 */
internal fun verifiedPlaybackBadges(rows: List<StreamInfoRow>): List<Int> {
    val video = rows.firstNotNullOfOrNull { it.value as? StreamInfoValue.Video }
    val audio = rows.firstNotNullOfOrNull { it.value as? StreamInfoValue.Audio }
    val hdr = rows.firstNotNullOfOrNull { it.value as? StreamInfoValue.Hdr }
    val codec = audio?.codec?.lowercase(Locale.ROOT).orEmpty()

    return buildList {
        val width = video?.width ?: 0
        val height = video?.height ?: 0
        if (height >= 2160 && width >= 3840) add(R.string.ondemand_badge_4k)
        else if (height >= 1080 && width >= 1920) add(R.string.ondemand_badge_1080p)
        else if (height >= 720 && width >= 1280) add(R.string.ondemand_badge_720p)

        // A PQ signal is HDR, but does not by itself prove HDR10+ or Dolby Vision.
        when (hdr?.mode) {
            StreamHdrMode.HDR10_PQ -> add(R.string.ondemand_badge_hdr_pq)
            StreamHdrMode.HLG -> add(R.string.ondemand_badge_hlg)
            else -> Unit
        }
        when {
            "eac3-joc" in codec || "ec-3-joc" in codec || "e-ac-3-joc" in codec -> add(R.string.ondemand_badge_atmos_stream)
            "dtsl" in codec || Regex("dts[- ]?hd\\s*(ma|master audio)").containsMatchIn(codec) -> add(R.string.ondemand_badge_dts_hd_ma)
            "dts" in codec -> add(R.string.ondemand_badge_dts)
            "eac3" in codec || "e-ac-3" in codec -> add(R.string.ondemand_badge_dd_plus)
            codec == "ac3" || codec == "a52" -> add(R.string.ondemand_badge_dd)
        }
        when (audio?.channelCount) {
            8 -> add(R.string.ondemand_badge_7_1_source)
            6 -> add(R.string.ondemand_badge_5_1_source)
            2 -> add(R.string.ondemand_badge_stereo_source)
            else -> Unit
        }
    }
}
