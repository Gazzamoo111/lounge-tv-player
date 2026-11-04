package tv.own.owntv.features.live

/** Lounge/BEST sports policy. Saved provider records remain intact. */
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

    /**
     * Known Fox AU service identities. Provider ids are preferred because duplicate display names can
     * drift; the name fallback keeps a re-import usable. These ids only affect guide lookup, never tuning.
     */
    fun foxGuideId(providerId: String?, name: String): String? {
        return when (providerId?.substringAfterLast(":")) {
            "646948", "646971", "1520176" -> "FOXCricket.au"
            "646970", "1520171" -> "FoxLeague.au"
            "646969", "1520175" -> "FoxSports3.au"
            "646968", "1520170" -> "FoxFooty.au"
            "646967", "1520174" -> "FoxSports5.au"
            "646966", "1520169" -> "FoxSports6.au"
            "646965", "1520173" -> "FoxSportsMore.au"
            else -> {
                val n = name.trim()
                when {
                    Regex("^AU\\s*[|:]?\\s*(?:KAYO\\s*-\\s*)?FOX(?: SPORTS)?\\s*501(?:\\s+CRICKET)?(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(n) ||
                        Regex("^AU\\s*[|:]?\\s*FOX CRICKET(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(n) -> "FOXCricket.au"
                    Regex("^AU\\s*[|:]?\\s*(?:KAYO\\s*-\\s*)?FOX SPORTS\\s*502(?:\\s+LEAUGE|\\s+LEAGUE)?(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(n) -> "FoxLeague.au"
                    Regex("^AU\\s*[|:]?\\s*(?:KAYO\\s*-\\s*)?FOX SPORTS\\s*503(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(n) -> "FoxSports3.au"
                    Regex("^AU\\s*[|:]?\\s*(?:KAYO\\s*-\\s*)?FOX SPORTS\\s*504(?:\\s+FOTTY|\\s+FOOTY)?(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(n) -> "FoxFooty.au"
                    Regex("^AU\\s*[|:]?\\s*(?:KAYO\\s*-\\s*)?FOX SPORTS\\s*505(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(n) -> "FoxSports5.au"
                    Regex("^AU\\s*[|:]?\\s*(?:KAYO\\s*-\\s*)?FOX SPORTS\\s*506(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(n) -> "FoxSports6.au"
                    Regex("^AU\\s*[|:]?\\s*(?:KAYO\\s*-\\s*)?FOX SPORTS\\s*507(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(n) -> "FoxSportsMore.au"
                    else -> null
                }
            }
        }
    }
}
