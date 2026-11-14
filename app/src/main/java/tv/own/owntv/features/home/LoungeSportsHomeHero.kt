package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.ui.theme.mpx

/** One full-screen sports feature, cycling real fixtures with adaptive event/team art direction. */
@Composable
internal fun LoungeSportsHomeHero(
    matches: List<Match>,
    start: Dp,
    firstFocus: FocusRequester?,
    onFocus: () -> Unit,
    onOpen: (Match) -> Unit,
    onRemind: (Match) -> Unit,
    active: Boolean = true,
    autoRotate: Boolean = true,
) {
    if (matches.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val match = matches[index.mod(matches.size)]
    LaunchedEffect(active, focused, autoRotate, matches.map { it.key }) {
        while (active && autoRotate && !focused && matches.size > 1) {
            delay(8_000L)
            index = (index + 1).mod(matches.size)
        }
    }
    Box(Modifier.fillMaxWidth().height(835.mpx)
        .onFocusChanged { focused = it.hasFocus; if (it.hasFocus) onFocus() }
        .onPreviewKeyEvent { e ->
            if (!focused || e.type != KeyEventType.KeyDown || matches.size <= 1) false
            else when (e.key) {
                Key.DirectionRight -> { index = (index + 1).mod(matches.size); true }
                Key.DirectionLeft -> { index = (index - 1).mod(matches.size); true }
                else -> false
            }
        }) {
        LoungeCinematicSportsHeroCard(
            match = match,
            modifier = Modifier.fillMaxSize()
                .then(if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
            onOpen = { onOpen(match) },
            onRemind = { onRemind(match) },
        )
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.mpx),
            horizontalArrangement = Arrangement.spacedBy(9.mpx),
            verticalAlignment = Alignment.CenterVertically) {
            repeat(matches.size.coerceAtMost(10)) { dot ->
                Box(Modifier.size(if (dot == index.mod(matches.size)) 13.mpx else 8.mpx)
                    .background(
                        if (dot == index.mod(matches.size)) Color.White else Color.White.copy(alpha = 0.35f),
                        CircleShape
                    ))
            }
        }
    }
}
