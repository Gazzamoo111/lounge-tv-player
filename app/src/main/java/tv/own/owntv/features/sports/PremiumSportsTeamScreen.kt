package tv.own.owntv.features.sports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageTab
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * Lounge team page backed by OwnTV Core 1.0.71. It never chooses a stream.
 * Selecting a fixture opens Lounge's match page, where channel discovery remains evidence-gated.
 */
@Composable
internal fun PremiumSportsTeamScreen(
    vm: SportsViewModel,
    ref: TeamRef,
    onOpenMatch: (Match) -> Unit,
    onBack: () -> Unit,
    active: Boolean,
    contentStart: Dp,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = active) { onBack() }
    val state = remember(ref) { vm.teamPage(ref) }.collectAsStateWithLifecycle(null).value
    val config by vm.sportsConfig.collectAsStateWithLifecycle()
    val team = ref.team
    val followedTeam = FollowedTeam(ref.league.sport, team.id, team.name, team.abbreviation)
    val following = config.teams.any { it.key == followedTeam.key }
    var results by remember(ref) { mutableStateOf(false) }
    val tabFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { tabFocus.requestFocus() }
    }

    Column(
        modifier.fillMaxSize().padding(start = contentStart, end = 60.mpx, top = 38.mpx),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.mpx),
        ) {
            Crest(team, 88)
            Column(Modifier.weight(1f)) {
                Text(
                    team.name,
                    style = stageText(46, 800),
                    color = StageColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ref.league.name,
                    style = stageText(20, 500),
                    color = stageAccent.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.mpx),
                )
            }
            SportsChip(
                stringResource(if (following) R.string.sports_following else R.string.sports_follow),
                on = following,
                icon = OwnTVIcon.STAR,
            ) { vm.toggleTeam(followedTeam) }
        }

        Row(
            Modifier.padding(top = 22.mpx).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            StageTab(
                label = stringResource(R.string.sports_tab_fixtures),
                count = "",
                selected = !results,
                onClick = { results = false },
                modifier = if (!results) Modifier.focusRequester(tabFocus) else Modifier,
            )
            StageTab(
                label = stringResource(R.string.sports_tab_results),
                count = "",
                selected = results,
                onClick = { results = true },
                modifier = if (results) Modifier.focusRequester(tabFocus) else Modifier,
            )
        }

        val schedule = state?.schedule
        val matches = if (results) schedule?.results.orEmpty() else schedule?.fixtures.orEmpty()
        when {
            schedule == null -> Text(
                "Loading schedule…",
                style = stageText(19, 500),
                color = StageColors.Muted,
                modifier = Modifier.padding(top = 28.mpx, start = 16.mpx),
            )
            matches.isEmpty() -> Text(
                stringResource(R.string.sports_empty_unreachable),
                style = stageText(19, 500),
                color = StageColors.Muted,
                modifier = Modifier.padding(top = 28.mpx, start = 16.mpx),
            )
            else -> LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(top = 16.mpx).focusGroup(),
                contentPadding = PaddingValues(bottom = 72.mpx),
                verticalArrangement = Arrangement.spacedBy(5.mpx),
            ) {
                items(matches, key = { it.key }) { match ->
                    TeamFixtureRow(match, config.spoilerFree) { onOpenMatch(match) }
                }
            }
        }

        StageKeyHints(
            listOf(stringResource(R.string.common_back) to stringResource(R.string.common_back)),
            Modifier.align(Alignment.End).padding(bottom = 22.mpx),
            textSize = 18,
        )
    }
}

@Composable
private fun TeamFixtureRow(match: Match, spoilerFree: Boolean, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    StageRow(
        onClick = onClick,
        height = 82.mpx,
        modifier = Modifier.fillMaxWidth(),
        horizontalPadding = 18.mpx,
    ) { _ ->
        Column(Modifier.weight(1f)) {
            val title = if (match.state == MatchState.POST && !spoilerFree) {
                MatchNotifications.scoreLine(context, match)
            } else {
                MatchNotifications.pair(context, match)
            }
            Text(
                title,
                style = stageText(20, 700),
                color = StageColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    match.isLive -> match.statusText.ifBlank { "LIVE" }
                    else -> PremiumSportsPresentation.nzKickoffSummary(match.startMs)
                },
                style = stageText(17, 500),
                color = if (match.isLive) stageAccent.accent else StageColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            refLeagueLabel(match),
            style = stageText(16, 500),
            color = StageColors.Dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun refLeagueLabel(match: Match): String =
    tv.own.owntv.core.sports.SportsCatalog.league(match.feed)?.name
        ?: match.leagueName.takeIf { it.isNotBlank() }
        ?: match.leagueSlug
