package tv.own.owntv.features.mylounge

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.reminder.ReminderManager
import tv.own.owntv.features.home.OnNowCard
import tv.own.owntv.features.more.MoreCountsViewModel
import tv.own.owntv.features.more.RowAction
import tv.own.owntv.features.more.StageActionRow
import tv.own.owntv.features.sports.data.FixtureReminderScheduler
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.loungeVodTitle
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.stage.StageSegmented
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.util.Date

/** Provider categories are inconsistent, so use a consistent customer-facing grouping. */
internal enum class LoungeLiveCategory { SPORTS, ENTERTAINMENT, MOVIES, KIDS_FAMILY }

internal fun loungeLiveCategory(name: String, categoryName: String): LoungeLiveCategory {
    val label = categoryName + '\n' + name
    if (Regex("(?i)kid|family|cartoon|disney|nick|junior|preschool|baby|boomerang").containsMatchIn(label))
        return LoungeLiveCategory.KIDS_FAMILY
    if (Regex("(?i)movie|cinema|\\bfilm\\b|sky store").containsMatchIn(label))
        return LoungeLiveCategory.MOVIES
    if (Regex("(?i)sport|rugby|cricket|football|soccer|\\bnba\\b|\\bnfl\\b|\\bmlb\\b|\\bnhl\\b|espn|\\bf1\\b|motorsport|ufc|wwe|racing|tennis|golf|\\bbein\\b").containsMatchIn(label))
        return LoungeLiveCategory.SPORTS
    return LoungeLiveCategory.ENTERTAINMENT
}

private fun loungeDate(context: android.content.Context, ms: Long, separator: String): String =
    android.text.format.DateFormat.getMediumDateFormat(context).format(Date(ms)) +
        separator + android.text.format.DateFormat.getTimeFormat(context).format(Date(ms))

/** Read the actual profile's saved favourites and the two existing reminder sources. */
@Composable
fun MyLoungeScreen(
    profileId: Long,
    onPlayChannel: (Long) -> Unit,
    onOpenMovie: (MovieEntity) -> Unit,
    onOpenSeries: (SeriesEntity) -> Unit,
    onOpenLiveGuide: () -> Unit,
    onOpenSports: () -> Unit,
    onChildFocused: () -> Unit,
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    vm: MoreCountsViewModel = koinViewModel(),
) {
    val categoryDao = koinInject<CategoryDao>()
    val reminderManager = koinInject<ReminderManager>()
    val fixtureScheduler = koinInject<FixtureReminderScheduler>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val separator = stringResource(R.string.content_epg_bits_separator)
    val lists by vm.favouriteLists.collectAsStateWithLifecycle()
    val programmeFlow = remember(profileId, reminderManager) { reminderManager.observe(profileId) }
    val fixtureFlow = remember(profileId, fixtureScheduler) { fixtureScheduler.observe(profileId) }
    val programmes by programmeFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val fixtures by fixtureFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val categoryNames by produceState<Map<Long, String>>(initialValue = emptyMap(), key1 = lists.live, key2 = profileId) {
        value = withContext(Dispatchers.IO) {
            lists.live.mapNotNull { it.categoryId }.distinct().associateWith { categoryId ->
                categoryDao.getById(categoryId)?.name.orEmpty()
            }
        }
    }
    val primaryFocus = remember { FocusRequester() }
    DisposableEffect(onEntryHook) {
        onEntryHook?.invoke { runCatching { primaryFocus.requestFocus() }.getOrDefault(false) }
        onDispose { onEntryHook?.invoke(null) }
    }
    var tab by remember(profileId) { mutableIntStateOf(0) }
    var liveGroup by remember(profileId) { mutableIntStateOf(0) }
    var vodGroup by remember(profileId) { mutableIntStateOf(0) }
    val liveOptions = listOf(
        stringResource(R.string.lounge_my_sports), stringResource(R.string.lounge_my_entertainment),
        stringResource(R.string.lounge_my_movies), stringResource(R.string.lounge_my_kids),
    )
    val vodOptions = listOf(stringResource(R.string.lounge_my_movies), stringResource(R.string.lounge_my_series))
    val cancelLabel = stringResource(R.string.lounge_my_cancel)
    val shownLive = lists.live.filter {
        loungeLiveCategory(it.name, categoryNames[it.categoryId].orEmpty()).ordinal == liveGroup
    }
    val now = System.currentTimeMillis()
    val validProgrammes = programmes.filter { it.stopMs > now }
    val validFixtures = fixtures.filter { it.startMs + 21_600_000L > now }

    Column(
        modifier.fillMaxSize().padding(start = 64.mpx, top = 136.mpx, end = 64.mpx, bottom = 42.mpx)
            .onFocusChanged { if (it.hasFocus) onChildFocused() }.focusGroup(),
    ) {
        Text(stringResource(R.string.lounge_my_title), style = stageText(42, 800), color = StageColors.Text)
        Spacer(Modifier.height(24.mpx))
        StageSegmented(
            options = listOf(
                stringResource(R.string.lounge_my_live), stringResource(R.string.lounge_my_vod),
                stringResource(R.string.lounge_my_reminders),
            ),
            selected = tab, onSelect = { tab = it }, modifier = Modifier.focusRequester(primaryFocus),
            icons = listOf(OwnTVIcon.LIVE_TV, OwnTVIcon.FAVORITE, OwnTVIcon.BELL),
        )
        Spacer(Modifier.height(27.mpx))
        when (tab) {
            0 -> {
                StageSegmented(options = liveOptions, selected = liveGroup, onSelect = { liveGroup = it })
                Spacer(Modifier.height(23.mpx))
                if (shownLive.isEmpty()) {
                    MyLoungeEmpty(stringResource(R.string.lounge_my_no_live))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.mpx), contentPadding = PaddingValues(end = 28.mpx)) {
                        items(shownLive, key = { it.id }) { channel ->
                            OnNowCard(
                                channel = channel, programme = lists.onNow[channel.id]?.now,
                                now = lists.now, onClick = { onPlayChannel(channel.id) },
                                width = 400.mpx, oneLine = true,
                            )
                        }
                    }
                }
            }
            1 -> {
                StageSegmented(options = vodOptions, selected = vodGroup, onSelect = { vodGroup = it })
                Spacer(Modifier.height(23.mpx))
                if (vodGroup == 0 && lists.movies.isEmpty() || vodGroup == 1 && lists.series.isEmpty()) {
                    MyLoungeEmpty(stringResource(R.string.lounge_my_no_vod))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(22.mpx), contentPadding = PaddingValues(end = 28.mpx)) {
                        if (vodGroup == 0) {
                            items(lists.movies, key = { it.id }) { movie ->
                                LoungeVodCard(loungeVodTitle(movie.name), movie.posterUrl) { onOpenMovie(movie) }
                            }
                        } else {
                            items(lists.series, key = { it.id }) { series ->
                                LoungeVodCard(loungeVodTitle(series.name), series.posterUrl) { onOpenSeries(series) }
                            }
                        }
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                item {
                    LoungeHeading(stringResource(R.string.lounge_my_programmes))
                    if (validProgrammes.isEmpty()) MyLoungeEmpty(stringResource(R.string.lounge_my_guide_hint))
                }
                items(validProgrammes, key = { it.id }) { reminder ->
                    StageActionRow(
                        height = 86.mpx, focus = null, title = reminder.title,
                        line = reminder.channelName + separator + loungeDate(context, reminder.startMs, separator),
                        onClick = onOpenLiveGuide,
                        leading = { OwnTVIcon(OwnTVIcon.BELL, StageColors.Muted, Modifier.size(29.mpx)) },
                        actions = listOf(RowAction(cancelLabel, OwnTVIcon.CLOSE) {
                            scope.launch { reminderManager.dismiss(reminder.id) }
                        }),
                    )
                }
                item {
                    Spacer(Modifier.height(8.mpx))
                    LoungeHeading(stringResource(R.string.lounge_my_events))
                    if (validFixtures.isEmpty()) MyLoungeEmpty(stringResource(R.string.lounge_my_sport_hint))
                }
                items(validFixtures, key = { it.fixtureId }) { fixture ->
                    StageActionRow(
                        height = 86.mpx, focus = null, title = fixture.title,
                        line = loungeDate(context, fixture.startMs, separator),
                        onClick = onOpenSports,
                        leading = { OwnTVIcon(OwnTVIcon.SPORTS, StageColors.Muted, Modifier.size(29.mpx)) },
                        actions = listOf(RowAction(cancelLabel, OwnTVIcon.CLOSE) {
                            fixtureScheduler.remove(profileId, fixture.fixtureId)
                        }),
                    )
                }
                if (validProgrammes.isEmpty() && validFixtures.isEmpty()) item {
                    MyLoungeEmpty(stringResource(R.string.lounge_my_no_reminders))
                    Spacer(Modifier.height(12.mpx))
                    LoungeNavButton(stringResource(R.string.lounge_my_open_guide), onOpenLiveGuide)
                    Spacer(Modifier.height(12.mpx))
                    LoungeNavButton(stringResource(R.string.lounge_my_open_sports), onOpenSports)
                }
            }
        }
    }
}

@Composable
private fun LoungeVodCard(title: String, poster: String?, onClick: () -> Unit) {
    StagePoster(title = title, onClick = onClick, width = 200.mpx, height = 300.mpx) {
        if (!poster.isNullOrBlank()) {
            AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(StageColors.ControlFill, RoundedCornerShape(16.mpx)), contentAlignment = Alignment.Center) {
                OwnTVIcon(OwnTVIcon.MOVIES, StageColors.Muted, Modifier.size(38.mpx))
            }
        }
    }
}

@Composable
private fun LoungeHeading(label: String) {
    Text(label, style = stageText(25, 800), color = StageColors.Text, modifier = Modifier.padding(bottom = 10.mpx))
}

@Composable
private fun MyLoungeEmpty(message: String) {
    Text(message, style = stageText(20, 500), color = StageColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 20.mpx))
}

@Composable
private fun LoungeNavButton(label: String, onClick: () -> Unit) {
    StageSurface(
        onClick = onClick, radius = 14.mpx,
        idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(14.mpx)),
    ) { focused ->
        Text(label, style = stageText(18, 700), color = if (focused) stageAccent.onAccent else StageColors.Text, modifier = Modifier.padding(horizontal = 18.mpx, vertical = 14.mpx))
    }
}
