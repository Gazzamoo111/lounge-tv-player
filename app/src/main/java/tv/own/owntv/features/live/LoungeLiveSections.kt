package tv.own.owntv.features.live

import tv.own.owntv.R
import java.util.Locale

/** Editorial sections for the original provider folder; no stream/database mutation. */
enum class LoungeLiveSection(val titleRes: Int) {
    MY_CATEGORIES(R.string.lounge_section_custom),
    SEASONAL(R.string.lounge_section_seasonal),
    AUSTRALIA(R.string.lounge_section_aus),
    DOCUMENTARIES(R.string.lounge_section_documentaries),
    ENTERTAINMENT(R.string.lounge_section_entertainment),
    KIDS(R.string.lounge_section_kids),
    MOVIES(R.string.lounge_section_movies),
    NEW_ZEALAND(R.string.lounge_section_nz),
    SPORTS(R.string.lounge_section_sports),
    PPV(R.string.lounge_section_ppv),
    STREAMING(R.string.lounge_section_streaming),
    US_UK(R.string.lounge_section_us_uk),
    MUSIC(R.string.lounge_section_music),
    INTERNATIONAL(R.string.lounge_section_other),
}

/** Specific curated overrides have precedence over fuzzy keyword matching. */
internal fun loungeLiveSectionOf(providerCategory: String): LoungeLiveSection {
    val n = providerCategory.trim().lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*\\|\\s*"), "| ")
    fun any(vararg clues: String) = clues.any { n.contains(it) }
    val parent = n.substringBefore('•').trim()
    return when {
        parent == "24/7" -> LoungeLiveSection.SEASONAL
        parent == "australia" -> LoungeLiveSection.AUSTRALIA
        parent == "documentaries & lifestyle" -> LoungeLiveSection.DOCUMENTARIES
        parent == "entertainment" -> LoungeLiveSection.ENTERTAINMENT
        parent == "kids & family" -> LoungeLiveSection.KIDS
        parent == "movies" -> LoungeLiveSection.MOVIES
        parent == "new zealand" -> LoungeLiveSection.NEW_ZEALAND
        parent == "sports" -> LoungeLiveSection.SPORTS
        n.startsWith("nz|") || n == "nz" || n.startsWith("nz freeview") -> LoungeLiveSection.NEW_ZEALAND
        n.startsWith("au| 9now") || n.startsWith("au| australia") -> LoungeLiveSection.AUSTRALIA
        // User-curated category moves from OTHER.
        n == "uk| skymix series" || n == "uk| bbciplayer series" ||
            n.startsWith("uk| prime ᴿᴬᵂ") || n == "uk| prime video series" ||
            n == "uk| itv x vip" -> LoungeLiveSection.ENTERTAINMENT
        n == "uk| skymix docs" -> LoungeLiveSection.DOCUMENTARIES
        n == "uk| sky store" -> LoungeLiveSection.MOVIES
        n == "uk| mc video ᴴᴰ" -> LoungeLiveSection.MUSIC
        n == "uk| fa player" || n == "uk| uefa replay" ||
            n == "uk| championship" || n == "us| ncaab package" ||
            n == "us| ncaaf package" -> LoungeLiveSection.SPORTS
        n == "uk| amazon prime +" -> LoungeLiveSection.PPV
        // The large uncategorised US/UK linear services are separated from themed bundles.
        n.startsWith("us| tv ") || n == "us| tv" || n == "us| prime" ||
            n == "uk| general" || n == "uk| general hevc" ->
            LoungeLiveSection.US_UK
        // Standalone streaming services and named platform channels.
        any("netflix", "apple tv+", "disney+", "hulu", "paramount+", "peacock",
            "tubi", "pluto", "hbo max", "amazon prime", "prime video",
            "discovery +", "now tv", "max espn") -> LoungeLiveSection.STREAMING
        n.startsWith("24/7") -> LoungeLiveSection.SEASONAL
        any("ppv", "pay per view", "prime +") -> LoungeLiveSection.PPV
        any("sport", "rugby", "nrl", "cricket", "football", "soccer", "nfl", "nba",
            "ncaa", "nbl", "afl", "f1", "formula", "motorsport", "racing", "tennis",
            "golf", "ufc", "wwe", "fight", "boxing", "kayo", "espn", "dazn",
            "bein", "sky sp", "league", "mlb", "nhl", "championship", "fa player") ->
            LoungeLiveSection.SPORTS
        any("christmas", "xmas", "holiday", "halloween") -> LoungeLiveSection.SEASONAL
        any("kids", "cartoon", "nickelodeon", "junior", "preschool") -> LoungeLiveSection.KIDS
        any("movies", "movie", "cinema", "film", "starz", "hallmark",
            "mgm", "box office", "showtime") -> LoungeLiveSection.MOVIES
        any("documentary", "documentaries", "discovery", "history",
            "national geographic", "nat geo", "factual", "investigation",
            "food network", "hgtv", "travel", "nature", "lifestyle", "docs") ->
            LoungeLiveSection.DOCUMENTARIES
        any("music", "radio", "mtv", "vh1", "concert", "mc video") -> LoungeLiveSection.MUSIC
        any("entertainment", "general", "comedy", "drama", "reality",
            "premium", "freeview", "network", "series") -> LoungeLiveSection.ENTERTAINMENT
        // No separate News & Business section; any remaining news programmes
        // appear as part of their region's general/mixed channel lineup.
        else -> LoungeLiveSection.INTERNATIONAL
    }
}


/** In Lounge Core QA, the parent before • is the accordion heading; show only the clean leaf below it. */
internal fun loungeLiveLeafTitle(displayName: String): String {
    val parts = displayName.split('•', limit = 2)
    return if (parts.size == 2) parts[1].trim() else displayName.trim()
}
