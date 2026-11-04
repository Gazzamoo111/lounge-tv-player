package tv.own.owntv.features.sports

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier

class BackendSportsCandidatesTest {
    private fun option(id: Long, tier: WatchTier, marker: String? = null) = WatchOption(
        channel = ChannelEntity(id=id, sourceId=1L, name="service $id", streamUrl="https://example.invalid/$id"),
        tier=tier, guideTitle=marker,
    )
    @Test fun `R08 legacy confidence never promotes verification`() {
        assertEquals(WatchTier.LIKELY, validatedBackendTier(1, "definite", false))
        assertEquals(WatchTier.LIKELY, validatedBackendTier(2, "verified", false))
        assertEquals(WatchTier.GUIDE, validatedBackendTier(2, "verified", true))
    }
    @Test fun `R06 local guide survives weak backend and unavailable backend`() {
        val local=listOf(option(1,WatchTier.GUIDE),option(2,WatchTier.LIKELY))
        assertEquals(local,mergeBackendSportsOptions(emptyList(),local))
        val merged=mergeBackendSportsOptions(listOf(option(3,WatchTier.LIKELY)),local)
        assertEquals(1L,merged.first().channel.id)
        assertTrue(merged.any{it.channel.id==2L})
    }
    @Test fun `assignment evidence never makes a channel verified`() {
        assertFalse(evidenceSupportsClaim("verified", "assignment"))
        assertTrue(evidenceSupportsClaim("plausible", "assignment"))
        assertTrue(evidenceSupportsClaim("verified", "programme"))
        assertTrue(evidenceSupportsClaim("verified", "event-feed"))
        assertFalse(evidenceSupportsClaim("unverified", "programme"))
    }
    @Test fun `recommendations are bounded to eight`() {
        val merged=mergeBackendSportsOptions((1L..20L).map{option(it,WatchTier.LIKELY)}, emptyList())
        assertEquals(8,merged.size)
    }
}
