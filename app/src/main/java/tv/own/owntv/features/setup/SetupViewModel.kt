package tv.own.owntv.features.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.entity.CustomCategoryMemberEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncScopeChoice
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Drives onboarding for a profile (first-run and "add profile"): create the profile, then add content
 * (new source, link an existing unlocked profile's playlists, restore a backup, or skip). The new
 * profile is only made active on [finish], so the wizard stays put until the user completes it.
 *
 * The import sequence itself is core's [SourceImporter], shared with the mobile app; this holds the
 * three things that are the television's own — the LAN companion server, the semi-auto EPG prompt,
 * and the job whose lifetime "Run in background" depends on.
 */
class SetupViewModel(
    private val context: Context,
    private val importer: SourceImporter,
    private val sourceRepository: SourceRepository,
    private val movieDao: MovieDao,
    private val customCategoryDao: CustomCategoryDao,
    private val customize: CustomizationStore,
    private val okHttpClient: OkHttpClient,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val epgSourceStore: tv.own.owntv.core.epg.EpgSourceStore,
    private val settings: SettingsRepository,
    private val companion: tv.own.owntv.core.companion.CompanionController,
    /** A restore runs here, not in [viewModelScope]: Back while it runs leaves the app, and a restore
     *  cancelled half way leaves a profile with some of its data and none of the rest. */
    private val appScope: kotlinx.coroutines.CoroutineScope,
    private val profiles: tv.own.owntv.core.profile.ProfileManager,
    private val profileDao: tv.own.owntv.core.database.dao.ProfileDao,
) : ViewModel() {

    // ---- Remote (companion) add-source: a LAN web form fills the Add Source screen from another device. ----
    /** Server lifecycle (Idle / Starting / Listening with PIN+QR / Failed) for the Remote screen. */
    val remoteState get() = companion.state

    /** Live submission stream — the Remote screen collects it to hand off to the Manual form. */
    val remotePayloads get() = companion.payloads

    /** Retained last submission, so the Manual form pre-fills even after the Remote screen left. */
    val remotePayload get() = companion.lastPayload

    fun startRemoteListener(port: Int) = companion.start(port)
    fun stopRemoteListener() = companion.stop()
    fun consumeRemotePayload() = companion.consumePayload()

    // ---- Remote restore: another device uploads a backup JSON to the TV over the LAN companion server. ----
    /** Uploaded backup files — the remote-restore screen collects this and hands each to [importBackup]. */
    val remoteBackups get() = companion.backups

    fun startRemoteRestore(port: Int) = companion.startForBackupRestore(port)
    fun stopRemoteRestore() = companion.stop()

    val state: StateFlow<SourceImporter.ImportState> = importer.state
    val progress = importer.progress

    // Semi-auto EPG: after the first playlist imports, offer a one-tap guide sync (with a live count) if it
    // has a guide feed.
    private var pendingEpgSource: SourceEntity? = null
    private val _epgSync = MutableStateFlow<tv.own.owntv.features.settings.EpgSyncUi>(tv.own.owntv.features.settings.EpgSyncUi.Hidden)
    val epgSync: StateFlow<tv.own.owntv.features.settings.EpgSyncUi> = _epgSync.asStateFlow()

    private var importJob: Job? = null

    fun syncPendingEpg() {
        val src = pendingEpgSource ?: return
        viewModelScope.launch {
            tv.own.owntv.features.settings.runSemiAutoEpgSync(src, epgRepository, epgSourceStore, settings) { _epgSync.value = it }
        }
    }

    fun dismissPendingEpg() { pendingEpgSource = null; _epgSync.value = tv.own.owntv.features.settings.EpgSyncUi.Hidden }

    /**
     * "Run in background" for the semi-auto EPG sync: enter the app now while the guide keeps
     * downloading. The sync launched by [syncPendingEpg] runs in this activity-scoped [viewModelScope],
     * so it survives leaving the wizard — exactly like the playlist [continueInBackground]. We only
     * finish onboarding; the in-flight job is deliberately not cancelled.
     */
    fun syncEpgInBackground(onDone: (Long?) -> Unit = {}) {
        pendingEpgSource = null
        finish(onDone)
    }

    /**
     * Creates the profile (not active yet); the rest of onboarding attaches content to it. [picture],
     * when one was chosen, is stored once the profile has an id to store it under.
     */
    fun createProfile(name: String, avatarId: Int, isKids: Boolean, pin: String?, picture: File?, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = importer.createProfile(name, avatarId, isKids, pin)
            if (picture != null) profileDao.getById(id)?.let { profiles.setCustomAvatar(it, picture) }
            onCreated(id)
        }
    }

    fun startXtream(
        name: String,
        server: String,
        username: String,
        password: String,
        userAgent: String = "",
        epgUrl: String = "",
        autoRefresh: PlaylistRefresh = PlaylistRefresh(PlaylistAutoRefresh.MANUAL, 1),
        live: SyncScopeChoice = SyncScopeChoice.Now,
        movies: SyncScopeChoice = SyncScopeChoice.Now,
        series: SyncScopeChoice = SyncScopeChoice.Now,
        preferHls: Boolean = false,
        httpReferer: String = "",
    ) = runImport {
        val effectiveName = if (BuildConfig.LOUNGE_CORE_QA) context.getString(R.string.lounge_core_qa_source_name) else name
        val effectiveServer = if (BuildConfig.LOUNGE_CORE_QA) BuildConfig.LOUNGE_CORE_XTREAM_URL else server
        val effectiveLive = if (BuildConfig.LOUNGE_CORE_QA) SyncScopeChoice.Off else live
        importer.xtream(
            effectiveName,
            effectiveServer,
            username,
            password,
            userAgent,
            epgUrl,
            autoRefresh,
            effectiveLive,
            movies,
            series,
            preferHls,
            httpReferer = httpReferer,
        )
    }

    fun startStalker(
        name: String,
        portalUrl: String,
        mac: String,
        serialNumber: String = "",
        deviceId: String = "",
        deviceId2: String = "",
        signature: String = "",
        userAgent: String = "",
        autoRefresh: PlaylistRefresh = PlaylistRefresh.OFF,
        live: SyncScopeChoice = SyncScopeChoice.Now,
        movies: SyncScopeChoice = SyncScopeChoice.Later,
        series: SyncScopeChoice = SyncScopeChoice.Later,
        httpReferer: String = "",
    ) = runImport {
        importer.stalker(
            name, portalUrl, mac, serialNumber, deviceId, deviceId2, signature, userAgent,
            autoRefresh, live, movies, series, httpReferer = httpReferer,
        )
    }

    fun startM3u(name: String, url: String, userAgent: String = "", epgUrl: String = "", autoRefresh: PlaylistRefresh = PlaylistRefresh.OFF, httpReferer: String = "") =
        runImport { importer.m3u(name, url, userAgent, epgUrl, autoRefresh, httpReferer = httpReferer) }

    /**
     * Runs one import in the activity-scoped [viewModelScope] so "Run in background" can walk away
     * from it, then raises the semi-auto EPG prompt if the playlist that just landed has a guide feed.
     */
    private fun runImport(block: suspend () -> Unit) {
        importJob?.cancel()
        val job = viewModelScope.launch {
            block()
            val done = importer.state.value as? SourceImporter.ImportState.Success ?: return@launch
            val source = done.source ?: return@launch
            if (!importer.backgroundHandoff && source.type != SourceType.XTREAM && epgRepository.guideUrl(source) != null) {
                pendingEpgSource = source
                _epgSync.value = tv.own.owntv.features.settings.EpgSyncUi.Ask(source.name)
            }
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    /** Playlists belonging to unlocked (no-PIN) profiles that aren't already on the new profile. */
    suspend fun availableExistingSources(): List<SourceEntity> = importer.availableExistingSources()

    /** Link the chosen existing sources to the new profile, then re-sync each one. */
    fun linkExisting(sourceIds: Set<Long>) = runImport { importer.linkExisting(sourceIds) }

    /**
     * Restore a backup file (merges profiles & sources, then activates one). Encrypted backups first
     * ask for the backup password via [SourceImporter.ImportState.NeedPassword].
     *
     * [sections] is what the user ticked before the restore began, and defaults to all of it — which
     * is what the wizard did unconditionally until it gained a picker of its own. More → Backup &
     * Restore has always asked; the first run, where a restore is most likely, did not.
     */
    fun importBackup(
        file: File,
        onDone: (Long?) -> Unit,
        sections: Set<BackupManager.Section> = BackupManager.Section.entries.toSet(),
        /** Take another device's hardware settings too — see [BackupManager.import]. */
        deviceSettings: Boolean = false,
    ) {
        appScope.launch {
            if (importer.importBackup(file, sections, deviceSettings)) {
                withContext(Dispatchers.Main) { onRestored(onDone) }
            }
        }
    }

    /** Continue an encrypted restore once the user provides (or skips, password = null) the passphrase. */
    fun restoreWithPassword(
        file: File,
        password: String?,
        onDone: (Long?) -> Unit,
        sections: Set<BackupManager.Section> = BackupManager.Section.entries.toSet(),
        deviceSettings: Boolean = false,
    ) {
        appScope.launch {
            if (importer.restoreWithPassword(file, password, sections, deviceSettings)) {
                withContext(Dispatchers.Main) { onRestored(onDone) }
            }
        }
    }

    // A backup may restore several profiles or a PIN-locked active profile. Restoring data is not
    // authenticating a viewer, so let MainActivity require the selected profile's PIN rather than
    // treating the restore as a gate pass.
    private fun onRestored(onDone: (Long?) -> Unit) = onDone(null)

    fun reset() = importer.reset()

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        importer.reset()
    }

    /**
     * "Run in background": enter the app now while the import keeps running. This ViewModel is
     * activity-scoped, so the in-flight import survives leaving the wizard — the sync continues
     * exactly as if the user had waited (success still runs ImportFinalizer + the remainder enqueue).
     * Deliberately does NOT cancel: cancelling would delete the half-imported source. The semi-auto
     * EPG prompt is skipped (its dialog lives in the wizard); EPG stays user-initiated from
     * Settings → EPG Sources, matching the app's EPG opt-in policy.
     */
    fun continueInBackground(onDone: (Long?) -> Unit = {}) {
        importer.backgroundHandoff = true
        dismissPendingEpg()
        finish(onDone)
    }

    /** Completes onboarding → makes the new profile active, routing the app into the shell. */
    fun finish(onDone: (Long?) -> Unit = {}) {
        viewModelScope.launch {
            val profileId = importer.finish()
            if (BuildConfig.LOUNGE_CORE_QA && profileId != null) {
                val xtreamSource = sourceRepository.observeSources(profileId).first()
                    .firstOrNull { it.type == SourceType.XTREAM && it.url == BuildConfig.LOUNGE_CORE_XTREAM_URL }
                if (xtreamSource != null) {
                    runCatching { ensureCoreQaLiveSource(profileId, xtreamSource) }
                    runCatching { ensureCoreQaMovieTaxonomy(profileId, xtreamSource) }
                }
            }
            onDone(profileId)
        }
    }

    private suspend fun ensureCoreQaLiveSource(profileId: Long, xtreamSource: SourceEntity) {
        val username = xtreamSource.username.orEmpty()
        val password = xtreamSource.password.orEmpty()
        if (username.isBlank() || password.isBlank()) return

        val encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8.toString())
        val encodedPass = URLEncoder.encode(password, StandardCharsets.UTF_8.toString())
        val liveUrl = BuildConfig.LOUNGE_CORE_LIVE_M3U_URL +
            "?username=" + encodedUser + "&password=" + encodedPass

        val existing = sourceRepository.observeSources(profileId).first()
        if (existing.any { it.type == SourceType.M3U && it.url.startsWith(BuildConfig.LOUNGE_CORE_LIVE_M3U_URL) }) return

        val source = sourceRepository.addM3uSource(
            profileId = profileId,
            name = context.getString(R.string.lounge_core_qa_live_source_name),
            url = liveUrl,
            epgUrl = BuildConfig.LOUNGE_CORE_LIVE_EPG_URL,
        )
        sourceRepository.sync(
            source = source,
            contentTypes = SyncContentTypes(live = true, movies = false, series = false),
            forcePrune = true,
            onProgress = {},
        )
    }

    private suspend fun ensureCoreQaMovieTaxonomy(profileId: Long, xtreamSource: SourceEntity) {
        val body = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(BuildConfig.LOUNGE_CORE_VOD_TAXONOMY_URL)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()
            }
        } ?: return

        val root = JSONObject(body)
        val categories = root.optJSONArray("categories") ?: return
        val current = customize.observe(profileId, MediaType.MOVIE).first()
        current.customCategories
            .filter { it.name.startsWith("Studios •") || it.name.startsWith("Collections •") }
            .forEach { old ->
                customCategoryDao.clearContext(profileId, MediaType.MOVIE, old.id)
                customize.deleteCustomCategory(profileId, MediaType.MOVIE, old.id)
            }
        val refreshed = customize.observe(profileId, MediaType.MOVIE).first()
        val existingByName = refreshed.customCategories.associateBy { it.name }
        val orderedKeys = mutableListOf<String>()

        for (i in 0 until categories.length()) {
            val categoryJson = categories.optJSONObject(i) ?: continue
            val name = categoryJson.optString("name").trim()
            if (name.isBlank()) continue

            val category = existingByName[name]
                ?: customize.createCustomCategory(profileId, MediaType.MOVIE, name)
            orderedKeys += category.id

            val providerIdsJson = categoryJson.optJSONArray("provider_item_ids") ?: continue
            val providerIds = buildList {
                for (j in 0 until providerIdsJson.length()) {
                    providerIdsJson.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            if (providerIds.isEmpty()) {
                customCategoryDao.clearContext(profileId, MediaType.MOVIE, category.id)
                continue
            }

            val localMovies = providerIds.chunked(400)
                .flatMap { movieDao.findByRemoteIds(xtreamSource.id, it) }
            val byRemote = localMovies.mapNotNull { movie ->
                movie.remoteId?.let { it to movie }
            }.toMap()

            val rows = providerIds.mapNotNull { byRemote[it] }
                .distinctBy { it.id }
                .mapIndexed { position, movie ->
                    CustomCategoryMemberEntity(
                        profileId = profileId,
                        mediaType = MediaType.MOVIE,
                        contextKey = category.id,
                        itemId = movie.id,
                        position = position,
                    )
                }
            customCategoryDao.replaceContext(profileId, MediaType.MOVIE, category.id, rows)
        }

        customize.setCategoryOrder(profileId, MediaType.MOVIE, orderedKeys)
    }
}
