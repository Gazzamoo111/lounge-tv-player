package tv.own.owntv.features.live

import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import java.util.Locale

/** Display-only deduplication. Backups and original provider rows are never deleted. */
internal object LoungeLiveDuplicates {
    data class Result(val hiddenIds: Set<Long>, val backups: Map<Long, List<Long>>)

    private val quality = Regex("\\b(?:HEVC|FHD|HD|UHD|4K|RAW|VIP|60\\s*FPS|BK)\\b")
    private val cities = Regex("\\b(?:SYDNEY|NSW|MELBOURNE|VIC|PERTH|WA|BRISBANE|QLD|ADELAIDE|SA|CANBERRA|DARWIN|HOBART|GOLD.COAST|LISMORE|NEWCASTLE|NATIONAL)\\b")
    private val prefix = Regex("^(?:AU|UK|US)\\s*(?:\\||:)\\s*")
    private val superscript = Regex("[ᴿᴬᵂ⁶⁰ᶠᵖˢ]+")
    private val dividers = Regex("^#{3,}")
    private val parentheses = Regex("\\([^)]*\\)")
    private val punctuation = Regex("[^A-Z0-9]+")

    private fun region(name: String): String? = when {
        name.startsWith("AU|", ignoreCase = true) -> "AU"
        name.startsWith("UK|", ignoreCase = true) -> "UK"
        name.startsWith("US|", ignoreCase = true) -> "US"
        else -> null
    }

    private fun key(name: String, area: String): String {
        var n = superscript.replace(name.uppercase(Locale.ROOT), " ")
        n = prefix.replace(n, "")
        if (area == "AU") {
            n = cities.replace(n, " ")
                .replace("9NOW", "")
                .replace("CHANNEL 9 GEM", "9GEM")
                .replace("CHANNEL 9", "9")
                .replace("CHANNEL 10", "10")
                .replace("9 GO", "9GO")
                .replace("9 GEM", "9GEM")
                .replace("9 LIFE", "9LIFE")
                .replace("9 RUSH", "9RUSH")
                .replace("KAYO - ", "")
                .replace("FOX SPORTS 502 LEAUGE", "FOX SPORTS 502")
        }
        n = quality.replace(n, " ")
        // US affiliate callsigns (WKAS, WKGB, etc.) and AU regional labels
        // identify distinct feeds. Only remove UK backup markers.
        if (area == "UK") n = n.replace("(BK)", " ")
        return punctuation.replace(n, " ").trim()
    }

    // Metadata priority is only a trial default, not verified stream reliability.
    private fun score(ch: ChannelEntity, cat: CategoryEntity): Int {
        val n = ch.name.uppercase(Locale.ROOT)
        val au = if (region(cat.name) == "AU") {
            when {
                n.contains("SYDNEY") || n.contains("NSW") -> 15
                n.contains("NATIONAL") -> 12
                n.contains("MELBOURNE") || n.contains("VIC") -> 3
                else -> 0
            }
        } else 0
        return (if (ch.epgChannelId.isNullOrBlank()) 0 else 100) +
            (if (ch.logoUrl.isNullOrBlank()) 0 else 6) + au +
            (if (n.contains("HEVC")) -20 else 0) +
            (if (n.contains("UHD") || n.contains("4K")) -30 else 0) +
            (if (n.contains("FHD")) 8 else if (n.contains("HD")) 12 else 0)
    }

    fun choose(channels: List<ChannelEntity>, categories: Map<Long, CategoryEntity>,
               auditedSources: Set<Long>, hiddenCategories: Set<Long>): Result {
        val candidates = channels.filter { ch ->
            ch.sourceId in auditedSources &&
                ch.categoryId != null && ch.categoryId !in hiddenCategories &&
                categories[ch.categoryId]?.let { region(it.name) != null } == true &&
                !dividers.containsMatchIn(ch.name.trim())
        }
        val groups = candidates.groupBy { ch ->
            val cat = categories.getValue(ch.categoryId!!)
            val area = region(cat.name)!!
            val identity = key(ch.name, area)
            // Event slot numbers can refer to entirely different live matches
            // on different channels: never merge them by their generic slot name.
            val eventFolder = Regex("PPV|EVENT|PACKAGE|REPLAY|24/7", RegexOption.IGNORE_CASE)
                .containsMatchIn(cat.name)
            val marathonFolder = area == "US" && Regex("CINEMANIA|NETFLIX|PLUTO|ON AIR|PRIME|MOVIES|TV SHOWS|DISNEY", RegexOption.IGNORE_CASE)
                .containsMatchIn(cat.name)
            if (identity.isBlank() || eventFolder || marathonFolder) "unique:" + ch.id
            else area + ":" + identity
        }
        val hidden = HashSet<Long>()
        val backups = HashMap<Long, List<Long>>()
        for (group in groups.values) {
            if (group.size < 2) continue
            val sorted = group.sortedWith(compareByDescending<ChannelEntity> { ch ->
                score(ch, categories.getValue(ch.categoryId!!))
            }.thenBy { it.sortOrder }.thenBy { it.id })
            val primary = sorted.first()
            val other = sorted.drop(1).map { it.id }
            hidden.addAll(other)
            backups[primary.id] = other
        }
        return Result(hidden, backups)
    }
}
