package tv.own.owntv.features.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.WatchFilter
import tv.own.owntv.core.sports.WatchList
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier

class BackendSportsCandidatesTest {
    private fun option(id: Long, name: String, tier: WatchTier, marker: String? = null) =
        WatchOption(
            channel = ChannelEntity(id = id, sourceId = 1L, name = name, streamUrl = "https://example.invalid/" + id),
            tier = tier,
            guideTitle = marker,
        )

    @Test
    fun `backend confidence order wins and weak local guesses are dropped`() {
        val backend = listOf(
            option(-1, "Exact event", WatchTier.GUIDE, BACKEND_CONFIDENCE_PREFIX + "definite"),
            option(-2, "Likely broadcaster", WatchTier.EVENT, BACKEND_CONFIDENCE_PREFIX + "likely"),
            option(-3, "Maybe network", WatchTier.LIKELY, BACKEND_CONFIDENCE_PREFIX + "maybe"),
        )
        val local = listOf(
            option(1, "Guide confirmed", WatchTier.GUIDE),
            option(2, "Music Choice Jazz", WatchTier.LIKELY),
        )

        val merged = mergeBackendSportsOptions(backend, local)
        val shown = WatchList.of(merged, WatchFilter()).shown

        assertEquals(listOf("Exact event", "Guide confirmed", "Likely broadcaster", "Maybe network"), shown.map { it.channel.name })
        assertFalse(shown.any { it.channel.name == "Music Choice Jazz" })
    }

    @Test
    fun `local results remain intact when backend is unavailable`() {
        val local = listOf(option(1, "NBA TV", WatchTier.LIKELY))
        assertEquals(local, mergeBackendSportsOptions(emptyList(), local))
    }
}
