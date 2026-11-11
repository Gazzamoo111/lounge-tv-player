package tv.own.owntv.features.live

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleProgrammeDetailsTest {
    private fun row(id: Long) = EpgProgrammeEntity(id, 1, "guide", 1000, 2000, "Programme$id")
    @Test fun `browsing a 48-programme day reads full text only for the settled row`() = runTest {
        var reads = 0
        val details = ScheduleProgrammeDetails { id -> reads++; row(id).copy(description = "synopsis", year = 2020) }
        val day = (1L..48).map(::row)
        val result = async { details.afterFocus(day[10]) }
        runCurrent(); assertEquals(0, reads)
        advanceTimeBy(151); runCurrent()
        assertEquals(1, reads)
        assertEquals("synopsis", result.await().description)
        assertEquals(2020, result.await().year)
        // Before: opening this day fetched descriptions for all 48 programmes.
    }
    @Test fun `rapid focus cancels obsolete detail reads and revisiting uses cache`() = runTest {
        var reads = 0
        val details = ScheduleProgrammeDetails { id -> reads++; row(id) }
        repeat(20) { i ->
            val abandoned = async { details.afterFocus(row(i.toLong() + 1)) }
            runCurrent(); advanceTimeBy(50); abandoned.cancel()
        }
        details.afterFocus(row(21))
        details.afterFocus(row(21))
        assertEquals(1, reads)
    }
    @Test fun `full metadata never overwrites manually shifted display times`() = runTest {
        val details = ScheduleProgrammeDetails { row(it).copy(year = 2021, episode = "1.2") }
        val selected = row(1).copy(startMs = 5000, stopMs = 6000)
        val result = details.afterFocus(selected)
        assertEquals(5000L, result.startMs); assertEquals(6000L, result.stopMs)
        assertEquals("1.2", result.episode)
    }
    @Test fun `provider rows and failed metadata reads retain available programme information`() = runTest {
        val details = ScheduleProgrammeDetails { error("database unavailable") }
        val provider = row(0).copy(description = "provider description")
        assertEquals(provider, details.afterFocus(provider))
        assertEquals(row(1), details.afterFocus(row(1)))
    }
    @Test fun `unexpected row identity is ignored and detail cache is bounded`() = runTest {
        var reads = 0
        val details = ScheduleProgrammeDetails { id -> reads++; row(id) }
        for (i in 1L..33) details.afterFocus(row(i))
        details.afterFocus(row(1))
        assertEquals(34, reads)
        assertEquals(row(3), ScheduleProgrammeDetails { row(99) }.afterFocus(row(3)))
    }
}
