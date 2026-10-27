package tv.own.owntv.features.live

import tv.own.owntv.core.model.SourceType
import java.util.Locale

/**
 * Source-scoped, reversible default curation for the audited 2026 BEST/Q Live
 * catalogue. Provider database rows, URLs, TVG ids, favourites and playback are
 * never deleted or renamed. Independent customer M3U sources are left alone.
 */
internal object LoungeLiveCatalogueCleanup {
    data class Folder(
        val id: Long,
        val sourceId: Long,
        val sourceType: SourceType?,
        val name: String,
        val count: Int?,
        val synced: Boolean,
    )
    data class Plan(
        val hiddenIds: Set<Long> = emptySet(),
        val sourceIds: Set<Long> = emptySet(),
        val originalNames: Map<Long, String> = emptyMap(),
        val displayNames: Map<Long, String> = emptyMap(),
    )

    private val signatures = setOf(
        "us| espn+ ppv vip", "us| paramount+ ppv", "uk| sports",
        "uk| movies", "nz| sky sports nz", "nz| sky sport",
        "au| australia", "us| flo network", "world live sports",
        "us| biblical/religious",
    )

    /** Audited choices are data, not an over-broad "hide all PPV" name rule. */
    private val EMPTY_POLICY = emptySet<String>()

    private fun norm(raw: String) = raw.trim().lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

    fun plan(folders: List<Folder>, reviewedHidden: Set<String> = EMPTY_POLICY): Plan {
        val auditedSources = folders.groupBy { it.sourceId }.filter { (_, entries) ->
            entries.any { it.sourceType == SourceType.XTREAM } &&
                signatures.count { clue ->
                    entries.any { folder -> norm(folder.name).startsWith(clue) }
                } >= 6
        }.keys
        val relevant = folders.filter { it.sourceId in auditedSources }
        val hidden = relevant.filter { folder ->
            shouldHide(folder.name, folder.count, folder.synced, reviewedHidden)
        }.mapTo(HashSet()) { it.id }
        return Plan(
            hiddenIds = hidden,
            sourceIds = auditedSources,
            originalNames = relevant.associate { it.id to it.name },
            displayNames = relevant.associate { it.id to categoryTitle(it.name) },
        )
    }

    /** Exact category rules from the owner's reviewed screenshot decisions.
     * A KEEP always wins over name heuristics. Unknown/new folders are left
     * visible for review, rather than silently hiding important small sports.
     */
    internal fun shouldHide(
        name: String,
        count: Int?,
        synced: Boolean = true,
        reviewedHidden: Set<String> = EMPTY_POLICY,
    ): Boolean {
        val n = norm(name)
        if (n.startsWith("nz| sky sport")) return false
        return reviewedHidden.any { norm(it) == n }
    }

    fun divider(name: String): Boolean {
        val t = name.trim()
        return t.startsWith("###") && t.endsWith("###")
    }

    fun categoryTitle(original: String): String {
        val n = original.replace(Regex("[ᴿᴬᵂ⁶⁰ᶠᵖˢ]+"), " ")
            .replace(Regex("\\bVIP\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim()
        val match = Regex("^([A-Z]{2})\\|\\s*(.+)$").matchEntire(n)
        val prefix = match?.groupValues?.get(1)
        val name = (match?.groupValues?.get(2) ?: n)
            .lowercase(Locale.ROOT)
            .split(' ')
            .joinToString(" ") { word ->
                if (word.uppercase(Locale.ROOT) in setOf("NZ", "US", "UK", "AU", "NRL", "NFL", "NBA", "MLB", "NHL", "F1", "UFC", "WWE", "HD", "HEVC", "ESPN", "TNT", "HBO", "FOX", "4K", "RAW", "PPV")) word.uppercase(Locale.ROOT)
                else word.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        return when {
            prefix == null -> name
            prefix == "NZ" && name.startsWith("Sky") -> "NZ · " + name
            prefix == "AU" && name.equals("Australia", true) -> "Australia"
            else -> prefix + " · " + name
        }
    }

    fun channelTitle(original: String): String {
        if (divider(original)) return original
        var s = original.trim()
            .replace(Regex("^(?:NZ|AU|UK|US|IR|CA|NA|TV|PRIME|UK-BBCI)\\s*\\|\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[ᴿᴬᵂ⁶⁰ᶠᵖˢ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        // Drop ornamental quality suffixes, but keep RAW on Sky Sport so a
        // customer can distinguish backup feeds when required.
        // Keep HD/FHD/HEVC distinguishable until stream reliability tests can
        // select a primary and fallback: otherwise duplicate streams look identical.
        return s.ifBlank { original }
    }
}
