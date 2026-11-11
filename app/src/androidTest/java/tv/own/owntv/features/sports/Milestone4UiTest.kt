package tv.own.owntv.features.sports

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.theme.*
import tv.own.owntv.player.*
import kotlinx.coroutines.flow.MutableStateFlow
import tv.own.owntv.ui.theme.*
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.StartupMode
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.i18n.LocaleStore

/** Renderer fixtures are labelled TEST. These tests never open provider streams. */
@RunWith(AndroidJUnit4::class)
class Milestone4UiTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private fun event(id: String, start: Long, state: MatchState) = Match(id, Sport.SOCCER, "soccer/eng.1", "eng.1",
        "TEST competition", start, state, "TEST status", "TEST fixture $id", competitors = listOf(
            Competitor(Team("1", "TEST Blue", abbreviation = "BLU"), true, null, Score("1", 1)),
            Competitor(Team("2", "TEST Cyan", abbreviation = "CYN"), false, null, Score("0", 0))), venue = "TEST venue")
    @Composable private fun Theme(content: @Composable () -> Unit) {
        OwnTVTheme(tv.own.owntv.core.theme.ThemeMode.DARK, tv.own.owntv.core.theme.AccentColor.BLUE, false, animationLevel = AnimationLevel.OFF, content = content)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        // Write into the debuggable app's private files so CI can export renders via run-as
        // even on Android versions that restrict /sdcard/Android/data access.
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "m4-screenshots").apply { mkdirs() }
        val output = File(dir, "$name.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        android.util.Log.i("LoungeM4Screens", "Rendered ${output.absolutePath} (${output.length()} bytes)")
    }
    private fun remoteBack() {
        // Fire remotes send KEYCODE_BACK to the currently focused WINDOW. Espresso's root picker
        // can instead wait on the unfocused host Activity behind an Android dialog.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }
    @Test fun sports_navigation_browses_without_starting_playback_and_renders_actual_screens() {
        val matches = listOf(event("live", now - 60_000, MatchState.IN), event("soon", now + 3600_000, MatchState.PRE))
        var opened = 0; var selections = 0; var previewPicks = 0
        var focusEntry: (() -> Boolean)? = null
        var inputMode: InputModeManager? = null
        var selectedEvent: Match? = null
        compose.setContent { Theme {
            inputMode = LocalInputModeManager.current
            PremiumSportsContent(SportsHubState(discovery = SportLoungeDiscoverySnapshot(matches = matches)), 40.mpx, null,
                { focusEntry = it }, {},
                onSelect = { selections++; selectedEvent = it }, onOpenMatch = { opened++ }, onOpenLeague = {}, onFollow = {}, onHold = {},
                detail = { match, compact -> SportsPreviewPanel(match, emptyList(), null, false,
                    onOpen = { opened++ }, onFind = { previewPicks++ },
                    onPreview = { error("TEST must not tune a stream") },
                    onFullScreen = { error("TEST must not tune a stream") },
                    notice = "TEST preview: no provider connection", compact = compact, preview = {}) })
        } }
        compose.onNodeWithText("My Sports").assertExists()
        // Exercise the screen's real entry focus hook, just as the TV shell does.
        // RequestFocus on a merged text semantics node does not necessarily target its StageSurface.
        compose.runOnIdle {
            inputMode!!.requestInputMode(InputMode.Touch)
            assertEquals(InputMode.Touch, inputMode!!.inputMode)
            assertTrue("Sports should accept entry focus", focusEntry?.invoke() == true)
            assertEquals(InputMode.Keyboard, inputMode!!.inputMode)
        }
        compose.onNodeWithText("My Sports").assertIsFocused()
        capture("01-my-sports-test-data")
        compose.onNodeWithText("Watch in Preview").performClick()
        assertEquals(1, previewPicks)
        assertEquals(0, opened)
        compose.onNodeWithText("My Sports").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        compose.onNodeWithText("All Events").assertIsFocused()
        compose.onNodeWithText("All Events").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("My Sports").assertIsFocused()
        compose.onNodeWithText("Next event").performClick()
        assertEquals(0, opened)
        compose.onNodeWithText("My Sports").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("All Events").assertIsFocused()
        compose.onNodeWithText("All Events").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Choose Leagues").assertExists()
        compose.onNodeWithText("Choose Leagues").performClick()
        compose.onNode(hasText("All") and isFocused()).assertExists()
        remoteBack()
        compose.onNodeWithText("Choose Leagues").assertIsFocused()
        compose.onNodeWithText("Choose Leagues").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Choose Leagues").assertIsFocused()
        compose.onNodeWithText("Choose Leagues").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(hasText("TEST competition") and isFocused()).assertExists()
        compose.runOnIdle { assertEquals("live", selectedEvent?.id) }
        capture("02-all-events-test-data")
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(hasText("TEST competition") and isFocused()).assertExists()
        compose.runOnIdle { assertEquals("soon", selectedEvent?.id) }
        compose.onNodeWithText("Tables").performClick()
        compose.onNodeWithText("Choose a competition to view available standings.").assertExists()
        compose.onNodeWithText("League Networks").performClick()
        compose.onNodeWithText("No broadcaster information is available for these events.").assertExists()
        assertEquals(0, opened)
        assertTrue(selections > 0)
    }
    @Test fun lower_row_focus_updates_active_fixture_without_discovery_until_OK() {
        val matches = listOf(event("live", now - 60_000, MatchState.IN), event("soon", now + 3600_000, MatchState.PRE))
        var requests = 0
        var selectedEvent: Match? = null
        var inputMode: InputModeManager? = null
        compose.setContent { Theme {
            inputMode = LocalInputModeManager.current
            PremiumSportsContent(SportsHubState(discovery = SportLoungeDiscoverySnapshot(matches = matches)), 40.mpx, null,
                null, {}, onSelect = { selectedEvent = it }, onOpenMatch = { requests++ }, onOpenLeague = {},
                onFollow = {}, onHold = {}, detail = { _, _ -> })
        } }
        compose.runOnIdle { inputMode!!.requestInputMode(InputMode.Keyboard) }
        // The last visible competition card belongs to the lower Live & Upcoming rail.
        compose.onNodeWithText("Live & Upcoming").performScrollTo()
        compose.onAllNodesWithText("TEST competition").onLast().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onAllNodesWithText("TEST competition").onLast().assertIsFocused()
        compose.runOnIdle { assertEquals("soon", selectedEvent?.id); assertEquals(0, requests) }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, requests); assertEquals("soon", selectedEvent?.id) }
    }
    @Test fun team_following_changes_content_and_roster_is_rendered() {
        var c by mutableStateOf(SportsConfig())
        val firstSport = SportsCatalog.sports.first()
        val league = SportsCatalog.leaguesOf(firstSport).first()
        compose.setContent { Theme { PremiumFollowContent(c, { listOf(Team("test1", "TEST Team Blue"), Team("test2", "TEST Team Cyan")) },
            { t -> c = c.copy(teams = if (c.teams.any { it.key == t.key }) c.teams.filterNot { it.key == t.key } else c.teams + t) },
            { k -> c = c.copy(leagues = if (k in c.leagues) c.leagues - k else c.leagues + k) },
            { _, _ -> }, { _, _ -> }, {}, 40.mpx) } }
        compose.onNodeWithText(league.name).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("TEST Team Blue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("TEST Team Blue").performClick()
        compose.runOnIdle { assertEquals("test1", c.teams.single().id) }
        capture("03-follow-teams-test-data")
        compose.onAllNodesWithText("TEST Team Blue").onFirst().performClick()
        compose.runOnIdle { assertTrue(c.teams.isEmpty()) }
    }
    @Test fun team_and_league_shared_ids_render_and_reorder_independently() {
        val shared = FollowedTeam(Sport.RUGBY_LEAGUE, "3", "TEST shared team ID")
        val config = SportsConfig(leagues = listOf("rugby-league/3"), teams = listOf(shared,
            FollowedTeam(Sport.RUGBY_LEAGUE, "4", "TEST other team")))
        var teamMove: Pair<String, Int>? = null
        compose.setContent { Theme {
            PremiumFollowContent(config, { emptyList() }, {}, {},
                { key, delta -> teamMove = key to delta },
                { _, _ -> error("Team reorder must not alter a league sharing its ID") }, {}, 40.mpx)
        } }
        compose.onNodeWithText(shared.name).assertExists()
        compose.onNodeWithText("Arrange").performClick()
        compose.onAllNodesWithText(shared.name).onLast().performClick()
        compose.onNodeWithText("Move later").performClick()
        compose.runOnIdle { assertEquals(shared.key to 1, teamMove) }
        remoteBack()
        compose.onNodeWithText("Arrange").assertExists()
    }
    @Test fun player_panel_displays_genuine_supplied_badges_and_only_explicit_actions_fire() {
        var toggles = 0; var guide = 0; var tools = 0; var tunes = 0
        compose.setContent { Theme {
            val focus = remember { FocusRequester() }
            Box(Modifier.fillMaxSize().background(Color(0xFF09203B))) {
                PremiumLivePanel("TEST channel", "7", null, XtEpgEntry("TEST programme", "Renderer validation. No stream is playing.", now-60_000, now+600_000),
                    XtEpgEntry("TEST next programme", null, now+600_000, now+1200_000), listOf("TEST supplied codec"), focus,
                    surfChannels = listOf(tv.own.owntv.core.database.entity.ChannelEntity(id = 42, sourceId = 1, name = "TEST Adjacent Channel",
                        streamUrl = "https://example.invalid/test", number = 42),
                        tv.own.owntv.core.database.entity.ChannelEntity(id = 43, sourceId = 1, name = "TEST Current Channel",
                        streamUrl = "https://example.invalid/current", number = 43)),
                    onToggle = { toggles++ }, onGuide = { guide++ }, onMore = { tools++ }, onClose = {},
                    onTuneBrowsed = { tunes++ },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(30.mpx),
                    currentChannelId = 43)
            }
        } }
        compose.onNodeWithText("TEST supplied codec").assertExists()
        compose.onNodeWithText("TEST programme").assertExists()
        compose.onNodeWithText("TEST next programme").assertExists()
        compose.onNodeWithTag("live-synopsis-browser").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(0, tunes)
        capture("04-player-overlay-test-data")
        assertEquals(0, toggles)
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.MediaPlayPause); pressKey(Key.DirectionUp); pressKey(Key.DirectionDown) }
        assertEquals(1, toggles); assertEquals(1, guide); assertEquals(1, tools); assertEquals(0, tunes)
    }

    @Test fun feed_picker_uses_remote_focus_and_never_plays_on_browse_or_back() {
        val channel = tv.own.owntv.core.database.entity.ChannelEntity(id = 42, sourceId = 1, name = "TEST EPG-supported feed",
            streamUrl = "https://example.invalid/test")
        val feeds = listOf(WatchOption(channel, WatchTier.GUIDE),
            WatchOption(channel.copy(id = 43, name = "TEST possible feed"), WatchTier.LIKELY))
        var open by mutableStateOf(false)
        var picks = 0
        var picked: Long? = null
        compose.setContent { Theme {
            val focus = remember { FocusRequester() }
            tv.own.owntv.ui.lounge.LoungeAction("TEST open feeds", modifier = Modifier.focusRequester(focus)) { open = true }
            if (open) SportsFeedPicker(feeds, { picks++; picked = it.channel.id; open = false }, { open = false }, focus)
        } }
        compose.onNodeWithText("TEST open feeds").performClick()
        compose.onNodeWithText("TEST EPG-supported feed").assertIsFocused()
        compose.onNodeWithText("TEST EPG-supported feed").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("TEST possible feed").assertIsFocused()
        assertEquals(0, picks)
        capture("05-sports-feed-picker-test-data")
        remoteBack()
        compose.onNodeWithText("TEST open feeds").assertIsFocused()
        assertEquals(0, picks)
        compose.onNodeWithText("TEST open feeds").performClick()
        compose.onNodeWithText("TEST EPG-supported feed").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, picks)
        assertEquals(42L, picked)
        compose.onNodeWithText("TEST open feeds").assertIsFocused()
    }

    @Test fun guide_artwork_cancels_obsolete_requests_and_labels_channel_identity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val poster = File(context.cacheDir, "test-channel-artwork.png")
        val bitmap = Bitmap.createBitmap(342, 513, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(13, 45, 72))
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.CYAN; textSize = 25f }
        canvas.drawText("TEST CHANNEL ART", 20f, 245f, paint)
        canvas.drawText("Not now-playing proof", 20f, 290f, paint)
        poster.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val started = java.util.concurrent.atomic.AtomicBoolean(false)
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val requests = java.util.concurrent.atomic.AtomicInteger(0)
        var title by mutableStateOf("TEST obsolete programme")
        val lookup: suspend (String, Int?, tv.own.owntv.core.metadata.MetadataType?) -> String? = { query, _, _ ->
            requests.incrementAndGet()
            if (query == "TEST obsolete programme") {
                started.set(true)
                try { kotlinx.coroutines.awaitCancellation() } finally { cancelled.set(true) }
            } else poster.toURI().toString()
        }
        compose.setContent { Theme {
            Row(Modifier.fillMaxSize().background(Color(0xFF09203B)).padding(50.mpx),
                horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
                val programme = tv.own.owntv.core.database.entity.EpgProgrammeEntity(
                    sourceId = 1, epgChannelId = "test", title = title, startMs = now, stopMs = now + 60000)
                tv.own.owntv.features.live.ProgrammeArtwork(programme, null,
                    Modifier.width(170.mpx).height(250.mpx), channelName = "24/7 • Back to the Future", loadArtwork = lookup)
                tv.own.owntv.features.epg.GuideProgrammeBlock("TEST guide header", "Back to the Future",
                    "TEST 24/7 channel identity", "The schedule does not identify the current film or sequel.", Modifier.weight(1f))
            }
        } }
        compose.waitUntil(5_000) { started.get() }
        compose.runOnIdle { title = "24/7 • 24/7 BACK TO THE FUTURE" }
        compose.waitUntil(5_000) { cancelled.get() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Channel artwork").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Channel artwork").assertExists()
        assertEquals(2, requests.get())
        capture("06-guide-channel-artwork-test-data")
    }
    private class QuietEngine : PlaybackEngine {
        var commands = 0
        override val isPlaying = MutableStateFlow(true)
        override val buffering = MutableStateFlow(false)
        override val error = MutableStateFlow<PlaybackFailure?>(null)
        override val videoRes = MutableStateFlow<String?>(null)
        override val volume = MutableStateFlow(100)
        override val zoomMode = MutableStateFlow(ZoomMode.FIT)
        override val audioCount = MutableStateFlow(0)
        override val subCount = MutableStateFlow(0)
        override val currentMeta = MutableStateFlow(MediaMeta(title = "TEST healthy session"))
        override val isLiveContent = true
        override fun togglePlayPause() { commands++ }
        override fun setZoomMode(mode: ZoomMode) { commands++ }
        override fun adjustVolume(delta: Int) { commands++ }
        override fun toggleMute() { commands++ }
        override fun retry() { commands++ }
        override fun selectAudio(id: Int) { commands++ }
        override fun selectSubtitle(id: Int) { commands++ }
        override fun disableSubtitles() { commands++ }
        override fun audioTracks() = emptyList<TrackOption>()
        override fun textTracks() = emptyList<TrackOption>()
    }
    @Test fun hidden_live_hud_keeps_channel_list_on_left_and_history_on_right() {
        val engine = QuietEngine()
        var channelListOpens = 0
        var historyOpens = 0
        compose.setContent { Theme {
            PlayerHud(
                engine,
                onBack = {},
                onOpenChannelList = { channelListOpens++ },
                onOpenHistoryList = { historyOpens++ },
                premiumLivePanel = { chips, focus, more, close ->
                    PremiumLivePanel(
                        "TEST channel",
                        "7",
                        null,
                        null,
                        null,
                        chips,
                        focus,
                        onToggle = engine::togglePlayPause,
                        onGuide = {},
                        onMore = more,
                        onClose = close,
                    )
                },
            )
        } }
        remoteBack()
        compose.onNodeWithTag("live-synopsis-browser").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle {
            assertEquals(1, channelListOpens)
            assertEquals(0, historyOpens)
        }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle {
            assertEquals(1, channelListOpens)
            assertEquals(1, historyOpens)
        }
        assertEquals(0, engine.commands)
    }

    @Test fun actual_hud_tools_back_and_remount_issue_no_playback_commands() {
        val engine = QuietEngine()
        var attached by mutableStateOf(true)
        // Keep this interaction test inside the HUD's auto-hide interval. Advance frames explicitly
        // so wall-clock instrumentation and Compose's virtual auto-hide timer cannot race Back.
        compose.mainClock.autoAdvance = false
        compose.setContent { Theme {
            if (attached) PlayerHud(engine, onBack = {}, premiumLivePanel = { chips, focus, more, close ->
                PremiumLivePanel("TEST healthy session", null, null, null, null, chips, focus,
                    onToggle = engine::togglePlayPause, onGuide = {}, onMore = more, onClose = close)
            })
        } }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("Playback controls").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("Playback controls").assertDoesNotExist()
        remoteBack()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("Playback controls").assertExists()
        repeat(3) {
            compose.runOnIdle { attached = false }; compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
            compose.runOnIdle { attached = true }; compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
            compose.onNodeWithText("Playback controls").assertExists()
        }
        assertEquals(0, engine.commands)
    }
    @Test fun real_room_folder_membership_order_and_removal_survive_database_reopen() = runBlocking {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        c.deleteDatabase("m4-folder-persistence-test")
        fun open() = androidx.room.Room.databaseBuilder(c, tv.own.owntv.core.database.OwnTVDatabase::class.java, "m4-folder-persistence-test").build()
        val db = open()
        db.profileDao().insert(tv.own.owntv.core.database.entity.ProfileEntity(700001L, "TEST profile A", 0))
        db.profileDao().insert(tv.own.owntv.core.database.entity.ProfileEntity(700002L, "TEST profile B", 0))
        val key = "custom:m4-preserved"
        val dao = db.customCategoryDao()
        dao.appendItem(700001L, MediaType.LIVE, key, 10)
        dao.appendItem(700001L, MediaType.LIVE, key, 20)
        dao.appendItem(700002L, MediaType.LIVE, key, 30)
        assertFalse(dao.exists(700002L, MediaType.LIVE, key, 10))
        dao.deleteItem(700001L, MediaType.LIVE, key, 10)
        dao.appendItem(700001L, MediaType.LIVE, key, 40)
        val rows = dao.getAllOnce().filter { it.profileId == 700001L }.sortedByDescending { it.position }
        dao.replaceContext(700001L, MediaType.LIVE, key, rows.mapIndexed { index, r -> r.copy(id = 0, position = index) })
        db.close()
        val reopened = open()
        val saved = reopened.customCategoryDao().getAllOnce().filter { it.profileId == 700001L }.sortedBy { it.position }
        assertEquals(listOf(40L, 20L), saved.map { it.itemId })
        assertFalse(reopened.customCategoryDao().exists(700001L, MediaType.LIVE, key, 10))
        reopened.close()
    }
    @Test fun real_datastore_survives_reader_recreation_and_keeps_profiles_separate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsRepository(context, LocaleStore.from(context))
        val a = 700001L; val b = 700002L
        settings.updateSportsConfig(a) { SportsConfig(leagues = listOf("soccer/eng.1"), teams = listOf(FollowedTeam(Sport.SOCCER,"test","TEST team"))) }
        settings.updateSportsConfig(b) { SportsConfig(leagues = listOf("basketball/nba")) }
        settings.setStartupMode(a, StartupMode.FAVORITES)
        val restarted = SettingsRepository(context, LocaleStore.from(context))
        assertEquals("test", restarted.sportsConfig(a).first().teams.single().id)
        assertTrue(restarted.sportsConfig(b).first().teams.isEmpty())
        assertEquals(StartupMode.FAVORITES, restarted.startupMode(a).first())
        assertEquals(StartupMode.HOME, restarted.startupMode(b).first())
        val originalProfile = settings.activeProfileId.first()
        try {
            settings.setActiveProfile(a)
            assertEquals("test", restarted.activeSportsConfig.first().teams.single().id)
            settings.setActiveProfile(b)
            assertTrue(restarted.activeSportsConfig.first().teams.isEmpty())
            settings.setActiveProfile(a)
            assertEquals("test", restarted.activeSportsConfig.first().teams.single().id)
        } finally { settings.setActiveProfile(originalProfile) }
        val store = CustomizationStore(context)
        val folder = store.createCustomCategory(a, MediaType.LIVE, "Favourites · TEST sport")
        store.renameCategory(a, MediaType.LIVE, folder.id, "TEST renamed")
        store.setCategoryOrder(a, MediaType.LIVE, listOf(folder.id))
        store.setCategoryHidden(a, MediaType.LIVE, folder.id, true)
        val reloaded = CustomizationStore(context)
        val saved = reloaded.observe(a, MediaType.LIVE).first()
        assertEquals("TEST renamed", saved.categoryNames[folder.id])
        assertTrue(folder.id in saved.hiddenCategories)
        assertTrue(folder.id in saved.categoryOrder)
        assertFalse(reloaded.observe(b, MediaType.LIVE).first().customCategories.any { it.id == folder.id })
        store.deleteCustomCategory(a, MediaType.LIVE, folder.id)
        assertFalse(reloaded.observe(a, MediaType.LIVE).first().customCategories.any { it.id == folder.id })
    }
}
