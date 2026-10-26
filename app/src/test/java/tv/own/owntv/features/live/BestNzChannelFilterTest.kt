package tv.own.owntv.features.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.model.SourceType

class BestNzChannelFilterTest {
    @Test fun hidesEveryNonSportsBestNzChannel() {
        assertTrue(shouldHideBestNzChannel(SourceType.XTREAM, "NZ", "NZ | TVNZ 1"))
        assertTrue(shouldHideBestNzChannel(SourceType.XTREAM, "NZ | General", "NZ | Three"))
        assertTrue(shouldHideBestNzChannel(SourceType.XTREAM, "NZ | HD", "NZ | Bravo HD"))
        assertTrue(shouldHideBestNzChannel(SourceType.XTREAM, "NZ | Sky Sport RAW", "NZ | TVNZ DUKE"))
        assertTrue(shouldHideBestNzChannel(SourceType.XTREAM, "New Zealand", "Whakaata Māori"))
    }

    @Test fun keepsOnlyNzSkySportRawOrHd() {
        assertFalse(shouldHideBestNzChannel(SourceType.XTREAM, "NZ | Sky Sport RAW", "Sky Sport 1"))
        assertFalse(shouldHideBestNzChannel(SourceType.XTREAM, "NZ | Sky Sports HD", "NZ Sky Sport 2"))
        // BEST's HD/RAW groups do not consistently include "Sky Sport" in every feed name.
        assertFalse(shouldHideBestNzChannel(SourceType.XTREAM, "NZ|Sky Sports NZ HD", "SS1"))
        assertFalse(shouldHideBestNzChannel(SourceType.XTREAM, "NZ|Sky Sports NZ RAW", "NZ Sports 5"))
        assertFalse(shouldHideBestNzChannel(SourceType.XTREAM, "NZ|Sky Sports NZ HD", "SKY SP 3"))
        assertTrue(shouldHideBestNzChannel(SourceType.XTREAM, "NZ | Sky Sports SD", "Sky Sport 1"))
        assertTrue(shouldHideBestNzChannel(SourceType.XTREAM, "NZ | Entertainment", "SS1"))
        assertFalse(shouldHideBestNzChannel(SourceType.XTREAM, "NZ | Sports", "NZ Sky Sport 3 RAW"))
        assertFalse(shouldHideBestNzChannel(SourceType.XTREAM, "NZ", "NZ Sky Sport 5 HD"))
        assertTrue(shouldHideBestNzChannel(SourceType.XTREAM, "NZ | Sports", "NZ Sky Sport 1"))
        assertTrue(shouldHideBestNzChannel(SourceType.XTREAM, "NZ", "NZ Sky Sport Select"))
    }

    @Test fun removesOtherBestNzCategoriesFromRail() {
        assertTrue(shouldHideBestNzCategory(SourceType.XTREAM, "NZ"))
        assertTrue(shouldHideBestNzCategory(SourceType.XTREAM, "NZ | General"))
        assertTrue(shouldHideBestNzCategory(SourceType.XTREAM, "New Zealand"))
        assertFalse(shouldHideBestNzCategory(SourceType.XTREAM, "NZ | Sky Sport RAW"))
        assertFalse(shouldHideBestNzCategory(SourceType.XTREAM, "NZ | Sky Sports HD"))
        assertFalse(shouldHideBestNzCategory(SourceType.XTREAM, "NZ|Sky Sports NZ HD"))
        assertFalse(shouldHideBestNzCategory(SourceType.XTREAM, "NZ|Sky Sports NZ RAW"))
        assertTrue(shouldHideBestNzCategory(SourceType.XTREAM, "NZ | Sky Sports SD"))
        assertTrue(isAllowedBestNzSportsCategory(SourceType.XTREAM, "NZ|Sky Sports NZ HD"))
        assertFalse(isAllowedBestNzSportsCategory(SourceType.M3U, "NZ|Sky Sports NZ HD"))
        assertFalse(shouldHideBestNzCategory(SourceType.XTREAM, "UK | Movies"))
    }

    @Test fun restoresRawHdFolderBasedOnStreamEvenWhenFolderLabelIsGeneric() {
        assertTrue(isBestNzSkySportRawHdChannel(SourceType.XTREAM, "NZ", "NZ Sky Sport 1 RAW"))
        assertTrue(isBestNzSkySportRawHdChannel(SourceType.XTREAM, "NZ | Sports", "NZ Sky Sport 1 HD"))
        assertTrue(isBestNzSkySportRawHdChannel(SourceType.XTREAM, "NZ Sky Sport HD", "Sky Sport 5 HD"))
        assertFalse(isBestNzSkySportRawHdChannel(SourceType.XTREAM, "NZ", "NZ Sky Sport 1 SD"))
        assertFalse(isBestNzSkySportRawHdChannel(SourceType.XTREAM, "NZ", "NZ TVNZ 1 HD"))
        assertFalse(isBestNzSkySportRawHdChannel(SourceType.XTREAM, "AU Sports", "AU Sky Sport 1 HD"))
        assertFalse(isBestNzSkySportRawHdChannel(SourceType.M3U, "NZ", "NZ Sky Sport 1 HD"))
    }

    @Test fun neverChangesOtherRegionsOrIndependentM3u() {
        assertFalse(shouldHideBestNzChannel(SourceType.M3U, "NZ | General", "TVNZ 1"))
        assertFalse(shouldHideBestNzCategory(SourceType.M3U, "NZ | General"))
        assertFalse(shouldHideBestNzChannel(SourceType.XTREAM, "AU | Sports", "AU Sky Sport 1"))
        assertFalse(shouldHideBestNzChannel(SourceType.XTREAM, "UK | Sky Sports HD", "Sky Sports Main Event"))
    }

    @Test fun legacyBrokenTvnzLabelsStillIdentified() {
        assertTrue(isBrokenBestTvnzLabel("NZ | TVNZ 1"))
        assertTrue(isBrokenBestTvnzLabel("TVNZ 2 HD"))
        assertTrue(isBrokenBestTvnzLabel("NZ - TVNZ DUKE"))
        assertFalse(isBrokenBestTvnzLabel("NZ Sky Sport 1 RAW"))
    }
}
