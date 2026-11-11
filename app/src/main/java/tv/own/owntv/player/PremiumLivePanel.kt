package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.ui.lounge.LoungeLogo
import tv.own.owntv.ui.lounge.LoungeSurface
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Compact, single-focus Live synopsis browser.
 *
 * This composable never owns or starts playback. LEFT/RIGHT only change [browsedChannel] through
 * [onBrowse]; the stream stays with the current player until OK invokes [onTuneBrowsed]. Keeping the
 * whole panel as one focus target also prevents a Fire TV remote from becoming trapped in a row of
 * transport buttons while the user is simply reading programme information.
 */
@Composable
internal fun PremiumLivePanel(
    title: String,
    number: String?,
    logo: String?,
    current: XtEpgEntry?,
    next: XtEpgEntry?,
    technical: List<String>,
    browserFocus: FocusRequester,
    currentChannelId: Long? = null,
    surfChannels: List<ChannelEntity> = emptyList(),
    browsedChannel: ChannelEntity? = null,
    onBrowse: (ChannelEntity) -> Unit = {},
    onTuneBrowsed: (ChannelEntity) -> Unit = {},
    onGuide: () -> Unit,
    onMore: () -> Unit,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var panelFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }

    val anchorId = browsedChannel?.id ?: currentChannelId
    val anchorIndex = surfChannels.indexOfFirst { it.id == anchorId }.let { if (it >= 0) it else 0 }
    val highlighted = browsedChannel ?: surfChannels.getOrNull(anchorIndex)
    val canSurf = surfChannels.size > 1

    fun browse(delta: Int) {
        if (!canSurf) return
        onBrowse(surfChannels[(anchorIndex + delta).mod(surfChannels.size)])
    }

    Column(
        modifier
            .fillMaxWidth()
            .testTag("live-synopsis-browser")
            .focusRequester(browserFocus)
            .onFocusChanged { panelFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { browse(-1); true }
                    Key.DirectionRight -> { browse(1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        highlighted?.takeIf { it.id != currentChannelId }?.let(onTuneBrowsed)
                        true
                    }
                    Key.DirectionUp -> { onGuide(); true }
                    Key.DirectionDown -> { onMore(); true }
                    Key.MediaPlayPause -> { onToggle(); true }
                    Key.Back -> { onClose(); true }
                    else -> false
                }
            }
            .background(Color(0xEA081628), RoundedCornerShape(26.mpx))
            .border(
                if (panelFocused) 2.mpx else 1.mpx,
                if (panelFocused) LoungeSurface.Cyan else LoungeSurface.Line,
                RoundedCornerShape(26.mpx),
            )
            .padding(horizontal = 22.mpx, vertical = 18.mpx),
        verticalArrangement = Arrangement.spacedBy(13.mpx),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .size(92.mpx)
                    .background(LoungeSurface.Panel, RoundedCornerShape(20.mpx))
                    .border(1.mpx, LoungeSurface.Line, RoundedCornerShape(20.mpx)),
                contentAlignment = Alignment.Center,
            ) {
                LoungeLogo(logo, title, Modifier.size(66.mpx))
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.mpx)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                    Text(
                        listOfNotNull(number, title).joinToString(stringResource(R.string.content_epg_bits_separator)),
                        style = stageText(21, 700),
                        color = LoungeSurface.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    StageTag(stringResource(R.string.player_live), tint = Color(0xFFFF5568))
                }
                Text(
                    current?.title ?: stringResource(R.string.m4_no_programme),
                    style = stageText(29, 800),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                current?.let { programme ->
                    Text(
                        stringResource(
                            R.string.lounge_programme_time_range,
                            programme.startMs.nzClock(),
                            programme.stopMs.nzClock(),
                        ),
                        style = stageText(17, 500),
                        color = LoungeSurface.Muted,
                    )
                    if (programme.stopMs > programme.startMs) {
                        val progress = ((now - programme.startMs).toFloat() /
                            (programme.stopMs - programme.startMs)).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(4.mpx).background(LoungeSurface.Line)) {
                            Box(Modifier.fillMaxWidth(progress).height(4.mpx).background(LoungeSurface.Cyan))
                        }
                    }
                }
                current?.description?.takeIf { it.isNotBlank() }?.let { synopsis ->
                    Text(
                        synopsis,
                        style = stageText(18, 400),
                        color = LoungeSurface.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(Modifier.width(250.mpx), verticalArrangement = Arrangement.spacedBy(7.mpx)) {
                next?.let { programme ->
                    Text(stringResource(R.string.m4_up_next), style = stageText(16, 700), color = LoungeSurface.Muted)
                    Text(
                        programme.title,
                        style = stageText(20, 700),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(programme.startMs.nzClock(), style = stageText(17, 500), color = LoungeSurface.Cyan)
                }
                if (technical.isNotEmpty()) {
                    Spacer(Modifier.height(3.mpx))
                    Text(
                        technical.joinToString(stringResource(R.string.content_epg_bits_separator)),
                        style = stageText(15, 700),
                        color = LoungeSurface.Cyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (canSurf) {
                    stringResource(R.string.lounge_surf_position, anchorIndex + 1, surfChannels.size)
                } else title,
                style = stageText(16, 700),
                color = LoungeSurface.Cyan,
            )
            Text(
                stringResource(R.string.lounge_surf_confirm_hint),
                style = stageText(15, 600),
                color = LoungeSurface.Muted,
            )
            Text(
                stringResource(R.string.lounge_surf_actions_hint),
                style = stageText(15, 600),
                color = LoungeSurface.Muted,
            )
        }
    }
}

private fun Long.nzClock(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.of("Pacific/Auckland"))
    .format(DateTimeFormatter.ofPattern("h:mm a"))
