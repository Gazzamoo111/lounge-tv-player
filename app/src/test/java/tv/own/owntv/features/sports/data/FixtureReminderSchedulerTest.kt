package tv.own.owntv.features.sports.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FixtureReminderSchedulerTest {
    @Test fun `set dedupes per profile and survives restart without channel`() = runBlocking {
        val disk = MemoryStore(); val first = FixtureReminderScheduler(disk)
        first.set(4, fixture(2_000_000), 5); first.set(4, fixture(2_000_000), 10); first.set(5, fixture(2_000_000), 5)
        val restored = FixtureReminderScheduler(disk)
        assertEquals(1, restored.observe(4).first().size); assertEquals(10, restored.observe(4).first().single().leadMinutes)
        assertFalse(disk.rows.toString().contains("channel", true))
    }
    @Test fun `remove persists`() = runBlocking {
        val disk = MemoryStore(); val scheduler = FixtureReminderScheduler(disk); scheduler.set(4, fixture(2_000_000), 5)
        assertTrue(scheduler.remove(4, "nfl:42")); assertFalse(scheduler.remove(4, "nfl:42")); assertTrue(FixtureReminderScheduler(disk).observe(4).first().isEmpty())
    }
    @Test fun `kickoff change rearms while unchanged fixture does not redeliver`() = runBlocking {
        val disk = MemoryStore(); val scheduler = FixtureReminderScheduler(disk); scheduler.set(4, fixture(2_000_000), 5)
        assertEquals(1, scheduler.takeDue(4, 1_700_000).size); assertTrue(scheduler.takeDue(4, 1_700_001).isEmpty())
        val restored = FixtureReminderScheduler(disk); restored.reconcile(4, listOf(fixture(2_000_000))); assertTrue(restored.takeDue(4, 1_800_000).isEmpty())
        restored.reconcile(4, listOf(fixture(3_000_000))); assertNull(restored.observe(4).first().single().deliveredStartMs)
        assertTrue(restored.takeDue(4, 2_699_999).isEmpty()); assertEquals(1, restored.takeDue(4, 2_700_000).size)
    }
    @Test fun `cancelled fixture removes reminder before it can fire`() = runBlocking {
        val disk = MemoryStore(); val scheduler = FixtureReminderScheduler(disk); scheduler.set(4, fixture(2_000_000), 5)
        scheduler.reconcile(4, listOf(fixture(2_000_000).copy(active = false)))
        assertTrue(scheduler.observe(4).first().isEmpty())
        assertTrue(scheduler.takeDue(4, 2_000_000).isEmpty())
    }
    private fun fixture(start: Long) = FixtureSchedule("nfl:42", "A v B", start)
    private class MemoryStore : FixtureReminderPersistence {
        var rows = emptyList<FixtureReminder>(); override fun load() = rows.toList(); override fun save(rows: List<FixtureReminder>) { this.rows = rows.toList() }
    }
}
