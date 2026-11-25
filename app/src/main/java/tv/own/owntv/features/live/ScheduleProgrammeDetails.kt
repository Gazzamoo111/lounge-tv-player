package tv.own.owntv.features.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/** Read full metadata only for the row the viewer settles on, not the entire day's text. */
internal class ScheduleProgrammeDetails(private val load: suspend (Long) -> EpgProgrammeEntity?) {
    private val cache = object : LinkedHashMap<Long, EpgProgrammeEntity>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, EpgProgrammeEntity>?) = size > 32
    }

    suspend fun afterFocus(summary: EpgProgrammeEntity): EpgProgrammeEntity {
        if (summary.id <= 0) return summary // provider-only row already carries its available text
        val full = cache[summary.id] ?: run {
            delay(150)
            val loaded = try { load(summary.id) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            loaded?.takeIf { it.id == summary.id }?.also { cache[summary.id] = it }
        } ?: return summary
        // Stored metadata uses the feed's clock; keep the user's manually shifted guide times.
        return full.copy(startMs = summary.startMs, stopMs = summary.stopMs,
            description = full.description ?: summary.description)
    }
}
