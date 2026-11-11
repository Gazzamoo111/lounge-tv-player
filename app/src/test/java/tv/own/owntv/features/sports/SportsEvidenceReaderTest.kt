package tv.own.owntv.features.sports

import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.EpgProgrammeText
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.*

class SportsEvidenceReaderTest {
    private val start = 1_800_000_000_000L
    private val match = Match(id = "event", sport = Sport.BASKETBALL, feed = "basketball/nba", leagueSlug = "nba",
        leagueName = "NBA", startMs = start, state = MatchState.IN, statusText = "Live", title = "Lakers vs Celtics",
        competitors = listOf(Competitor(Team("1", "Lakers"), true, null, null), Competitor(Team("2", "Celtics"), false, null, null)))
    private fun option(id: Long = 1, key: String = "espn") = WatchOption(
        ChannelEntity(id = id, sourceId = 1, name = "ESPN", streamUrl = "https://example.invalid/$id", epgChannelId = key),
        WatchTier.GUIDE, BACKEND_CONFIDENCE_PREFIX + "definite")
    private fun row(title: String, key: String = "espn", at: Long = start, description: String? = null) =
        EpgProgrammeEntity(id = 1, sourceId = 1, epgChannelId = key, startMs = at, stopMs = at + 120 * 60_000, title = title, description = description)
    private fun dao(rows: List<EpgProgrammeEntity>, beforeQuery: (List<String>, Long, Long) -> Unit = { _, _, _ -> }): EpgDao =
        Proxy.newProxyInstance(EpgDao::class.java.classLoader, arrayOf(EpgDao::class.java)) { _, method, args ->
            when (method.name) {
                "programmeSummariesForChannels" -> {
                    @Suppress("UNCHECKED_CAST") val keys = args[0] as List<String>
                    val from = args[1] as Long; val to = args[2] as Long
                    beforeQuery(keys, from, to)
                    rows.filter { it.epgChannelId in keys && it.stopMs > from && it.startMs < to }.map { it.copy(description = null) }
                }
                "programmeDescriptions" -> rows.map { EpgProgrammeText(it.id, it.description) }
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as EpgDao

    @Test fun `backend confidence cannot substitute for guide evidence`() = runBlocking {
        val result = SportsEvidenceReader(dao(emptyList())).assess(match, listOf(option()), SectionCustomizations(), 0)
        assertEquals(WatchTier.LIKELY, result.single().tier)
        assertEquals(FIXTURE_EVIDENCE_PREFIX + "UNVERIFIED", result.single().guideTitle)
    }
    @Test fun `stored descriptions contribute evidence and actual title is retained`() = runBlocking {
        val result = SportsEvidenceReader(dao(listOf(row("Live NBA", description = "Lakers face Celtics"))))
            .assess(match, listOf(option()), SectionCustomizations(), 0)
        assertEquals(WatchTier.GUIDE, result.single().tier)
        assertEquals("Live NBA", result.single().guideTitle)
    }
    @Test fun `manual guide mapping and per-channel offset override provider and global values`() = runBlocking {
        val option = option()
        val custom = SectionCustomizations(epgMatches = mapOf(CustomizeKeys.channel(option.channel) to "MANUAL"),
            epgShifts = mapOf(CustomizeKeys.channel(option.channel) to "60"))
        val database = dao(listOf(row("Lakers vs Celtics", "manual", start - 60 * 60_000))) { keys, from, _ ->
            assertEquals(listOf("manual"), keys)
            assertEquals(start - 150 * 60_000, from)
        }
        val result = SportsEvidenceReader(database).assess(match, listOf(option), custom, 180)
        assertEquals(WatchTier.GUIDE, result.single().tier)
    }
    @Test fun `wrong opponent in stored guide removes name-based recommendation`() = runBlocking {
        val result = SportsEvidenceReader(dao(listOf(row("Lakers vs Warriors"))))
            .assess(match, listOf(option()), SectionCustomizations(), 0)
        assertTrue(result.isEmpty())
    }
    @Test fun `database failures degrade to unverified suggestions`() = runBlocking {
        val result = SportsEvidenceReader(dao(emptyList()) { _, _, _ -> error("offline database") })
            .assess(match, listOf(option()), SectionCustomizations(), 0)
        assertEquals(WatchTier.LIKELY, result.single().tier)
    }
    @Test(expected = CancellationException::class)
    fun `cancelled search must not emit stale suggestions`() = runBlocking {
        SportsEvidenceReader(dao(emptyList()) { _, _, _ -> throw CancellationException() })
            .assess(match, listOf(option()), SectionCustomizations(), 0)
        Unit
    }
    @Test fun `catalogue-wide evidence reads are bounded and chunked`() = runBlocking {
        var queried = 0
        val database = dao(emptyList()) { keys, _, _ -> assertTrue(keys.size <= 128); queried += keys.size }
        val options = (1L..513).map { option(it, "key$it") }
        val result = SportsEvidenceReader(database).assess(match, options, SectionCustomizations(), 0)
        assertEquals(512, queried)
        assertEquals(513, result.size)
    }
    @Test fun `same provider feed is deduplicated while distinct backups remain`() {
        val local = option().copy(channel = option().channel.copy(remoteId = "123"))
        val backend = local.copy(channel = local.channel.copy(id = -1, remoteId = "sport-backend:123"))
        val backup = local.copy(channel = local.channel.copy(id = 2, remoteId = "124"))
        assertEquals(listOf(local, backup), deduplicateFixtureOptions(listOf(backend, local, backup)))
    }

}
