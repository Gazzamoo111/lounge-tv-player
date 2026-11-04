package tv.own.owntv.player
import org.junit.Assert.*
import org.junit.Test
class TuneGenerationTest {
 @Test fun supersededFramesCannotClearCurrentLoading() {
  val t=TuneGeneration();val a=t.select(0);val c=t.select(100)
  assertFalse(t.frame(a,110));assertTrue(t.loading);assertTrue(t.frame(c,120));assertFalse(t.loading)
 }
 @Test fun tracesAreBoundedAndFailureIsGenerationScoped() {
  val t=TuneGeneration();repeat(500){t.select(it.toLong())};assertTrue(t.snapshot().size<=128)
  val current=t.generation;assertFalse(t.fail(current-1,501));assertTrue(t.fail(current,502));assertFalse(t.loading)
 }
}
