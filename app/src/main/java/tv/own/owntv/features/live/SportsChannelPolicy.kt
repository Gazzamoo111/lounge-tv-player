package tv.own.owntv.features.live

/** BEST policy only. Saved records remain intact; exact token matching preserves ESPN10/11/2. */
internal object SportsChannelPolicy {
    fun excludedBest(providerId: String?, name: String): Boolean =
        providerId in setOf("646974", "1520168") ||
            Regex("^AU\\s*[|:]?\\s*(?:KAYO\\s*[-:]?\\s*)?ESPN\\s*1(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(name.trim())
}
