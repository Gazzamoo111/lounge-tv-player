package tv.own.owntv.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
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
 * No stream preview, background autoplay, event-artwork scanning or tune on focus.
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
        SportsHeroBackdrop(event)

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



/**
 * Every Home Major Event has a locally packaged high-resolution sports background.
 * Separate assets avoid atlas blurring and remove any dependency on
 * image APIs, remote URL availability, or event-specific image matching.
 * Live teams, venue, NZ time, reminder and Watch actions remain unchanged.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SportsHeroBackdrop(event: Match) {
    val slot = sportArtworkSlot(event)
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val imageRes = when (slot) {
        0 -> R.drawable.lounge_hero_nfl
        1 -> R.drawable.lounge_hero_nba
        2 -> R.drawable.lounge_hero_rugby
        3 -> R.drawable.lounge_hero_soccer
        4 -> R.drawable.lounge_hero_combat
        5 -> R.drawable.lounge_hero_f1
        6 -> R.drawable.lounge_hero_league
        7 -> R.drawable.lounge_hero_cricket
        else -> R.drawable.lounge_hero_soccer
    }
    val art = remember(resources, imageRes) {
        android.graphics.BitmapFactory.decodeResource(resources, imageRes)?.asImageBitmap()
    }
    Box(Modifier.fillMaxSize().background(eventArtworkBrush(event))) {
        // Asset is rendered for EVERY event, including those with no artwork URL.
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Give the artwork a luminous finish while preserving its source pixels.
        Box(Modifier.fillMaxSize().drawBehind {
            val w = size.width
            val h = size.height
            drawRect(brush = Brush.radialGradient(
                colors = listOf(
                    eventAccent(event).copy(alpha = 0.24f),
                    Color.Transparent
                ),
                center = Offset(w * 0.74f, h * 0.52f),
                radius = w * 0.42f,
            ))
            drawLine(
                Color.White.copy(alpha = 0.12f),
                Offset(w * 0.57f, h * 0.91f),
                Offset(w * 0.98f, h * 0.82f),
                2f
            )
        })
        // Readable left-side copy with clean real crest artwork to the right.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0.0f to Color(0xFF07111E),
            0.28f to Color(0xF2071424),
            0.47f to Color(0xBF0A1727),
            0.66f to Color(0x330B1725),
            1.0f to Color(0x1007101C),
        )))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0.0f to Color(0x18050B15),
            0.70f to Color.Transparent,
            1.0f to Color(0x66050B15),
        )))
        if (!isFormulaOne(event)) EventMatchupOverlay(event)
        if (isFormulaOne(event)) {
            Text(
                stringResource(R.string.lounge_hero_mark_f1),
                style = stageText(90, 900),
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(end = 52.mpx, top = 38.mpx),
            )
        }
    }
}

/** Stable sport-specific AI art mapping; no hardcoded fixture identities. */
internal fun sportArtworkSlot(event: Match): Int = when (event.sport) {
    Sport.FOOTBALL -> 0
    Sport.BASKETBALL -> 1
    Sport.RUGBY -> 2
    Sport.RUGBY_LEAGUE, Sport.AUSSIE_RULES -> 6
    Sport.MMA -> 4
    Sport.RACING -> 5
    Sport.SOCCER -> 3
    Sport.CRICKET -> 7
    else -> 3
}

private fun isFormulaOne(event: Match): Boolean {
    if (event.sport != Sport.RACING) return false
    val league = event.leagueName.lowercase(Locale.ROOT)
    val title = event.title.lowercase(Locale.ROOT)
    return "formula 1" in league || "formula one" in league ||
        Regex("(^|[^a-z0-9])f1([^a-z0-9]|$)").containsMatchIn(league) ||
        "formula 1" in title
}

/**
 * Actual home/away crests from the fixture feed, or prominent team wordmarks.
 * Never invents team identities or stretches their feed crests.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.EventMatchupOverlay(event: Match) {
    val sides = event.competitors.take(2)
    if (sides.isEmpty()) {
        Text(
            event.leagueName.uppercase(Locale.ROOT),
            style = stageText(42, 900), color = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 64.mpx)
                .width(470.mpx),
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Row(
        Modifier.align(Alignment.CenterEnd).padding(end = 44.mpx, top = 20.mpx),
        horizontalArrangement = Arrangement.spacedBy(12.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sides.forEachIndexed { index, side ->
            val logoUrl = side.team.logoUrl?.takeIf { it.startsWith("https://") }
            var logoReady by remember(event.key, logoUrl) { mutableStateOf(false) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.mpx),
                modifier = Modifier.width(286.mpx),
            ) {
                Box(Modifier.size(275.mpx), contentAlignment = Alignment.Center) {
                    if (!logoReady) {
                        Text(
                            side.team.shortName.ifBlank { side.team.name }.uppercase(Locale.ROOT),
                            style = stageText(34, 900), color = Color.White,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(265.mpx),
                        )
                    }
                    if (logoUrl != null) {
                        AsyncImage(
                            model = logoUrl, contentDescription = side.team.name,
                            contentScale = ContentScale.Fit,
                            onSuccess = { logoReady = true },
                            onError = { logoReady = false },
                            modifier = Modifier.size(270.mpx).alpha(if (logoReady) 1f else 0f),
                        )
                    }
                }
                Text(
                    side.team.shortName.ifBlank { side.team.name },
                    style = stageText(20, 800), color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(275.mpx),
                )
            }
            if (index == 0 && sides.size > 1) {
                Text(
                    stringResource(R.string.lounge_major_events_vs),
                    style = stageText(30, 900),
                    color = Color.White.copy(alpha = 0.86f),
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

private fun eventAccent(event: Match): Color = when (event.sport) {
    Sport.MMA -> Color(0xFFFF5B63)
    Sport.RUGBY -> Color(0xFF39C6A0)
    Sport.RUGBY_LEAGUE -> Color(0xFF4DA8FF)
    Sport.CRICKET -> Color(0xFF43C89C)
    Sport.BASKETBALL -> Color(0xFFFF9639)
    Sport.FOOTBALL -> Color(0xFFF4B44D)
    Sport.SOCCER -> Color(0xFF63DCC6)
    Sport.RACING -> Color(0xFFFF555B)
    else -> Color(0xFF4DBBFF)
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
