package tv.own.owntv.features.sports

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sports.WatchReader
import tv.own.owntv.core.sports.SportsMemory
import tv.own.owntv.core.sports.model.Match

/** Shared by Home/player routes and Sport Lounge. Cached local guesses cannot bypass QA evidence. */
class EventStreamResolver(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val watchReader: WatchReader,
) {
    private val backend=BackendSportsCandidateProvider()
    private val lock=Mutex()
    private val cache=linkedMapOf<String,Pair<Long,EventStreamResolution>>()
    suspend fun resolve(match:Match):EventStreamResolution = lock.withLock {
        val profile=activeProfileSources(settings,sourceDao).first()
        val source=profile.sources.firstOrNull { it.syncLive &&
            tv.own.owntv.features.setup.isLoungeXtream(it.type,it.url) &&
            !it.username.isNullOrBlank() && !it.password.isNullOrBlank() }
        val key="${profile.profileId}:${source?.id}:${match.key}:${match.startMs}"
        val now=System.currentTimeMillis()
        cache[key]?.takeIf { now-it.first in 0..30_000L }?.let { return@withLock it.second }
        val result=if(tv.own.owntv.BuildConfig.DEBUG) {
            source?.let { backend.resolve(match,it) } ?: EventStreamResolution(emptyList(),SportsStreamCoverage(reasons=listOf("lounge-source-not-configured")))
        } else {
            // Production is not deployed by this PR; preserve its existing local-reader path.
            val config=settings.activeSportsConfig.first()
            val local=watchReader.found(match) { SportsMemory.rememberedFor(config,it) }
                ?: watchReader.watchFor(listOf(match)) { SportsMemory.rememberedFor(config,it) }[match.key].orEmpty()
            val remote=source?.let { backend.options(match,it) }.orEmpty()
            EventStreamResolution(mergeBackendSportsOptions(match,remote,local),SportsStreamCoverage())
        }
        if(cache.size>=24)cache.remove(cache.keys.first())
        cache[key]=now to result
        result
    }
}
