package tv.own.owntv.features.sports

import android.graphics.Bitmap
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.player.EnginePreference
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.features.home.LoungeSportsHomeHero
import tv.own.owntv.features.shell.components.VodPosterArt
import tv.own.owntv.player.MpvVideoSurface
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.theme.*
import java.io.File

/** Local synthetic AV and labelled UI fixtures. No BEST credentials or provider streams. */
@RunWith(AndroidJUnit4::class)
class Milestone6UiTest {
    @get:Rule val compose = createComposeRule()
    @Composable private fun Theme(content: @Composable () -> Unit) {
        OwnTVTheme(tv.own.owntv.core.theme.ThemeMode.DARK, tv.own.owntv.core.theme.AccentColor.BLUE, false, animationLevel = AnimationLevel.OFF, content = content)
    }
    private fun output(name: String, json: JSONObject) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "m6-results").apply { mkdirs() }
        File(dir, "$name.json").writeText(json.toString(2))
        android.util.Log.i("LoungeM6Performance", json.toString())
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "m6-results").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun sports_first_home_carousel_uses_real_fixture_actions_and_local_time() {
        val now = System.currentTimeMillis()
        fun match(id: String, state: MatchState, start: Long) = Match(id, Sport.RUGBY, "rugby/164205", "164205",
            "TEST international rugby", start, state, "TEST status", "TEST fixture $id", competitors = listOf(
                Competitor(Team("nz", "TEST Aotearoa", abbreviation = "NZ", color = "143A2E"), true, null, null),
                Competitor(Team("au", "TEST Australia", abbreviation = "AU", color = "D3A820"), false, null, null)))
        val matches = listOf(match("live", MatchState.IN, now - 60000), match("soon", MatchState.PRE, now + 3600000))
        var opened: Match? = null; var reminded: Match? = null
        compose.setContent { Theme {
            Column(Modifier.fillMaxSize().background(Color(0xFF09203B)).padding(top = 40.mpx)) {
                LoungeSportsHomeHero(matches, 40.mpx, null, {}, { opened = it }, { reminded = it }, active = false)
            }
        } }
        compose.onNodeWithText("Featured live sport").assertExists()
        compose.onNodeWithText("View event & feeds").performClick()
        assertEquals("live", opened?.id)
        compose.onNodeWithText("Next event").performClick()
        compose.onNodeWithText("Set reminder").performClick()
        assertEquals("soon", reminded?.id)
        capture("07-home-sport-lounge-test-data")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Sport Lounge", context.getString(tv.own.owntv.R.string.sports_title))
        assertEquals("My Sports", context.getString(tv.own.owntv.R.string.m4_my_sports))
    }
    @Test fun vod_poster_remote_navigation_records_input_to_idle_latency() {
        val first = FocusRequester()
        compose.setContent { Theme {
            val mode = LocalInputModeManager.current
            LazyRow(Modifier.fillMaxSize().background(Color(0xFF09203B)).padding(40.mpx), horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
                items((0..15).toList()) { index -> StagePoster("TEST movie $index", {}, 200.mpx, 300.mpx,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    artwork = { VodPosterArt(null, OwnTVIcon.MOVIES) }) }
            }
            LaunchedEffect(Unit) { mode.requestInputMode(InputMode.Keyboard); withFrameNanos { }; first.requestFocus() }
        } }
        compose.onNodeWithText("TEST movie 0").assertIsFocused()
        val latencies = mutableListOf<Long>()
        repeat(3) {
            repeat(4) {
                val at = SystemClock.elapsedRealtimeNanos()
                compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
                compose.waitForIdle(); latencies += (SystemClock.elapsedRealtimeNanos() - at) / 1000000
            }
            repeat(4) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) } }
        }
        compose.onNodeWithText("TEST movie 0").assertIsFocused()
        val ordered = latencies.sorted()
        output("navigation", JSONObject().put("environment", "Android emulator; TEST poster placeholders; instrumentation round trip")
            .put("samples", latencies.size).put("p50_input_to_idle_ms", ordered[ordered.size / 2])
            .put("max_input_to_idle_ms", ordered.last()).put("network_poster_fetches", 0))
        capture("08-vod-navigation-test-data")
    }
    @Test fun live_preview_fullscreen_moves_the_same_surface_without_reopening_playback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val media = File(instrumentation.targetContext.cacheDir, "m6-surface-handoff.mp4")
        instrumentation.context.assets.open("m6-local-av.mp4").use { source ->
            media.outputStream().use { source.copyTo(it) }
        }
        val engine = GlobalContext.get().get<tv.own.owntv.player.LivePreviewEngine>()
        var fullscreen by mutableStateOf(false)
        compose.setContent { Theme {
            val shared = remember(engine) { tv.own.owntv.player.SharedLiveVideoSurface(engine) }
            CompositionLocalProvider(tv.own.owntv.player.LocalSharedLiveVideoSurface provides shared) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (!fullscreen) Box(Modifier.width(500.mpx).height(280.mpx)) {
                        tv.own.owntv.player.ExoPreviewSurface(engine, Modifier.fillMaxSize())
                    }
                    if (fullscreen) Box(Modifier.fillMaxSize()) {
                        tv.own.owntv.player.ExoPreviewSurface(engine, Modifier.fillMaxSize(), keepAwake = true)
                    }
                }
            }
        } }
        try {
            // Exactly one play request: every later operation moves its existing view, not its stream.
            compose.runOnIdle { engine.play(media.absolutePath, muted = false) }
            compose.waitUntil(30_000) { engine.error.value != null ||
                (engine.isPlaying.value && engine.videoSize.value != null) }
            assertNull(engine.error.value)
            Thread.sleep(350)
            val originalView = liveSurfaceView()
            val originalSurface = originalView.holder.surface
            assertRenderedFrame(originalView)
            var before = 0L
            compose.runOnIdle { before = engine.livePositionMs() }
            repeat(4) {
                compose.runOnIdle { fullscreen = !fullscreen }
                compose.waitForIdle()
                val movedView = liveSurfaceView()
                assertSame("Preview/fullscreen must retain the physical video view", originalView, movedView)
                assertSame("The existing decoder must retain its output surface", originalSurface, movedView.holder.surface)
                Thread.sleep(350)
                assertRenderedFrame(movedView)
                assertEquals(media.absolutePath, engine.currentUrl)
                assertNull(engine.error.value)
            }
            compose.runOnIdle {
                assertTrue("Playback must advance throughout both directions", engine.livePositionMs() > before + 800)
            }
            output("live-surface-handoff", JSONObject()
                .put("environment", "Android emulator; local synthetic H264/AAC MP4")
                .put("transitions", 4).put("play_requests", 1).put("same_surface_view", true)
                .put("same_surface", true).put("video_pixels_verified", true)
                .put("physical_fire_tv_acceptance", "required"))
        } finally { compose.runOnIdle { engine.stop() } }
    }

    private fun liveSurfaceView(): android.view.SurfaceView {
        var found: android.view.SurfaceView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
            fun visit(view: android.view.View) {
                if (view is android.view.SurfaceView) found = view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            visit(activity.window.decorView)
        }
        return requireNotNull(found) { "Live video must have a physical SurfaceView" }
    }

    private fun assertRenderedFrame(view: android.view.SurfaceView) {
        val bitmap = Bitmap.createBitmap(80, 45, Bitmap.Config.ARGB_8888)
        val finished = java.util.concurrent.CountDownLatch(1)
        var status = android.view.PixelCopy.ERROR_UNKNOWN
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            android.view.PixelCopy.request(view, bitmap, { result -> status = result; finished.countDown() },
                android.os.Handler(android.os.Looper.getMainLooper()))
        }
        assertTrue("PixelCopy must return", finished.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(android.view.PixelCopy.SUCCESS, status)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("The actual video surface must contain the colour test pattern", pixels.any {
            android.graphics.Color.red(it) > 40 || android.graphics.Color.green(it) > 40 || android.graphics.Color.blue(it) > 40
        })
        bitmap.recycle()
    }

    @Test fun exo_local_vod_playback_seeking_and_overlay_preserve_session() = localVod(EnginePreference.EXO_ONLY)
    @Test fun mpv_local_vod_playback_seeking_and_overlay_preserve_session() = localVod(EnginePreference.MPV_ONLY)
    private fun localVod(engine: EnginePreference) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val media = File(context.cacheDir, "m6-local-av.mp4")
        instrumentation.context.assets.open("m6-local-av.mp4").use { source -> media.outputStream().use { source.copyTo(it) } }
        val player = GlobalContext.get().get<OwnTVPlayer>()
        var overlay by mutableStateOf(false)
        compose.setContent { Theme {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                MpvVideoSurface(player, Modifier.fillMaxSize(), autoFrameRate = false)
                if (overlay) androidx.tv.material3.Text("TEST local AV probe; no provider stream")
            }
        } }
        val started = SystemClock.elapsedRealtime()
        val cpuStart = Process.getElapsedCpuTime()
        var bufferingTransitions = 0; var wasBuffering = false
        try {
            // Both engines accept an absolute local filesystem path. Java File.toURI() yields file:/...,
            // which libmpv treated as /file:/... and falsely reported as FileCorrupt on the emulator.
            assertTrue("Synthetic MP4 must exist before native playback", media.isFile && media.length() > 0)
            compose.runOnIdle { player.play(media.absolutePath, title = "TEST local AV probe", isLive = false, vodEngineOverride = engine) }
            compose.waitUntil(30000) { player.error.value != null || (player.isPlaying.value && player.position.value > 250 && player.videoSize.value != null) }
            assertNull("Native AV probe failed", player.error.value)
            assertEquals(engine == EnginePreference.EXO_ONLY, player.isExoActive)
            val readyMs = SystemClock.elapsedRealtime() - started
            val before = player.position.value
            repeat(50) { index ->
                if (index % 10 == 0) compose.runOnIdle { overlay = !overlay }
                val buffering = player.buffering.value
                if (buffering && !wasBuffering) bufferingTransitions++
                wasBuffering = buffering
                Thread.sleep(100)
            }
            assertTrue("Position should advance while controls change", player.position.value > before + 2500)
            compose.runOnIdle { player.seekBy(3000) }
            compose.waitUntil(10000) { player.position.value >= before + 6000 || player.error.value != null }
            assertNull(player.error.value)
            val memory = Debug.MemoryInfo(); Debug.getMemoryInfo(memory)
            output("vod-${engine.name}", JSONObject().put("environment", "Android 35 x86_64 emulator; local synthetic H264/AAC MP4")
                .put("engine", engine.name).put("time_to_playing_position_ms", readyMs)
                .put("position_ms", player.position.value).put("post_start_buffering_transitions", bufferingTransitions)
                .put("process_cpu_ms", Process.getElapsedCpuTime() - cpuStart).put("process_pss_kb", memory.totalPss)
                .put("av_output_drift", "not measured; physical Fire Stick acceptance required")
                .put("provider_network", "none"))
        } finally { compose.runOnIdle { player.stop() } }
    }
}
