package tv.own.owntv.features.sports

import org.junit.Assert.*
import org.junit.Test

class FixtureEvidenceTest {
    private val start = 1_800_000_000_000L
    private val fixture = FixtureEvidence.Fixture(listOf("Los Angeles Lakers", "Lakers"), listOf("Boston Celtics", "Celtics"), "Lakers vs Celtics", start)
    private fun programme(title: String, description: String? = null, offsetMinutes: Int = 0, duration: Int = 120) =
        FixtureEvidence.Programme(title, description, start + offsetMinutes * 60_000, start + (offsetMinutes + duration) * 60_000)
    private fun assess(channel: String = "ESPN", rows: List<FixtureEvidence.Programme> = emptyList(), hint: Boolean = false) =
        FixtureEvidence.assess(fixture, channel, rows, hint).level

    @Test fun `both teams in overlapping EPG establish guide evidence`() {
        assertEquals(FixtureEvidence.Level.GUIDE, assess(rows = listOf(programme("NBA: Lakers vs Celtics"))))
    }
    @Test fun `description can supply teams when programme title is generic`() {
        assertEquals(FixtureEvidence.Level.GUIDE, assess(rows = listOf(programme("Live NBA", "Los Angeles Lakers meet Boston Celtics"))))
    }
    @Test fun `one team is insufficient to promote a broadcaster`() {
        assertEquals(FixtureEvidence.Level.BROADCASTER, assess(rows = listOf(programme("Lakers coverage")), hint = true))
    }
    @Test fun `explicit wrong opponent defeats exact event channel name`() {
        assertEquals(FixtureEvidence.Level.REJECTED, assess("Lakers vs Celtics HD", listOf(programme("Lakers vs Warriors"))))
    }
    @Test fun `contradictory event channel name is excluded without EPG`() {
        assertEquals(FixtureEvidence.Level.REJECTED, assess("NBA Lakers vs Warriors"))
    }
    @Test fun `replay and highlights cannot be promoted even with both teams`() {
        assertEquals(FixtureEvidence.Level.REJECTED, assess(rows = listOf(programme("Lakers vs Celtics Replay"))))
        assertEquals(FixtureEvidence.Level.REJECTED, assess("Lakers Celtics HIGHLIGHTS"))
    }
    @Test fun `old programme evidence is rejected without rejecting a generic network`() {
        assertEquals(FixtureEvidence.Level.UNVERIFIED, assess(rows = listOf(programme("Lakers vs Celtics", offsetMinutes = -180))))
    }
    @Test fun `distant future game does not count as current evidence`() {
        assertEquals(FixtureEvidence.Level.UNVERIFIED, assess(rows = listOf(programme("Lakers vs Celtics", offsetMinutes = 120))))
    }
    @Test fun `immediately following different fixture does not veto current fixture`() {
        assertEquals(FixtureEvidence.Level.GUIDE, assess(rows = listOf(
            programme("Lakers vs Celtics", duration = 30), programme("Lakers vs Warriors", offsetMinutes = 30))))
    }
    @Test fun `overlapping conflicting source listings are rejected conservatively`() {
        assertEquals(FixtureEvidence.Level.REJECTED, assess(rows = listOf(programme("Lakers vs Celtics"), programme("Lakers vs Warriors"))))
    }
    @Test fun `event name and broadcaster remain distinct from guide evidence`() {
        assertEquals(FixtureEvidence.Level.EVENT_NAME, assess("NBA Lakers vs Celtics FHD"))
        assertEquals(FixtureEvidence.Level.BROADCASTER, assess(hint = true))
        assertEquals(FixtureEvidence.Level.UNVERIFIED, assess())
    }
    @Test fun `shared aliases cannot identify both teams`() {
        val f = FixtureEvidence.Fixture(listOf("Manchester United", "United"), listOf("Newcastle United", "United"), "Match", start)
        assertEquals(FixtureEvidence.Level.UNVERIFIED, FixtureEvidence.assess(f, "United Live", emptyList(), false).level)
    }
    @Test fun `team words require boundaries so jazz does not match jazzercise`() {
        val f = FixtureEvidence.Fixture(listOf("Jazz"), listOf("Heat"), "Jazz vs Heat", start)
        assertEquals(FixtureEvidence.Level.UNVERIFIED, FixtureEvidence.assess(f, "Jazzercise Heatwave", emptyList(), false).level)
    }
    @Test fun `a non-team event requires its full event title`() {
        val f = FixtureEvidence.Fixture(emptyList(), emptyList(), "UFC 333", start)
        assertEquals(FixtureEvidence.Level.UNVERIFIED, FixtureEvidence.assess(f, "UFC 334", emptyList(), false).level)
        assertEquals(FixtureEvidence.Level.EVENT_NAME, FixtureEvidence.assess(f, "UFC 333 HD", emptyList(), false).level)
        assertEquals(FixtureEvidence.Level.EVENT_NAME, FixtureEvidence.assess(f.copy(title = "Formula One Brazilian Grand Prix"), "Formula One Brazilian Grand Prix 4K", emptyList(), false).level)
    }
    @Test fun `invalid zero duration listings never promote`() {
        assertEquals(FixtureEvidence.Level.UNVERIFIED, assess(rows = listOf(programme("Lakers vs Celtics", duration = 0))))
    }
}
