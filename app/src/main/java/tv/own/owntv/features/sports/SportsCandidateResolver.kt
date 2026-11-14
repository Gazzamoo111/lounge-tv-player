package tv.own.owntv.features.sports

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.own.owntv.BuildConfig
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.Match

/** One QA evidence/correction path for match pages, reminders and player alternatives. */
class SportsCandidateResolver(
    context: Context,
    private val settings: SettingsRepository,
    private val customizations: CustomizationStore,
    epgDao: EpgDao,
) {
    private val reader = SportsEvidenceReader(epgDao)
    private val corrections = FixtureCorrections(PreferenceFixtureCorrections(context))
    private val _revision = MutableStateFlow(0)
    val revision: kotlinx.coroutines.flow.StateFlow<Int> = _revision

    suspend fun inspect(match: Match, options: List<WatchOption>): List<WatchOption> {
        if (!BuildConfig.LOUNGE_TV_2) return options
        val pid = settings.activeProfileIdNow()
        if (pid < 0) return emptyList()
        val custom = customizations.observe(pid, MediaType.LIVE).first()
        val assessed = reader.assess(match, deduplicateFixtureOptions(options), custom, settings.epgOffsetMinutes.first())
        // Never hand a previous profile's candidate set to a newly selected profile.
        if (settings.activeProfileIdNow() != pid) return emptyList()
        return assessed.filter { nbaEventCandidateAllowed(match, it) }
    }

    fun rejected(profile: Long, match: Match): Set<String> =
        if (BuildConfig.LOUNGE_TV_2 && profile >= 0) corrections.rejected(profile, match.key, match.startMs) else emptySet()

    suspend fun resolve(match: Match, options: List<WatchOption>): List<WatchOption> {
        val pid = settings.activeProfileIdNow()
        val assessed = inspect(match, options)
        val (rejected, selected) = withContext(Dispatchers.IO) {
            rejected(pid, match) to corrections.selected(pid, match.key, match.startMs)
        }
        if (settings.activeProfileIdNow() != pid) return emptyList()
        return assessed.filterNot { key(it.channel) in rejected }.sortedBy { key(it.channel) != selected }
    }

    suspend fun prioritizeFixtureChoice(match: Match, options: List<WatchOption>): List<WatchOption> {
        if (!BuildConfig.LOUNGE_TV_2) return options
        val pid = settings.activeProfileIdNow()
        val selected = withContext(Dispatchers.IO) { corrections.selected(pid, match.key, match.startMs) }
        if (settings.activeProfileIdNow() != pid) return emptyList()
        return options.sortedBy { key(it.channel) != selected }
    }

    suspend fun remember(match: Match, channel: ChannelEntity) {
        val pid = settings.activeProfileIdNow()
        if (!BuildConfig.LOUNGE_TV_2 || pid < 0) return
        withContext(Dispatchers.IO) { corrections.remember(pid, match.key, match.startMs, key(channel)) }
    }

    suspend fun reject(match: Match, channel: ChannelEntity): Boolean {
        val pid = settings.activeProfileIdNow()
        if (!BuildConfig.LOUNGE_TV_2 || pid < 0) return false
        val saved = withContext(Dispatchers.IO) { corrections.reject(pid, match.key, match.startMs, key(channel)) }
        if (saved) _revision.value++
        return saved
    }

    suspend fun restore(match: Match): Boolean {
        val pid = settings.activeProfileIdNow()
        if (!BuildConfig.LOUNGE_TV_2 || pid < 0) return false
        val saved = withContext(Dispatchers.IO) { corrections.restore(pid, match.key, match.startMs) }
        if (saved) _revision.value++
        return saved
    }

    fun key(channel: ChannelEntity) = FixtureCorrections.channelKey(channel.sourceId, channel.remoteId, channel.name)
}
