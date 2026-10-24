package tv.own.owntv.features.home

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Display-only discovery of date-labelled combat broadcasts in the customer's imported catalogue.
 *
 * A name match is NOT proof that the stream opens or shows the correct event. Only a separate,
 * verified event/source mapping may ever be advertised as "verified" or used for automatic failover.
 * Never expose bare numbered PPV feeds as featured events.
 */
object LoungeEventDiscovery {
    private val numbered = Regex("""\b(UFC|BKFC|PFL|Bellator)\s*[-:#]?\s*(\d{1,4})\b""", RegexOption.IGNORE_CASE)
    private val boxingContext = Regex("""\b(boxing|matchroom|dazn|top\s*rank)\b""", RegexOption.IGNORE_CASE)
    private val bout = Regex("""\b([A-Za-z][A-Za-z'-]{2,24})\s+(?:vs?\.?|versus)\s+([A-Za-z][A-Za-z'-]{2,24})\b""", RegexOption.IGNORE_CASE)
    private val date = Regex("""\b(20\d{2}-\d{2}-\d{2})\b""")
    private val excluded = Regex(
        """press\s*conference|weigh[_\s-]*in|ceremonial|face\s*off|preview|countdown|highlights|replay|multiview|em\s+portugu[eê]s|en\s+espa[nñ]ol|spanish|french|arabic""",
        RegexOption.IGNORE_CASE,
    )
    private val day = 86_400_000L

    internal data class Name(val title: String, val startDayMs: Long, val preference: Int)
    data class Group<T>(val title: String, val feeds: List<T>)

    internal fun parse(name: String, nowMs: Long): Name? {
        if (excluded.containsMatchIn(name)) return null
        val event = numbered.find(name)
        val boxing = if (event == null && boxingContext.containsMatchIn(name)) bout.find(name) else null
        if (event == null && boxing == null) return null
        // The schedule date is mandatory; undated numbered PPV slots may be stale for months.
        val dayText = date.find(name)?.groupValues?.get(1) ?: return null
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val eventDay = runCatching { formatter.parse(dayText)?.time }.getOrNull() ?: return null
        if (eventDay < nowMs - (2 * day) || eventDay > nowMs + (3 * day)) return null
        val title = if (event != null) {
            "${event.groupValues[1].uppercase(Locale.ROOT)} ${event.groupValues[2]}"
        } else {
            boxing!!.value
        }
        val preference = (if (name.contains("English", true)) 80 else 0) +
            (if (name.contains("RAW", true)) 20 else 0) +
            (if (name.contains("HD", true)) 10 else 0)
        return Name(title, eventDay, preference)
    }

    internal fun <T> group(
        items: List<T>,
        nowMs: Long,
        name: (T) -> String,
        id: (T) -> Any,
    ): List<Group<T>> =
        items.distinctBy(id).mapNotNull { item -> parse(name(item), nowMs)?.let { it to item } }
            .groupBy { it.first.title }
            .values
            .sortedBy { entries -> abs(entries.first().first.startDayMs - nowMs) }
            .take(8)
            .map { entries ->
                Group(entries.first().first.title, entries.sortedByDescending { it.first.preference }.take(6).map { it.second })
            }
}
