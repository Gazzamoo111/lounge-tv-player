package tv.own.owntv.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.time.ZoneId

/** Event-led replacement for the old followed-league rail hub. */
@Composable
fun SportLoungeScreen(
    vm: SportsViewModel,
    onOpenMatch: (Match) -> Unit,
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onChildFocused: () -> Unit,
    onEntryHook: (((() -> Boolean)?) -> Unit)?,
    contentStart: Dp,
    modifier: Modifier = Modifier,
    restoreKey: String? = null,
) {
    val state by vm.hub.collectAsStateWithLifecycle()
    var timeFilter by remember { mutableStateOf(SportLoungeTimeFilter.LIVE_NOW) }
    var category by remember { mutableStateOf(SportLoungeCategory.ALL) }
    var myTeamsOnly by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val allMatches = remember(state.board.matches, state.discovery.matches) {
        SportLoungeRules.dedupe(state.board.matches + state.discovery.matches)
    }
    val followedKeys = state.config.teams.mapTo(hashSetOf()) { it.key }
    val selection = remember(allMatches, timeFilter, category, followedKeys, myTeamsOnly, nowMs) {
        SportLoungeRules.select(
            source = allMatches,
            timeFilter = timeFilter,
            category = category,
            nowMs = nowMs,
            zoneId = ZoneId.systemDefault(),
            followedTeamKeys = followedKeys,
            myTeamsOnly = myTeamsOnly,
        )
    }
    val cardFocus = remember { HashMap<String, FocusRequester>() }
    fun focusOf(key: String) = cardFocus.getOrPut(key) { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    val firstCard = selection.matches.firstOrNull()
    val entry by rememberUpdatedState(firstCard?.let { focusOf(it.key) } ?: tabFocus)
    DisposableEffect(onEntryHook) {
        onEntryHook?.invoke { runCatching { entry.requestFocus() }.getOrDefault(false) }
        onDispose { onEntryHook?.invoke(null) }
    }
    LaunchedEffect(restoreKey, selection.matches.map { it.key }) {
        val key = restoreKey?.takeIf { candidate -> selection.matches.any { it.key == candidate } } ?: return@LaunchedEffect
        repeat(10) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { focusOf(key).requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }

    var menuFor by remember { mutableStateOf<Match?>(null) }
    var menuChannels by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    LaunchedEffect(menuFor?.key) { menuChannels = menuFor?.let { vm.channelsFound(it) }.orEmpty() }

    BoxWithConstraints(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        val usable = maxWidth - contentStart - 60.mpx
        val columns = when {
            usable >= 1_260.mpx -> 3
            usable >= 760.mpx -> 2
            else -> 1
        }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = contentStart, end = 60.mpx, top = 39.mpx)) {
                Text(stringResource(R.string.sport_lounge_title), style = stageText(48, 800), color = StageColors.Text, maxLines = 1)
                Text(
                    stringResource(R.string.sport_lounge_subtitle),
                    style = stageText(20, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.mpx),
                )
            }
            LazyRow(
                Modifier.padding(top = 21.mpx).focusRestorer().focusGroup(),
                contentPadding = PaddingValues(start = contentStart, end = 60.mpx),
                horizontalArrangement = Arrangement.spacedBy(12.mpx),
            ) {
                items(SportLoungeTimeFilter.entries, key = { it.name }) { filter ->
                    val count = when (filter) {
                        SportLoungeTimeFilter.LIVE_NOW -> selection.counts.liveNow
                        SportLoungeTimeFilter.TODAY -> selection.counts.today
                        SportLoungeTimeFilter.UPCOMING -> selection.counts.upcoming
                    }
                    SportsChip(
                        text = stringResource(R.string.sport_lounge_filter_count, timeFilterLabel(filter), count),
                        on = timeFilter == filter,
                        modifier = if (filter == SportLoungeTimeFilter.LIVE_NOW) Modifier.focusRequester(tabFocus) else Modifier,
                    ) { timeFilter = filter }
                }
            }
            LazyRow(
                Modifier.padding(top = 12.mpx).focusRestorer().focusGroup(),
                contentPadding = PaddingValues(start = contentStart, end = 60.mpx),
                horizontalArrangement = Arrangement.spacedBy(12.mpx),
            ) {
                item {
                    SportsChip(stringResource(R.string.sports_my_teams), myTeamsOnly, icon = OwnTVIcon.STAR) {
                        myTeamsOnly = !myTeamsOnly
                    }
                }
                items(selection.categories, key = { it.name }) { item ->
                    SportsChip(categoryLabel(item), category == item) { category = item }
                }
                item { SportsChip("", false, icon = OwnTVIcon.REFRESH) { vm.rebuild() } }
            }

            if (selection.matches.isEmpty()) {
                SportLoungeEmptyState(
                    filter = timeFilter,
                    loading = state.discoveryLoading || (state.board.builtFor == null && state.board.rebuiltAt == null && state.board.failing.isEmpty()),
                    failing = state.board.failing.isNotEmpty() || state.discovery.failedFeeds > 0,
                    rebuiltAt = listOfNotNull(state.board.rebuiltAt, state.discovery.updatedAt).maxOrNull(),
                    modifier = Modifier.padding(start = contentStart, end = 60.mpx, top = 72.mpx),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.weight(1f).padding(top = 24.mpx).focusGroup(),
                    contentPadding = PaddingValues(start = contentStart, end = 60.mpx, bottom = 108.mpx),
                    horizontalArrangement = Arrangement.spacedBy(18.mpx),
                    verticalArrangement = Arrangement.spacedBy(18.mpx),
                ) {
                    items(selection.matches, key = { it.key }) { match ->
                        val context = LocalContext.current
                        SportLoungeEventCard(
                            match = match,
                            config = state.config,
                            nowMs = nowMs,
                            hasPlaylist = state.hasPlaylist,
                            reminded = SportsViewModel.reminderKey(match.startMs, MatchNotifications.pair(context, match)) in state.reminders,
                            onClick = { onOpenMatch(match) },
                            onHold = { menuFor = match },
                            modifier = Modifier.focusRequester(focusOf(match.key)),
                        )
                    }
                }
            }
        }
        StageKeyHints(
            listOf(
                stringResource(R.string.common_ok) to stringResource(R.string.sports_hint_match_page),
                stringResource(R.string.content_key_hold_ok) to stringResource(R.string.sports_hint_follow_remind),
            ),
            Modifier.align(Alignment.BottomEnd).padding(end = 60.mpx, bottom = 27.mpx),
            textSize = 18,
        )
    }

    menuFor?.let { match ->
        FollowRemindMenu(
            match = match,
            config = state.config,
            channels = menuChannels,
            now = nowMs,
            onFollow = vm::toggleTeam,
            onRemind = { vm.remind(match, menuChannels) },
            onDismiss = { menuFor = null },
        )
    }
}

@Composable
private fun SportLoungeEventCard(
    match: Match,
    config: tv.own.owntv.core.sports.SportsConfig,
    nowMs: Long,
    hasPlaylist: Boolean,
    reminded: Boolean,
    onClick: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val radius = 20.mpx
    val locale = LocalConfiguration.current.locales[0]
    val accent = stageAccent
    StageSurface(
        onClick = onClick,
        onLongClick = onHold,
        radius = radius,
        focusStyle = StageFocus.FX,
        contentAlignment = Alignment.TopStart,
        modifier = modifier.fillMaxWidth().height(226.mpx).onPreviewKeyEvent { event ->
            val play = event.key == Key.MediaPlay || event.key == Key.MediaPlayPause
            if (play && event.type == KeyEventType.KeyUp) onClick()
            play
        },
        idle = Modifier.background(StageColors.Ink.copy(alpha = 0.05f), RoundedCornerShape(radius)).drawBehind {
            drawInnerRing(Color.White.copy(alpha = 0.06f), 1.mpx.toPx(), radius.toPx())
        },
    ) { focused ->
        Column(Modifier.fillMaxSize().padding(horizontal = 20.mpx, vertical = 17.mpx)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sport_lounge_event_meta, sportLabel(match.sport), leagueName(match)),
                    style = stageText(16.5f, 700), color = if (focused) accent.accent else StageColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                when {
                    match.isLive -> LiveMark(clockText(match), 16.5f)
                    match.off || match.state == MatchState.POST -> Text(match.statusText, style = stageText(16.5f, 700), color = StageColors.Muted, maxLines = 1)
                    else -> Text(
                        listOfNotNull(dayOf(match.startMs, locale), timeText(match.startMs)).joinToString(" "),
                        style = stageText(17, 800), color = StageColors.Text, maxLines = 1,
                    )
                }
            }
            if (match.competitors.size >= 2) {
                listOfNotNull(match.home, match.away).forEach { side ->
                    Row(
                        Modifier.padding(top = 13.mpx),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.mpx),
                    ) {
                        Crest(side.team, 40)
                        Text(teamLabel(side.team), style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (match.state != MatchState.PRE && !match.off) {
                            Text(if (config.spoilerFree) stringResource(R.string.sport_lounge_score_hidden) else scoreText(match.sport, side.score), style = stageText(24, 800), color = StageColors.Text, maxLines = 1)
                        }
                    }
                }
            } else {
                Text(match.title, style = stageText(22, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 20.mpx))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.mpx).background(Color.White.copy(alpha = 0.07f)))
            Row(Modifier.padding(top = 10.mpx), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (hasPlaylist) R.string.sport_lounge_find_channels else R.string.sport_lounge_no_playlist),
                    style = stageText(15.5f, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (reminded) StageTag(stringResource(R.string.sports_reminder_set))
            }
        }
    }
}

@Composable
private fun SportLoungeEmptyState(
    filter: SportLoungeTimeFilter,
    loading: Boolean,
    failing: Boolean,
    rebuiltAt: Long?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.mpx)) {
        val title = when {
            loading -> R.string.sport_lounge_loading
            filter == SportLoungeTimeFilter.LIVE_NOW -> R.string.sport_lounge_empty_live
            filter == SportLoungeTimeFilter.TODAY -> R.string.sport_lounge_empty_today
            else -> R.string.sport_lounge_empty_upcoming
        }
        Text(stringResource(title), style = stageText(27, 800), color = StageColors.Text, maxLines = 2)
        Text(
            stringResource(if (failing) R.string.sport_lounge_source_problem else R.string.sport_lounge_coverage_note),
            style = stageText(19, 400), color = if (failing) StageColors.Warn else StageColors.Muted, maxLines = 3,
        )
        rebuiltAt?.let {
            Text(stringResource(R.string.sport_lounge_last_updated, timeText(it)), style = stageText(17, 400), color = StageColors.Dim)
        }
    }
}

@Composable
private fun timeFilterLabel(filter: SportLoungeTimeFilter): String = stringResource(
    when (filter) {
        SportLoungeTimeFilter.LIVE_NOW -> R.string.sports_live_now
        SportLoungeTimeFilter.TODAY -> R.string.content_epg_today
        SportLoungeTimeFilter.UPCOMING -> R.string.sport_lounge_upcoming
    },
)

@Composable
private fun categoryLabel(category: SportLoungeCategory): String = when (category) {
    SportLoungeCategory.ALL -> stringResource(R.string.sport_lounge_all_sports)
    SportLoungeCategory.NFL -> stringResource(R.string.sport_lounge_nfl)
    SportLoungeCategory.LEAGUE -> stringResource(R.string.sport_lounge_league)
    SportLoungeCategory.RUGBY -> stringResource(R.string.sports_sport_rugby)
    SportLoungeCategory.CRICKET -> stringResource(R.string.sports_sport_cricket)
    SportLoungeCategory.BASKETBALL -> stringResource(R.string.sports_sport_basketball)
    SportLoungeCategory.FOOTBALL -> stringResource(R.string.sports_sport_soccer)
    SportLoungeCategory.COMBAT -> stringResource(R.string.sports_sport_combat)
    SportLoungeCategory.MOTORSPORT -> stringResource(R.string.sports_sport_motorsport)
    SportLoungeCategory.TENNIS -> stringResource(R.string.sports_sport_tennis)
    SportLoungeCategory.HOCKEY -> stringResource(R.string.sports_sport_hockey)
    SportLoungeCategory.BASEBALL -> stringResource(R.string.sports_sport_baseball)
    SportLoungeCategory.GOLF -> stringResource(R.string.sports_sport_golf)
    SportLoungeCategory.AFL -> stringResource(R.string.sports_sport_aussie_rules)
    SportLoungeCategory.VOLLEYBALL -> stringResource(R.string.sports_sport_volleyball)
    SportLoungeCategory.LACROSSE -> stringResource(R.string.sports_sport_lacrosse)
}
