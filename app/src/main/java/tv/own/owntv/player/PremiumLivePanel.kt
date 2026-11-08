package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.ui.lounge.*
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Presentation only: owns no engine, source resolution, networking or playback lifecycle. */
@Composable
internal fun PremiumLivePanel(title: String, number: String?, logo: String?, current: XtEpgEntry?, next: XtEpgEntry?,
    technical: List<String>, isPlaying: Boolean, recent: List<ChannelEntity>, playFocus: FocusRequester,
    onToggle: () -> Unit, onGuide: () -> Unit, onMore: () -> Unit, onRecent: (ChannelEntity) -> Unit,
    artwork: @Composable () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); now = System.currentTimeMillis() } }
    Column(modifier.fillMaxWidth().background(Color(0xE9081628), RoundedCornerShape(30.mpx))
        .border(1.mpx, LoungeSurface.Line, RoundedCornerShape(30.mpx)).padding(24.mpx), verticalArrangement = Arrangement.spacedBy(18.mpx)) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.mpx)) {
            Box(Modifier.width(130.mpx).height(190.mpx)) { artwork() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.mpx)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    LoungeLogo(logo, title, Modifier.size(36.mpx))
                    Text(listOfNotNull(number, title).joinToString(stringResource(R.string.content_epg_bits_separator)), style = stageText(23, 700), color = LoungeSurface.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    StageTag(stringResource(R.string.player_live), tint = Color(0xFFFF5568))
                }
                Text(current?.title ?: stringResource(R.string.m4_no_programme), style = stageText(33, 800), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (technical.isNotEmpty()) Text(technical.joinToString(stringResource(R.string.content_epg_bits_separator)), style = stageText(17, 700), color = LoungeSurface.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                current?.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = stageText(20, 400), color = LoungeSurface.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                current?.takeIf { it.stopMs > it.startMs }?.let { programme ->
                    val progress = ((now - programme.startMs).toFloat() / (programme.stopMs - programme.startMs)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(4.mpx).background(LoungeSurface.Line)) {
                        Box(Modifier.fillMaxWidth(progress).height(4.mpx).background(LoungeSurface.Cyan))
                    }
                }
            }
            Column(Modifier.width(270.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                Text(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a")), style = stageText(32, 800), color = Color.White)
                next?.let { Text(stringResource(R.string.m4_up_next), style = stageText(17, 700), color = LoungeSurface.Muted)
                    Text(it.title, style = stageText(21, 700), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Instant.ofEpochMilli(it.startMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a")), style = stageText(18, 500), color = LoungeSurface.Muted) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
            LoungeAction(stringResource(if (isPlaying) R.string.m4_pause else R.string.m4_play), modifier = Modifier.focusRequester(playFocus), onClick = onToggle)
            LoungeAction(stringResource(R.string.m4_guide), onClick = onGuide)
            LoungeAction(stringResource(R.string.m4_controls), onClick = onMore)
        }
        if (recent.isNotEmpty()) {
            Text(stringResource(R.string.m4_recent), style = stageText(18, 700), color = LoungeSurface.Muted)
            LazyRow(Modifier.focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.mpx), contentPadding = PaddingValues(4.mpx)) {
                items(recent.take(12), key = { it.id }) { channel ->
                    LoungeAction(channel.name) { onRecent(channel) }
                }
            }
        }
    }
}
