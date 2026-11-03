package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import tv.own.owntv.features.sports.data.SportEventEditorial
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One lightweight, focus-safe spotlight above VOD rails.
 * No stream preview, background autoplay, external artwork lookup or tune on focus.
 * Pressing View event navigates to Sport Lounge, where candidates are verified/ranked.
 */
@Composable
internal fun MajorEventsHero(
    events: List<Match>,
    reminders: Set<String>,
    start: Dp,
    focusRequester: FocusRequester?,
    onViewEvent: (Match) -> Unit,
    onToggleReminder: (Match) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    editorialByEvent: Map<String, SportEventEditorial> = emptyMap(),
) {
    var selected by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var resetClock by remember { mutableIntStateOf(0) }
    val currentIndex = selected.coerceIn(0, (events.size - 1).coerceAtLeast(0))
    val event = events.getOrNull(currentIndex)
    val editorialCopy = event?.let { MajorEventEditorial.forMatch(it, editorialByEvent[it.key]) }

    // Same ten-second carousel and focusable progress pager as the original Home hero.
    LaunchedEffect(events.map { it.key }, currentIndex, resetClock) {
        if (events.size < 2) return@LaunchedEffect
        val started = System.nanoTime()
        while (true) {
            val elapsed = (System.nanoTime() - started) / 1_000_000L
            progress = (elapsed.toFloat() / 10_000f).coerceIn(0f, 1f)
            if (elapsed >= 10_000L) break
            delay(120L)
        }
        progress = 0f
        selected = (currentIndex + 1) % events.size
    }
    val navigate: (Int) -> Unit = { index ->
        selected = index
        progress = 0f
        resetClock++
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(520.mpx)
            .clip(RoundedCornerShape(24.mpx))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF111F39), Color(0xFF142E4B), Color(0xFF122039))
                )
            )
            .focusGroup()
    ) {
        if (event == null) {
            Column(Modifier.fillMaxSize().padding(start = start, top = 66.mpx), verticalArrangement = Arrangement.spacedBy(22.mpx)) {
                Text(stringResource(R.string.lounge_major_events_title), style = stageText(22, 800), color = Color(0xFF52D4FF))
                Text(stringResource(R.string.lounge_major_events_loading), style = stageText(44, 800), color = StageColors.Text)
                Text(stringResource(R.string.lounge_major_events_empty), style = stageText(20, 500), color = StageColors.Body)
            }
        } else {
        // Render event shell immediately. The existing Coil loader caches imagery to
        // disk/memory; no metadata lookup, blocking fetch or extra image framework.
        val artworkUrl = editorialCopy?.artworkUrl
        var artworkReady by remember(event.key, artworkUrl) { mutableStateOf(false) }
        val context = LocalContext.current
        val artworkRequest = remember(context, artworkUrl) {
            artworkUrl?.let {
                ImageRequest.Builder(context).data(it).size(1280, 720)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED).build()
            }
        }
        Box(
            Modifier.align(Alignment.CenterEnd).size(940.mpx, 520.mpx)
                .background(eventArtworkBrush(event)),
        ) {
            if (artworkRequest != null) {
                AsyncImage(
                    model = artworkRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { artworkReady = true },
                    onError = { artworkReady = false },
                    modifier = Modifier.fillMaxSize().alpha(if (artworkReady) 1f else 0f),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(
                        Color(0xFF111F39), Color(0xCC111F39), Color(0x00111F39),
                    ))
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x9907111D)))
                )
            )
            if (!artworkReady) {
                EventLogoFallback(event)
            } else {
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 34.mpx, bottom = 24.mpx),
                    horizontalArrangement = Arrangement.spacedBy(12.mpx),
                ) {
                    event.competitors.take(2).forEach { side ->
                        side.team.logoUrl?.takeIf { it.startsWith("https://") }?.let { logo ->
                            Box(
                                Modifier.size(82.mpx).clip(RoundedCornerShape(16.mpx))
                                    .background(Color(0xAA081624)),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = logo, contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(66.mpx),
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxSize().padding(start = start, top = 28.mpx, end = 24.mpx),
            verticalArrangement = Arrangement.spacedBy(10.mpx)
        ) {
            Text(
                stringResource(R.string.lounge_major_events_title),
                style = stageText(22, 800),
                color = Color(0xFF52D4FF)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                StageTag(stringResource(
                    if (event.state == MatchState.IN) R.string.lounge_major_events_live
                    else R.string.lounge_major_events_upcoming
                ))
                StageTag(editorialCopy?.competition ?: event.leagueName)
                editorialCopy?.importanceTag?.let { StageTag(it) }
            }
            Text(
                editorialCopy?.title ?: event.title,
                style = stageText(48, 800),
                color = StageColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(780.mpx)
            )
            Text(
                eventLocalTime(event.startMs) + " · " + stringResource(R.string.lounge_major_events_nz_time),
                style = stageText(21, 600),
                color = StageColors.Body
            )
            val meta = listOfNotNull(editorialCopy?.subtitle, editorialCopy?.venue).distinct().joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta, style = stageText(17, 500), color = StageColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(760.mpx),
                )
            }
            editorialCopy?.synopsis?.let { synopsis ->
                Text(
                    synopsis, style = stageText(21, 500), color = StageColors.Body,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(760.mpx),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                StageButton(
                    text = stringResource(
                        if (event.state == MatchState.IN) R.string.lounge_major_events_channels
                        else R.string.lounge_major_events_view
                    ),
                    icon = OwnTVIcon.PLAY,
                    onClick = { onViewEvent(event) },
                    modifier = Modifier
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged { if (it.hasFocus) onFocused() }
                )
                if (event.state == MatchState.PRE) {
                    StageButton(
                        text = stringResource(if (event.key in reminders)
                            R.string.lounge_major_events_reminder_set
                            else R.string.lounge_major_events_remind),
                        icon = OwnTVIcon.BELL,
                        onClick = { onToggleReminder(event) },
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) onFocused() }
                    )
                }
            }
            if (events.size > 1) {
                TrendingPager(
                    count = events.size,
                    activeIndex = currentIndex,
                    progress = progress,
                    onNavigate = navigate,
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) onFocused() },
                )
            }
            Text(
                stringResource(R.string.lounge_major_events_unverified),
                style = stageText(16, 400),
                color = StageColors.Muted
            )
        }
        }
    }
}


@Composable
private fun androidx.compose.foundation.layout.BoxScope.EventLogoFallback(event: Match) {
    Box(
        Modifier.align(Alignment.CenterEnd).padding(end = 34.mpx)
            .size(650.mpx, 390.mpx).clip(RoundedCornerShape(28.mpx))
            .background(eventArtworkBrush(event)),
    ) {
        event.competitors.firstOrNull()?.team?.logoUrl
            ?.takeIf { it.startsWith("https://") }
            ?.let { logo ->
                AsyncImage(
                    model = logo, contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.CenterEnd).size(360.mpx).alpha(0.12f),
                )
            }
        Text(
            event.leagueName.uppercase(), style = stageText(18, 800),
            color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = 26.mpx, top = 22.mpx, end = 24.mpx),
        )
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 26.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            event.competitors.take(2).forEachIndexed { index, side ->
                Box(
                    Modifier.size(190.mpx).clip(RoundedCornerShape(26.mpx))
                        .background(Color(0x33101B2C)),
                    contentAlignment = Alignment.Center,
                ) {
                    val logo = side.team.logoUrl?.takeIf { it.startsWith("https://") }
                    if (logo != null) {
                        AsyncImage(
                            model = logo, contentDescription = side.team.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(164.mpx),
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.mpx),
                        ) {
                            Text(
                                side.team.shortName.ifBlank { side.team.name }.take(4).uppercase(),
                                style = stageText(38, 800), color = StageColors.Text,
                            )
                            Text(
                                side.team.name, style = stageText(14, 600),
                                color = StageColors.Muted,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (index == 0 && event.competitors.size > 1) {
                    Text(
                        stringResource(R.string.lounge_major_events_vs),
                        style = stageText(22, 900), color = Color.White,
                    )
                }
            }
        }
    }
}

private fun eventArtworkBrush(event: Match): Brush = when (event.sport) {
    Sport.MMA -> Brush.linearGradient(listOf(Color(0xFF301313), Color(0xFF0E182A), Color(0xFF06111D)))
    Sport.RUGBY, Sport.RUGBY_LEAGUE -> Brush.linearGradient(listOf(Color(0xFF123B33), Color(0xFF102843), Color(0xFF07121D)))
    Sport.CRICKET -> Brush.linearGradient(listOf(Color(0xFF15324B), Color(0xFF102947), Color(0xFF07121E)))
    Sport.BASKETBALL -> Brush.linearGradient(listOf(Color(0xFF40210E), Color(0xFF142942), Color(0xFF07121E)))
    Sport.FOOTBALL -> Brush.linearGradient(listOf(Color(0xFF20320F), Color(0xFF152B42), Color(0xFF07121E)))
    Sport.SOCCER -> Brush.linearGradient(listOf(Color(0xFF173C32), Color(0xFF172846), Color(0xFF07121E)))
    Sport.RACING -> Brush.linearGradient(listOf(Color(0xFF421616), Color(0xFF17223B), Color(0xFF07121E)))
    else -> Brush.linearGradient(listOf(Color(0xFF183553), Color(0xFF142B47), Color(0xFF07121E)))
}

private fun eventLocalTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)
        .withZone(ZoneId.of("Pacific/Auckland"))
        .format(Instant.ofEpochMilli(epochMs))
