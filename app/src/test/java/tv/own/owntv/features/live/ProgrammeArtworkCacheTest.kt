package tv.own.owntv.features.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgrammeArtworkCacheTest {
    @Test fun `cached artwork is available while unrelated metadata is stalled`() = runTest {
        val cache = ProgrammeArtworkCache({ emptyList() }, {}, { testScheduler.currentTime })
        cache.resolve("warm") { ProgrammeArtworkCache.Fetch(true, "poster") }
        val gate = CompletableDeferred<Unit>()
        val slow = async { cache.resolve("slow") { gate.await(); ProgrammeArtworkCache.Fetch(true, null) } }
        runCurrent()
        val hit = async { cache.resolve("warm") { error("cache hit must not search") } }
        runCurrent()
        assertTrue("before: this hit waited behind the unrelated network mutex", hit.isCompleted)
        assertEquals("poster", hit.await())
        assertFalse(slow.isCompleted)
        gate.complete(Unit); slow.await()
    }
    @Test fun `two visible slots searching one title make one lookup and one disk write`() = runTest {
        var calls = 0; var writes = 0
        val cache = ProgrammeArtworkCache({ emptyList() }, { writes++ })
        val gate = CompletableDeferred<Unit>()
        val a = async { cache.resolve("same") { calls++; gate.await(); ProgrammeArtworkCache.Fetch(true, "poster") } }
        val b = async { cache.resolve("same") { calls++; ProgrammeArtworkCache.Fetch(true, "wrong") } }
        runCurrent(); gate.complete(Unit)
        assertEquals("poster", a.await()); assertEquals("poster", b.await())
        assertEquals(1, calls); assertEquals(1, writes)
    }
    @Test fun `transport failure is retried but completed no-match is cached`() = runTest {
        var calls = 0
        val cache = ProgrammeArtworkCache({ emptyList() }, {})
        cache.resolve("title") { calls++; ProgrammeArtworkCache.Fetch(false, null) }
        cache.resolve("title") { calls++; ProgrammeArtworkCache.Fetch(true, null) }
        cache.resolve("title") { error("negative cache should answer") }
        assertEquals(2, calls)
    }
    @Test fun `cancelled lookup releases queue and cannot poison cache`() = runTest {
        val cache = ProgrammeArtworkCache({ emptyList() }, {})
        val cancelled = async { cache.resolve("title") { awaitCancellation() } }
        runCurrent(); cancelled.cancel(); runCurrent()
        assertEquals("poster", cache.resolve("title") { ProgrammeArtworkCache.Fetch(true, "poster") })
    }
    @Test fun `persisted artwork loads once and expires without a permanent miss`() = runTest {
        var reads = 0
        val cache = ProgrammeArtworkCache({ reads++; listOf(ProgrammeArtworkCache.Entry("saved", "old", 1000)) }, {}, { testScheduler.currentTime })
        assertEquals("old", cache.resolve("saved") { error("not expired") })
        advanceTimeBy(1_001)
        assertEquals("new", cache.resolve("saved") { ProgrammeArtworkCache.Fetch(true, "new") })
        assertEquals(1, reads)
    }
    @Test fun `memory and persisted snapshots remain bounded and recently used titles survive`() = runTest {
        var snapshot = emptyList<ProgrammeArtworkCache.Entry>()
        val cache = ProgrammeArtworkCache({ emptyList() }, { snapshot = it }, capacity = 2)
        for (key in listOf("a", "b")) cache.resolve(key) { ProgrammeArtworkCache.Fetch(true, key) }
        cache.resolve("a") { error("cached") }
        cache.resolve("c") { ProgrammeArtworkCache.Fetch(true, "c") }
        assertEquals(setOf("a", "c"), snapshot.map { it.key }.toSet())
    }
}
