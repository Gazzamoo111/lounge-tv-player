package tv.own.owntv.features.live

import tv.own.owntv.R
import java.util.Locale

/**
 * Presentation-only sections. Provider category IDs, streams, favourites and guide
 * matching are never rewritten. A customer can still rename/move/merge folders.
 */
enum class LoungeLiveSection(val titleRes: Int) {
    MY_CATEGORIES(R.string.lounge_section_custom),
    SPORTS(R.string.lounge_section_sports),
    ENTERTAINMENT(R.string.lounge_section_entertainment),
    MOVIES(R.string.lounge_section_movies),
    DOCUMENTARIES(R.string.lounge_section_documentaries),
    KIDS(R.string.lounge_section_kids),
    NEWS(R.string.lounge_section_news),
    MUSIC(R.string.lounge_section_music),
    SEASONAL(R.string.lounge_section_seasonal),
    INTERNATIONAL(R.string.lounge_section_other),
}

/** Classifies the provider's ORIGINAL category, not a profile's editable display name. */
internal fun loungeLiveSectionOf(providerCategory: String): LoungeLiveSection {
    val n = providerCategory.lowercase(Locale.ROOT)
    fun any(vararg clues: String) = clues.any { n.contains(it) }
    return when {
        any("sport", "rugby", "nrl", "cricket", "football", "soccer", "nfl", "nba",
            "nbl", "afl", "f1", "formula", "motorsport", "racing", "tennis",
            "golf", "ufc", "wwe", "fight", "boxing", "kayo", "espn", "dazn",
            "bein", "ppv", "stan event", "sky sp", "league", "mlb", "nhl") ->
            LoungeLiveSection.SPORTS
        any("christmas", "xmas", "holiday", "halloween", "24/7", "24x7") ->
            LoungeLiveSection.SEASONAL
        any("kids", "cartoon", "nickelodeon", "disney", "junior", "preschool") ->
            LoungeLiveSection.KIDS
        any("movies", "movie", "cinema", "film", "hbo", "starz",
            "hallmark", "mgm", "box office", "showtime") -> LoungeLiveSection.MOVIES
        any("news", "cnn", "bbc world", "sky news", "al jazeera", "bloomberg",
            "cnbc", "business") -> LoungeLiveSection.NEWS
        any("documentary", "documentaries", "discovery", "history",
            "national geographic", "nat geo", "factual", "investigation",
            "food network", "hgtv", "travel", "nature") -> LoungeLiveSection.DOCUMENTARIES
        any("music", "radio", "mtv", "vh1", "concert") -> LoungeLiveSection.MUSIC
        any("entertainment", "general", "comedy", "drama", "reality",
            "lifestyle", "premium", "freeview", "network") -> LoungeLiveSection.ENTERTAINMENT
        else -> LoungeLiveSection.INTERNATIONAL
    }
}
