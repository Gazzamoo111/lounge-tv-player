package tv.own.owntv.features.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.model.SourceType

class BestNzChannelFilterTest {
    @Test fun filtersBrokenProviderFeedsWithoutASeparateFreeviewPlaylist() {
        assertTrue(shouldHideBrokenBestTvnz(SourceType.XTREAM, "NZ | TVNZ 1"))
        assertTrue(shouldHideBrokenBestTvnz(SourceType.XTREAM, "NZ | TVNZ 2 HD"))
        assertTrue(shouldHideBrokenBestTvnz(SourceType.XTREAM, "NZ | TVNZ DUKE"))
        assertFalse(shouldHideBrokenBestTvnz(SourceType.M3U, "TVNZ 1"))
        assertFalse(shouldHideBrokenBestTvnz(SourceType.XTREAM, "NZ Sky Sport 1 RAW"))
        assertFalse(shouldHideBrokenBestTvnz(SourceType.XTREAM, "NZ | Three"))
    }

    @Test fun matchesOnlyTvnzDuplicates() {
        assertTrue(isBrokenBestTvnzLabel("NZ | TVNZ 1"))
        assertTrue(isBrokenBestTvnzLabel("TVNZ 2 HD"))
        assertTrue(isBrokenBestTvnzLabel("NZ - TVNZ DUKE"))
        assertFalse(isBrokenBestTvnzLabel("NZ Sky Sport 1 RAW"))
        assertFalse(isBrokenBestTvnzLabel("NZ | Three"))
        assertFalse(isBrokenBestTvnzLabel("NZ | Bravo"))
        assertFalse(isBrokenBestTvnzLabel("Whakaata Māori"))
    }
}
