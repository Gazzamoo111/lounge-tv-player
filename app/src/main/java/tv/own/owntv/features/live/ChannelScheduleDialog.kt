package tv.own.owntv.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.*
import tv.own.owntv.ui.theme.*

/** Day-sized indexed reads. Opening this popup never acquires or releases the existing player. */
@Composable
internal fun ChannelScheduleDialog(
    channel: ChannelEntity,
    load: suspend (Long, Long) -> List<EpgProgrammeEntity>,
    loadDays: suspend () -> Int,
    loadDetails: suspend (Long) -> EpgProgrammeEntity?,
    menuOpen: Boolean,
    onProgramme: (EpgProgrammeEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var day by remember(channel.id) { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var days by remember { mutableIntStateOf(1) }
    var rows by remember(channel.id, day) { mutableStateOf<List<EpgProgrammeEntity>>(emptyList()) }
    var loading by remember(channel.id, day) { mutableStateOf(true) }
    var failed by remember(channel.id, day) { mutableStateOf(false) }
    var selected by remember(channel.id, day) { mutableIntStateOf(0) }
    val firstFocus = remember { FocusRequester() }
    val dayFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val detailsReader = remember(channel.id) { ScheduleProgrammeDetails(loadDetails) }
    val selectedRow = rows.getOrNull(selected)
    val fullDetails by produceState(selectedRow, selectedRow?.id, selectedRow?.startMs, selectedRow?.stopMs) {
        value = selectedRow
        selectedRow?.let { value = detailsReader.afterFocus(it) }
    }
    val selectedDetails = selectedRow?.let { row ->
        fullDetails?.takeIf { it.id == row.id && it.startMs == row.startMs && it.stopMs == row.stopMs }
    } ?: selectedRow
    val date = today.plusDays(day.toLong())
    val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val dateText = rememberBestDateFormatter("EEEEMMMMd")
    val timeText = rememberSystemTimeFormatter()
    LaunchedEffect(channel.id, day, revision) {
        loading = true; failed = false
        try {
            days = loadDays().coerceIn(1, 31)
            rows = load(from, to)
            val now = System.currentTimeMillis()
            selected = rows.indexOfFirst { it.stopMs > now }.coerceAtLeast(0)
            listState.scrollToItem(selected)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true; rows = emptyList() }
        finally { loading = false }
    }
    LaunchedEffect(loading, menuOpen) {
        if (!loading && !menuOpen) {
            withFrameNanos { }
            runCatching { if (rows.isNotEmpty()) firstFocus.requestFocus() else dayFocus.requestFocus() }
        }
    }
    StagePopup(
        title = stringResource(R.string.lounge_v2_full_schedule),
        eyebrow = ProviderTags.parse(channel.name).name,
        body = dateText(from), onDismiss = onDismiss, width = 1320.mpx, scroll = false,
        buttons = { StageButton(stringResource(R.string.content_close), onClick = onDismiss) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
            if (day > 0) StageButton(stringResource(R.string.lounge_v2_previous_day), onClick = { day-- })
            StageButton(dateText(from), onClick = { revision++ }, modifier = Modifier.focusRequester(dayFocus))
            if (day + 1 < days) StageButton(stringResource(R.string.lounge_v2_next_day), onClick = { day++ })
        }
        Row(Modifier.fillMaxWidth().height(470.mpx).padding(top = 18.mpx), horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
            Column(Modifier.weight(1f)) {
                if (loading) {
                    OwnTVSpinner(sizeDp = 28)
                    Text(stringResource(R.string.lounge_v2_loading), color = StageColors.Muted)
                } else if (rows.isEmpty()) {
                    Text(stringResource(if (failed) R.string.lounge_v2_schedule_error else R.string.lounge_v2_empty), color = StageColors.Muted)
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(rows, key = { _, p -> "${p.startMs}|${p.stopMs}|${p.title}" }) { i, p ->
                            StagePopupOption(title = p.title,
                                subtitle = stringResource(R.string.content_live_time_range_plain, timeText(p.startMs), timeText(p.stopMs)),
                                onClick = { onProgramme(selectedDetails?.takeIf { it.id == p.id && it.startMs == p.startMs } ?: p) },
                                modifier = Modifier.then(if (i == selected) Modifier.focusRequester(firstFocus) else Modifier)
                                    .onFocusChanged { if (it.hasFocus) selected = i },
                            )
                        }
                    }
                }
            }
            val p = selectedDetails
            Column(Modifier.width(300.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                ProgrammeArtwork(p, channel.displayLogoUrl, Modifier.width(180.mpx).height(260.mpx), channelName = channel.name)
                Text(p?.title.orEmpty(), style = stageText(22, 700), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(p?.description.orEmpty(), style = stageText(17, 400), color = StageColors.Muted, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
