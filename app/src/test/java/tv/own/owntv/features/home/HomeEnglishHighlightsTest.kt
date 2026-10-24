package tv.own.owntv.features.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEnglishHighlightsTest {
    @Test fun excludesLabelledForeignLanguageSports() {
        assertFalse(isEnglishHomeHighlight("DE - Sky Sport Bundesliga"))
        assertFalse(isEnglishHomeHighlight("DE | Sky Sports"))
        assertFalse(isEnglishHomeHighlight("ES: UFC"))
        assertFalse(isEnglishHomeHighlight("Sky Sports (GER)"))
        assertFalse(isEnglishHomeHighlight("Sky Sports German"))
    }

    @Test fun keepsOriginalEnglishSportsChannels() {
        assertTrue(isEnglishHomeHighlight("NZ Sky Sport 1 RAW"))
        assertTrue(isEnglishHomeHighlight("UK Sky Sports Main Event"))
        assertTrue(isEnglishHomeHighlight("AU Fox League 502"))
        assertTrue(isEnglishHomeHighlight("UFC 333"))
    }
}
