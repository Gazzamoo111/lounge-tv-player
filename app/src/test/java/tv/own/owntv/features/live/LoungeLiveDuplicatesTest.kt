package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.model.MediaType

class LoungeLiveDuplicatesTest {
    private fun cat(id: Long, label: String) = CategoryEntity(
        id = id, sourceId = 1L, mediaType = MediaType.LIVE, name = label,
    )
    private fun channel(id: Long, category: Long, label: String, epg: String? = null) = ChannelEntity(
        id = id, sourceId = 1L, categoryId = category,
        name = label, streamUrl = "https://example.invalid/test", epgChannelId = epg,
    )

    @Test fun ukQualityVersionsShareOneVisiblePrimary() {
        val categories = listOf(cat(1, "UK| SPORTS"), cat(2, "UK| SPORTS HEVC")).associateBy { it.id }
        val channels = listOf(
            channel(11, 1, "UK| SKY SPORTS CRICKET HD", "SkySportsCricket.uk"),
            channel(12, 2, "UK| SKY SPORTS CRICKET HEVC FHD", "SkySportsCricket.uk"),
            channel(13, 1, "UK| SKY SPORTS CRICKET UHD/4K", "SkySportsCricket.uk"),
        )
        val result = LoungeLiveDuplicates.choose(channels, categories, setOf(1), emptySet())
        assertEquals(setOf(12L, 13L), result.hiddenIds)
        assertEquals(listOf(12L, 13L).toSet(), result.backups[11L]?.toSet())
    }

    @Test fun nineNowAndNineGemHaveOneDefaultWithoutLosingBackup() {
        val categories = listOf(cat(1, "AU| 9NOW ᴿᴬᵂ"), cat(2, "AU| AUSTRALIA")).associateBy { it.id }
        val channels = listOf(
            channel(21, 1, "AU: 9NOW 9GEM (NSW) ᴿᴬᵂ"),
            channel(22, 1, "AU: 9NOW 9GEM (VIC) ᴿᴬᵂ"),
            channel(23, 2, "AU| CHANNEL 9 GEM", "9Gem.au"),
        )
        val result = LoungeLiveDuplicates.choose(channels, categories, setOf(1), emptySet())
        assertEquals(setOf(21L, 22L), result.hiddenIds)
        assertEquals(2, result.backups[23L]?.size)
    }

    @Test fun separateUsLocalAffiliatesAndEventSlotsNeverMerge() {
        val categories = listOf(cat(1, "US| FOX NETWORK"), cat(2, "US| NFL PACKAGE")).associateBy { it.id }
        val channels = listOf(
            channel(31, 1, "US| FOX 11 LOS ANGELES CA (KTTV)"),
            channel(32, 1, "US| FOX 5 ATLANTA GA (WAGA)"),
            channel(33, 2, "US| EVENT 01"),
            channel(34, 2, "US| EVENT 01"),
        )
        assertTrue(LoungeLiveDuplicates.choose(channels, categories, setOf(1), emptySet()).hiddenIds.isEmpty())
    }

    @Test fun independentM3uAndAlreadyHiddenFoldersAreNeverUsedAsPrimaries() {
        val categories = listOf(cat(1, "UK| SPORTS"), cat(2, "UK| SPORTS HEVC")).associateBy { it.id }
        val channels = listOf(
            channel(41, 1, "UK| SKY SPORTS CRICKET HD", "SkySportsCricket.uk"),
            channel(42, 2, "UK| SKY SPORTS CRICKET HEVC HD", "SkySportsCricket.uk"),
        )
        assertTrue(LoungeLiveDuplicates.choose(channels, categories, emptySet(), emptySet()).hiddenIds.isEmpty())
        assertTrue(LoungeLiveDuplicates.choose(channels, categories, setOf(1), setOf(1)).hiddenIds.isEmpty())
        assertFalse(LoungeLiveDuplicates.choose(channels, categories, setOf(1), emptySet()).hiddenIds.isEmpty())
    }
}
