package tv.own.owntv.features.home

import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.features.sports.SportLoungeRules

/**
 * Curated, provider-neutral Home headline selection. No stream requests, network calls,
 * artwork downloads or mutable state: this runs only on already-cached fixture data.
 * A featured event is not an assertion that a broadcast stream has been verified.
 */
internal object MajorEventSelection {
    private const val WINDOW_MS = 14L * 24 * 60 * 60 * 1000

    fun select(fixtures: List<Match>, nowMs: Long, limit: Int = 6): List<Match> {
        if (limit <= 0) return emptyList()
        val candidates = SportLoungeRules.dedupe(fixtures).asSequence()
            .filter { event ->
                event.state == MatchState.IN ||
                    (event.state == MatchState.PRE && event.startMs in nowMs..(nowMs + WINDOW_MS))
            }
            .sortedWith(compareByDescending<Match> { priority(it, nowMs) }
                .thenBy { it.startMs }
                .thenBy { it.key })
            .toList()
        // Variety matters: six generic basketball fixtures must not displace a boxing main event,
        // a rugby international or an important cricket match.
        val selected = mutableListOf<Match>()
        val sportsUsed = mutableSetOf<Sport>()
        for (event in candidates) {
            if (event.sport !in sportsUsed) {
                selected.add(event)
                sportsUsed.add(event.sport)
                if (selected.size == limit) return selected
            }
        }
        for (event in candidates) {
            if (event.key !in selected.map { it.key } && selected.count { it.sport == event.sport } < 2) {
                selected.add(event)
                if (selected.size == limit) return selected
            }
        }
        return selected
    }

    private fun priority(m: Match, nowMs: Long): Int {
        val label = (m.leagueName + " " + m.title + " " + m.feed +
            " " + m.competitors.joinToString(" ") { it.team.name }).lowercase()
        val headliner = when {
            listOf("world cup", "semi-final", "semifinal", "grand final", "championship", "title fight",
                "international", "test match", "ufc", "all blacks", "black caps", "state of origin",
                "playoffs", "super bowl", "champions league", "fifa", "olympic").any(label::contains) -> 60
            listOf("final", "boxing", "nrl", "premier league", "nfl", "nba", "cricket", "rugby")
                .any(label::contains) -> 35
            else -> 0
        }
        val local = if (listOf("new zealand", "all blacks", "black caps", "australia", "kiwis",
                "gallen", "warriors", "breakers").any(label::contains)) 20 else 0
        val sport = when (m.sport) {
            Sport.MMA -> 32
            Sport.RUGBY, Sport.RUGBY_LEAGUE -> 26
            Sport.CRICKET, Sport.SOCCER -> 20
            Sport.FOOTBALL, Sport.BASKETBALL -> 17
            else -> 6
        }
        val imminent = if (m.state == MatchState.IN) 45
            else when (m.startMs - nowMs) {
                in 0..86_400_000L -> 18
                in 0..259_200_000L -> 10
                else -> 0
            }
        return headliner + local + sport + imminent
    }
}
