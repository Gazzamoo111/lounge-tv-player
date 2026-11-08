package tv.own.owntv.features.customize

/** Favourites folders reuse the profile's custom-category storage, including backup and sync. */
internal object FavouriteFolders {
    const val PREFIX = "Favourites · "
    private const val MAX_TITLE_LENGTH = 48

    fun isFolder(name: String): Boolean =
        name.startsWith(PREFIX, ignoreCase = true) && name.drop(PREFIX.length).isNotBlank()

    fun displayName(name: String): String =
        if (isFolder(name)) name.drop(PREFIX.length).trim() else name

    fun canonicalName(input: String): String? {
        val compact = input.trim().replace(Regex("\\s+"), " ")
        val title = (if (compact.startsWith(PREFIX, ignoreCase = true)) {
            compact.drop(PREFIX.length)
        } else compact).take(MAX_TITLE_LENGTH).trim()
        return title.takeIf { it.isNotEmpty() }?.let { PREFIX + it }
    }

    fun sameFolder(a: String, b: String): Boolean =
        isFolder(a) && isFolder(b) && displayName(a).equals(displayName(b), ignoreCase = true)
}
