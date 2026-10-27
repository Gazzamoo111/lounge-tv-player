package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.model.SourceType

class LoungeLiveCatalogueCleanupTest {
    private fun folder(id: Long, name: String, count: Int, type: SourceType = SourceType.XTREAM, source: Long = 1L) =
        LoungeLiveCatalogueCleanup.Folder(id, source, type, name, count, synced = true)

    private val audited = listOf(
        folder(1, "US| ESPN+ PPV VIP", 1001),
        folder(2, "US| PARAMOUNT+ PPV", 301),
        folder(3, "UK| SPORTS", 85),
        folder(4, "UK| MOVIES", 68),
        folder(5, "NZ| SKY SPORT ᴿᴬᵂ", 10),
        folder(6, "AU| AUSTRALIA", 100),
        folder(7, "US| FLO NETWORK", 1001),
        folder(8, "US| BIBLICAL/RELIGIOUS", 10),
        folder(9, "US| ESPN PLUS", 500),
        folder(10, "WORLD LIVE SPORTS", 597),
        folder(11, "4K UHD 3840P", 1),
        folder(12, "NZ| SKY SPORTS NZ", 13),
        folder(13, "NZ| NEW ZEALAND", 50),
        folder(14, "UK| UFC/BOXING PPV", 18),
        folder(15, "UK| APPLE TV F1 PPV", 21),
        folder(16, "AU| NRL TV PPV", 11),
        folder(17, "AU| STAN PPV", 21),
        folder(18, "24/7 KIDS/FAMILY VIP", 86),
    )

    @Test fun aggressiveHidesBulkEventsButProtectsEssentialSports() {
        val plan = LoungeLiveCatalogueCleanup.plan(audited)
        assertTrue(plan.sourceIds.contains(1L))
        for (id in listOf(1L, 2L, 7L, 8L, 9L, 10L, 11L, 13L)) {
            assertTrue("expected hidden category id=$id", id in plan.hiddenIds)
        }
        for (id in listOf(3L, 4L, 5L, 6L, 12L, 14L, 15L, 16L, 17L, 18L)) {
            assertFalse("important channel folder hidden id=$id", id in plan.hiddenIds)
        }
    }

    @Test fun neverHidesIndependentPlaylists() {
        val other = listOf(
            folder(101, "US| ESPN+ PPV VIP", 2, type = SourceType.M3U, source = 9),
            folder(102, "NZ| SKY SPORT ᴿᴬᵂ", 5, type = SourceType.M3U, source = 9),
        )
        val plan = LoungeLiveCatalogueCleanup.plan(audited + other)
        assertFalse(101L in plan.hiddenIds)
        assertFalse(102L in plan.hiddenIds)
        assertFalse(9L in plan.sourceIds)
    }

    @Test fun incompleteSyncDoesNotHideSmallFolders() {
        assertFalse(LoungeLiveCatalogueCleanup.shouldHide("4K UHD 3840P", count = 1, synced = false))
        assertTrue(LoungeLiveCatalogueCleanup.shouldHide("4K UHD 3840P", count = 1, synced = true))
    }

    @Test fun cleansTitlesWithoutChangingProviderIdentity() {
        assertEquals("SKY SPORT 1", LoungeLiveCatalogueCleanup.channelTitle("NZ| SKY SPORT 1 FHD"))
        assertEquals("Sky Sports Cricket", LoungeLiveCatalogueCleanup.channelTitle("UK| Sky Sports Cricket HD"))
        assertTrue(LoungeLiveCatalogueCleanup.divider("##### MOVIES #####"))
        assertEquals("Australia", LoungeLiveCatalogueCleanup.categoryTitle("AU| AUSTRALIA"))
        assertEquals("US · Sports Network", LoungeLiveCatalogueCleanup.categoryTitle("US| SPORTS NETWORK"))
    }

    @Test fun keepsNzGenericSkyAlongsideRaw() {
        assertTrue(isAllowedBestNzSportsCategory(SourceType.XTREAM, "NZ| SKY SPORTS NZ"))
        assertFalse(shouldHideBestNzChannel(SourceType.XTREAM, "NZ| SKY SPORTS NZ", "NZ| SKY NZ ESPN"))
        assertTrue(shouldHideBestNzChannel(SourceType.XTREAM, "NZ| NEW ZEALAND", "NZ| TVNZ 1 HD"))
    }
}
