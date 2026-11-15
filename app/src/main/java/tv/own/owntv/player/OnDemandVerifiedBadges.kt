package tv.own.owntv.player

import java.util.Locale

/**
 * A decoder-evidence-only technical badge set, not an advertised source badge.
 * No filename, title, playlist name, provider quality rank, or unselected track
 * is used here. These describe the active stream, not device output capability.
 */
internal fun verifiedPlaybackBadges(rows: List<StreamInfoRow>): List<String> {
    val video = rows.firstNotNullOfOrNull { it.value as? StreamInfoValue.Video }
    val audio = rows.firstNotNullOfOrNull { it.value as? StreamInfoValue.Audio }
    val hdr = rows.firstNotNullOfOrNull { it.value as? StreamInfoValue.Hdr }
    val codec = audio?.codec?.lowercase(Locale.ROOT).orEmpty()

    return buildList {
        val width = video?.width ?: 0
        val height = video?.height ?: 0
        if (height >= 2160 && width >= 3840) add("4K")
        else if (height >= 1080 && width >= 1920) add("1080p")
        else if (height >= 720 && width >= 1280) add("720p")

        // A PQ signal is HDR, but does not by itself prove HDR10+ or Dolby Vision.
        when (hdr?.mode) {
            StreamHdrMode.HDR10_PQ -> add("HDR PQ")
            StreamHdrMode.HLG -> add("HLG")
            else -> Unit
        }
        when {
            "eac3-joc" in codec || "ec-3-joc" in codec || "e-ac-3-joc" in codec -> add("Atmos stream")
            "dtsl" in codec || Regex("dts[- ]?hd\\s*(ma|master audio)").containsMatchIn(codec) -> add("DTS-HD MA")
            "dts" in codec -> add("DTS")
            "eac3" in codec || "e-ac-3" in codec -> add("Dolby Digital+")
            codec == "ac3" || codec == "a52" -> add("Dolby Digital")
        }
        when (audio?.channelCount) {
            8 -> add("7.1 source")
            6 -> add("5.1 source")
            2 -> add("Stereo source")
            else -> Unit
        }
    }
}
