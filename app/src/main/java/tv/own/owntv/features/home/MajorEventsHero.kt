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
        // Full-bleed artwork, with a sport-specific photographic fallback for every
        // fixture. These are illustrative sport photos, never another event's promo.
        // Exact match posters take priority when supplied and image loading is async.
        val context = LocalContext.current
        val art = editorialCopy?.artworkUrl
        var exactArtReady by remember(event.key, art) { mutableStateOf(false) }
        val backingPhoto = remember(event.key) { MajorEventEditorial.sportBackdrop(event) }
        val backdropRequest = remember(context, backingPhoto) {
            ImageRequest.Builder(context).data(backingPhoto).size(1600, 900)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED).build()
        }
        val exactRequest = remember(context, art) {
            art?.let {
                ImageRequest.Builder(context).data(it).size(1600, 900)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED).build()
            }
        }
        Box(Modifier.fillMaxSize().background(eventArtworkBrush(event))) {
            AsyncImage(
                model = backdropRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize(),
            )
            if (exactRequest != null) {
                AsyncImage(
                    model = exactRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterEnd,
                    onSuccess = { exactArtReady = true },
                    onError = { exactArtReady = false },
                    modifier = Modifier.fillMaxSize().alpha(if (exactArtReady) 1f else 0f),
                )
            }
            // The artwork covers the *entire* 520px hero. Fade only beneath
            // text/actions, leaving the right half cinematic and unobstructed.
            Box(Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to Color(0xFA081321),
                    0.33f to Color(0xEC0A1829),
                    0.59f to Color(0x99101B2A),
                    0.82f to Color(0x33101B2A),
                    1.0f to Color(0x22060B13),
                )
            ))
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x3307111D),
                    0.72f to Color.Transparent,
                    1.0f to Color(0x8807111D),
                )
            ))
            // Generic sports photos are identified by the *actual* fixture
            // competitors, not unrelated athletes or invented matchup art.
            if (!exactArtReady) EventMatchupOverlay(event)
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
            eventSynopsis(event, editorialCopy)?.let { synopsis ->
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
private fun androidx.compose.foundation.layout.BoxScope.EventMatchupOverlay(event: Match) {
    if (event.competitors.isEmpty()) return
    Row(
        Modifier.align(Alignment.BottomEnd).padding(end = 50.mpx, bottom = 46.mpx),
        horizontalArrangement = Arrangement.spacedBy(16.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        event.competitors.take(2).forEachIndexed { index, side ->
            val logo = side.team.logoUrl?.takeIf { it.startsWith("https://") }
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = side.team.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(132.mpx),
                )
            } else {
                Text(
                    side.team.shortName.ifBlank { side.team.name }.take(10).uppercase(),
                    style = stageText(28, 900),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(158.mpx),
                )
            }
            if (index == 0 && event.competitors.size > 1) {
                Text(
                    stringResource(R.string.lounge_major_events_vs),
                    style = stageText(24, 900),
                    color = Color.White,
                )
            }
        }
    }
}

/** Render text from device translations when the cached backend supplies no editorial copy. */
@Composable
private fun eventSynopsis(event: Match, copy: MajorEventHeroCopy?): String? {
    copy?.synopsis?.takeIf(String::isNotBlank)?.let { return it }
    val names = event.competitors.take(2).map { it.team.name.trim() }
        .filter { it.isNotBlank() && !it.equals("TBD", ignoreCase = true) &&
            !it.equals("TBA", ignoreCase = true) && !it.equals("Unknown", ignoreCase = true) }
    if (names.size != 2) return null
    val league = copy?.competition ?: event.leagueName
    return when (event.sport) {
        Sport.MMA -> stringResource(R.string.lounge_event_synopsis_combat, names[0], names[1], league)
        Sport.RUGBY -> stringResource(R.string.lounge_event_synopsis_rugby, names[0], names[1], league)
        Sport.RUGBY_LEAGUE -> stringResource(R.string.lounge_event_synopsis_league, names[0], names[1], league)
        Sport.CRICKET -> stringResource(R.string.lounge_event_synopsis_cricket, names[0], names[1], league)
        Sport.BASKETBALL -> stringResource(R.string.lounge_event_synopsis_basketball, names[0], names[1], league)
        Sport.FOOTBALL, Sport.SOCCER -> stringResource(R.string.lounge_event_synopsis_team, names[0], names[1], league)
        else -> null
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
