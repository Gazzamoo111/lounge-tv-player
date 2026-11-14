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

/** Identical real-controller scenarios run against the prior patched core and milestone 3. */
@OptIn(ExperimentalCoroutinesApi::class)
class LivePlaybackWorkBenchmarkTest {
    private val before = System.getenv("LOUNGE_M3_BASELINE") == "true"
    private class Engines : LiveEngines {
        override var exoUrl: String? = null
        override val exoIsHls = false
        override val exoFailed = false
        override var mpvHasStream = false
        override var mpvUrl: String? = null
        val opens = ArrayList<String>()
        var replacementsWithoutStop = 0
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
    private fun report(name: String, value: Int) = println("M3_COMPARISON baseline=$before metric=$name value=$value")

    @Test fun `five same-stream fullscreen requests`() = runTest {
        val engines = Engines(); val host = Host(this).apply { preference = EnginePreference.MPV_FIRST }
        val c = LiveTuneController(backgroundScope, engines, host)
        repeat(5) { c.tune(channel(1)); runCurrent() }
        report("mpv_opens_for_five_same_stream_requests", engines.opens.size)
        assertEquals(if (before) 5 else 1, engines.opens.size)
        c.stop()
    }
    @Test fun `single-session fullscreen switch releases old stream first`() = runTest {
        LiveStreamQuirks.clearForTest()
        try {
            LiveStreamQuirks.rememberSessionLimit(channel(2).streamUrl)
            val engines = Engines(); val c = LiveTuneController(backgroundScope, engines, Host(this))
            c.tune(channel(2)); runCurrent()
            c.tune(channel(3)); runCurrent()
            advanceTimeBy(2_001); runCurrent()
            report("source_replacements_without_explicit_session_release", engines.replacementsWithoutStop)
            assertEquals(if (before) 1 else 0, engines.replacementsWithoutStop)
            assertEquals(2, engines.opens.size)
            c.stop()
        } finally { LiveStreamQuirks.clearForTest() }
    }
    @Test fun `obsolete non-cooperative lookup cannot retake latest channel`() = runTest {
        val engines = Engines(); val host = Host(this).apply { waitForFirst = true }
        val c = LiveTuneController(backgroundScope, engines, host)
        c.tune(channel(4)); runCurrent()
        c.tune(channel(5, source = 2)); runCurrent()
        host.firstLookup!!.resume(null); runCurrent()
        val stale = engines.opens.count { it == channel(4).streamUrl }
        report("stale_channel_opens_after_newer_pick", stale)
        assertEquals(if (before) 1 else 0, stale)
        assertEquals(if (before) channel(4).streamUrl else channel(5).streamUrl, engines.opens.last())
        c.stop()
    }
    @Test(timeout = 10_000) fun `cancelled HTTP request releases blocked socket before read timeout`() = kotlinx.coroutines.runBlocking {
        java.net.ServerSocket(0).use { server ->
            val accepted = java.util.concurrent.atomic.AtomicReference<java.net.Socket?>()
            val received = java.util.concurrent.CountDownLatch(1)
            val closed = java.util.concurrent.CountDownLatch(1)
            Thread({
                try { server.accept().use { socket ->
                    accepted.set(socket)
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { }
                    received.countDown()
                    if (input.read() == -1) closed.countDown()
                } } catch (_: Exception) { }
            }, "m3-comparison-http").apply { isDaemon = true; start() }
            val client = okhttp3.OkHttpClient.Builder().readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
            val request = async(kotlinx.coroutines.Dispatchers.Default) {
                tv.own.owntv.core.network.HttpClient(client).getText("http://127.0.0.1:${server.localPort}/resolve")
            }
            try {
                assertTrue(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { received.await(3, java.util.concurrent.TimeUnit.SECONDS) })
                request.cancel()
                val released = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { closed.await(2, java.util.concurrent.TimeUnit.SECONDS) }
                report("cancelled_http_socket_released_before_read_timeout", if (released) 1 else 0)
                assertEquals(!before, released)
            } finally {
                accepted.get()?.close()
                request.cancel()
                request.join()
                client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
            }
        }
    }

}
