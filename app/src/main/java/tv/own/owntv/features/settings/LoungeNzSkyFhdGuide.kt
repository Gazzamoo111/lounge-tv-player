package tv.own.owntv.features.settings

/**
 * Verified Sky Sport 1–9 NZ linear EPG IDs for BEST's FHD listing variants.
 * RAW, Select, Premier League, pop-ups and unrelated channels are excluded.
 */
internal object LoungeNzSkyFhdGuide {
    private val fhd = Regex(
        "^(?:NZ\\s*[|:\\-]\\s*)?SKY\\s*SPORTS?\\s*([1-9])\\s*FHD$",
        RegexOption.IGNORE_CASE,
    )

    fun epgId(channelName: String, categoryName: String, isXtream: Boolean): String? {
        if (!isXtream) return null
        if (!Regex("^\\s*NZ\\s*[|:\\-]\\s*SKY\\s*SPORT", RegexOption.IGNORE_CASE)
                .containsMatchIn(categoryName)) return null
        val match = fhd.matchEntire(channelName.trim()) ?: return null
        return "skysport" + match.groupValues[1] + ".nz"
    }
}
