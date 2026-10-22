package tv.own.owntv.features.home

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.TrendingDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.home.GuideSliceState
import tv.own.owntv.core.home.HeroItem
import tv.own.owntv.core.home.HomeFeedReader
import tv.own.owntv.core.home.TrendingHomeItem
import tv.own.owntv.core.home.homeKey
import tv.own.owntv.core.launcher.LauncherContinuationItem
import tv.own.owntv.core.model.HomeConfig
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.AutoRefresh
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.player.HeroPreviewEngine

@Immutable
data class HomeHeroMetadata(
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val plot: String? = null,
)

/**
 * What the Trending hero's info line and buttons need beyond the snapshot: TMDB genres (empty until the
 * cached details are read, or when TMDB has none) and how many entries of the same title the active
 * playlists hold ("All versions 3").
 */
@Immutable
data class TrendingExtras(
    val genres: List<String> = emptyList(),
    val versions: Int = 1,
    /** TMDB title art, shown instead of the text title when there is one (as the films/series hero). */
    val logoUrl: String? = null,
)

@Immutable
data class TrendingDetailsMetadata(
    val cache: MetadataCacheEntity?,
    val tmdbWins: Boolean,
)

@Immutable
sealed interface CatalogueFeature {
    data class Movie(val movie: MovieEntity) : CatalogueFeature

    data class Series(val series: SeriesEntity) : CatalogueFeature

    data class Live(
        val channel: ChannelEntity,
        val programme: EpgProgrammeEntity?,
    ) : CatalogueFeature
}

internal enum class CatalogueFeatureKind { MOVIE, SERIES, LIVE }

internal data class CatalogueFeatureCandidate(
    val kind: CatalogueFeatureKind,
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val hasBackdrop: Boolean,
    val hasArtwork: Boolean,
)

/** Provider artwork wins, then provider order/name keeps the same fresh profile deterministic. */
internal fun chooseCatalogueFeatureCandidate(candidates: List<CatalogueFeatureCandidate>): CatalogueFeatureCandidate? =
    candidates.minWithOrNull(
        compareByDescending<CatalogueFeatureCandidate> { it.hasBackdrop }
            .thenByDescending { it.hasArtwork }
            .thenBy { it.sortOrder }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.kind.ordinal }
            .thenBy { it.id },
    )

private data class CatalogueObservationKey(
    val profileId: Long,
    val liveSourceIds: List<Long>,
    val movieSourceIds: List<Long>,
    val seriesSourceIds: List<Long>,
)

@Immutable
data class HomeUiState(
    val trendingItems: List<TrendingHomeItem> = emptyList(),
    val activeTrendingIndex: Int = 0,
    val trendingPreferredLanguage: String = "EN",
    val trendingSeasonCounts: Map<Long, Int> = emptyMap(),
    /** Keyed by [TrendingHomeItem.stableKey]; filled for the title on screen. */
    val trendingExtras: Map<String, TrendingExtras> = emptyMap(),
    val heroItems: List<HeroItem> = emptyList(),
    val activeHeroIndex: Int = 0,
    val continueMovies: List<LauncherContinuationItem> = emptyList(),
    val continueSeries: List<LauncherContinuationItem> = emptyList(),
    val heroMetadata: Map<String, HomeHeroMetadata> = emptyMap(),
    val recentLive: List<ChannelEntity> = emptyList(),
    val favoriteLive: List<ChannelEntity> = emptyList(),
    val favoriteMovies: List<MovieEntity> = emptyList(),
    val favoriteSeries: List<SeriesEntity> = emptyList(),
    val catalogueMovies: List<MovieEntity> = emptyList(),
    val catalogueSeries: List<SeriesEntity> = emptyList(),
    val catalogueLive: List<ChannelEntity> = emptyList(),
    val catalogueFeature: CatalogueFeature? = null,
    val config: HomeConfig = HomeConfig(),
    val recentGuide: GuideSliceState = GuideSliceState(),
    val favoriteGuide: GuideSliceState = GuideSliceState(),
    /**
     * True until the first [HomeViewModel.loadHomeData] completes. Home's queries are profile-scoped and
     * already indexed, but on a cold boot their first reads come off slow eMMC (pages not yet in the OS
     * page cache) — that's the ~half-second gap between the shell painting and `home-data`. While that
     * runs Home draws nothing over the background rather than flashing the empty state (which looks wrong
     * for a user who does have history). Flips to false the moment real data publishes, and stays false
     * thereafter (refreshes don't blank it again).
     */
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

/** What the shared top-bar Continue chip points at. */
enum class ContinueKind { LIVE, MOVIE, EPISODE }
enum class ContinueAction { RESUME, PLAY, NEXT_UP, LAST_CHANNEL }

/** A single resumable target: semantic action and display name. */
@Immutable
data class ContinueTarget(
    val kind: ContinueKind,
    /** Display name (movie/series/channel). */
    val name: String,
    val action: ContinueAction,
    val channelId: Long = -1L,
    val movieId: Long = -1L,
    val seriesId: Long = -1L,
    val episodeId: Long = -1L,
    val positionMs: Long = 0L,
)

class HomeViewModel(
    private val feed: HomeFeedReader,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val categoryDao: CategoryDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    private val heroPreviewEngine: HeroPreviewEngine,
    private val historyDao: tv.own.owntv.core.database.dao.HistoryDao,
    private val progressDao: tv.own.owntv.core.database.dao.ProgressDao,
    private val metadata: MetadataRepository,
    private val trendingDao: TrendingDao,
    private val favoriteDao: tv.own.owntv.core.database.dao.FavoriteDao,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val catalogSyncScheduler: CatalogSyncScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Single entry point for "rebuild the Home rails". Every trigger goes through here rather than
     * calling loadHomeData directly, because there is more than one of them and on a cold start they
     * all fire at once: the trending-table observer below gets Room's immediate first emission, and the
     * shell runs its own refresh as soon as Home is the selected section. Measured on a TCL, that put
     * two full loads — ~15 dependent queries each — on the database concurrently at the single most
     * contended moment of launch, and the two completion stamps landed 6ms apart, so the second one was
     * pure duplicated work. Conflating buffer + debounce collapses that burst into one load, and
     * collectLatest keeps a genuine post-sync invalidation from queueing behind a stale in-flight pass.
     */
    private val reloadRequests = MutableSharedFlow<Long?>(
        replay = 1, // the first request is emitted before the collector below is running — don't lose it
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        viewModelScope.launch {
            reloadRequests.collectLatest { known ->
                (known ?: currentProfileId())?.let { profileId ->
                    loadHomeData(profileId)
                }
            }
        }
        // Room invalidates this after the worker atomically replaces a snapshot, so Home updates even
        // when the user stays on the screen throughout a background post-sync refresh.
        //
        // drop(1) discards Room's *initial* emission, which carries no news: it fires the moment this
        // flow is collected, and loadHomeData reads the trending table itself anyway, so acting on it
        // only duplicated the load the shell already asks for when Home becomes the selected section.
        // On a cold start that duplicate was the single largest delay on the screen — the two triggers
        // arrived hundreds of milliseconds apart, so no amount of coalescing could merge them without
        // holding the first one back. Only genuine post-first invalidations reach here now.
        viewModelScope.launch {
            trendingDao.observeAllItems().drop(1).collect {
                reloadRequests.emit(null)
            }
        }
    }

    // --- The single most-recent resumable item, for the shared top-bar Continue chip. ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val continueTarget: StateFlow<ContinueTarget?> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(null)
            else historyDao.observeMostRecent(pid).map { h -> h?.let { resolveContinue(pid, it) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun resolveContinue(
        pid: Long,
        h: tv.own.owntv.core.database.entity.WatchHistoryEntity,
    ): ContinueTarget? = when (h.mediaType) {
        MediaType.MOVIE -> movieDao.getById(h.itemId)?.let { m ->
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, m.categoryId, profileDao, categoryDao)) return@let null
            val pos = progressDao.get(pid, MediaType.MOVIE, m.id)?.positionMs ?: 0L
            ContinueTarget(ContinueKind.MOVIE, m.name, if (pos > 0) ContinueAction.RESUME else ContinueAction.PLAY, movieId = m.id, positionMs = pos)
        }
        MediaType.EPISODE -> seriesDao.getEpisodeById(h.itemId)?.let { ep ->
            seriesDao.getSeriesById(ep.seriesId)?.let { s ->
                if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, s.categoryId, profileDao, categoryDao)) return@let null
                val pos = progressDao.get(pid, MediaType.EPISODE, ep.id)?.positionMs ?: 0L
                ContinueTarget(ContinueKind.EPISODE, s.name, if (pos > 0) ContinueAction.RESUME else ContinueAction.NEXT_UP, seriesId = ep.seriesId, episodeId = ep.id, positionMs = pos)
            }
        }
        MediaType.LIVE -> channelDao.getById(h.itemId)?.let { c ->
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, c.categoryId, profileDao, categoryDao)) return@let null
            ContinueTarget(ContinueKind.LIVE, c.name, ContinueAction.LAST_CHANNEL, channelId = c.id)
        }
        else -> null
    }

    private val _heroFocused = MutableStateFlow(false)
    private val _previewEnabled = MutableStateFlow(true)
    private val _lastHeroInteractionMs = MutableStateFlow(0L)
    private val resolvingHeroKeys = mutableSetOf<String>()
    private val recoveryAttemptedSources = mutableSetOf<Long>()
    private var catalogueObserver: Job? = null
    private var observedCatalogueKey: CatalogueObservationKey? = null

    val lastHeroInteractionMs: StateFlow<Long> = _lastHeroInteractionMs.asStateFlow()

    val isPreviewActive: StateFlow<Boolean> =
        combine(_heroFocused, _previewEnabled, settings.heroPreviewEnabled, _uiState) { focused, enabled, setting, state ->
            focused && enabled && setting && state.heroItems.isNotEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setPreviewEnabled(enabled: Boolean) {
        _previewEnabled.value = enabled
    }

    fun setHeroFocused(focused: Boolean) {
        _heroFocused.value = focused
    }

    fun navigateHero(index: Int) {
        val items = _uiState.value.heroItems
        if (index !in items.indices) return
        _uiState.value = _uiState.value.copy(activeHeroIndex = index)
    }

    fun navigateTrending(index: Int) {
        val items = _uiState.value.trendingItems
        if (index !in items.indices) return
        _uiState.value = _uiState.value.copy(activeTrendingIndex = index)
        resolveTrendingExtras(items[index])
    }

    /** Favourite ids per type, for the hero's ♥ (the same favourites Movies and Series show). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun favoriteIds(type: MediaType): StateFlow<Set<Long>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else favoriteDao.observeFavoriteIds(pid, type) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val favoriteMovieIds: StateFlow<Set<Long>> = favoriteIds(MediaType.MOVIE)
    val favoriteSeriesIds: StateFlow<Set<Long>> = favoriteIds(MediaType.SERIES)

    fun toggleTrendingFavorite(item: TrendingHomeItem) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val (type, id, favs) = when (item) {
                is TrendingHomeItem.Movie -> Triple(MediaType.MOVIE, item.movie.id, favoriteMovieIds.value)
                is TrendingHomeItem.Series -> Triple(MediaType.SERIES, item.series.id, favoriteSeriesIds.value)
            }
            if (id in favs) {
                userDataWriter.removeFavorite(pid, type, id)
            } else {
                favoriteDao.add(tv.own.owntv.core.database.entity.FavoriteEntity(profileId = pid, mediaType = type, itemId = id))
            }
        }
    }

    private val resolvingTrendingKeys = mutableSetOf<String>()

    /** Genres and title art from the cached TMDB details, and the count of same-title entries in the active playlists. */
    private fun resolveTrendingExtras(item: TrendingHomeItem) {
        val key = item.stableKey
        if (_uiState.value.trendingExtras.containsKey(key) || !resolvingTrendingKeys.add(key)) return
        viewModelScope.launch {
            try {
                val extras = withContext(Dispatchers.IO) {
                    val cache = runCatching { resolveTrendingDetails(item).cache }.getOrNull()
                    val genres = cache?.genresJson
                        ?.let { json -> runCatching { org.json.JSONArray(json) }.getOrNull() }
                        ?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }
                        .orEmpty()
                    TrendingExtras(
                        genres = genres,
                        versions = runCatching { versionCount(item) }.getOrDefault(1),
                        logoUrl = MetadataImages.logo(cache?.logoPath),
                    )
                }
                _uiState.value = _uiState.value.copy(trendingExtras = _uiState.value.trendingExtras + (key to extras))
            } finally {
                resolvingTrendingKeys.remove(key)
            }
        }
    }

    /**
     * Entries of the same title (same title signature, the year within one where both have one) across
     * the playlists the section is showing — what "All versions" then lists.
     */
    private suspend fun versionCount(item: TrendingHomeItem): Int {
        val pid = currentProfileId() ?: return 1
        val (type, signature, year) = when (item) {
            is TrendingHomeItem.Movie -> Triple(MediaType.MOVIE, item.movie.titleSignature, item.movie.parsedYear)
            is TrendingHomeItem.Series -> Triple(MediaType.SERIES, item.series.titleSignature, item.series.parsedYear)
        }
        if (signature.isBlank()) return 1
        val sources = tv.own.owntv.core.repository.activeSourceIds(settings, sourceDao, pid, type)
        val rows = sources.flatMap { sid ->
            when (type) {
                MediaType.MOVIE -> movieDao.trendingExact(sid, listOf(signature))
                else -> seriesDao.trendingExact(sid, listOf(signature))
            }
        }
        return rows.count { row -> year == null || row.parsedYear == null || kotlin.math.abs(row.parsedYear!! - year) <= 1 }
            .coerceAtLeast(1)
    }

    /** Re-resolve the exact saved provider row immediately before an action in case a sync replaced it. */
    suspend fun revalidateTrendingItem(item: TrendingHomeItem): TrendingHomeItem? = withContext(Dispatchers.IO) {
        when (item) {
            is TrendingHomeItem.Movie -> movieDao.getById(item.movie.id)
                ?.takeIf { it.sourceId == item.snapshot.sourceId }
                ?.let { item.copy(movie = it) }
            is TrendingHomeItem.Series -> seriesDao.getSeriesById(item.series.id)
                ?.takeIf { it.sourceId == item.snapshot.sourceId }
                ?.let { item.copy(series = it) }
        }
    }

    /** Load the same full cached TMDB payload used by Movies/Series details, using Trending's exact id. */
    suspend fun resolveTrendingDetails(item: TrendingHomeItem): TrendingDetailsMetadata = withContext(Dispatchers.IO) {
        val config = settings.metadataConfig()
        val cache = when (item) {
            is TrendingHomeItem.Movie -> metadata.resolveKnownMovie(item.movie, item.snapshot.tmdbId)
            is TrendingHomeItem.Series -> metadata.resolveKnownSeries(item.series, item.snapshot.tmdbId)
        }
        TrendingDetailsMetadata(cache = cache, tmdbWins = config.mode.tmdbWins)
    }

    fun onHeroUserNavigate(index: Int) {
        _lastHeroInteractionMs.value = System.currentTimeMillis()
        navigateHero(index)
        resolveHeroMetadata(index)
    }

    fun stopPreview() {
        heroPreviewEngine.stop()
    }

    /**
     * Start the hero preview for [hero] with the SAME request identity the player would use: the
     * playlist's User-Agent plus the item's own headers. Without them a source that needs a custom UA or
     * a Referer had a home screen that 403'd on every preview while the item itself played fine.
     */
    suspend fun startPreview(hero: HeroItem) {
        val source = withContext(Dispatchers.IO) {
            runCatching { sourceDao.getById(hero.sourceId) }.getOrNull()
        }
        heroPreviewEngine.play(
            hero.streamUrl, hero.seekToMs, source?.userAgent,
            tv.own.owntv.core.settings.SourceOverrides.headersWithReferer(hero.httpHeaders, source),
        )
    }

    /**
     * [profileId] lets a caller that already holds a *validated* active profile skip the lookup this
     * would otherwise do — a settings read plus a database round-trip that measured ~99ms on a TCL,
     * spent while the rails are still blank. The shell qualifies: it only composes once the launch
     * gate has confirmed the active id against Room's profile list. Everyone else passes nothing and
     * gets the lookup.
     */
    fun refresh(profileId: Long? = null) {
        if (_uiState.value.loadFailed) {
            recoveryAttemptedSources.clear()
            _uiState.value = _uiState.value.copy(isLoading = true, loadFailed = false)
        }
        reloadRequests.tryEmit(profileId?.takeIf { it >= 0 })
    }

    /**
     * The rails themselves are core's — the phone app builds the same feed from the same reader. What
     * stays here is what only a television does with it: the caches keyed to the previous pass, and the
     * hero carousel's position.
     */
    private suspend fun loadHomeData(profileId: Long) {
        val previous = _uiState.value
        try {
            val liveSourceIds = tv.own.owntv.core.repository.activeSourceIds(settings, sourceDao, profileId, MediaType.LIVE)
            val movieSourceIds = tv.own.owntv.core.repository.activeSourceIds(settings, sourceDao, profileId, MediaType.MOVIE)
            val seriesSourceIds = tv.own.owntv.core.repository.activeSourceIds(settings, sourceDao, profileId, MediaType.SERIES)
            val data = feed.load(profileId)
            val catalogueMovies = visibleMovies(profileId, movieSourceIds)
            val catalogueSeries = visibleSeries(profileId, seriesSourceIds)
            val catalogueLive = visibleChannels(profileId, liveSourceIds)
            val feature = chooseCatalogueFeature(catalogueMovies, catalogueSeries, catalogueLive)
            _uiState.value = HomeUiState(
            trendingItems = data.trendingItems,
            activeTrendingIndex = previous.activeTrendingIndex
                .coerceIn(0, (data.trendingItems.size - 1).coerceAtLeast(0)),
            trendingPreferredLanguage = data.trendingPreferredLanguage,
            trendingSeasonCounts = data.trendingSeasonCounts,
            trendingExtras = previous.trendingExtras.filterKeys { key -> data.trendingItems.any { it.stableKey == key } },
            heroItems = data.heroItems,
            activeHeroIndex = 0,
            continueMovies = data.continueMovies,
            continueSeries = data.continueSeries,
            heroMetadata = previous.heroMetadata.filterKeys { key -> data.heroItems.any { it.homeKey == key } },
            recentLive = data.recentLive,
            favoriteLive = data.favoriteLive,
            favoriteMovies = data.favoriteMovies,
            favoriteSeries = data.favoriteSeries,
            catalogueMovies = catalogueMovies,
            catalogueSeries = catalogueSeries,
            catalogueLive = catalogueLive,
            catalogueFeature = feature,
            config = data.config,
            recentGuide = data.recentGuide,
            favoriteGuide = data.favoriteGuide,
            isLoading = false,
            loadFailed = false,
            )
            observeCatalogue(profileId, liveSourceIds, movieSourceIds, seriesSourceIds)
            recoverMissingCatalogue(profileId)
            tv.own.owntv.core.util.Perf.stamp("home-data")
            _uiState.value.trendingItems.getOrNull(_uiState.value.activeTrendingIndex)?.let(::resolveTrendingExtras)
            // Keep watching shows every card as a still, so each needs its artwork up front, not on focus.
            _uiState.value.heroItems.indices.forEach { resolveHeroMetadata(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Log.w(HomeViewModel::class.java.simpleName, failure)
            _uiState.value = previous.copy(isLoading = false, loadFailed = true)
        }
    }

    private suspend fun visibleMovies(profileId: Long, sourceIds: List<Long>): List<MovieEntity> =
        progressivelyFilter(
            sourceIds = sourceIds,
            load = { limit -> movieDao.searchList(String(), sourceIds, limit) },
            allowed = { item -> tv.own.owntv.core.content.AdultCategoryClassifier.allows(profileId, item.categoryId, profileDao, categoryDao) },
        )

    private suspend fun visibleSeries(profileId: Long, sourceIds: List<Long>): List<SeriesEntity> =
        progressivelyFilter(
            sourceIds = sourceIds,
            load = { limit -> seriesDao.searchList(String(), sourceIds, limit) },
            allowed = { item -> tv.own.owntv.core.content.AdultCategoryClassifier.allows(profileId, item.categoryId, profileDao, categoryDao) },
        )

    private suspend fun visibleChannels(profileId: Long, sourceIds: List<Long>): List<ChannelEntity> =
        progressivelyFilter(
            sourceIds = sourceIds,
            load = { limit -> channelDao.snapshotAll(sourceIds, limit) },
            allowed = { item -> tv.own.owntv.core.content.AdultCategoryClassifier.allows(profileId, item.categoryId, profileDao, categoryDao) },
        )

    /** Keep widening the bounded DAO window until enough profile-visible entries are found or exhausted. */
    private suspend fun <T> progressivelyFilter(
        sourceIds: List<Long>,
        load: suspend (Int) -> List<T>,
        allowed: suspend (T) -> Boolean,
    ): List<T> {
        if (sourceIds.isEmpty()) return emptyList()
        var limit = CATALOGUE_LIMIT * 2
        while (true) {
            val loaded = load(limit)
            val visible = loaded.filter { allowed(it) }
            if (visible.size >= CATALOGUE_LIMIT || loaded.size < limit || limit >= CATALOGUE_SCAN_LIMIT) {
                return visible.take(CATALOGUE_LIMIT)
            }
            limit = (limit * 2).coerceAtMost(CATALOGUE_SCAN_LIMIT)
        }
    }

    private suspend fun chooseCatalogueFeature(
        movies: List<MovieEntity>,
        series: List<SeriesEntity>,
        channels: List<ChannelEntity>,
    ): CatalogueFeature? {
        val candidate = chooseCatalogueFeatureCandidate(
            movies.map { CatalogueFeatureCandidate(CatalogueFeatureKind.MOVIE, it.id, it.name, it.sortOrder, !it.backdropUrl.isNullOrBlank(), !it.posterUrl.isNullOrBlank()) } +
                series.map { CatalogueFeatureCandidate(CatalogueFeatureKind.SERIES, it.id, it.name, it.sortOrder, !it.backdropUrl.isNullOrBlank(), !it.posterUrl.isNullOrBlank()) } +
                channels.map { CatalogueFeatureCandidate(CatalogueFeatureKind.LIVE, it.id, it.name, it.sortOrder, false, !it.logoUrl.isNullOrBlank()) },
        ) ?: return null
        return when (candidate.kind) {
            CatalogueFeatureKind.MOVIE -> CatalogueFeature.Movie(movies.first { it.id == candidate.id })
            CatalogueFeatureKind.SERIES -> CatalogueFeature.Series(series.first { it.id == candidate.id })
            CatalogueFeatureKind.LIVE -> {
                val channel = channels.first { it.id == candidate.id }
                val programme = channel.epgChannelId?.takeIf(String::isNotBlank)?.let { epgId ->
                    runCatching { epgDao.nowPlaying(epgId, System.currentTimeMillis()) }.getOrNull()
                }
                CatalogueFeature.Live(channel, programme)
            }
        }
    }

    private fun observeCatalogue(
        profileId: Long,
        liveSourceIds: List<Long>,
        movieSourceIds: List<Long>,
        seriesSourceIds: List<Long>,
    ) {
        val key = CatalogueObservationKey(profileId, liveSourceIds.sorted(), movieSourceIds.sorted(), seriesSourceIds.sorted())
        if (key == observedCatalogueKey) return
        observedCatalogueKey = key
        catalogueObserver?.cancel()
        catalogueObserver = viewModelScope.launch {
            combine(
                channelDao.countAll(liveSourceIds),
                movieDao.countAll(movieSourceIds),
                seriesDao.countAll(seriesSourceIds),
            ) { live, movies, series -> Triple(live, movies, series) }
                .drop(1)
                .collect { reloadRequests.emit(profileId) }
        }
    }

    private fun recoverMissingCatalogue(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                sourceDao.sourceIdsForProfile(profileId).forEach { sourceId ->
                    val source = sourceDao.getById(sourceId) ?: return@forEach
                    val missingEnabledContent =
                        (source.syncLive && channelDao.countForSourceOnce(sourceId) == 0) ||
                            (source.syncMovies && movieDao.countForSourceOnce(sourceId) == 0) ||
                            (source.syncSeries && seriesDao.countForSourceOnce(sourceId) == 0)
                    if (missingEnabledContent && sourceId !in recoveryAttemptedSources) {
                        catalogSyncScheduler.enqueueSync(
                            sourceId = sourceId,
                            reason = AutoRefresh.REASON,
                            policy = ExistingWorkPolicy.KEEP,
                        )
                        recoveryAttemptedSources.add(sourceId)
                    }
                }
            }.onFailure { Log.w(HomeViewModel::class.java.simpleName, it) }
        }
    }

    private fun resolveHeroMetadata(index: Int) {
        val item = _uiState.value.heroItems.getOrNull(index) ?: return
        if (item is HeroItem.LiveHero) return

        val key = item.homeKey
        if (_uiState.value.heroMetadata.containsKey(key)) return
        if (!resolvingHeroKeys.add(key)) return

        viewModelScope.launch {
            try {
                // Off the cold-start path: the rails paint first, the stills fill in after.
                delay(250)
                if (_uiState.value.heroItems.none { it.homeKey == key }) return@launch

                val resolved = withContext(Dispatchers.IO) { heroMetadata(item) } ?: return@launch
                _uiState.value = _uiState.value.copy(
                    heroMetadata = _uiState.value.heroMetadata + (key to resolved),
                )
            } finally {
                resolvingHeroKeys.remove(key)
            }
        }
    }

    private suspend fun heroMetadata(item: HeroItem): HomeHeroMetadata? = when (item) {
        is HeroItem.MovieHero -> metadata.resolveMovie(item.movie)?.let { cache ->
            HomeHeroMetadata(
                backdropUrl = MetadataImages.backdrop(cache.backdropPath, size = "w1280"),
                logoUrl = MetadataImages.logo(cache.logoPath),
                plot = cache.overview?.takeIf { it.isNotBlank() },
            )
        }
        is HeroItem.SeriesHero -> {
            val show = metadata.resolveSeries(item.series)
            val episode = if (show?.overview.isNullOrBlank()) metadata.resolveEpisode(item.series, item.episode) else null
            when {
                show != null || episode != null -> HomeHeroMetadata(
                    backdropUrl = MetadataImages.backdrop(show?.backdropPath, size = "w1280")
                        ?: MetadataImages.backdrop(episode?.backdropPath ?: episode?.posterPath, size = "w1280"),
                    logoUrl = MetadataImages.logo(show?.logoPath),
                    plot = show?.overview?.takeIf { it.isNotBlank() } ?: episode?.overview?.takeIf { it.isNotBlank() },
                )
                else -> null
            }
        }
        is HeroItem.LiveHero -> null
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    private companion object {
        const val CATALOGUE_LIMIT = 20
        const val CATALOGUE_SCAN_LIMIT = 16_384
    }

}
