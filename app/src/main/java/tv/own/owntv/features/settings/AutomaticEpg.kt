package tv.own.owntv.features.settings

import androidx.work.ExistingWorkPolicy
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.AutoRefresh
import tv.own.owntv.core.sync.work.EpgSyncScheduler

/** Auto-configure the provider XMLTV AND the supplemental Lounge guide on Xtream sign-in.
 *
 * Existing installations are picked up when their saved Xtream source is observed at app start.
 * No manual EPG entry and no extra playlist credentials are required. An existing source and its
 * preferences are never overwritten. WorkManager downloads the guides off the UI thread.
 */
internal suspend fun ensureAutomaticXtreamGuides(
    playlists: List<SourceEntity>,
    repository: EpgRepository,
    store: EpgSourceStore,
    settings: SettingsRepository,
    scheduler: EpgSyncScheduler,
) {
    val visited = mutableSetOf<String>()
    for (playlist in playlists) {
        if (playlist.type != SourceType.XTREAM) continue

        // Provider's authenticated xmltv.php first, our public XMLTV second.
        for (url in guideUrlsWithLounge(playlist.type, repository.guideUrls(playlist))) {
            if (url.isBlank() || !visited.add(url)) continue

            val existing = store.getAll().firstOrNull { it.url == url }
            val guide = existing ?: store.ensure(
                name = playlist.name,
                url = url,
                userAgent = if (url == LOUNGE_EPG_URL) null else playlist.userAgent,
            )
            if (existing == null) {
                // Only new sources receive the default; leave any existing user's choice alone.
                settings.setEpgAutoRefresh(guide.id, EpgRefresh(EpgAutoRefresh.HOURS_6))
            }
            if (shouldPrimeGuide(existing)) {
                scheduler.enqueueSync(
                    sourceId = guide.id,
                    reason = AutoRefresh.REASON,
                    policy = ExistingWorkPolicy.KEEP,
                )
            }
        }
    }
}

/** Never replace an existing successful sync; resuming a failed/unstarted feed is safe. */
internal fun shouldPrimeGuide(existing: EpgSource?): Boolean =
    existing?.lastSyncAt == null
