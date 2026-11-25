package tv.own.owntv.features.sports

import java.text.Normalizer
import java.util.Locale

/** Scheduling/text evidence only. Deliberately has no playback-verified state. */
internal object FixtureEvidence {
    enum class Level { GUIDE, EVENT_NAME, BROADCASTER, UNVERIFIED, REJECTED }
    data class Fixture(val home: List<String>, val away: List<String>, val title: String, val startMs: Long)
    data class Programme(val title: String, val description: String?, val startMs: Long, val stopMs: Long)
    data class Result(val level: Level, val programmeTitle: String? = null)
    private const val MINUTE = 60_000L
    private val fixtureSeparator = Regex("\\s+(?:vs\\.?|v|@|versus)\\s+", RegexOption.IGNORE_CASE)
    private val generic = setOf("united", "city", "fc", "afc", "sc", "live", "sport", "sports")

    fun normalized(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun aliases(names: List<String>) = names.map(::normalized).distinct()
        .filter { it.length >= 4 && it !in generic }
    private fun contains(text: String, names: List<String>) = names.any { " $it " in " $text " }

    fun assess(fixture: Fixture, channelName: String, programmes: List<Programme>, broadcasterHint: Boolean): Result {
        if (isRetrospectiveSportsFeed(channelName)) return Result(Level.REJECTED)
        val shared = aliases(fixture.home).intersect(aliases(fixture.away).toSet())
        val home = aliases(fixture.home).filterNot { it in shared }
        val away = aliases(fixture.away).filterNot { it in shared }
        fun both(text: String) = home.isNotEmpty() && away.isNotEmpty() && contains(text, home) && contains(text, away)
        fun contradiction(text: String): Boolean = fixtureSeparator.containsMatchIn(text) &&
            home.isNotEmpty() && away.isNotEmpty() &&
            (contains(normalized(text), home) xor contains(normalized(text), away))
        if (contradiction(channelName)) return Result(Level.REJECTED)
        val relevant = programmes.filter {
            it.stopMs > it.startMs && it.stopMs > fixture.startMs &&
                it.startMs >= fixture.startMs - 90 * MINUTE && it.startMs <= fixture.startMs + 45 * MINUTE
        }
        val atKickoff = relevant.filter { it.startMs <= fixture.startMs && it.stopMs > fixture.startMs }
        val checkedForContradictions = atKickoff.ifEmpty { relevant }
        val conflicting = checkedForContradictions.any { contradiction(it.title) || isRetrospectiveSportsFeed(it.title) }
        if (conflicting) return Result(Level.REJECTED)
        val eventTitle = normalized(fixture.title)
        val exactEvent = eventTitle.length >= 5 && eventTitle !in generic &&
            (eventTitle.any { it.isDigit() } || eventTitle.length >= 10)
        val guide = relevant.firstOrNull {
            val text = normalized(it.title + " " + it.description.orEmpty())
            both(text) || (home.isEmpty() && away.isEmpty() && exactEvent && " $eventTitle " in " $text ")
        }
        if (guide != null) return Result(Level.GUIDE, guide.title)
        val channel = normalized(channelName)
        if (both(channel) || (home.isEmpty() && away.isEmpty() && exactEvent && " $eventTitle " in " $channel ")) {
            return Result(Level.EVENT_NAME)
        }
        return Result(if (broadcasterHint) Level.BROADCASTER else Level.UNVERIFIED)
    }
}
