package tv.own.owntv.features.sports

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.*

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

    private fun soccerMatch() = Match(
        "regression", Sport.SOCCER, "soccer/uefa.champions", "uefa.champions", "UEFA Champions League",
        1_000_000, MatchState.IN, "IN", "Liverpool v Real Madrid", competitors=listOf(
            Competitor(Team("1","Liverpool"),true,null,null),
            Competitor(Team("2","Real Madrid"),false,null,null),
        ),
    )
    @Test fun `generic networks cannot flood local merge but supported Stan guide survives`() {
        val generic=(1L..200L).map { option(it,WatchTier.LIKELY).let { o -> o.copy(channel=o.channel.copy(name="US| PARAMOUNT PLUS $it")) } }
        val stan=option(500,WatchTier.GUIDE,"Liverpool v Real Madrid").let { it.copy(channel=it.channel.copy(name="AU| STAN SPORT 1 HD")) }
        val merged=mergeBackendSportsOptions(soccerMatch(),emptyList(),generic+stan)
        assertEquals(listOf(500L),merged.map{it.channel.id})
        assertEquals(WatchTier.GUIDE,merged.single().tier)
    }
    @Test fun `valid local soccer alternative survives failed backend without trusting contradictory guide`() {
        val bein=option(501,WatchTier.GUIDE,"ATP Masters 1000").let { it.copy(channel=it.channel.copy(name="AU| BEIN SPORTS 1 HD",remoteId="646977")) }
        val merged=mergeBackendSportsOptions(soccerMatch(),emptyList(),listOf(bein))
        assertEquals(501L,merged.single().channel.id);assertEquals(WatchTier.LIKELY,merged.single().tier)
        assertNull(merged.single().guideTitle)
    }
}
