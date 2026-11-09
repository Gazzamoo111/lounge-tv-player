package tv.own.owntv.features.sports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import tv.own.owntv.player.describe
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.player.ExoPreviewSurface
import tv.own.owntv.ui.lounge.*
import tv.own.owntv.ui.stage.*
import tv.own.owntv.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun PremiumSportsScreen(vm: SportsViewModel, liveVm: LiveViewModel,
    onOpenMatch: (Match) -> Unit, onOpenLeague: (String) -> Unit,
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onChildFocused: () -> Unit, onEntryHook: (((() -> Boolean)?) -> Unit)?,
    contentStart: Dp, active: Boolean, restoreKey: String?, modifier: Modifier = Modifier) {
    val state by vm.hub.collectAsStateWithLifecycle()
    val settings: SettingsRepository = koinInject()
    val pid by settings.activeProfileId.collectAsStateWithLifecycle(-1L)
    val playback: SportsPlayback = koinInject()
    val session by playback.session.collectAsStateWithLifecycle()
    val playing by liveVm.previewChannel.collectAsStateWithLifecycle()
    val previewMeta by liveVm.previewEngine.currentMeta.collectAsStateWithLifecycle()
    val previewPlaying by liveVm.previewEngine.isPlaying.collectAsStateWithLifecycle()
    val previewLoading by liveVm.previewEngine.buffering.collectAsStateWithLifecycle()
    val previewBlocked by liveVm.previewBlockedSingleSession.collectAsStateWithLifecycle()
    val previewError by liveVm.previewEngine.error.collectAsStateWithLifecycle()
    val corrections by vm.candidateRevision.collectAsStateWithLifecycle()
    var selected by remember(pid) { mutableStateOf<Match?>(null) }
    var options by remember(pid) { mutableStateOf<List<WatchOption>>(emptyList()) }
    var following by remember(pid) { mutableStateOf(false) }
    var menuFor by remember(pid) { mutableStateOf<Match?>(null) }
    var finding by remember(pid) { mutableStateOf(false) }
    var searched by remember(pid) { mutableStateOf(false) }
    var searchFailed by remember(pid) { mutableStateOf(false) }
    var chooseFeed by remember(pid) { mutableStateOf(false) }
    var request by remember(pid) { mutableIntStateOf(0) }
    var handledRequest by remember(pid) { mutableIntStateOf(0) }
    val previewFocus = remember(pid) { FocusRequester() }
    var restorePreview by remember(pid) { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) restorePreview = true
        else if (restorePreview) {
            withFrameNanos { }
            runCatching { previewFocus.requestFocus() }
            restorePreview = false
        }
    }
    val currentActive by rememberUpdatedState(active)
    val previewScope = rememberCoroutineScope()
    val startPreview: (Match, List<WatchOption>, WatchOption) -> Unit = { match, feeds, option ->
        previewScope.launch {
            if (currentActive && settings.activeProfileIdNow() == pid && selected?.key == match.key && selected?.startMs == match.startMs) {
                vm.playedFrom(match, feeds, option.channel)
                liveVm.onChannelFocused(option.channel)
                liveVm.playPreview(option.channel, withAudio = true)
            }
        }
    }
    val pickPreview: (Match) -> Unit = { match ->
        selected = match
        request++ // Only an explicit action can discover feeds or start a stream.
    }
    LaunchedEffect(pid, active, selected?.key, selected?.startMs, state.config, corrections, request) {
        options = emptyList()
        chooseFeed = false
        finding = false
        searched = false
        searchFailed = false
        val explicit = request != handledRequest
        handledRequest = request
        val match = selected ?: return@LaunchedEffect
        if (!active || pid < 0) return@LaunchedEffect
        finding = explicit
        try {
            if (!explicit) delay(200)
            val found = if (explicit) vm.previewChoices(match) else vm.channelsFound(match)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (settings.activeProfileIdNow() != pid) return@LaunchedEffect
            options = found
            searched = explicit
            if (explicit) {
                val single = directSportsPreviewChoice(found)
                if (single != null) startPreview(match, found, single)
                else chooseFeed = found.isNotEmpty()
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { options = emptyList(); searched = explicit; searchFailed = explicit }
        finally { finding = false }
    }
    key(pid) {
        if (following) {
            PremiumFollowScreen(onDone = { following = false }, contentStart = contentStart, modifier = modifier)
        } else PremiumSportsContent(state, contentStart, restoreKey, onEntryHook, onChildFocused,
            onSelect = { selected = it }, onOpenMatch = pickPreview, onOpenLeague = onOpenLeague,
            onFollow = { following = true }, onHold = { menuFor = it }, modifier = modifier,
            detail = { m, compact ->
                val valid = selected?.key == m?.key && selected?.startMs == m?.startMs
                SportsPreviewPanel(m, if (valid) options else emptyList(), previewMeta.title.takeIf { previewPlaying },
                    PremiumSportsPresentation.previewMatches(session, m, playing?.id),
                    onOpen = { m?.let(onOpenMatch) },
                    onFind = { m?.let(pickPreview) }, finding = finding && valid, searched = searched && valid,
                    onPreview = { o -> if (m != null) {
                        if (options.size == 1 && directSportsPreviewChoice(options) != null) startPreview(m, options, o)
                        else chooseFeed = true
                    } }, previewFocus = previewFocus,
                    onFullScreen = { o -> if (active && m != null) { vm.playedFrom(m, options, o.channel); onPlayChannel(o.channel, options.map { it.channel }) } },
                    compact = compact,
                    notice = when {
                        valid && finding -> stringResource(R.string.m4_finding_for_preview)
                        valid && searchFailed -> stringResource(R.string.m6_feed_search_error)
                        valid && searched && options.isEmpty() -> stringResource(R.string.m6_no_verified_feeds)
                        previewBlocked -> stringResource(R.string.m4_preview_blocked)
                        previewError != null -> {
                            val resources = LocalContext.current.resources
                            previewError!!.describe { id, args -> resources.getString(id, *args.toTypedArray()) }
                        }
                        previewLoading -> stringResource(R.string.m4_preview_loading)
                        else -> null
                    },
                    preview = { if (active && playing != null) ExoPreviewSurface(liveVm.previewEngine, Modifier.fillMaxSize()) })
            })
    }
    if (chooseFeed && selected != null) SportsFeedPicker(options,
        onPick = { option ->
            selected?.let { startPreview(it, options, option) }
            chooseFeed = false
        }, onDismiss = { chooseFeed = false }, restoreFocus = previewFocus)
    val held = menuFor
    if (held != null) FollowRemindMenu(held, state.config, emptyList(), System.currentTimeMillis(),
        onFollow = vm::toggleTeam, onRemind = { vm.remind(held, emptyList()) },
        onDismiss = { menuFor = null }, reminded = held.key in state.reminders, channelIndependentReminder = true)
}

/** Real content and navigation also exercised by the isolated Compose renderer. */
@Composable
internal fun PremiumSportsContent(state: SportsHubState, contentStart: Dp, restoreKey: String?,
    onEntryHook: (((() -> Boolean)?) -> Unit)?, onChildFocused: () -> Unit,
    onSelect: (Match?) -> Unit, onOpenMatch: (Match) -> Unit, onOpenLeague: (String) -> Unit,
    onFollow: () -> Unit, onHold: (Match) -> Unit, modifier: Modifier = Modifier,
    detail: @Composable (Match?, Boolean) -> Unit) {
    var destination by rememberSaveable { mutableStateOf(SportsDestination.MY_SPORTS) }
    var sportName by rememberSaveable { mutableStateOf<String?>(null) }
    val sport = Sport.entries.firstOrNull { it.name == sportName }
    var day by rememberSaveable { mutableIntStateOf(0) }
    var leagueFilter by remember { mutableStateOf<Set<String>>(emptySet()) }
    var choose by remember { mutableStateOf(false) }
    var heroIndex by rememberSaveable { mutableIntStateOf(0) }
    // A row-selected fixture becomes the current Sports hero, even if it lies outside the top 12.
    var pickedHomeKey by rememberSaveable { mutableStateOf(restoreKey) }
    var selectedKey by rememberSaveable { mutableStateOf(restoreKey) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val all = remember(state.board.matches, state.discovery.matches) { SportLoungeRules.dedupe(state.board.matches + state.discovery.matches) }
    val ranked = remember(all, state.config, now, sport) { PremiumSportsPresentation.ranked(all, state.config, now).filter { sport == null || it.sport == sport } }
    val heroes = ranked.take(12)
    val featured = ranked.firstOrNull { it.key == pickedHomeKey } ?: heroes.getOrNull(heroIndex.mod(heroes.size.coerceAtLeast(1)))
    val pickHome: (Match) -> Unit = { m ->
        pickedHomeKey = m.key
        onOpenMatch(m)
    }
    val date = Instant.ofEpochMilli(now).atZone(PremiumSportsPresentation.zone).toLocalDate().plusDays(day.toLong())
    val dayEvents = remember(all, date, sport, leagueFilter) { PremiumSportsPresentation.onDay(all, date, sport, leagueFilter) }
    val selected = if (destination == SportsDestination.MY_SPORTS) featured else dayEvents.firstOrNull { it.key == selectedKey } ?: dayEvents.firstOrNull()
    LaunchedEffect(selected?.key, selected?.startMs) { onSelect(selected) }
    val focusers = remember { HashMap<String, FocusRequester>() }
    fun focus(key: String) = focusers.getOrPut(key) { FocusRequester() }
    val entry = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    val chooseFocus = remember { FocusRequester() }
    val leagueEntry = remember { FocusRequester() }
    var restoreChoose by remember { mutableStateOf(false) }
    LaunchedEffect(choose) {
        if (choose) restoreChoose = true
        else if (restoreChoose) { withFrameNanos { }; runCatching { chooseFocus.requestFocus() }; restoreChoose = false }
    }
    DisposableEffect(onEntryHook) {
        // This is the shell's explicit remote-entry action. Standard clickable focus targets are
        // SystemDefined: touch mode refuses focus even when the node is attached and visible.
        onEntryHook?.invoke {
            inputMode.requestInputMode(InputMode.Keyboard)
            runCatching { entry.requestFocus() }.getOrDefault(false)
        }
        onDispose { onEntryHook?.invoke(null) }
    }
    val eventList = rememberLazyListState()
    LaunchedEffect(restoreKey, destination) {
        val index = dayEvents.indexOfFirst { it.key == restoreKey }
        if (restoreKey != null && destination == SportsDestination.ALL_EVENTS && index >= 0) {
            eventList.scrollToItem(index)
            withFrameNanos { }; runCatching { focus(restoreKey).requestFocus() }
        }
    }
    BackHandler(enabled = choose) { choose = false }
    Column(modifier.fillMaxSize().loungeBackdrop().padding(start = contentStart, end = 48.mpx, top = 32.mpx, bottom = 24.mpx)
        .onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        // The entry target must outlive horizontal lazy composition. Keep My Sports attached
        // while the remaining tabs can scroll at larger accessibility font/zoom settings.
        Row(Modifier.focusRestorer().focusGroup().padding(6.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sports_title), style = stageText(43, 800), color = Color.White,
                modifier = Modifier.padding(end = 16.mpx))
            LoungeAction(stringResource(R.string.m4_my_sports), destination == SportsDestination.MY_SPORTS,
                Modifier.focusRequester(entry)) { destination = SportsDestination.MY_SPORTS }
            LazyRow(Modifier.weight(1f).focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.mpx),
                contentPadding = PaddingValues(4.mpx)) {
                items(SportsDestination.entries.filterNot { it == SportsDestination.MY_SPORTS }) { tab ->
                    LoungeAction(stringResource(when(tab) {
                        SportsDestination.MY_SPORTS -> R.string.m4_my_sports
                        SportsDestination.ALL_EVENTS -> R.string.m4_all_events
                        SportsDestination.TABLES -> R.string.m4_tables
                        SportsDestination.NETWORKS -> R.string.m4_networks
                    }), destination == tab) { destination = tab }
                }
                item { LoungeAction(stringResource(R.string.m4_follow), onClick = onFollow) }
            }
        }
        LazyRow(Modifier.padding(vertical = 12.mpx).focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            item { LoungeAction(stringResource(R.string.content_epg_all), sport == null) { sportName = null } }
            items(state.config.orderSports(SportsCatalog.sports), key = { it.name }) { s ->
                LoungeAction(stringResource(R.string.m4_sport_shortcut, sportEmoji(s), sportLabel(s)), sport == s) { sportName = if (sport == s) null else s.name }
            }
        }
        when (destination) {
            SportsDestination.MY_SPORTS -> LazyColumn(Modifier.weight(1f).focusGroup(), verticalArrangement = Arrangement.spacedBy(18.mpx), contentPadding = PaddingValues(bottom = 12.mpx)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(22.mpx), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1.75f)) {
                            if (featured != null) PremiumEventCard(featured, state.config.spoilerFree, true,
                                Modifier.fillMaxWidth().height(300.mpx).onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown || heroes.size < 2) false else when(e.key) {
                                        Key.DirectionLeft -> { pickedHomeKey = null; heroIndex = PremiumSportsPresentation.carouselIndex(heroIndex, -1, heroes.size); true }
                                        Key.DirectionRight -> { pickedHomeKey = null; heroIndex = PremiumSportsPresentation.carouselIndex(heroIndex, 1, heroes.size); true }
                                        else -> false
                                    }
                                }, { pickHome(featured) }, { onHold(featured) })
                            else Text(stringResource(R.string.m4_no_events), style = stageText(28, 700), color = LoungeSurface.Muted)
                            if (heroes.size > 1) Row(Modifier.padding(top = 10.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                                LoungeAction(stringResource(R.string.m4_previous)) { pickedHomeKey = null; heroIndex = PremiumSportsPresentation.carouselIndex(heroIndex, -1, heroes.size) }
                                Text(stringResource(R.string.m4_carousel_count,
                                    heroes.indexOfFirst { it.key == featured?.key }.takeIf { it >= 0 }?.plus(1) ?: 1, heroes.size),
                                    style = stageText(19, 700), color = LoungeSurface.Muted)
                                LoungeAction(stringResource(R.string.m4_next)) { pickedHomeKey = null; heroIndex = PremiumSportsPresentation.carouselIndex(heroIndex, 1, heroes.size) }
                            }
                        }
                        Box(Modifier.weight(1f)) { detail(featured, true) }
                    }
                }
                item { SportsEventRail(stringResource(R.string.m4_live_upcoming), ranked.take(24), state.config, pickHome, onHold, onHighlight = { pickedHomeKey = it.key }) }
                val mine = ranked.filter { PremiumSportsPresentation.follows(it, state.config) }
                if (mine.isNotEmpty()) item { SportsEventRail(stringResource(R.string.m4_for_you), mine.take(24), state.config, pickHome, onHold, onHighlight = { pickedHomeKey = it.key }) }
                state.config.leagues.forEach { league ->
                    val matches = ranked.filter { it.feed == league }
                    if (matches.isNotEmpty()) item(key = league) { SportsEventRail(matches.first().leagueName, matches.take(12), state.config, pickHome, onHold, onHighlight = { pickedHomeKey = it.key }) }
                }
            }
            SportsDestination.ALL_EVENTS -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.mpx), modifier = Modifier.padding(bottom = 14.mpx).focusRestorer().focusGroup()) {
                    items((-1..6).toList()) { offset ->
                        val d = date.minusDays(day.toLong()).plusDays(offset.toLong())
                        LoungeAction(d.format(DateTimeFormatter.ofPattern("EEE d MMM")), day == offset) { day = offset }
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(26.mpx)) {
                    Column(Modifier.weight(1.2f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.mpx), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.m4_league_count, (if (leagueFilter.isEmpty()) all.map { it.feed }.toSet() else leagueFilter).size), style = stageText(21, 700), color = LoungeSurface.Muted)
                            LoungeAction(stringResource(R.string.m4_choose_leagues), modifier = Modifier.focusRequester(chooseFocus)) { choose = true }
                        }
                        if (dayEvents.isEmpty()) Text(stringResource(R.string.m4_no_events), style = stageText(25, 700), color = LoungeSurface.Muted, modifier = Modifier.padding(24.mpx))
                        LazyColumn(state = eventList, modifier = Modifier.padding(top = 16.mpx).focusGroup(), verticalArrangement = Arrangement.spacedBy(16.mpx), contentPadding = PaddingValues(5.mpx)) {
                            itemsIndexed(dayEvents, key = { _, m -> m.key }) { index, m ->
                                Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                                    if (index == 0 || dayEvents[index - 1].state != m.state) Text(stringResource(when(m.state) {
                                        MatchState.IN -> R.string.sports_live_now
                                        MatchState.PRE -> R.string.sports_starting_soon
                                        MatchState.POST -> R.string.sports_tab_results
                                        else -> R.string.sports_tab_fixtures
                                    }), style = stageText(24, 800), color = Color.White)
                                    PremiumEventCard(m, state.config.spoilerFree, false,
                                        Modifier.fillMaxWidth().height(162.mpx).focusRequester(focus(m.key)).onFocusChanged { if (it.isFocused) selectedKey = m.key },
                                        { selectedKey = m.key; onOpenMatch(m) }, { onHold(m) })
                                }
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) { detail(selected, false) }
                }
            }
            SportsDestination.TABLES, SportsDestination.NETWORKS -> {
                Text(stringResource(if (destination == SportsDestination.TABLES) R.string.m4_no_table else R.string.m4_networks_help), style = stageText(22, 500), color = LoungeSurface.Muted, modifier = Modifier.padding(vertical = 18.mpx))
                val leagues = all.filter { sport == null || it.sport == sport }.groupBy { it.feed }
                LazyColumn(Modifier.weight(1f).focusGroup(), verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                    items(leagues.keys.toList(), key = { it }) { league ->
                        val matches = leagues.getValue(league)
                        val networks = matches.flatMap { it.broadcasts }.map { it.name }.distinct()
                        val title = matches.first().leagueName
                        StageSurface(onClick = { onOpenLeague(league) }, radius = 20.mpx, focusStyle = StageFocus.FX,
                            modifier = Modifier.fillMaxWidth().background(LoungeSurface.Panel, RoundedCornerShape(20.mpx)).padding(4.mpx)) { _ ->
                            Column(Modifier.padding(22.mpx)) {
                                Text(title, style = stageText(28, 800), color = Color.White)
                                if (destination == SportsDestination.NETWORKS) Text(networks.joinToString(stringResource(R.string.content_epg_bits_separator)).ifBlank { stringResource(R.string.m4_no_networks) }, style = stageText(21, 500), color = LoungeSurface.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
    if (choose) StagePopup(onDismiss = { choose = false }, title = stringResource(R.string.m4_choose_leagues), width = 800.mpx,
        buttons = { StageButton(stringResource(R.string.m4_done), onClick = { choose = false }) }) {
        LaunchedEffect(Unit) { withFrameNanos { }; leagueEntry.requestFocus() }
        LazyColumn(Modifier.heightIn(max = 650.mpx), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
            item { StagePopupOption(stringResource(R.string.content_epg_all), chosen = leagueFilter.isEmpty(), modifier = Modifier.focusRequester(leagueEntry), onClick = { leagueFilter = emptySet() }) }
            items(all.groupBy { it.feed }.entries.toList(), key = { it.key }) { (key, matches) ->
                StagePopupOption(matches.first().leagueName, chosen = key in leagueFilter, onClick = { leagueFilter = if (key in leagueFilter) leagueFilter - key else leagueFilter + key })
            }
        }
    }
}

@Composable
private fun SportsEventRail(title: String, matches: List<Match>, config: SportsConfig, onOpen: (Match) -> Unit,
    onHold: (Match) -> Unit, onHighlight: (Match) -> Unit) {
    Column {
        Text(title, style = stageText(28, 800), color = Color.White, modifier = Modifier.padding(bottom = 12.mpx))
        LazyRow(Modifier.focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(16.mpx), contentPadding = PaddingValues(5.mpx)) {
            items(matches, key = { it.key }) { m -> PremiumEventCard(m, config.spoilerFree, false, Modifier.width(405.mpx).height(196.mpx).onFocusChanged { if (it.isFocused) onHighlight(m) }, { onOpen(m) }, { onHold(m) }) }
        }
    }
}

@Composable
internal fun PremiumEventCard(m: Match, spoilerFree: Boolean, featured: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, onHold: () -> Unit, showTime: Boolean = true, backgroundArtwork: Int? = null) {
    StageSurface(onClick, radius = 24.mpx, modifier = modifier.clip(RoundedCornerShape(24.mpx))
        .background(Brush.horizontalGradient(listOf(teamTint(m.home?.team)?.copy(alpha = 0.65f) ?: Color(0xFF11385A), teamTint(m.away?.team)?.copy(alpha = 0.65f) ?: Color(0xFF18283D))))
        .border(1.mpx, LoungeSurface.Line, RoundedCornerShape(24.mpx)), focusStyle = StageFocus.FX, onLongClick = onHold) { focused ->
        if (backgroundArtwork != null) androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(backgroundArtwork), contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.matchParentSize(), alpha = 0.28f)
        Column(Modifier.fillMaxSize().padding(if (featured) 26.mpx else 18.mpx), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(m.leagueName, style = stageText(if (featured) 23 else 18, 700), color = LoungeSurface.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (m.isLive) StageTag(stringResource(R.string.player_live), tint = Color(0xFFFF5568))
            }
            if (m.competitors.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                m.competitors.take(2).forEach { side ->
                    LoungeLogo(side.team.logoUrl, side.team.abbreviation.ifBlank { side.team.shortName }, Modifier.size(if (featured) 78.mpx else 48.mpx))
                    Column(Modifier.weight(1f)) {
                        Text(side.team.shortName, style = stageText(if (featured) 31 else 22, 800), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!spoilerFree && m.state != MatchState.PRE) side.score?.let { score -> Text(score.text, style = stageText(if (featured) 35 else 24, 800), color = LoungeSurface.Cyan) }
                    }
                }
            } else Text(m.title, style = stageText(if (featured) 34 else 25, 800), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val local = Instant.ofEpochMilli(m.startMs).atZone(PremiumSportsPresentation.zone).format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")) +
                stringResource(R.string.content_epg_bits_separator) + stringResource(R.string.m4_nz_time)
            Text(listOfNotNull(if (m.isLive) m.clock?.display?.takeIf { it.isNotBlank() } ?: m.statusText else if (m.state == MatchState.PRE && showTime) local else m.statusText, m.venue.takeIf { featured }).joinToString(stringResource(R.string.content_epg_bits_separator)),
                style = stageText(if (featured) 20 else 17, 600), color = LoungeSurface.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (focused) Box(Modifier.matchParentSize().border(2.mpx, LoungeSurface.Cyan, RoundedCornerShape(24.mpx)))
    }
}

@Composable
internal fun SportsPreviewPanel(match: Match?, options: List<WatchOption>, playingName: String?, matching: Boolean,
    onOpen: () -> Unit, onPreview: (WatchOption) -> Unit, onFullScreen: (WatchOption) -> Unit, notice: String?,
    compact: Boolean = false, preview: @Composable () -> Unit,
    onFind: () -> Unit = {}, finding: Boolean = false, searched: Boolean = false, previewFocus: FocusRequester? = null) {
    Column(Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.fillMaxHeight()),
        verticalArrangement = Arrangement.spacedBy(14.mpx)) {
        // Keep the decoder's surface outside the scrollable feed details. D-pad navigation to a
        // lower action never scrolls away the preview or replaces its playback owner.
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(24.mpx)).background(Color.Black), contentAlignment = Alignment.Center) {
            preview()
            if (notice != null || playingName == null) Text(notice ?: stringResource(R.string.m4_preview_empty), style = stageText(21, 500), color = LoungeSurface.Muted, modifier = Modifier.padding(24.mpx))
        }
        Column(Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.weight(1f).verticalScroll(rememberScrollState())),
            verticalArrangement = Arrangement.spacedBy(14.mpx)) {
            if (!compact && playingName != null) Text(playingName, style = stageText(20, 800), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!compact || playingName != null) Text(stringResource(if (playingName == null) R.string.m4_preview_empty else if (matching) R.string.m4_preview_selected else R.string.m4_preview_unrelated), style = stageText(19, 500), color = LoungeSurface.Muted)
            if (match != null) {
                val option = options.firstOrNull()
                if (!compact || option == null) LoungeAction(stringResource(R.string.m4_event_details), onClick = onOpen)
                if (option == null) {
                    LoungeAction(stringResource(when { finding -> R.string.m4_finding_for_preview; searched -> R.string.m6_retry_feeds; else -> R.string.m4_pick_here }),
                        modifier = if (previewFocus != null) Modifier.focusRequester(previewFocus) else Modifier,
                        onClick = { if (!finding) onFind() })
                    if (!compact) Text(stringResource(if (finding) R.string.m4_finding_for_preview else R.string.m4_feed_none),
                        style = stageText(19, 500), color = LoungeSurface.Muted)
                } else {
                    if (!compact) Text(option.channel.name, style = stageText(22, 800), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(when (option.tier) { WatchTier.GUIDE -> R.string.m4_feed_epg; WatchTier.EVENT -> R.string.m4_feed_event; WatchTier.REMEMBERED -> R.string.m4_feed_saved; else -> R.string.m4_feed_possible }), style = stageText(18, 500), color = LoungeSurface.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                        LoungeAction(stringResource(R.string.m4_preview_feed),
                            modifier = if (previewFocus != null) Modifier.focusRequester(previewFocus) else Modifier) { onPreview(option) }
                        LoungeAction(stringResource(R.string.m4_fullscreen)) { onFullScreen(option) }
                    }

                }
            }
        }
    }
}

/** Explicit choice, including low-evidence candidates. No stream opens from dialog focus. */
@Composable
internal fun SportsFeedPicker(options: List<WatchOption>, onPick: (WatchOption) -> Unit,
    onDismiss: () -> Unit, restoreFocus: FocusRequester) {
    val entry = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    DisposableEffect(Unit) { onDispose { runCatching { restoreFocus.requestFocus() } } }
    StagePopup(onDismiss = onDismiss, title = stringResource(R.string.m4_choose_feed), width = 800.mpx,
        buttons = { StageButton(stringResource(R.string.m4_done), onClick = onDismiss) }) {
        LaunchedEffect(Unit) { withFrameNanos { }; inputMode.requestInputMode(InputMode.Keyboard); entry.requestFocus() }
        LazyColumn(Modifier.heightIn(max = 600.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
            itemsIndexed(options, key = { _, option -> option.channel.id }) { index, option ->
                Column {
                    StagePopupOption(option.channel.name, chosen = false,
                        modifier = if (index == 0) Modifier.focusRequester(entry) else Modifier,
                        onClick = { onPick(option) })
                    Text(stringResource(when (option.tier) {
                        WatchTier.GUIDE -> R.string.m4_feed_epg
                        WatchTier.EVENT -> R.string.m4_feed_event
                        WatchTier.REMEMBERED -> R.string.m4_feed_saved
                        else -> R.string.m4_feed_possible
                    }), style = stageText(18, 500), color = LoungeSurface.Muted,
                        modifier = Modifier.padding(horizontal = 18.mpx, vertical = 6.mpx))
                }
            }
        }
    }
}
