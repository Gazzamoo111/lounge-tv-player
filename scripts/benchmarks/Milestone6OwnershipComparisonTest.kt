package tv.own.owntv.player

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.player.EnginePreference
import tv.own.owntv.core.stalker.ReconnectUrlProvider

/** The SAME real-controller cases run against QA #610's effective core and the targeted repair. */
@OptIn(ExperimentalCoroutinesApi::class)
class Milestone6OwnershipComparisonTest {
    private val before = System.getenv("LOUNGE_M6_BASELINE") == "true"
    private class Engines : LiveEngines {
        override var exoUrl: String? = null
        override val exoIsHls = false
        override val exoFailed = false
        override var mpvHasStream = false
        override var mpvUrl: String? = null
        val opens = ArrayList<String>()
        var replacementsWithoutStop = 0
        var mpvStops = 0
        override fun exoPlay(url: String, muted: Boolean, request: LiveRequest) {
            if (exoUrl != null && exoUrl != url) replacementsWithoutStop++
            exoUrl = url; opens += url
        }
        override fun exoSetMuted(muted: Boolean) {}
        override fun exoStop() { exoUrl = null }
        override fun exoReleaseUhdDecoder() {}
        override fun exoAbandon(reason: String) {}
        override suspend fun watchExo(channelName: String, stillOurs: () -> Boolean, handOver: suspend (String) -> Unit,
            onOpened: () -> Unit, postponeDeadline: (Long) -> Unit, log: (String) -> Unit) { onOpened() }
        override fun mpvPlay(url: String, request: LiveRequest) { mpvHasStream = true; mpvUrl = url; opens += url }
        override fun mpvStop() { mpvStops++; mpvHasStream = false; mpvUrl = null }
        override suspend fun mpvStopAndAwaitRelease() { mpvStop() }
        override fun mpvAbandon(reason: String) {}
        override suspend fun awaitMpvOutcome(timeoutMs: Long) = MpvOutcome(true, null)
        override fun setReconnectProvider(provider: ReconnectUrlProvider?) {}
    }
    private class Host(private val test: TestScope) : LiveTuneController.Host {
        var preference = EnginePreference.EXO_FIRST
        var waitForFirst = false
        var firstLookup: Continuation<SourceEntity?>? = null
        override suspend fun sourceOf(sourceId: Long): SourceEntity? =
            if (sourceId == 1L && waitForFirst) suspendCoroutine { firstLookup = it } else null
        override fun needsResolve(source: SourceEntity?) = false
        override suspend fun resolve(source: SourceEntity, cmd: String): String? = null
        override suspend fun enginePin(channel: ChannelEntity): Boolean? = null
        override suspend fun pin(channel: ChannelEntity, onMpv: Boolean) {}
        override suspend fun globalPreference() = preference
        override suspend fun globalBudgetSecs() = 30
        override fun meta(channel: ChannelEntity) = MediaMeta(channel.name)
        override fun recordLadderEvent(onExo: Boolean, reason: PlayerFailureReason, detail: String) {}
        override fun nowMs() = test.testScheduler.currentTime
    }
    private fun channel(id: Long, source: Long = 1) = ChannelEntity(id = id, sourceId = source,
        name = "channel$id", streamUrl = "http://m3-work-benchmark.invalid/live/$id.ts")

    private fun cleanup(controller: LiveTuneController) {
        if (before) controller.stop() else controller.javaClass.getMethod("stopBrowsingPreview").invoke(controller)
    }
    @Test fun `browse cleanup cannot interrupt a movie owning the shared MPV session`() = runTest {
        val engines = Engines().apply { mpvHasStream = true; mpvUrl = "http://vod-baseline.invalid/movie.mp4" }
        val controller = LiveTuneController(backgroundScope, engines, Host(this))
        cleanup(controller); runCurrent()
        println("M6_COMPARISON baseline=$before metric=foreign_vod_stop_commands value=${engines.mpvStops}")
        assertEquals(if (before) 1 else 0, engines.mpvStops)
        assertEquals(!before, engines.mpvHasStream)
        controller.stop()
    }
    @Test fun `browse cleanup still releases its own live MPV session`() = runTest {
        val engines = Engines(); val host = Host(this).apply { preference = EnginePreference.MPV_FIRST }
        val controller = LiveTuneController(backgroundScope, engines, host)
        controller.tune(channel(2)); runCurrent(); assertTrue(engines.mpvHasStream)
        val stops = engines.mpvStops
        cleanup(controller); runCurrent()
        println("M6_COMPARISON baseline=$before metric=owned_live_stop_commands value=${engines.mpvStops-stops}")
        assertEquals(1, engines.mpvStops - stops); assertFalse(engines.mpvHasStream)
        controller.stop()
    }
}
