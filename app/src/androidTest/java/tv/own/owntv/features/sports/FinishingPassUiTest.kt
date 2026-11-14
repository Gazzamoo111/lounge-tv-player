package tv.own.owntv.features.sports

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.ui.theme.mpx
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.parser.XmltvParser
import tv.own.owntv.core.theme.*
import tv.own.owntv.player.PremiumLivePanel
import tv.own.owntv.ui.theme.OwnTVTheme
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
class FinishingPassUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun adjacentInformationDoesNotRetuneBeforeConfirmation() {
        val channels = (1L..3L).map { ChannelEntity(id = it, sourceId = 1, name = "TEST $it", streamUrl = "http://test/$it") }
        var tuned = 1L
        var browsed: ChannelEntity? = null
        var closed = 0
        var currentChannelId by mutableStateOf(1L)
        var highlighted by mutableStateOf<ChannelEntity?>(null)
        compose.setContent {
            val focus = remember { FocusRequester() }
            OwnTVTheme(ThemeMode.DARK, AccentColor.BLUE, false, animationLevel = AnimationLevel.OFF) {
                PremiumLivePanel("TEST info", null, null, null, null, emptyList(), focus,
                    currentChannelId = currentChannelId, surfChannels = channels,
                    browsedChannel = highlighted, onBrowse = { highlighted = it; browsed = it },
                    onTuneBrowsed = { tuned = it.id }, onGuide = {}, onMore = {}, onToggle = {},
                    onClose = { closed++ })
            }
        }
        compose.onNodeWithTag("live-synopsis-browser").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(2L, browsed?.id); assertEquals(1L, tuned) }
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals(1L, browsed?.id); assertEquals(1L, tuned) }
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(2L, tuned) }
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.Back) }
        compose.runOnIdle { assertEquals(1, closed) }

        // Numeric tuning can move playback outside its previous list before the async rebuild lands.
        // The displayed current channel must stay owner when OK is pressed without information browsing.
        compose.runOnIdle { currentChannelId = 99L; highlighted = null; tuned = 99L }
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(99L, tuned) }
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals(3L, browsed?.id); assertEquals(99L, tuned) }
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(3L, tuned) }
        compose.runOnIdle { highlighted = null; tuned = 99L }
        compose.onNodeWithTag("live-synopsis-browser").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(1L, browsed?.id); assertEquals(99L, tuned) }
    }
    @Test fun horizontalRemoteNavigationReachesPreviewWithoutChangingEvent() {
        val now = System.currentTimeMillis()
        val matches = (1..2).map { Match(it.toString(), Sport.SOCCER, "soccer/eng.1", "eng.1",
            "TEST competition", now + it * 60_000, MatchState.PRE, "TEST scheduled", "TEST event $it") }
        val focus = FocusRequester()
        var mode: InputModeManager? = null
        var selected: Match? = null
        var finds = 0
        compose.setContent {
            mode = LocalInputModeManager.current
            OwnTVTheme(ThemeMode.DARK, AccentColor.BLUE, false, animationLevel = AnimationLevel.OFF) {
                PremiumSportsContent(SportsHubState(discovery = SportLoungeDiscoverySnapshot(matches = matches)),
                    40.mpx, null, null, {}, onSelect = { selected = it }, onOpenMatch = {},
                    onOpenLeague = {}, onFollow = {}, onHold = {}, previewFocus = focus,
                    detail = { m, compact -> SportsPreviewPanel(m, emptyList(), null, false, {}, {}, {},
                        "TEST preview focus", compact = compact, preview = {}, onFind = { finds++ }, previewFocus = focus) })
            }
        }
        compose.runOnIdle { mode!!.requestInputMode(InputMode.Keyboard) }
        compose.onAllNodesWithText("TEST competition").onFirst().performSemanticsAction(SemanticsActions.RequestFocus)
        val before = selected?.key
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("TEST preview focus").assertIsFocused()
        compose.runOnIdle { assertEquals(before, selected?.key) }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, finds) }
    }

    @Test fun xmltvReadFailurePropagatesRatherThanReportingACompleteGuide() = runBlocking {
        val bytes = ("<tv><channel id=\"one\"><display-name>One</display-name></channel>" +
            "<programme channel=\"one\" start=\"20261111000000 +0000\" stop=\"20261111010000 +0000\"><title>First</title></programme>" +
            " ".repeat(16_384)).toByteArray()
        val input = object : InputStream() {
            var offset = 0
            override fun read(): Int {
                if (offset >= bytes.size) throw IOException("TEST interrupted download")
                return bytes[offset++].toInt() and 255
            }
        }
        try {
            XmltvParser.parse(input, onChannel = { _, _, _ -> }, onProgramme = { _, _, _, _, _, _ -> })
            fail("A dropped stream must not look complete")
        } catch (expected: IOException) { assertTrue(expected.message.orEmpty().contains("interrupted")) }
    }
}
