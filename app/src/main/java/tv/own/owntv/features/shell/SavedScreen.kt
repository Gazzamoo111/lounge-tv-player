package tv.own.owntv.features.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.features.home.OnNowCard
import tv.own.owntv.features.more.MoreCountsViewModel
import tv.own.owntv.features.sports.data.FixtureReminderScheduler
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dedicated Saved destination. Downloads remains Downloads; this screen owns the two things the
 * customer deliberately saves for later: favourites and sport-event reminders.
 */
@Composable
internal fun SavedScreen(
    activeProfileId: Long?,
    onPlayChannel: (Long) -> Unit,
    onPlayMovie: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onChildFocused: () -> Unit,
    onEntryHook: (((() -> Boolean)?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val vm: MoreCountsViewModel = koinViewModel()
    val scheduler = koinInject<FixtureReminderScheduler>()
    val profileId = activeProfileId ?: -1L
    val reminders by scheduler.observe(profileId).collectAsStateWithLifecycle(initialValue = emptyList())
    val favourites by vm.favouriteLists.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val firstTab = remember { FocusRequester() }

    SideEffect {
        onEntryHook?.invoke { runCatching { firstTab.requestFocus() }.getOrDefault(false) }
    }

    val sportLive = remember(favourites.live) { favourites.live.filter(::isSportFavourite) }
    val kidsLive = remember(favourites.live) { favourites.live.filter(::isKidsFavourite) }
    val entertainmentLive = remember(favourites.live, sportLive, kidsLive) {
        favourites.live.filter { channel -> channel !in sportLive && channel !in kidsLive }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(start = 155.mpx, top = 132.mpx, end = 60.mpx, bottom = 36.mpx)
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(20.mpx),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.lounge_saved_title), style = stageText(42, 800), color = StageColors.Text)
            StageButton(
                text = stringResource(R.string.lounge_saved_favourites),
                onClick = { tab = 0 },
                modifier = Modifier.focusRequester(firstTab),
            )
            StageButton(
                text = stringResource(R.string.lounge_saved_reminders) + " (${reminders.size})",
                onClick = { tab = 1 },
            )
        }

        if (tab == 0) {
            Text(stringResource(R.string.lounge_saved_hold_remove), style = stageText(15, 500), color = StageColors.Muted)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(28.mpx),
                contentPadding = PaddingValues(bottom = 36.mpx),
            ) {
                if (sportLive.isNotEmpty()) {
                    item {
                        SavedLiveRow(
                            title = stringResource(R.string.lounge_saved_sport),
                            channels = sportLive,
                            vm = vm,
                            onPlayChannel = onPlayChannel,
                            onNow = favourites.onNow,
                            now = favourites.now,
                        )
                    }
                }
                if (kidsLive.isNotEmpty()) {
                    item {
                        SavedLiveRow(
                            title = stringResource(R.string.lounge_saved_kids),
                            channels = kidsLive,
                            vm = vm,
                            onPlayChannel = onPlayChannel,
                            onNow = favourites.onNow,
                            now = favourites.now,
                        )
                    }
                }
                if (favourites.movies.isNotEmpty()) {
                    item {
                        SavedPosterRow(
                            title = stringResource(R.string.common_nav_movies),
                            rows = favourites.movies.map { SavedPosterItem(MediaType.MOVIE, it.id, it.name, it.posterUrl) },
                            onOpen = { onPlayMovie(it.id) },
                            onRemove = { vm.removeFavourite(it.type, it.id) },
                        )
                    }
                }
                if (favourites.series.isNotEmpty()) {
                    item {
                        SavedPosterRow(
                            title = stringResource(R.string.common_nav_series),
                            rows = favourites.series.map { SavedPosterItem(MediaType.SERIES, it.id, it.name, it.posterUrl) },
                            onOpen = { onOpenSeries(it.id) },
                            onRemove = { vm.removeFavourite(it.type, it.id) },
                        )
                    }
                }
                if (entertainmentLive.isNotEmpty()) {
                    item {
                        SavedLiveRow(
                            title = stringResource(R.string.lounge_saved_entertainment),
                            channels = entertainmentLive,
                            vm = vm,
                            onPlayChannel = onPlayChannel,
                            onNow = favourites.onNow,
                            now = favourites.now,
                        )
                    }
                }
            }
        } else if (reminders.isEmpty()) {
            Text(stringResource(R.string.lounge_saved_no_reminders), style = stageText(22, 500), color = StageColors.Body)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.mpx), contentPadding = PaddingValues(bottom = 32.mpx)) {
                items(reminders, key = { it.fixtureId }) { reminder ->
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF182B40)).padding(18.mpx),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                            Text(reminder.title, style = stageText(24, 700), color = StageColors.Text)
                            Text(
                                DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)
                                    .withZone(ZoneId.of("Pacific/Auckland"))
                                    .format(Instant.ofEpochMilli(reminder.startMs)),
                                style = stageText(18, 500),
                                color = StageColors.Body,
                            )
                        }
                        StageButton(
                            text = stringResource(R.string.lounge_saved_remove_reminder),
                            onClick = { scheduler.remove(profileId, reminder.fixtureId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedLiveRow(
    title: String,
    channels: List<ChannelEntity>,
    vm: MoreCountsViewModel,
    onPlayChannel: (Long) -> Unit,
    onNow: Map<Long, tv.own.owntv.core.live.GuideSlot>,
    now: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
        Text(title, style = stageText(25, 800), color = StageColors.Text)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.mpx), contentPadding = PaddingValues(end = 30.mpx)) {
            items(channels, key = { it.id }) { channel ->
                OnNowCard(
                    channel = channel,
                    programme = onNow[channel.id]?.now,
                    now = now,
                    onClick = { onPlayChannel(channel.id) },
                    onLongClick = { vm.removeFavourite(MediaType.LIVE, channel.id) },
                    width = 390.mpx,
                    oneLine = true,
                )
            }
        }
    }
}

private data class SavedPosterItem(
    val type: MediaType,
    val id: Long,
    val title: String,
    val artwork: String?,
)

@Composable
private fun SavedPosterRow(
    title: String,
    rows: List<SavedPosterItem>,
    onOpen: (SavedPosterItem) -> Unit,
    onRemove: (SavedPosterItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
        Text(title, style = stageText(25, 800), color = StageColors.Text)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(22.mpx), contentPadding = PaddingValues(end = 30.mpx)) {
            items(rows, key = { it.type.name + ":" + it.id }) { item ->
                StagePoster(
                    title = item.title,
                    line = stringResource(if (item.type == MediaType.MOVIE) R.string.search_movie else R.string.search_series),
                    onClick = { onOpen(item) },
                    onLongClick = { onRemove(item) },
                    width = 178.mpx,
                    height = 267.mpx,
                ) {
                    if (!item.artwork.isNullOrBlank()) {
                        AsyncImage(
                            model = item.artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(StageColors.ControlFill), contentAlignment = Alignment.Center) {
                            OwnTVIcon(
                                if (item.type == MediaType.MOVIE) OwnTVIcon.MOVIES else OwnTVIcon.SERIES,
                                StageColors.Dim,
                                Modifier.size(42.mpx),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isSportFavourite(channel: ChannelEntity): Boolean {
    val value = channel.name.lowercase()
    return SPORT_HINTS.any(value::contains)
}

private fun isKidsFavourite(channel: ChannelEntity): Boolean {
    val value = channel.name.lowercase()
    return KIDS_HINTS.any(value::contains)
}

private val SPORT_HINTS = listOf(
    "sport", "espn", "nba", "nfl", "nrl", "rugby", "cricket", "ufc", "boxing", "fight",
    "golf", "football", "soccer", "tennis", "f1", "motogp", "nhl", "mlb", "wwe",
)

private val KIDS_HINTS = listOf(
    "kids", "nick", "cartoon", "disney", "junior", "cbeebies", "boomerang", "pbs kids",
)
