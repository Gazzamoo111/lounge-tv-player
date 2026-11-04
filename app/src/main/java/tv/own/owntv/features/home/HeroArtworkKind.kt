package tv.own.owntv.features.home

import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.Sport
import java.util.Locale

internal enum class HeroArtworkKind {
    NFL, NBA, RUGBY, SOCCER, MMA, F1, RUGBY_LEAGUE, CRICKET, BOXING, WWE
}

internal fun heroArtworkKind(event: Match): HeroArtworkKind = when (event.sport) {
    Sport.FOOTBALL -> HeroArtworkKind.NFL
    Sport.BASKETBALL -> HeroArtworkKind.NBA
    Sport.RUGBY -> HeroArtworkKind.RUGBY
    Sport.RUGBY_LEAGUE, Sport.AUSSIE_RULES -> HeroArtworkKind.RUGBY_LEAGUE
    Sport.RACING -> HeroArtworkKind.F1
    Sport.SOCCER -> HeroArtworkKind.SOCCER
    Sport.CRICKET -> HeroArtworkKind.CRICKET
    Sport.MMA -> combatArtworkKind(event)
    else -> HeroArtworkKind.SOCCER
}

private fun combatArtworkKind(event: Match): HeroArtworkKind {
    val label = listOf(event.feed, event.leagueSlug, event.leagueName, event.title)
        .joinToString(" ")
        .lowercase(Locale.ROOT)

    if (Regex("\\b(wwe|wrestlemania|smackdown|nxt|royal rumble|summerslam|survivor series)\\b")
            .containsMatchIn(label)) {
        return HeroArtworkKind.WWE
    }
    if (Regex("\\b(bare ?knuckle|bkfc)\\b").containsMatchIn(label)) {
        return HeroArtworkKind.MMA
    }
    if (Regex("\\b(boxing|boxerbook|wbc|wba|ibf|wbo)\\b").containsMatchIn(label) ||
        "matchroom" in label || "golden boy" in label) {
        return HeroArtworkKind.BOXING
    }
    return HeroArtworkKind.MMA
}
