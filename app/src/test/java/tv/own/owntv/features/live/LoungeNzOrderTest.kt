package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoungeNzOrderTest {
    @Test fun essentialFreeviewChannelsSortAheadOfAllOptionalChannels() {
        val names = listOf("HGTV", "JuiceTV", "Whakaata Māori", "Three",
            "Te Reo", "TVNZ 2", "TVNZ 1", "Bravo")
        val sorted = names.withIndex()
            .sortedWith(compareBy<IndexedValue<String>>(
                { LoungeNzOrder.priority(it.value) }, { it.index },
            )).map { it.value }
        assertEquals(listOf("TVNZ 1", "TVNZ 2", "Three", "Whakaata Māori", "Te Reo"),
            sorted.take(5))
        assertTrue(sorted.indexOf("HGTV") >= 5)
    }
}
