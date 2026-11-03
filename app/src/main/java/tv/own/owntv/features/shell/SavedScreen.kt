package tv.own.owntv.features.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.features.more.FavouritesPage
import tv.own.owntv.features.more.MoreCountsViewModel
import tv.own.owntv.features.sports.data.FixtureReminderScheduler
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dedicated Saved destination: the existing profile-aware favourites plus removable event reminders. */
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val firstTab = androidx.compose.runtime.remember { FocusRequester() }
    SideEffect { onEntryHook?.invoke { runCatching { firstTab.requestFocus() }.getOrDefault(false) } }
    Column(
        modifier.fillMaxSize().padding(start = 155.mpx, top = 140.mpx, end = 60.mpx, bottom = 36.mpx)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(22.mpx),
    ) {
        Text(stringResource(R.string.lounge_saved_title), style = stageText(42, 800), color = StageColors.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
            StageButton(
                text = stringResource(R.string.lounge_saved_favourites),
                onClick = { tab = 0; onChildFocused() },
                modifier = Modifier.focusRequester(firstTab),
            )
            StageButton(
                text = stringResource(R.string.lounge_saved_reminders) + " (${reminders.size})",
                onClick = { tab = 1; onChildFocused() },
            )
        }
        if (tab == 0) {
            FavouritesPage(
                vm = vm,
                setEntry = { /* The Saved rail enters via the tab buttons. */ },
                onPlayChannel = onPlayChannel,
                onPlayMovie = onPlayMovie,
                onOpenSeries = onOpenSeries,
            )
        } else {
            if (reminders.isEmpty()) {
                Text(stringResource(R.string.lounge_saved_no_reminders), style = stageText(22, 500), color = StageColors.Body)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                    items(reminders, key = { it.fixtureId }) { reminder ->
                        Row(
                            Modifier.fillMaxWidth().background(Color(0xFF182B40)).padding(18.mpx),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                                Text(reminder.title, style = stageText(24, 700), color = StageColors.Text)
                                Text(
                                    DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)
                                        .withZone(ZoneId.of("Pacific/Auckland"))
                                        .format(Instant.ofEpochMilli(reminder.startMs)),
                                    style = stageText(18, 500), color = StageColors.Body,
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
}
