package tv.own.owntv.features.sports

import org.junit.Assert.*
import org.junit.Test

class FixtureCorrectionsTest {
    private class Store : FixtureCorrectionPersistence {
        var value: String? = null
        var fail = false
        override fun read() = value
        override fun write(value: String): Boolean { if (fail) return false; this.value = value; return true }
    }
    private val start = 1_800_000_000_000L
    private val feed = FixtureCorrections.channelKey(1, "123", "Sky Sport")

    @Test fun `reports persist across restart`() {
        val store = Store()
        assertTrue(FixtureCorrections(store) { start }.reject(1, "nba#event", start, feed))
        assertEquals(setOf(feed), FixtureCorrections(store) { start }.rejected(1, "nba#event", start))
    }
    @Test fun `profile fixture date and source identities remain isolated`() {
        val corrections = FixtureCorrections(Store()) { start }
        corrections.reject(1, "event", start, feed)
        assertTrue(corrections.rejected(2, "event", start).isEmpty())
        assertTrue(corrections.rejected(1, "other", start).isEmpty())
        assertTrue(corrections.rejected(1, "event", start + 86_400_000).isEmpty())
        assertNotEquals(feed, FixtureCorrections.channelKey(2, "123", "Sky Sport"))
    }
    @Test fun `backend and local discovery of same provider feed share report`() {
        assertEquals(feed, FixtureCorrections.channelKey(1, "sport-backend:123", "Other display name"))
        assertNotEquals(feed, FixtureCorrections.channelKey(1, "124", "Sky Sport"))
    }
    @Test fun `restore only removes the selected fixture report`() {
        val corrections = FixtureCorrections(Store()) { start }
        corrections.reject(1, "event", start, feed)
        corrections.reject(1, "other", start, feed)
        assertTrue(corrections.restore(1, "event", start))
        assertTrue(corrections.rejected(1, "event", start).isEmpty())
        assertEquals(setOf(feed), corrections.rejected(1, "other", start))
    }
    @Test fun `reports expire rather than permanently hiding channels`() {
        var now = start
        val corrections = FixtureCorrections(Store()) { now }
        corrections.reject(1, "event", start, feed)
        now += 37 * 60 * 60_000L
        assertTrue(corrections.rejected(1, "event", start).isEmpty())
    }
    @Test fun `failed save does not pretend a report persisted`() {
        val store = Store().apply { fail = true }
        val corrections = FixtureCorrections(store) { start }
        assertFalse(corrections.reject(1, "event", start, feed))
        assertTrue(corrections.rejected(1, "event", start).isEmpty())
    }
    @Test fun `failed restore retains the saved correction`() {
        val store = Store()
        val corrections = FixtureCorrections(store) { start }
        corrections.reject(1, "event", start, feed)
        store.fail = true
        assertFalse(corrections.restore(1, "event", start))
        assertEquals(setOf(feed), corrections.rejected(1, "event", start))
    }
    @Test fun `corrupt cache is recoverable and no raw credentials are stored`() {
        val store = Store().apply { value = "invalid JSON" }
        val corrections = FixtureCorrections(store) { start }
        assertTrue(corrections.rejected(1, "event", start).isEmpty())
        corrections.reject(1, "event", start, FixtureCorrections.channelKey(1, null, "private user password"))
        assertFalse(store.value.orEmpty().contains("private user password"))
    }
    @Test fun `storage is bounded to recent fixtures`() {
        val store = Store()
        val corrections = FixtureCorrections(store) { start }
        for (i in 0..64) corrections.reject(1, "event$i", start, feed)
        assertTrue(corrections.rejected(1, "event0", start).isEmpty())
        assertEquals(setOf(feed), corrections.rejected(1, "event64", start))
    }
}
