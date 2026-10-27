package tv.own.owntv.features.sports.data

import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.sports.model.Competitor
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Score
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.Team

enum class SportEventState { SCHEDULED, LIVE, FINISHED, POSTPONED, CANCELLED }

data class SportEventCompetitor(
    val id: String,
    val name: String,
    val shortName: String,
    val abbreviation: String,
    val crestUrl: String?,
    val home: Boolean,
    val score: String?,
)

/** Provider-neutral fixture contract shared by the device cache and the proposed central gateway. */
data class SportEvent(
    val stableId: String,
    val source: String,
    val externalId: String,
    val sport: String,
    val leagueId: String,
    val leagueName: String,
    val feedId: String,
    val leagueSlug: String,
    val title: String,
    val subtitle: String?,
    val startUtcMs: Long,
    val state: SportEventState,
    val statusText: String,
    val competitors: List<SportEventCompetitor>,
    val sourceUpdatedAtMs: Long,
    val lastSuccessfulUpdateMs: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", stableId)
        .put("source", source)
        .put("externalId", externalId)
        .put("sport", sport)
        .put("leagueId", leagueId)
        .put("leagueName", leagueName)
        .put("feedId", feedId)
        .put("leagueSlug", leagueSlug)
        .put("title", title)
        .put("subtitle", subtitle)
        .put("startUtcMs", startUtcMs)
        .put("state", state.name.lowercase())
        .put("statusText", statusText)
        .put("sourceUpdatedAtMs", sourceUpdatedAtMs)
        .put("lastSuccessfulUpdateMs", lastSuccessfulUpdateMs)
        .put("competitors", JSONArray().apply {
            competitors.forEach { side ->
                put(
                    JSONObject()
                        .put("id", side.id)
                        .put("name", side.name)
                        .put("shortName", side.shortName)
                        .put("abbreviation", side.abbreviation)
                        .put("crestUrl", side.crestUrl)
                        .put("home", side.home)
                        .put("score", side.score),
                )
            }
        })

    fun toMatch(): Match? {
        val kind = Sport.ofPath(sport) ?: return null
        return Match(
            id = externalId,
            sport = kind,
            feed = feedId,
            leagueSlug = leagueSlug,
            leagueName = leagueName,
            startMs = startUtcMs,
            state = state.toMatchState(),
            statusText = statusText,
            title = title,
            subtitle = subtitle,
            competitors = competitors.map { side ->
                Competitor(
                    team = Team(side.id, side.name, side.shortName, side.abbreviation, logoUrl = side.crestUrl),
                    home = side.home,
                    winner = null,
                    score = side.score?.let { Score(it, it.toIntOrNull()) },
                )
            },
        )
    }

    companion object {
        fun fromMatch(match: Match, source: String, updatedAtMs: Long): SportEvent = SportEvent(
            stableId = "$source:${match.key}",
            source = source,
            externalId = match.id,
            sport = match.sport.path,
            leagueId = League.keyOf(match.sport, match.leagueSlug),
            leagueName = match.leagueName,
            feedId = match.feed,
            leagueSlug = match.leagueSlug,
            title = match.title,
            subtitle = match.subtitle,
            startUtcMs = match.startMs,
            state = match.state.toSportEventState(),
            statusText = match.statusText,
            competitors = match.competitors.map { side ->
                SportEventCompetitor(
                    id = side.team.id,
                    name = side.team.name,
                    shortName = side.team.shortName,
                    abbreviation = side.team.abbreviation,
                    crestUrl = side.team.logoUrl,
                    home = side.home,
                    score = side.score?.text,
                )
            },
            sourceUpdatedAtMs = updatedAtMs,
            lastSuccessfulUpdateMs = updatedAtMs,
        )

        fun fromJson(json: JSONObject): SportEvent? = runCatching {
            val sides = json.getJSONArray("competitors")
            SportEvent(
                stableId = json.getString("id"),
                source = json.getString("source"),
                externalId = json.getString("externalId"),
                sport = json.getString("sport"),
                leagueId = json.getString("leagueId"),
                leagueName = json.getString("leagueName"),
                feedId = json.getString("feedId"),
                leagueSlug = json.getString("leagueSlug"),
                title = json.getString("title"),
                subtitle = json.optString("subtitle").takeIf { !json.isNull("subtitle") },
                startUtcMs = json.getLong("startUtcMs"),
                state = SportEventState.valueOf(json.getString("state").uppercase()),
                statusText = json.getString("statusText"),
                competitors = (0 until sides.length()).map { index ->
                    val side = sides.getJSONObject(index)
                    SportEventCompetitor(
                        id = side.getString("id"),
                        name = side.getString("name"),
                        shortName = side.getString("shortName"),
                        abbreviation = side.optString("abbreviation"),
                        crestUrl = side.optString("crestUrl").takeIf { !side.isNull("crestUrl") },
                        home = side.getBoolean("home"),
                        score = side.optString("score").takeIf { !side.isNull("score") },
                    )
                },
                sourceUpdatedAtMs = json.getLong("sourceUpdatedAtMs"),
                lastSuccessfulUpdateMs = json.getLong("lastSuccessfulUpdateMs"),
            )
        }.getOrNull()
    }
}

private fun MatchState.toSportEventState(): SportEventState = when (this) {
    MatchState.PRE -> SportEventState.SCHEDULED
    MatchState.IN -> SportEventState.LIVE
    MatchState.POST -> SportEventState.FINISHED
    MatchState.POSTPONED -> SportEventState.POSTPONED
    MatchState.CANCELLED -> SportEventState.CANCELLED
}

private fun SportEventState.toMatchState(): MatchState = when (this) {
    SportEventState.SCHEDULED -> MatchState.PRE
    SportEventState.LIVE -> MatchState.IN
    SportEventState.FINISHED -> MatchState.POST
    SportEventState.POSTPONED -> MatchState.POSTPONED
    SportEventState.CANCELLED -> MatchState.CANCELLED
}
