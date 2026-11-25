package tv.own.owntv.features.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.epg.EpgShift
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.Match
import java.util.Locale

internal const val FIXTURE_EVIDENCE_PREFIX = "__LTV_EVIDENCE__:"

/** Read stored guide only: finding channels must never probe streams or open provider connections. */
internal class SportsEvidenceReader(private val epgDao: EpgDao) {
    suspend fun assess(match: Match, options: List<WatchOption>, custom: SectionCustomizations, globalShift: Int): List<WatchOption> = withContext(Dispatchers.IO) {
        val fixture = FixtureEvidence.Fixture(
            listOfNotNull(match.home?.team?.name, match.home?.team?.shortName),
            listOfNotNull(match.away?.team?.name, match.away?.team?.shortName), match.title, match.startMs,
        )
        val from = match.startMs - 90 * 60_000L
        val to = match.startMs + 45 * 60_000L + 1
        // Bound inventory work and SQLite parameters. Others retain honest name-only evidence.
        val inspected = options.take(512)
        val guideByChannel = HashMap<Long, List<FixtureEvidence.Programme>>()
        try {
        inspected.groupBy { EpgShift.minutesFor(custom, it.channel, globalShift) }.forEach { (shift, group) ->
            val keys = group.associate { o ->
                o.channel.id to (custom.epgMatchResolver.epgIdFor(o.channel) ?: o.channel.epgChannelId)
                    ?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            }
            val rows = keys.values.filterNotNull().distinct().chunked(128).flatMap { ids ->
                epgDao.programmeSummariesForChannels(ids, EpgShift.toStored(from, shift), EpgShift.toStored(to, shift))
            }
            val descriptions = rows.map { it.id }.filter { it > 0 }.distinct().chunked(400)
                .flatMap { epgDao.programmeDescriptions(it) }.associate { it.id to it.description }
            val byKey = rows.groupBy { it.epgChannelId }
            keys.forEach { (id, key) ->
                guideByChannel[id] = byKey[key].orEmpty().map { p ->
                    val shifted = EpgShift.apply(p, shift)
                    FixtureEvidence.Programme(p.title, descriptions[p.id], shifted.startMs, shifted.stopMs)
                }
            }
        }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { guideByChannel.clear() } // Missing DB data never becomes positive EPG evidence.
        options.mapNotNull { option ->
            val backendHint = option.guideTitle?.removePrefix(BACKEND_CONFIDENCE_PREFIX)
            val evidence = FixtureEvidence.assess(fixture, option.channel.name, guideByChannel[option.channel.id].orEmpty(),
                option.tier == WatchTier.LIKELY || backendHint in setOf("likely", "maybe"))
            if (evidence.level == FixtureEvidence.Level.REJECTED) null else option.copy(
                tier = when (evidence.level) {
                    FixtureEvidence.Level.GUIDE -> WatchTier.GUIDE
                    FixtureEvidence.Level.EVENT_NAME -> WatchTier.EVENT
                    else -> if (option.tier == WatchTier.REMEMBERED) WatchTier.REMEMBERED else WatchTier.LIKELY
                },
                guideTitle = if (evidence.level == FixtureEvidence.Level.GUIDE) evidence.programmeTitle else FIXTURE_EVIDENCE_PREFIX + evidence.level.name,
            )
        }.sortedBy { when (it.tier) {
            WatchTier.GUIDE -> 0
            WatchTier.EVENT -> 1
            WatchTier.REMEMBERED -> 2
            else -> 3
        } }
    }
}

/** A provider feed discovered twice is one choice; prefer the stored row and its manual mapping. */
internal fun deduplicateFixtureOptions(options: List<WatchOption>): List<WatchOption> {
    val found = LinkedHashMap<String, WatchOption>()
    options.forEach { option ->
        val ch = option.channel
        val key = FixtureCorrections.channelKey(ch.sourceId, ch.remoteId, ch.name)
        val old = found[key]
        if (old == null || (old.channel.id < 0 && ch.id > 0)) found[key] = option
    }
    return found.values.toList()
}
