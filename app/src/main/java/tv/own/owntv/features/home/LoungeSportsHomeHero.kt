package tv.own.owntv.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import tv.own.owntv.ui.lounge.LoungePremiumTokens
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.ui.lounge.LoungeAction
import tv.own.owntv.ui.theme.*

/** Sports-first Home carousel using the existing branded event card and real source identities. */
@Composable
internal fun LoungeSportsHomeHero(matches: List<Match>, start: Dp, firstFocus: FocusRequester?,
    onFocus: () -> Unit, onOpen: (Match) -> Unit, onRemind: (Match) -> Unit, active: Boolean = true) {
    if (matches.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val match = matches[index.mod(matches.size)]
    LaunchedEffect(active, focused, matches.map { it.key }) {
        while (active && !focused && matches.size > 1) { delay(12_000); index = (index + 1).mod(matches.size) }
    }
    Column(Modifier.fillMaxWidth().padding(start = start, end = 48.mpx)
        .onFocusChanged { focused = it.hasFocus; if (it.hasFocus) onFocus() }, verticalArrangement = Arrangement.spacedBy(16.mpx)) {
        Text(stringResource(R.string.m6_sports_hero), style = stageText(25, 750),
            color = LoungePremiumTokens.Body)
        LoungeCinematicSportsHeroCard(
            match = match,
            modifier = Modifier.fillMaxWidth().height(350.mpx)
                .then(if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
            onOpen = { onOpen(match) },
            onRemind = { onRemind(match) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
            LoungeAction(stringResource(R.string.m4_previous)) { index = (index - 1).mod(matches.size) }
            LoungeAction(stringResource(R.string.m6_watch_event)) { onOpen(match) }
            if (!match.isLive) LoungeAction(stringResource(R.string.m6_remind)) { onRemind(match) }
            LoungeAction(stringResource(R.string.m4_next)) { index = (index + 1).mod(matches.size) }
        }
    }
}
