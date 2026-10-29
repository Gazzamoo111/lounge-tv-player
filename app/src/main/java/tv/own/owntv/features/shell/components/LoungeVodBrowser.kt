@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.settings.SettingsRepository.SortMode
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.ui.components.LoungeBrowseFilter
import tv.own.owntv.ui.components.LoungeBrowseRules
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.loungeVodTitle
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** Editorial collections explicitly carry their full destination, not just sixteen preview IDs. */
internal data class LoungeCollection<T>(
    val id: String,
    val label: String,
    val key: LiveKey = LiveKey.All,
    val titles: List<T> = emptyList(),
    val filter: LoungeBrowseFilter = LoungeBrowseFilter(),
    val ranks: Map<Long, Int> = emptyMap(),
    val preview: Flow<List<T>>? = null,
)

/** Shared TV browser. Only discovery nests LazyRows; the library is one paged LazyVerticalGrid. */
@Composable
internal fun <T : Any> LoungeVodBrowser(
    series: Boolean,
    profileId: Long,
    category: LiveKey,
    categories: List<LiveRailItem>,
    collections: List<LoungeCollection<T>>,
    items: LazyPagingItems<T>,
    idOf: (T) -> Long,
    titleOf: (T) -> String,
    posterOf: (T) -> String?,
    progressOf: (T) -> Float?,
    hero: VodTitleInfo?,
    heroItem: T?,
    query: String,
    onQuery: (String) -> Unit,
    sort: SortMode,
    yearSort: Boolean,
    onYearSort: () -> Unit,
    onSort: (SortMode) -> Unit,
    onSelect: (LiveKey, LoungeBrowseFilter) -> Unit,
    onFocus: (T) -> Unit,
    onOpen: (T) -> Unit,
    onPlayHero: (T) -> Unit,
    onMenu: (T, Int) -> Unit,
    modalOpen: Boolean,
    restoreFocus: Boolean = false,
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val holder = rememberSaveableStateHolder()
    holder.SaveableStateProvider(profileId) {
        var library by rememberSaveable { mutableStateOf(category != LiveKey.All) }
        var collectionLabel by rememberSaveable { mutableStateOf<String?>(null) }
        var categorySheet by rememberSaveable { mutableStateOf(false) }
        var sortOpen by remember { mutableStateOf(false) }
        var focusEpoch by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        val entry = remember { FocusRequester() }
        val sortFocus = remember { FocusRequester() }
        val saved = rememberSaveableStateHolder()
        val section = stringResource(if (series) R.string.common_nav_series else R.string.common_nav_movies)
        val browseAll = stringResource(if (series) R.string.lounge_browse_all_series else R.string.lounge_browse_all_movies)
        val categoryLabel = categories.firstOrNull { it.key == category }?.title
            ?.let { ProviderTags.parse(it).name } ?: browseAll
        val route = if (library) "grid:$category:${collectionLabel.orEmpty()}" else "discovery"
        var focusBrowser by remember { mutableStateOf<(() -> Boolean)?>(null) }
        DisposableEffect(onEntryHook, route) {
            onEntryHook?.invoke { focusBrowser?.invoke() ?: runCatching { entry.requestFocus() }.getOrDefault(false) }
            onDispose { onEntryHook?.invoke(null) }
        }
        fun openCollection(collection: LoungeCollection<T>) {
            collectionLabel = collection.label
            library = true
            onQuery("")
            onSelect(collection.key, collection.filter)
            focusEpoch++
        }
        fun discovery() {
            library = false
            collectionLabel = null
            onQuery("")
            onSelect(LiveKey.All, LoungeBrowseFilter())
            focusEpoch++
        }
        // A modal owns Back. Category → discovery is one level, including sidebar entry.
        BackHandler(enabled = !modalOpen && (categorySheet || library)) {
            if (categorySheet) { categorySheet = false; focusEpoch++ } else discovery()
        }
        BoxWithConstraints(modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF080C14))) {
            val gutter = 42.mpx
            val columns = LoungeBrowseRules.columns((maxWidth / 1.mpx) - 84f)
            val gap = 24.mpx
            val posterWidth = (maxWidth - gutter * 2 - gap * (columns - 1)) / columns
            Column(Modifier.fillMaxSize().padding(top = 28.mpx)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = gutter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.mpx),
                ) {
                    Text(if (library) collectionLabel ?: categoryLabel else section,
                        color = StageColors.Text, style = stageText(32, 800), modifier = Modifier.weight(1f))
                    if (library) StageTool(text = section, icon = OwnTVIcon.LIST, onClick = { discovery() })
                    StageTool(text = browseAll, icon = OwnTVIcon.LIST,
                        modifier = Modifier.focusRequester(entry), onClick = {
                            openCollection(LoungeCollection("all", browseAll))
                        })
                    StageTool(text = stringResource(R.string.content_category_browser_title), icon = OwnTVIcon.LIST,
                        onClick = { categorySheet = true })
                }
                if (library) {
                    Row(Modifier.padding(horizontal = gutter, vertical = 14.mpx),
                        horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                        StageSearchField(query = query, onQueryChange = onQuery,
                            placeholder = stringResource(R.string.content_search_in, collectionLabel ?: categoryLabel),
                            modifier = Modifier.width(500.mpx))
                        if (yearSort) StageTool(text = stringResource(R.string.lounge_browse_year_sort), icon = OwnTVIcon.SORT,
                            modifier = Modifier.focusRequester(sortFocus), onClick = { sortOpen = true })
                        else VodSortTool(sort, Modifier.focusRequester(sortFocus)) { sortOpen = true }
                    }
                }
                saved.SaveableStateProvider(route) {
                    if (!library) {
                        val vertical = rememberLazyListState()
                        var lastRow by rememberSaveable { mutableStateOf<String?>(null) }
                        val rowRestorers = remember { mutableMapOf<String, () -> Boolean>() }
                        val headers = remember { mutableMapOf<String, FocusRequester>() }
                        val heroFocus = remember { FocusRequester() }
                        val restore: () -> Boolean = {
                            if (lastRow != null) lastRow?.let { rowRestorers[it]?.invoke() } == true
                            else runCatching { heroFocus.requestFocus() }.getOrDefault(false) ||
                                runCatching { entry.requestFocus() }.getOrDefault(false)
                        }
                        SideEffect { focusBrowser = restore }
                        LaunchedEffect(modalOpen, focusEpoch, restoreFocus) {
                            if (!modalOpen) {
                                repeat(16) {
                                    withFrameNanos { }
                                    if (restore()) return@LaunchedEffect
                                    kotlinx.coroutines.delay(50)
                                }
                                runCatching { entry.requestFocus() }
                            }
                        }
                        LazyColumn(state = vertical, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 20.mpx, bottom = 45.mpx),
                            verticalArrangement = Arrangement.spacedBy(28.mpx)) {
                            if (hero != null && heroItem != null) item("hero") {
                                Box(Modifier.fillMaxWidth().height(360.mpx)) {
                                    VodCinematicBackdrop(hero.backdropUrl, series, Modifier.fillMaxSize())
                                    LoungeBrowseSpotlight(hero, Modifier.padding(start = gutter).width(950.mpx).height(280.mpx))
                                    Row(Modifier.align(Alignment.BottomStart).padding(start = gutter, bottom = 10.mpx),
                                        horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                                        StageTool(text = stringResource(R.string.lounge_browse_play), icon = OwnTVIcon.PLAY_CIRCLE,
                                            modifier = Modifier.focusRequester(heroFocus), onClick = { onPlayHero(heroItem) })
                                        StageTool(text = stringResource(R.string.lounge_browse_info), icon = OwnTVIcon.INFO,
                                            onClick = { onOpen(heroItem) })
                                    }
                                }
                            }
                            items(collections, key = { it.id }, contentType = { "collection" }) { collection ->
                                val loaded by produceState<List<T>?>(null, collection.preview) {
                                    collection.preview?.collect { value = it }
                                }
                                val titles = (loaded ?: collection.titles).distinctBy(idOf).take(LoungeBrowseRules.PREVIEW_LIMIT)
                                // Waiting rows keep their header: there is always a usable destination.
                                if ((collection.preview == null && titles.isNotEmpty()) || (collection.preview != null && (loaded == null || titles.size >= 10))) {
                                    val header = remember { FocusRequester() }
                                    DisposableEffect(collection.id) {
                                        headers[collection.id] = header
                                        onDispose { headers.remove(collection.id) }
                                    }
                                    LoungeDiscoveryRow(collection, titles, idOf, titleOf, posterOf, series,
                                        posterWidth, gutter, header,
                                        onFocus = { lastRow = collection.id; onFocus(it) },
                                        onHeaderFocus = { lastRow = collection.id },
                                        register = { callback -> if (callback == null) rowRestorers.remove(collection.id) else rowRestorers[collection.id] = callback },
                                        onOpen = onOpen, onAll = { openCollection(collection) },
                                        onLeft = { categorySheet = true },
                                        onNext = {
                                            val next = collections.indexOfFirst { it.id == collection.id } + 1
                                            if (next in collections.indices) scope.launch {
                                                vertical.animateScrollToItem(next + if (hero != null && heroItem != null) 1 else 0)
                                                withFrameNanos { }
                                                headers[collections[next].id]?.let { runCatching { it.requestFocus() } }
                                            }
                                        })
                                }
                            }
                        }
                    } else {
                        val grid = rememberLazyGridState()
                        var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
                        var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
                        val requesters = remember { mutableMapOf<Long, FocusRequester>() }
                        val restore: () -> Boolean = {
                            selectedId?.let { requesters[it] }?.let { runCatching { it.requestFocus() }.getOrDefault(false) }
                                ?: false
                        }
                        SideEffect { focusBrowser = restore }
                        LaunchedEffect(modalOpen, focusEpoch, restoreFocus, items.loadState.refresh) {
                            if (!modalOpen && items.loadState.refresh is LoadState.NotLoading && items.itemCount > 0) {
                                val ids = items.itemSnapshotList.map { it?.let(idOf) }
                                val index = LoungeBrowseRules.restoreIndex(ids, selectedId, selectedIndex)
                                if (grid.layoutInfo.visibleItemsInfo.none { it.index == index }) grid.scrollToItem(index)
                                repeat(8) {
                                    withFrameNanos { }
                                    val id = items.peek(index)?.let(idOf)
                                    if (id != null && runCatching { requesters[id]?.requestFocus() == true }.getOrDefault(false)) return@LaunchedEffect
                                }
                            }
                        }
                        var previousSort by rememberSaveable { mutableStateOf(sort.name) }
                        var previousQuery by rememberSaveable { mutableStateOf(query) }
                        var previousYearSort by rememberSaveable { mutableStateOf(yearSort) }
                        LaunchedEffect(sort, query, yearSort) {
                            if (previousSort != sort.name || previousQuery != query || previousYearSort != yearSort) {
                                selectedId = null; selectedIndex = 0
                                grid.scrollToItem(0)
                                previousSort = sort.name; previousQuery = query; previousYearSort = yearSort
                            }
                        }
                        LazyVerticalGrid(columns = GridCells.Fixed(columns), state = grid,
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(28.mpx),
                            contentPadding = PaddingValues(start = gutter, end = gutter, top = 22.mpx, bottom = 48.mpx),
                            modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(count = items.itemCount, key = items.itemKey(idOf), contentType = { "poster" }) { index ->
                                val item = items[index]
                                if (item == null) {
                                    Box(Modifier.width(posterWidth).height(posterWidth * 1.5f))
                                } else {
                                    val id = idOf(item)
                                    val focus = remember(id) { FocusRequester() }
                                    DisposableEffect(id) {
                                        requesters[id] = focus
                                        onDispose { requesters.remove(id) }
                                    }
                                    StagePoster(title = loungeVodTitle(titleOf(item)), width = posterWidth, height = posterWidth * 1.5f,
                                        progress = progressOf(item), onClick = { onOpen(item) }, onLongClick = { onMenu(item, index) },
                                        modifier = Modifier.focusRequester(focus).onFocusChanged {
                                            if (it.isFocused) { selectedId = id; selectedIndex = index; onFocus(item) }
                                        }.onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                                                Key.DirectionLeft -> if (index % columns == 0) { categorySheet = true; true } else false
                                                Key.DirectionRight -> index % columns == columns - 1 || index == items.itemCount - 1
                                                Key.DirectionUp -> if (index < columns) { runCatching { sortFocus.requestFocus() }; true } else false
                                                Key.DirectionDown -> if (index + columns >= items.itemCount && items.loadState.append.endOfPaginationReached) true else false
                                                Key.Menu -> { onMenu(item, index); true }
                                                else -> false
                                            }
                                        }) { VodPosterArt(posterOf(item), if (series) OwnTVIcon.SERIES else OwnTVIcon.MOVIES) }
                                }
                            }
                        }
                        if (items.loadState.refresh is LoadState.Error || items.loadState.append is LoadState.Error) {
                            StageTool(text = stringResource(R.string.lounge_browse_retry), icon = OwnTVIcon.REFRESH, onClick = { items.retry() })
                        } else if (items.itemCount == 0) {
                            Text(stringResource(if (items.loadState.refresh is LoadState.Loading) R.string.lounge_browse_loading else R.string.lounge_browse_empty),
                                color = StageColors.Muted, style = stageText(22, 500), modifier = Modifier.padding(gutter))
                        }
                    }
                }
            }
            if (categorySheet) {
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopStart,
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                    onDismissRequest = { categorySheet = false; focusEpoch++ },
                ) {
                LazyColumn(Modifier.fillMaxHeight().width(560.mpx).background(androidx.compose.ui.graphics.Color(0xFF080C14)).padding(30.mpx).focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                    item("discovery") {
                        StageTool(text = section, icon = OwnTVIcon.LIST, modifier = Modifier.focusRequester(first), onClick = {
                            categorySheet = false; discovery()
                        })
                    }
                    items(categories, key = { it.key.toString() }) { item ->
                        StageTool(text = if (item.key == LiveKey.All) browseAll else ProviderTags.parse(item.title.orEmpty()).name,
                            icon = OwnTVIcon.LIST, onClick = {
                                categorySheet = false
                                openCollection(LoungeCollection("category:${item.key}", if (item.key == LiveKey.All) browseAll else ProviderTags.parse(item.title.orEmpty()).name, item.key))
                            })
                    }
                }
                }
            }
        }
        if (sortOpen) VodSortMenu(series, sort, 800f, 160.mpx,
            onPick = { onSort(it); focusEpoch++ }, onDismiss = { sortOpen = false; focusEpoch++ },
            yearSelected = yearSort,
            onYear = onYearSort.takeIf { category == LiveKey.All || category is LiveKey.Folder })
    }
}

@Composable
private fun <T> LoungeDiscoveryRow(
    collection: LoungeCollection<T>, titles: List<T>, idOf: (T) -> Long, titleOf: (T) -> String,
    posterOf: (T) -> String?, series: Boolean, width: androidx.compose.ui.unit.Dp,
    gutter: androidx.compose.ui.unit.Dp, header: FocusRequester,
    onFocus: (T) -> Unit, onHeaderFocus: () -> Unit,
    register: ((() -> Boolean)?) -> Unit, onOpen: (T) -> Unit, onAll: () -> Unit,
    onLeft: () -> Unit, onNext: () -> Unit,
) {
    val horizontal = rememberLazyListState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var headerSelected by rememberSaveable { mutableStateOf(false) }
    val requesters = remember { mutableMapOf<Long, FocusRequester>() }
    val endFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val restore: () -> Boolean = {
        if (headerSelected) runCatching { header.requestFocus() }.getOrDefault(false)
        else if (selectedIndex == titles.size) runCatching { endFocus.requestFocus() }.getOrDefault(false)
        else selectedId?.let { requesters[it] }?.let { runCatching { it.requestFocus() }.getOrDefault(false) }
            ?: runCatching { header.requestFocus() }.getOrDefault(false)
    }
    DisposableEffect(restore) { register(restore); onDispose { register(null) } }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = gutter), verticalAlignment = Alignment.CenterVertically) {
            Text(collection.label, color = StageColors.Text, style = stageText(27, 800), modifier = Modifier.weight(1f))
            StageTool(text = stringResource(R.string.lounge_browse_view_all), icon = OwnTVIcon.CHEVRON,
                onClick = onAll, modifier = Modifier.focusRequester(header).onFocusChanged {
                    if (it.isFocused) { headerSelected = true; onHeaderFocus() }
                }.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        scope.launch {
                            horizontal.scrollToItem(selectedIndex.coerceAtMost(titles.size))
                            withFrameNanos { }
                            val target = titles.getOrNull(selectedIndex)?.let(idOf)?.let(requesters::get) ?: endFocus
                            runCatching { target.requestFocus() }
                        }
                        true
                    } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) { onLeft(); true } else false
                })
        }
        LazyRow(state = horizontal, contentPadding = PaddingValues(start = gutter, end = gutter, top = 22.mpx, bottom = 22.mpx),
            horizontalArrangement = Arrangement.spacedBy(24.mpx), modifier = Modifier.fillMaxWidth()) {
            items(titles, key = idOf, contentType = { "poster" }) { item ->
                val id = idOf(item)
                val focus = remember(id) { FocusRequester() }
                DisposableEffect(id) { requesters[id] = focus; onDispose { requesters.remove(id) } }
                LoungeRankedPoster(loungeVodTitle(titleOf(item)), posterOf(item), width, collection.ranks[id], series,
                    onClick = { onOpen(item) }, modifier = Modifier.focusRequester(focus).onFocusChanged {
                        if (it.isFocused) {
                            selectedId = id; selectedIndex = titles.indexOf(item); headerSelected = false; onFocus(item)
                        }
                    }.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                            Key.DirectionUp -> { runCatching { header.requestFocus() }; true }
                            Key.DirectionDown -> { onNext(); true }
                            Key.DirectionLeft -> if (selectedIndex == 0) { onLeft(); true } else false
                            else -> false
                        }
                    })
            }
            item("view-all", contentType = "view-all") {
                StagePoster(title = stringResource(R.string.lounge_browse_view_all), onClick = onAll, width = width, height = width * 1.5f,
                    modifier = Modifier.focusRequester(endFocus).onFocusChanged {
                        if (it.isFocused) { selectedId = null; selectedIndex = titles.size; headerSelected = false; onHeaderFocus() }
                    }.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                            Key.DirectionUp -> { runCatching { header.requestFocus() }; true }
                            Key.DirectionDown -> { onNext(); true }
                            Key.DirectionRight -> true
                            else -> false
                        }
                    }) {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF080C14)), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.lounge_browse_view_all), color = StageColors.Text, style = stageText(22, 700))
                    }
                }
            }
        }
    }
}
