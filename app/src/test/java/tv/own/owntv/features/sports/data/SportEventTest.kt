package tv.own.owntv.features.sports.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import java.io.File
import java.nio.file.Files

class SportEventTest {
    @Test fun `adapter and cache preserve stable identity and UTC time`() {
        val match = match("42", MatchState.PRE, 2_000_000L)
        val event = CoreMatchSportEventAdapter("espn").adapt(listOf(match), 1_000_000L).single()
        val dir = Files.createTempDirectory("sport-event-test").toFile()
        try {
            val cache = FileSportEventCache(File(dir, "events.json"))
            cache.write(listOf(event))
            val restored = cache.read().single()
            assertEquals("espn:rugby/test#42", restored.stableId)
            assertEquals(match.startMs, restored.startUtcMs)
            assertEquals(match.key, restored.toMatch()?.key)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun `out of order status cannot replace a finished event`() {
        val scheduled = SportEvent.fromMatch(match("42", MatchState.PRE, 2_000_000L), "espn", 3_000L)
        val finished = SportEvent.fromMatch(match("42", MatchState.POST, 2_000_000L), "espn", 2_000L)
        assertEquals(SportEventState.FINISHED, SportEventMerge.dedupe(listOf(finished, scheduled)).single().state)
    }

    @Test fun `stale cache is used only for a failed exact feed and bounded window`() {
        val now = 10_000_000L
        val rugby = SportEvent.fromMatch(match("rugby", MatchState.PRE, now + 60_000L), "espn", now - 5_000L)
        val other = SportEvent.fromMatch(match("other", MatchState.PRE, now + 60_000L).copy(feed = "rugby/other"), "espn", now - 5_000L)
        val kept = SportEventMerge.withSafeStale(emptyList(), listOf(rugby, other), setOf("rugby/test"), now)
        assertEquals(listOf(rugby.stableId), kept.map { it.stableId })
    }

    @Test fun `fixture model does not imply stream playability`() {
        val event = SportEvent.fromMatch(match("42", MatchState.IN, 2_000_000L), "espn", 1_000_000L)
        assertNotNull(event.toMatch())
        assertFalse(event.toJson().has("watchable"))
        assertFalse(event.toJson().has("channelId"))
    }

    private fun match(id: String, state: MatchState, startMs: Long) = Match(
        id = id,
        sport = Sport.RUGBY,
        feed = "rugby/test",
        leagueSlug = "test",
        leagueName = "Test Rugby",
        startMs = startMs,
        state = state,
        statusText = state.name,
        title = "A v B",
    )
}
