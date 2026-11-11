package tv.own.owntv.features.sports

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundDemandTest {
    @Test fun `fullscreen VOD cancels retained sports polling and resumes on return`() = runTest {
        var before = 0; var after = 0; var cancelled = 0
        val visible = MutableStateFlow(false)
        val baseline = backgroundScope.launch { flow { while (true) { before++; emit(before); delay(1000) } }.collect() }
        val fixed = backgroundScope.launch {
            visible.whileForeground({ 0 }) { flow {
                try { while (true) { after++; emit(after); delay(1000) } } finally { cancelled++ }
            } }.collect()
        }
        runCurrent(); advanceTimeBy(4001); runCurrent()
        assertEquals(5, before); assertEquals(0, after)
        println("M6_COMPARISON controlled_inactive_polls before=$before after=$after")
        visible.value = true; runCurrent(); assertEquals(1, after)
        visible.value = false; runCurrent(); assertEquals(1, cancelled)
        advanceTimeBy(3000); runCurrent(); assertEquals(1, after)
        visible.value = true; runCurrent(); assertEquals(2, after)
        baseline.cancel(); fixed.cancel()
    }
}
