package tv.own.owntv.features.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BestNzChannelFilterTest {
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
