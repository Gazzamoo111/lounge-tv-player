package tv.own.owntv.features.home

import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.Sport
import java.util.Locale

internal enum class HeroDivision { MEN, WOMEN }

internal fun heroDivision(event: Match): HeroDivision? {
    val label = buildList {
        add(event.feed)
        add(event.leagueSlug)
        add(event.leagueName)
        add(event.title)
        event.subtitle?.let(::add)
        event.competitors.forEach { side ->
            add(side.team.name)
            add(side.team.shortName)
            add(side.team.abbreviation)
        }
    }.joinToString(" ").lowercase(Locale.ROOT)

    if (Regex("\\b(women|women's|womens|female|ladies|girls)\\b").containsMatchIn(label) ||
        "nrlw" in label || "wta" in label || "lpga" in label) {
        return HeroDivision.WOMEN
    }
    if (Regex("\\b(men|men's|mens|male|boys)\\b").containsMatchIn(label) ||
        Regex("\\batp\\b").containsMatchIn(label)) {
        return HeroDivision.MEN
    }

    // The unqualified Rugby League World Cup competition is the men's competition;
    // the parallel women's tournament is explicitly labelled Women's in provider metadata.
    if (event.sport == Sport.RUGBY_LEAGUE && "world cup" in label) return HeroDivision.MEN

    return null
}

internal fun usesCompetitionIdentityOnly(event: Match): Boolean =
    event.sport == Sport.TENNIS ||
        event.sport == Sport.GOLF ||
        event.sport == Sport.RACING ||
        event.sport == Sport.MMA
