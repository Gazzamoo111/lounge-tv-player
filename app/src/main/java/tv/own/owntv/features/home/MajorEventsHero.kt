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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
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
) {
    if (events.isEmpty()) return
    var selected by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val currentIndex = selected.coerceIn(0, events.lastIndex)
    val event = events[currentIndex]

    // Never rotate an event out from under a customer who is interacting with the buttons.
    LaunchedEffect(events.map { it.key }, currentIndex, focused) {
        if (!focused && events.size > 1) {
            delay(12_000L)
            selected = (currentIndex + 1) % events.size
        }
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
            .onFocusChanged { focused = it.hasFocus }
            .focusGroup()
    ) {
        // Team crests come from the provider's actual fixture, not fuzzy name matching.
        // No artwork or metadata request is needed to render the event or enable navigation.
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 56.mpx),
            horizontalArrangement = Arrangement.spacedBy(26.mpx),
            verticalAlignment = Alignment.CenterVertically
        ) {
            event.competitors.take(2).forEach { side ->
                Box(
                    Modifier.size(145.mpx)
                        .clip(RoundedCornerShape(22.mpx))
                        .background(Color(0x223DCDF5)),
                    contentAlignment = Alignment.Center
                ) {
                    val logo = side.team.logoUrl?.takeIf { it.startsWith("https://") }
                    if (logo != null) {
                        AsyncImage(
                            model = logo,
                            contentDescription = side.team.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(128.mpx)
                        )
                    } else {
                        Text(
                            side.team.shortName.take(4).uppercase(),
                            style = stageText(34, 800),
                            color = StageColors.Text
                        )
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxSize().padding(start = start, top = 34.mpx, end = 24.mpx),
            verticalArrangement = Arrangement.spacedBy(18.mpx)
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
                StageTag(event.leagueName)
            }
            Text(
                event.title,
                style = stageText(54, 800),
                color = StageColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(800.mpx)
            )
            Text(
                eventLocalTime(event.startMs) + " · " + stringResource(R.string.lounge_major_events_nz_time),
                style = stageText(21, 600),
                color = StageColors.Body
            )
            Spacer(Modifier.height(4.mpx))
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
                        icon = OwnTVIcon.REMINDER,
                        onClick = { onToggleReminder(event) },
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) onFocused() }
                    )
                }
                if (events.size > 1) {
                    StageButton(
                        text = stringResource(R.string.lounge_major_events_next) + " ${currentIndex + 1}/${events.size}",
                        onClick = { selected = (currentIndex + 1) % events.size },
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) onFocused() }
                    )
                }
            }
            Text(
                stringResource(R.string.lounge_major_events_unverified),
                style = stageText(16, 400),
                color = StageColors.Muted
            )
        }
    }
}

private fun eventLocalTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)
        .withZone(ZoneId.of("Pacific/Auckland"))
        .format(Instant.ofEpochMilli(epochMs))
