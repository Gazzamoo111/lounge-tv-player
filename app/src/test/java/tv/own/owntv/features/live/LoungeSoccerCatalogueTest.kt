package tv.own.owntv.features.live
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
class LoungeSoccerCatalogueTest {
 @Test fun inactiveNoiseIsNotAdmittedAndSourceScopeIsPreserved() {
  val noise=(1L..300L).map{ChannelEntity(id=it,sourceId=1,name="Soccer PPV $it",remoteId=it.toString(),streamUrl="https://example.invalid/$it")}
  val permanent=ChannelEntity(id=500,sourceId=1,name="AU| beIN Sports 1",remoteId="best:646977",streamUrl="https://example.invalid/500")
  val uk=ChannelEntity(id=501,sourceId=1,name="UK| SKY SPORTS FOOTBALL FHD",remoteId="best:30498",streamUrl="https://example.invalid/501")\n  val current=listOf(LoungeSoccerCatalogue.EventEvidence(1,true,1000,10000,2000),LoungeSoccerCatalogue.EventEvidence(2,true,1000,10000,2000))
  assertEquals(listOf(500L,501L,1L,2L),LoungeSoccerCatalogue.project(noise+permanent+uk,setOf(1),current,3000).map{it.id})
  assertEquals(listOf(500L,501L),LoungeSoccerCatalogue.project(noise+permanent+uk,setOf(1),current,11000).map{it.id})
  assertTrue(LoungeSoccerCatalogue.project(noise+permanent+uk,emptySet(),current,3000).isEmpty())
 }
}
