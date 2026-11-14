package tv.own.owntv.features.live


/** Explicit children followed by the caller's existing sort. User moves always win. */
internal object LoungeEditorialOrder {
    fun icon(section: LoungeLiveSection): tv.own.owntv.ui.components.OwnTVIcon = when (section) {
        LoungeLiveSection.NEW_ZEALAND -> tv.own.owntv.ui.components.OwnTVIcon.NZ_FERN
        LoungeLiveSection.AUSTRALIA -> tv.own.owntv.ui.components.OwnTVIcon.AU_ROO
        LoungeLiveSection.SPORTS -> tv.own.owntv.ui.components.OwnTVIcon.SPORTS
        LoungeLiveSection.ENTERTAINMENT -> tv.own.owntv.ui.components.OwnTVIcon.SPARKLE
        LoungeLiveSection.STREAMING -> tv.own.owntv.ui.components.OwnTVIcon.PLAY_CIRCLE
        LoungeLiveSection.MOVIES -> tv.own.owntv.ui.components.OwnTVIcon.MOVIES
        LoungeLiveSection.DOCUMENTARIES -> tv.own.owntv.ui.components.OwnTVIcon.IMAGE
        LoungeLiveSection.KIDS -> tv.own.owntv.ui.components.OwnTVIcon.SUN
        LoungeLiveSection.SEASONAL -> tv.own.owntv.ui.components.OwnTVIcon.CLOCK
        LoungeLiveSection.US_UK -> tv.own.owntv.ui.components.OwnTVIcon.LIVE_TV
        LoungeLiveSection.MUSIC -> tv.own.owntv.ui.components.OwnTVIcon.AUDIO
        LoungeLiveSection.PPV -> tv.own.owntv.ui.components.OwnTVIcon.REC
        LoungeLiveSection.INTERNATIONAL -> tv.own.owntv.ui.components.OwnTVIcon.LANGUAGE
        LoungeLiveSection.MY_CATEGORIES -> tv.own.owntv.ui.components.OwnTVIcon.FOLDER
        LoungeLiveSection.FAVOURITE_FOLDERS -> tv.own.owntv.ui.components.OwnTVIcon.FAVORITE
    }
    /** Only selected editorial shelves; other categories keep provider/A-Z behavior. */
    fun channelsEnabled(category: String?): Boolean = category != null &&
        loungeLiveSectionOf(category) == LoungeLiveSection.KIDS &&
        loungeLiveFolderTitle(category).equals("Movies", ignoreCase = true)

    /** Apply explicit category positions before any section or editorial defaults. */
    fun <T> orderedCategories(
        rows: List<T>,
        savedKeys: List<String>,
        key: (T) -> String,
        fallback: Comparator<T>,
    ): List<T> {
        val positions = savedKeys.withIndex().associate { (i, key) -> key to i }
        return rows.sortedWith(compareBy<T>(
            { key(it) !in positions }, { positions[key(it)] ?: Int.MAX_VALUE },
        ).then(fallback))
    }

    val preferredChildren = mapOf(LoungeLiveSection.SPORTS to listOf("US Sports", "UK Sports"))

    fun categoryPriority(section: LoungeLiveSection?, label: String): Int {
        val names = preferredChildren[section].orEmpty()
        return names.indexOfFirst { it.equals(loungeLiveFolderTitle(label), ignoreCase = true) }
            .takeIf { it >= 0 } ?: names.size
    }
}
