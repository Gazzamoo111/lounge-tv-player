package tv.own.owntv.features.live

import tv.own.owntv.ui.components.OwnTVIcon
import java.util.Locale

/** Explicit children followed by the caller's existing sort. User moves always win. */
internal object LoungeEditorialOrder {
    /**
     * Every customer-facing accordion section has its own mark. These assignments intentionally do not
     * fall back to LIVE_TV/PERSON: unrelated categories must remain recognisable at a glance on a TV.
     */
    fun icon(section: LoungeLiveSection): OwnTVIcon = when (section) {
        LoungeLiveSection.MY_CATEGORIES -> OwnTVIcon.FOLDER
        LoungeLiveSection.FAVOURITE_FOLDERS -> OwnTVIcon.FAVORITE
        LoungeLiveSection.NEW_ZEALAND -> OwnTVIcon.NZ_FERN
        LoungeLiveSection.AUSTRALIA -> OwnTVIcon.AU_ROO
        LoungeLiveSection.SPORTS -> OwnTVIcon.SPORTS
        LoungeLiveSection.PPV -> OwnTVIcon.EVENT_TICKET
        LoungeLiveSection.ENTERTAINMENT -> OwnTVIcon.ENTERTAINMENT_SPOTLIGHT
        LoungeLiveSection.STREAMING -> OwnTVIcon.PLAY_CIRCLE
        LoungeLiveSection.US_UK -> OwnTVIcon.LIVE_TV
        LoungeLiveSection.MOVIES -> OwnTVIcon.MOVIES
        LoungeLiveSection.DOCUMENTARIES -> OwnTVIcon.DOCUMENTARY_COMPASS
        LoungeLiveSection.KIDS -> OwnTVIcon.KIDS_KITE
        LoungeLiveSection.MUSIC -> OwnTVIcon.AUDIO
        LoungeLiveSection.SEASONAL -> OwnTVIcon.CONTINUOUS_PLAY
        LoungeLiveSection.INTERNATIONAL -> OwnTVIcon.NETWORK
    }

    /**
     * Distinct marks for the important customer-facing folders within a section. The classifier is
     * deterministic and operates on both canonical gateway names ("Sports · NFL") and provider labels.
     * Unknown folders use their section's coherent mark rather than a random vector.
     */
    fun categoryIcon(section: LoungeLiveSection?, label: String): OwnTVIcon {
        val folder = loungeLiveFolderTitle(label).trim().lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
        return when {
            folder == "us sports" || folder.startsWith("us sports ") -> OwnTVIcon.US_SPORTS
            folder == "uk sports" || folder.startsWith("uk sports ") -> OwnTVIcon.UK_SPORTS
            "rugby league" in folder || "super league" in folder || Regex("(^|\\W)nrl(\\W|$)").containsMatchIn(folder) -> OwnTVIcon.RUGBY_LEAGUE
            "rugby union" in folder || ("rugby" in folder && "league" !in folder) -> OwnTVIcon.RUGBY_UNION
            "cricket" in folder -> OwnTVIcon.CRICKET
            Regex("(^|\\W)(nfl|american football)(\\W|$)").containsMatchIn(folder) -> OwnTVIcon.AMERICAN_FOOTBALL
            Regex("(^|\\W)(nba|basketball)(\\W|$)").containsMatchIn(folder) -> OwnTVIcon.BASKETBALL
            Regex("(^|\\W)(nhl|ice hockey|hockey)(\\W|$)").containsMatchIn(folder) -> OwnTVIcon.HOCKEY
            Regex("(^|\\W)(mlb|baseball)(\\W|$)").containsMatchIn(folder) -> OwnTVIcon.BASEBALL
            "tennis" in folder -> OwnTVIcon.TENNIS
            "golf" in folder -> OwnTVIcon.GOLF
            Regex("(^|\\W)(f1|formula 1|formula one|motorsport|motor sport|racing)(\\W|$)").containsMatchIn(folder) -> OwnTVIcon.MOTORSPORT
            Regex("(^|\\W)(ufc|mma|combat|boxing)(\\W|$)").containsMatchIn(folder) -> OwnTVIcon.MMA
            Regex("(^|\\W)(wwe|wrestling)(\\W|$)").containsMatchIn(folder) -> OwnTVIcon.WRESTLING
            "football" in folder || "soccer" in folder -> OwnTVIcon.FOOTBALL
            "relax" in folder || "ambient" in folder || "calm" in folder -> OwnTVIcon.RELAX_LEAF
            folder.startsWith("24/7") || "24 7" in folder || "always on" in folder -> OwnTVIcon.CONTINUOUS_PLAY
            "lifestyle" in folder || "home" in folder || "food" in folder || "travel" in folder -> OwnTVIcon.LIFESTYLE_HOME
            "documentary" in folder || folder == "docs" || "factual" in folder -> OwnTVIcon.DOCUMENTARY_COMPASS
            "kids" in folder || "family" in folder || "cartoon" in folder || "junior" in folder -> OwnTVIcon.KIDS_KITE
            "series" in folder || "shows" in folder -> OwnTVIcon.SERIES
            "movie" in folder || "cinema" in folder || "film" in folder -> OwnTVIcon.MOVIES
            "entertainment" in folder || "theatre" in folder || "comedy" in folder -> OwnTVIcon.ENTERTAINMENT_SPOTLIGHT
            "ppv" in folder || "pay per view" in folder || "live event" in folder -> OwnTVIcon.EVENT_TICKET
            else -> icon(section ?: LoungeLiveSection.INTERNATIONAL)
        }
    }

    /** Only selected editorial shelves; other categories keep provider/A-Z behavior. */
    fun channelsEnabled(category: String?): Boolean = category != null &&
        loungeLiveSectionOf(category) == LoungeLiveSection.KIDS &&
        loungeLiveFolderTitle(category).equals("Movies", ignoreCase = true)

    val preferredChildren = mapOf(LoungeLiveSection.SPORTS to listOf("US Sports", "UK Sports"))

    fun categoryPriority(section: LoungeLiveSection?, label: String): Int {
        val names = preferredChildren[section].orEmpty()
        return names.indexOfFirst { it.equals(loungeLiveFolderTitle(label), ignoreCase = true) }
            .takeIf { it >= 0 } ?: names.size
    }
}
