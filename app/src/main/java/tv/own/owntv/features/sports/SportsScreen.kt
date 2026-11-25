package tv.own.owntv.features.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.features.live.LiveViewModel

/**
 * The Sports section: the hub, and the match page opened from it (Back returns to the hub) — or from the
 * player's *Match* button, when Back plays that channel again.
 */
@Composable
fun SportsScreen(
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onAddPlaylist: () -> Unit,
    onChildFocused: () -> Unit,
    onEntryHook: (((() -> Boolean)?) -> Unit)?,
    contentStart: Dp,
    modifier: Modifier = Modifier,
    /** False while the player is full screen over the section: its Back is the player's. */
    active: Boolean = true,
    vm: SportsViewModel = koinViewModel(),
    liveVm: LiveViewModel = koinViewModel(),
) {
    val hubState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val profileSettings = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val hubProfile by profileSettings.activeProfileId.collectAsStateWithLifecycle(-1L)
    var match by remember { mutableStateOf<Match?>(null) }
    var filters by remember { mutableStateOf(false) }
    // The league page, over the hub or a match (Back returns to whichever it came from).
    var league by remember { mutableStateOf<String?>(null) }
    val openLeague: (String?) -> Unit = { k -> league = k; vm.openLeague(k) }
    // Set when the page was opened from the player's Match button: Back plays that channel again.
    var returnTo by remember { mutableStateOf<ChannelEntity?>(null) }
    // The card a match was opened from, so Back lands on it.
    var lastKey by remember { mutableStateOf<String?>(null) }
    val open: (Match?) -> Unit = { m -> if (m != null) lastKey = m.key; match = m; vm.open(m) }
    val playback = koinInject<SportsPlayback>()
    val pendingMatch by playback.pendingMatch.collectAsStateWithLifecycle()
    var initialNavigationApplied by remember { mutableStateOf(false) }
    // This screen can stay mounted behind fullscreen. New player/goal requests must be consumed when
    // it becomes active again, rather than relying on disposal and a second LaunchedEffect(Unit).
    LaunchedEffect(active, pendingMatch) {
        if (!active) return@LaunchedEffect
        val request = pendingMatch
        if (request != null) {
            if (!playback.pendingMatch.compareAndSet(request, null)) return@LaunchedEffect
            initialNavigationApplied = true
            playback.returnToMatch = null
            val (m, ch) = request
            returnTo = ch
            filters = false
            if (league != null) openLeague(null)
            if (tv.own.owntv.BuildConfig.LOUNGE_TV_2 && ch == null) {
                // Home event action is explicit: go directly to this fixture's Watch live page.
                // Open resets findAsked, so trigger the bounded search only after opening.
                open(m)
                vm.findChannels()
            } else open(m)
        } else if (!initialNavigationApplied) {
            initialNavigationApplied = true
            playback.returnToMatch?.let { m ->
                playback.returnToMatch = null
                open(m)
            }
        } else if (playback.returnToMatch?.key == match?.key) {
            // The retained page already has this match; discard its legacy remount request.
            playback.returnToMatch = null
        }
    }
    val current = match
    val leagueKey = league
    if (leagueKey != null) {
        SportsLeagueScreen(
            vm = vm,
            onOpenMatch = { m -> openLeague(null); open(m) },
            onBack = { openLeague(null) },
            active = active,
            contentStart = contentStart,
            modifier = modifier,
        )
    } else if (current == null) {
        if (tv.own.owntv.BuildConfig.LOUNGE_TV_2) hubState.SaveableStateProvider(hubProfile) {
            PremiumSportsScreen(
            vm, liveVm, onOpenMatch = { open(it) }, onOpenLeague = { openLeague(it) },
            onPlayChannel = onPlayChannel, onChildFocused = onChildFocused, onEntryHook = onEntryHook,
            contentStart = contentStart, active = active, restoreKey = lastKey, modifier = modifier,
            )
        } else SportLoungeScreen(
            vm = vm,
            onOpenMatch = open,
            onPlayChannel = onPlayChannel,
            onChildFocused = onChildFocused,
            onEntryHook = onEntryHook,
            contentStart = contentStart,
            modifier = modifier,
            restoreKey = lastKey,
        )
    } else {
        SportsMatchScreen(
            vm = vm,
            liveVm = liveVm,
            onPlayChannel = { ch, zap -> playback.returnToMatch = current; onPlayChannel(ch, zap) },
            onOpenFilters = { filters = true },
            onOpenLeague = { m -> openLeague(tv.own.owntv.core.sports.model.League.keyOf(m.sport, m.leagueSlug)) },
            onAddPlaylist = onAddPlaylist,
            onBack = {
                val ch = returnTo
                returnTo = null
                open(null)
                if (ch != null) onPlayChannel(ch, playback.session.value?.options?.map { it.channel }.orEmpty())
            },
            active = active,
            contentStart = contentStart,
            modifier = modifier,
        )
        val page = vm.matchPage.collectAsStateWithLifecycle().value
        if (filters && page != null && page.hasPlaylist) {
            SportsFilterPanel(page, onFilter = vm::setFilter, onRemember = vm::setRememberFilters, onDismiss = { filters = false })
        }
    }
}
