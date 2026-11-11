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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Core 1.0.71 month calendar rendered in Lounge's existing Sports visual language. */
@Composable
internal fun PremiumSportsCalendarScreen(
    vm: SportsViewModel,
    onOpenMatch: (Match) -> Unit,
    onBack: () -> Unit,
    active: Boolean,
    contentStart: Dp,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = active) { onBack() }
    val config by vm.sportsConfig.collectAsStateWithLifecycle()
    val monthState by vm.calendarMonth.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(YearMonth.now(PremiumSportsPresentation.zone)) }
    val today = LocalDate.now(PremiumSportsPresentation.zone)
    val leagueKeys = remember(config) { config.followedLeagues().toSet() }

    LaunchedEffect(month, leagueKeys) {
        vm.showMonth(leagueKeys, month, if (month == YearMonth.from(today)) today else month.atDay(1))
    }

    Column(
        modifier.fillMaxSize().padding(start = contentStart, end = 52.mpx, top = 34.mpx, bottom = 24.mpx),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
        ) {
            Text(
                stringResource(R.string.m4_calendar),
                style = stageText(43, 800),
                color = StageColors.Text,
                modifier = Modifier.weight(1f),
            )
            StageButton(stringResource(R.string.m4_previous_month), onClick = { month = month.minusMonths(1) })
            StageButton(stringResource(R.string.m4_next_month), onClick = { month = month.plusMonths(1) })
        }

        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
            style = stageText(26, 700),
            color = stageAccent.accent,
            modifier = Modifier.padding(top = 12.mpx, bottom = 16.mpx),
        )

        val days = monthState?.days.orEmpty().entries
            .filter { it.value.isNotEmpty() }
            .sortedBy { it.key }

        if (days.isEmpty()) {
            Text(
                stringResource(if (leagueKeys.isEmpty()) R.string.m4_calendar_follow_first else R.string.m4_calendar_empty),
                style = stageText(20, 500),
                color = StageColors.Muted,
                modifier = Modifier.padding(top = 22.mpx),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).focusGroup(),
                contentPadding = PaddingValues(bottom = 48.mpx),
                verticalArrangement = Arrangement.spacedBy(8.mpx),
            ) {
                days.forEach { (date, matches) ->
                    item("day-" + date.toString()) {
                        Text(
                            PremiumSportsPresentation.nzDayHeading(date),
                            style = stageText(24, 800),
                            color = StageColors.Text,
                            modifier = Modifier.padding(top = 14.mpx, bottom = 4.mpx),
                        )
                    }
                    items(matches, key = { it.key }) { match ->
                        CalendarFixtureRow(match) { onOpenMatch(match) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarFixtureRow(match: Match, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    StageRow(
        onClick = onClick,
        height = 78.mpx,
        modifier = Modifier.fillMaxWidth(),
        horizontalPadding = 18.mpx,
    ) { _ ->
        Column(Modifier.weight(1f)) {
            Text(
                MatchNotifications.pair(context, match),
                style = stageText(20, 700),
                color = StageColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                match.leagueName,
                style = stageText(16, 500),
                color = StageColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (match.isLive) match.statusText else PremiumSportsPresentation.nzKickoffSummary(match.startMs),
            style = stageText(17, 700),
            color = if (match.isLive) stageAccent.accent else StageColors.Body,
            maxLines = 1,
        )
    }
}
