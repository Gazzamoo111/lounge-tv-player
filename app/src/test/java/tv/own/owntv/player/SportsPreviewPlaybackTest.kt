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

/** Real playback controller, fake engines. No provider streams are opened. */
@OptIn(ExperimentalCoroutinesApi::class)
class SportsPreviewPlaybackTest {
    private class Engines : LiveEngines {
        override var exoUrl: String? = null
        override val exoIsHls = false
        override val exoFailed = false
        override var mpvHasStream = false
        override var mpvUrl: String? = null
        val opens = ArrayList<String>()
        var replacementsWithoutStop = 0
        var muted = true
        override fun exoPlay(url: String, muted: Boolean, request: LiveRequest) {
            if (exoUrl != null && exoUrl != url) replacementsWithoutStop++
            exoUrl = url; opens += url; this.muted = muted
        }
        override fun exoSetMuted(muted: Boolean) { this.muted = muted }
        override fun exoStop() { exoUrl = null }
        override fun exoReleaseUhdDecoder() {}
        override fun exoAbandon(reason: String) {}
        override suspend fun watchExo(channelName: String, stillOurs: () -> Boolean, handOver: suspend (String) -> Unit,
            onOpened: () -> Unit, postponeDeadline: (Long) -> Unit, log: (String) -> Unit) { onOpened() }
        override fun mpvPlay(url: String, request: LiveRequest) { mpvHasStream = true; mpvUrl = url; opens += url }
        override fun mpvStop() { mpvHasStream = false; mpvUrl = null }
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

    @Test fun `explicit Sports preview enables audio and reselection does not reopen`() = runTest {
        val engines = Engines()
        val controller = LiveTuneController(backgroundScope, engines, Host(this))
        controller.preview(channel(1), muted = false); runCurrent()
        assertFalse(engines.muted)
        repeat(5) { controller.preview(channel(1), muted = false); runCurrent() }
        assertEquals(1, engines.opens.size)
        assertFalse(engines.muted)
        controller.stop()
    }
    @Test fun `Sports preview fullscreen and return preserve one audible session`() = runTest {
        val engines = Engines()
        val controller = LiveTuneController(backgroundScope, engines, Host(this))
        controller.preview(channel(1), muted = false); runCurrent()
        controller.tune(channel(1)); runCurrent()
        controller.detach()
        controller.preview(channel(1), muted = false); runCurrent()
        assertEquals(1, engines.opens.size)
        assertFalse(engines.muted)
        controller.stop()
    }
    @Test fun `explicit audio cannot bypass an active single-connection MPV session`() = runTest {
        val engines = Engines().apply { mpvHasStream = true }
        // Isolated hostname: this test's learned limit cannot affect any other controller test.
        val limited = channel(1).copy(streamUrl = "http://sports-single-session-test.invalid/live/1.ts")
        LiveStreamQuirks.rememberSessionLimit(limited.streamUrl)
        val controller = LiveTuneController(backgroundScope, engines, Host(this))
        controller.preview(limited, muted = false); runCurrent()
        assertTrue(controller.previewBlockedSingleSession.value)
        assertTrue(engines.opens.isEmpty())
        controller.stop()
    }
}
