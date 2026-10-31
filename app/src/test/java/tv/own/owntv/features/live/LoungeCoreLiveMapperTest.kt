package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class LoungeCoreLiveMapperTest {
    private fun channel(
        id: Long,
        sourceId: Long,
        remoteId: String?,
        name: String,
        epg: String? = null,
    ) = ChannelEntity(
        id = id,
        sourceId = sourceId,
        categoryId = 10L,
        name = name,
        streamUrl = "http://example.test/live/$remoteId.ts",
        epgChannelId = epg,
        remoteId = remoteId,
        sortOrder = id.toInt(),
    )

    private val snapshot = LoungeCoreLiveSnapshot(
        schemaVersion = 1,
        generatedAt = "2026-11-01T00:00:00Z",
        channels = listOf(
            LoungeCoreLiveChannel(
                key = "nz-sky-sport-1",
                name = "Sky Sport 1",
                region = "NZ",
                category = "Sky Sport NZ",
                subcategory = "Sport",
                epgId = "SkySport1.nz",
                order = 110,
                is24x7 = false,
                sources = listOf(
                    LoungeCoreLiveSource(
                        streamId = 1001,
                        priority = 1,
                        role = "primary",
                        qualityHint = "FHD",
                        qualityScore = 95.0,
                        healthScore = 50.0,
                        runtimeHealth = "unknown",
                    ),
                    LoungeCoreLiveSource(
                        streamId = 1002,
                        priority = 2,
                        role = "backup",
                        qualityHint = "HD",
                        qualityScore = 85.0,
                        healthScore = 50.0,
                        runtimeHealth = "unknown",
                    ),
                    LoungeCoreLiveSource(
                        streamId = 1003,
                        priority = 3,
                        role = "backup",
                        qualityHint = "standard",
                        qualityScore = 75.0,
                        healthScore = 50.0,
                        runtimeHealth = "unknown",
                    ),
                ),
            ),
        ),
    )

    @Test
    fun oneCanonicalChannelVisibleAndBackupsRemainHidden() {
        val raw = listOf(
            channel(1, 7, "1001", "NZ| SKY SPORT 1 FHD", "old.epg"),
            channel(2, 7, "1002", "NZ| SKY SPORT 1 HD"),
            channel(3, 7, "1003", "NZ| SKY SPORTS NZ 1"),
            channel(4, 7, "9999", "NZ| RANDOM JUNK"),
        )

        val plan = LoungeCoreLiveMapper.plan(raw, setOf(7L), snapshot)

        assertFalse(1L in plan.hiddenIds)
        assertTrue(2L in plan.hiddenIds)
        assertTrue(3L in plan.hiddenIds)
        assertTrue(4L in plan.hiddenIds)
        assertEquals(listOf(2L, 3L), plan.backupsFor(1L))

        val visible = plan.present(raw.first())
        assertEquals("Sky Sport 1", visible?.name)
        assertEquals("SkySport1.nz", visible?.epgChannelId)
        assertEquals(110, visible?.sortOrder)
        assertEquals("NZ| SKY SPORT 1 FHD", visible?.providerName)
        assertEquals("nz-sky-sport-1", plan.canonicalKeyByChannelId[1L])
        assertEquals(3, plan.mappedBestCount)
        assertEquals(1, plan.unmappedBestCount)
    }

    @Test
    fun independentSourcesAreNeverTouched() {
        val independent = channel(9, 99, "1001", "My own M3U channel", "custom.epg")

        val plan = LoungeCoreLiveMapper.plan(
            channels = listOf(independent),
            auditedSourceIds = setOf(7L),
            snapshot = snapshot,
        )

        assertTrue(plan.hiddenIds.isEmpty())
        assertEquals(independent, plan.present(independent))
        assertNull(plan.canonicalKeyByChannelId[independent.id])
    }

    @Test
    fun locallyMissingPreferredSourcePromotesBestAvailableBackup() {
        val onlyBackup = channel(22, 7, "1002", "NZ| SKY SPORT 1 HD")

        val plan = LoungeCoreLiveMapper.plan(
            channels = listOf(onlyBackup),
            auditedSourceIds = setOf(7L),
            snapshot = snapshot,
        )

        assertFalse(22L in plan.hiddenIds)
        assertEquals("Sky Sport 1", plan.present(onlyBackup)?.name)
        assertTrue(plan.backupsFor(22L).isEmpty())
    }

    @Test
    fun unsupportedSchemaFailsOpenInsteadOfHidingProviderCatalogue() {
        val raw = channel(1, 7, "1001", "NZ| SKY SPORT 1 FHD")
        val future = snapshot.copy(schemaVersion = 99)

        val plan = LoungeCoreLiveMapper.plan(listOf(raw), setOf(7L), future)

        assertTrue(plan.hiddenIds.isEmpty())
        assertEquals(raw, plan.present(raw))
    }
}
