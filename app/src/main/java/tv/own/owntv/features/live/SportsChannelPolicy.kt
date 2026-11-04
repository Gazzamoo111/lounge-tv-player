package tv.own.owntv.features.live

/** BEST policy only. Saved records remain intact; exact token matching preserves ESPN10/11/2. */
internal object SportsChannelPolicy {
    fun excludedBest(providerId: String?, name: String): Boolean =
        providerId?.substringAfterLast(":") in setOf("646974", "1520168") ||
            Regex("^AU\\s*[|:]?\\s*(?:KAYO\\s*[-:]?\\s*)?ESPN\\s*1(?:\\s|$)", RegexOption.IGNORE_CASE)
                .containsMatchIn(name.trim())

    /**
     * These provider-labelled AU beIN streams have been observed carrying content that disagrees
     * with the published AU guide. Keep playback available, but do not present that guide as truth.
     */
    fun guideUntrusted(providerId: String?, name: String): Boolean =
        providerId?.substringAfterLast(":") in setOf("646977", "646976", "646975") ||
            Regex("^AU\\s*[|:]?\\s*BEIN SPORTS?\\s*[123](?:\\s|$)", RegexOption.IGNORE_CASE)
                .containsMatchIn(name.trim())
}
