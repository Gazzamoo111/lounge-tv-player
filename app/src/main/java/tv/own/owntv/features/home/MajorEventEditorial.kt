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

    /**
     * Two independently photographed, freely licensed sports scenes per sport.
     * Chosen by stable fixture key so an NBA-heavy Home does not repeat one image
     * on every slide. These are NOT claimed as footage of the named teams/players.
     * Exact event art from the editorial sidecar takes precedence in the hero.
     */
    fun sportBackdrop(match: Match): String {
        val imageIds = when (match.sport) {
            Sport.MMA -> listOf(26582515, 26891604)
            Sport.RUGBY_LEAGUE -> listOf(3746173, 35102715)
            Sport.RUGBY -> listOf(35102715, 3746173)
            Sport.CRICKET -> listOf(21415108, 21415109)
            Sport.BASKETBALL -> listOf(30555522, 30555529)
            Sport.FOOTBALL -> listOf(159505, 14300284)
            Sport.SOCCER -> listOf(37335305, 16014450)
            Sport.RACING -> listOf(30205195, 29252117)
            else -> listOf(16014450, 37335305)
        }
        val id = imageIds[Math.floorMod(match.key.hashCode(), imageIds.size)]
        return "https://images.pexels.com/photos/$id/pexels-photo-$id.jpeg?auto=compress&cs=tinysrgb&w=1600&h=900&fit=crop"
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
