package tv.own.owntv.features.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishBroadcastNameTest {
    @Test fun skipsDubbedChannelsFromHome() {
        assertFalse(englishBroadcastName("DE - Sky Sport 1"))
        assertFalse(englishBroadcastName("DE | Sky Sports HD"))
        assertFalse(englishBroadcastName("Sky Sports (GER)"))
        assertFalse(englishBroadcastName("Sky Sport German"))
        assertFalse(englishBroadcastName("FR: Sky Sports"))
        assertFalse(englishBroadcastName("ES | UFC 333"))
    }

    @Test fun retainsNzAuUkAndEnglishSports() {
        assertTrue(englishBroadcastName("NZ | Sky Sport 1 RAW"))
        assertTrue(englishBroadcastName("AU Fox League 502 HD"))
        assertTrue(englishBroadcastName("UK Sky Sports Main Event"))
        assertTrue(englishBroadcastName("UFC 333 English"))
        assertTrue(englishBroadcastName("US ESPN"))
    }
}
