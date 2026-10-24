package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class LoungeEventDiscoveryTest {
    private val now = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-10-25")!!.time

    @Test fun recognisesDatedUfcAndRejectsSideEvents() {
        assertEquals("UFC 333", LoungeEventDiscovery.parse("US (Paramount 090) | UFC 333: English (2026-10-24 09:00:00)", now)?.title)
        assertNull(LoungeEventDiscovery.parse("UFC 333: Press Conference (2026-10-24 09:00:00)", now))
        assertNull(LoungeEventDiscovery.parse("UFC 333: Ceremonial Weigh_In (2026-10-24 09:00:00)", now))
        assertNull(LoungeEventDiscovery.parse("UFC 333 em Português (2026-10-24 09:00:00)", now))
    }

    @Test fun neverPromotesUnscheduledOrStaleNumberedPpvSlots() {
        assertNull(LoungeEventDiscovery.parse("UK PPV 001", now))
        assertNull(LoungeEventDiscovery.parse("UFC 275 English", now))
        assertNull(LoungeEventDiscovery.parse("UFC 275 English 2022-06-11", now))
        assertNull(LoungeEventDiscovery.parse("UFC 333 English 2026-02-31", now))
        assertEquals("Fury vs Usyk", LoungeEventDiscovery.parse("Matchroom Boxing Fury vs Usyk 2026-10-25", now)?.title)
    }

    @Test fun groupsCandidateAlternativesAndPrefersEnglishButDoesNotVerifyThem() {
        val names = listOf(
            "US UFC 333 HD English 2026-10-24",
            "US UFC 333 2026-10-24",
            "US UFC 333 HD English 2026-10-24",
            "UFC 333 Multiview 2026-10-24",
            "BKFC 89 English 2026-10-25",
            "UFC 312 English 2025-02-08",
        )
        val grouped = LoungeEventDiscovery.group(names, now, { it }, { it })
        assertEquals(2, grouped.size)
        assertEquals("UFC 333", grouped.first { it.title == "UFC 333" }.title)
        val ufc = grouped.first { it.title == "UFC 333" }
        assertEquals(2, ufc.feeds.size)
        assertTrue(ufc.feeds.first().contains("HD English"))
    }
}
