package tv.own.owntv.features.sports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.features.settings.StageFieldRow
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.lounge.*
import tv.own.owntv.ui.stage.*
import tv.own.owntv.ui.theme.*

@Composable
internal fun PremiumFollowScreen(onDone: () -> Unit, contentStart: Dp, modifier: Modifier = Modifier,
    vm: SportsSettingsViewModel = koinViewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val pid by vm.profileId.collectAsStateWithLifecycle()
    key(pid) {
        PremiumFollowContent(config, vm::teamsForLeague, { vm.toggleTeam(it) }, { vm.toggleLeague(it) },
            { key, delta -> vm.moveFollowedTeam(key, delta) }, { key, delta -> vm.moveFollowedLeague(key, delta) },
            onDone, contentStart, modifier, searchTeams = vm::searchTeams)
    }
}

@Composable
internal fun PremiumFollowContent(config: SportsConfig, loadTeams: suspend (String) -> List<Team>,
    toggleTeam: (FollowedTeam) -> Unit, toggleLeague: (String) -> Unit,
    moveTeam: (String, Int) -> Unit, moveLeague: (String, Int) -> Unit,
    onDone: () -> Unit, contentStart: Dp, modifier: Modifier = Modifier, searchTeams: suspend (String) -> List<FollowedTeam> = { emptyList() }) {
        var sport by remember { mutableStateOf(config.orderSports(SportsCatalog.sports).first()) }
        var leagueKey by remember { mutableStateOf<String?>(null) }
        var query by remember { mutableStateOf("") }
        var teams by remember(sport, leagueKey) { mutableStateOf<List<Team>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var retry by remember { mutableIntStateOf(0) }
        var arrange by remember { mutableStateOf(false) }
        var picked by remember { mutableStateOf<String?>(null) }
        var pickedTeam by remember { mutableStateOf(false) }
        val searched by produceState(emptyList<FollowedTeam>(), query) {
            value = emptyList()
            if (query.trim().length >= 2) {
                kotlinx.coroutines.delay(250)
                try { value = searchTeams(query) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { value = emptyList() }
            }
        }
        val done = remember { FocusRequester() }
        val arrangeFocus = remember { FocusRequester() }
        var restoreArrange by remember { mutableStateOf(false) }
        LaunchedEffect(arrange) {
            if (arrange) restoreArrange = true
            else if (restoreArrange) { withFrameNanos { }; runCatching { arrangeFocus.requestFocus() }; restoreArrange = false }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { done.requestFocus() } }
        LaunchedEffect(leagueKey, retry) {
            teams = emptyList(); failed = false
            val key = leagueKey ?: return@LaunchedEffect
            loading = true
            try { teams = loadTeams(key) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failed = true }
            finally { loading = false }
        }
        BackHandler { if (arrange) arrange = false else onDone() }
        Column(modifier.fillMaxSize().loungeBackdrop().padding(start = contentStart, end = 48.mpx, top = 32.mpx, bottom = 30.mpx)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                Text(stringResource(R.string.m4_follow), style = stageText(40, 800), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                LoungeAction(stringResource(R.string.m4_arrange), modifier = Modifier.focusRequester(arrangeFocus)) { arrange = true }
                LoungeAction(stringResource(R.string.m4_done), modifier = Modifier.focusRequester(done), onClick = onDone)
            }
            Text(stringResource(R.string.m4_save_done), style = stageText(18, 500), color = LoungeSurface.Muted, modifier = Modifier.padding(top = 8.mpx))
            StageFieldRow(OwnTVIcon.SEARCH, stringResource(R.string.m4_search), query, { query = it }, modifier = Modifier.padding(vertical = 12.mpx))
            if (query.isNotBlank()) Text(stringResource(R.string.m4_search_scope), style = stageText(17, 500), color = LoungeSurface.Muted)
            if (searched.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                items(searched, key = { it.key }) { team ->
                    FollowChip(team.name, { toggleTeam(team) }, {})
                }
            }
            Text(stringResource(R.string.m4_following_count, config.teams.size + config.leagues.size), style = stageText(20, 800), color = LoungeSurface.Cyan)
            LazyRow(Modifier.padding(vertical = 12.mpx).focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                items(config.leagues, key = { "league:$it" }) { k ->
                    FollowChip(SportsCatalog.league(k)?.name ?: k, { toggleLeague(k) }, { moveLeague(k, -1) })
                }
                items(config.teams, key = { "team:${it.key}" }) { t -> FollowChip(t.name, { toggleTeam(t) }, { moveTeam(t.key, -1) }) }
            }
            Text(stringResource(R.string.m4_following_help), style = stageText(18, 500), color = LoungeSurface.Muted)
            Row(Modifier.weight(1f).padding(top = 18.mpx), horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
                LazyColumn(Modifier.width(280.mpx).focusGroup(), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                    items(config.orderSports(SportsCatalog.sports), key = { it.name }) { s -> LoungeAction(stringResource(R.string.m4_sport_shortcut, sportEmoji(s), sportLabel(s)), sport == s, Modifier.fillMaxWidth()) { sport = s; leagueKey = null } }
                }
                Column(Modifier.weight(1f)) {
                    val leagues = if (query.isBlank()) SportsCatalog.leaguesOf(sport) else SportsCatalog.leagues.filter { it.name.contains(query, true) || leagueKey == it.key }
                    LazyRow(Modifier.focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                        items(leagues, key = { it.key }) { l -> LoungeAction(l.name, leagueKey == l.key) { sport = l.sport; leagueKey = l.key; retry++ } }
                    }
                    val chosen = SportsCatalog.league(leagueKey ?: "")
                    if (chosen != null) {
                        LoungeAction(stringResource(if (chosen.key in config.leagues) R.string.sports_following else R.string.sports_follow) + stringResource(R.string.content_epg_bits_separator) + chosen.name,
                            chosen.key in config.leagues, Modifier.padding(vertical = 16.mpx)) { toggleLeague(chosen.key) }
                    }
                    if (loading || failed || chosen != null && teams.isEmpty()) Text(stringResource(when { loading -> R.string.m4_teams_loading; failed -> R.string.m4_teams_failed; else -> R.string.m4_teams_empty }), style = stageText(22, 500), color = LoungeSurface.Muted, modifier = Modifier.padding(20.mpx))
                    LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f).focusGroup(), horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalArrangement = Arrangement.spacedBy(14.mpx), contentPadding = PaddingValues(5.mpx)) {
                        items(teams.filter { query.isBlank() || it.name.contains(query, true) }, key = { it.id }) { t ->
                            val followed = FollowedTeam(sport, t.id, t.name, t.abbreviation)
                            val on = config.teams.any { it.key == followed.key }
                            StageSurface(onClick = { toggleTeam(followed) }, radius = 22.mpx, focusStyle = StageFocus.FX,
                                modifier = Modifier.fillMaxWidth().height(142.mpx).background(if (on) Color(0xFF13506A) else teamTint(t)?.copy(alpha = 0.35f) ?: LoungeSurface.Panel, RoundedCornerShape(22.mpx))) { _ ->
                                Column(Modifier.padding(16.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                                    LoungeLogo(t.logoUrl, t.abbreviation.ifBlank { t.shortName }, Modifier.size(52.mpx))
                                    Text(t.shortName, style = stageText(21, 800), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (arrange) {
            fun close() { arrange = false }
            StagePopup(onDismiss = { close() }, title = stringResource(R.string.m4_arrange), width = 850.mpx,
                buttons = {
                    StageButton(stringResource(R.string.m4_earlier), onClick = {
                        picked?.let { if (pickedTeam) moveTeam(it, -1) else moveLeague(it, -1) }
                    })
                    StageButton(stringResource(R.string.m4_later), onClick = {
                        picked?.let { if (pickedTeam) moveTeam(it, 1) else moveLeague(it, 1) }
                    })
                    StageButton(stringResource(R.string.m4_done), onClick = { close() })
                }) {
                LazyColumn(Modifier.heightIn(max = 600.mpx)) {
                    items(config.leagues, key = { "league:$it" }) { k -> StagePopupOption(SportsCatalog.league(k)?.name ?: k, chosen = picked == k && !pickedTeam, onClick = { picked = k; pickedTeam = false }) }
                    items(config.teams, key = { "team:${it.key}" }) { t -> StagePopupOption(t.name, chosen = picked == t.key && pickedTeam, onClick = { picked = t.key; pickedTeam = true }) }
                }
            }
        }
}

@Composable
private fun FollowChip(label: String, onClick: () -> Unit, onHold: () -> Unit) {
    StageSurface(onClick, radius = 28.mpx, focusStyle = StageFocus.FX, onLongClick = onHold,
        modifier = Modifier.height(55.mpx).background(LoungeSurface.Panel, RoundedCornerShape(28.mpx))) { _ ->
        Text(label, style = stageText(21, 700), color = Color.White, modifier = Modifier.padding(horizontal = 20.mpx))
    }
}
