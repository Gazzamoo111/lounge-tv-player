package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.features.sports.Crest
import tv.own.owntv.features.sports.sportEmoji
import tv.own.owntv.ui.stage.StageStill
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * Home's last shelf: up to ten genuinely live fixtures, mixed by sport.
 * Uses the Keep Watching 336 x 189 still size; OK resolves the fixture's
 * current candidate stream and opens it directly, never the Sport Lounge page.
 */
@Composable
internal fun HomeLiveSportsRow(
    matches: List<Match>,
    start: Dp,
    onPlay: (Match) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (matches.isEmpty()) return
    Column(modifier) {
        HomeRowHeader(stringResource(R.string.lounge_home_live_sports), small = matches.size.toString(), start = start)
        LazyRow(
            modifier = Modifier.focusRestorer().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx, top = 6.mpx, bottom = 26.mpx),
        ) {
            itemsIndexed(matches, key = { _, match -> match.key }) { index, match ->
                StageStill(
                    title = match.title,
                    line = match.leagueName,
                    width = 336.mpx,
                    height = 189.mpx,
                    titleSize = 19,
                    lineSize = 15,
                    onClick = { onPlay(match) },
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                ) {
                    Box(Modifier.fillMaxSize().background(StageColors.ControlFill, RoundedCornerShape(14.mpx))) {
                        Row(
                            Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(22.mpx),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (match.home?.team == null && match.away?.team == null) {
                                Text(sportEmoji(match.sport), style = stageText(52, 700), color = StageColors.Text)
                            } else {
                                Crest(match.home?.team, 84)
                                Text(stringResource(R.string.lounge_home_vs), style = stageText(17, 800), color = StageColors.Muted)
                                Crest(match.away?.team, 84)
                            }
                        }
                        Text(
                            stringResource(R.string.player_live),
                            modifier = Modifier.align(Alignment.TopStart).padding(14.mpx),
                            style = stageText(16, 800),
                            color = StageColors.Text,
                        )
                    }
                }
            }
        }
    }
}
