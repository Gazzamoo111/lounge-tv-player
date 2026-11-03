package tv.own.owntv.features.home

import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.features.sports.data.SportEventEditorial
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Pure, non-network Home editorial copy. Never affects fixture identity or streams. */
internal data class MajorEventHeroCopy(
    val title: String,
    val synopsis: String?,
    val artworkUrl: String?,
    val subtitle: String?,
    val importanceTag: String?,
    val competition: String,
    val venue: String?,
)

internal object MajorEventEditorial {
    // Official No Limit Boxing promotional art, specifically for the 6 Nov 2026 contest.
    private const val GALLEN_ASOFA_IMAGE = "https://cdn.sanity.io/images/ahlq1e7s/production/f47084b2b38a13ae8c43df3dc681570a69c96740-1800x964.png?w=1280&fit=max&auto=format"

    fun forMatch(match: Match, enriched: SportEventEditorial? = null): MajorEventHeroCopy {
        val competition = enriched?.competition.clean() ?: match.leagueName
        val synopsis = enriched?.synopsis.clean()?.takeIf { it.length <= 240 }
        val art = enriched?.heroArtUrl?.trim()?.takeIf {
            it.startsWith("https://") && it.none(Char::isWhitespace)
        } ?: knownEventImage(match)
        return MajorEventHeroCopy(
            title = enriched?.editorialTitle.clean() ?: match.title,
            synopsis = synopsis,
            artworkUrl = art,
            subtitle = enriched?.editorialSubtitle.clean(),
            importanceTag = enriched?.importanceTag.clean(),
            competition = competition,
            venue = enriched?.venue.clean() ?: match.venue.clean(),
        )
    }

    private fun knownEventImage(match: Match): String? {
        if (match.sport != Sport.MMA) return null
        val title = match.title.lowercase(Locale.ROOT)
        if (!title.contains("gallen") || !title.contains("asofa")) return null
        val date = Instant.ofEpochMilli(match.startMs)
            .atZone(ZoneId.of("Australia/Sydney")).toLocalDate()
        return GALLEN_ASOFA_IMAGE.takeIf { date == LocalDate.of(2026, 11, 6) }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
}
