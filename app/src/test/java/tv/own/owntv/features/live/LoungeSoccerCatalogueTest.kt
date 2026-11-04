package tv.own.owntv.features.live
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
class LoungeSoccerCatalogueTest {
 @Test fun inactiveNoiseIsNotAdmittedAndSourceScopeIsPreserved() {
  val noise=(1L..300L).map{ChannelEntity(id=it,sourceId=1,name="Soccer PPV $it",remoteId=it.toString(),streamUrl="https://example.invalid/$it")}
  val permanent=ChannelEntity(id=500,sourceId=1,name="AU| beIN Sports 1",remoteId="best:646977",streamUrl="https://example.invalid/500")
  val uk=ChannelEntity(id=501,sourceId=1,name="UK| SKY SPORTS FOOTBALL FHD",remoteId="best:30498",streamUrl="https://example.invalid/501")
  val current=listOf(LoungeSoccerCatalogue.EventEvidence(1,true,1000,10000,2000),LoungeSoccerCatalogue.EventEvidence(2,true,1000,10000,2000))
  assertEquals(listOf(500L,501L,1L,2L),LoungeSoccerCatalogue.project(noise+permanent+uk,setOf(1),current,3000).map{it.id})
  assertEquals(listOf(500L,501L),LoungeSoccerCatalogue.project(noise+permanent+uk,setOf(1),current,11000).map{it.id})
  assertTrue(LoungeSoccerCatalogue.project(noise+permanent+uk,emptySet(),current,3000).isEmpty())
 }
 @Test fun realCataloguedServicesPopulateSoccerForActiveLiveSources() {
  fun ch(id:Long,source:Long,name:String,remote:String) =
   ChannelEntity(id=id,sourceId=source,name=name,remoteId=remote,streamUrl="https://example.invalid/$id")
  val live=82L
  val all=listOf(
   ch(501,live,"AU| BEIN SPORTS 1 HD","646977"),
   ch(502,live,"AU| BEIN SPORTS 1 FHD","646977"),
   ch(503,live,"UK| TNT SPORTS 1 FHD","30567"),
   ch(504,live,"UK| SKY SPORTS FOOTBALL FHD","30498"),
   ch(505,live,"AU| ESPN 1 HD","646974"),
   ch(506,live,"AU| KAYO - ESPN 1","1520168"),
   ch(507,live,"Soccer PPV 105","other:105"),
   ch(508,1,"AU| BEIN SPORTS 2 HD","646976"),
  )
  assertEquals(listOf(501L,504L,503L),LoungeSoccerCatalogue.project(all,setOf(live),emptyList(),3000).map{it.id})
  assertTrue(LoungeSoccerCatalogue.project(all,setOf(999L),emptyList(),3000).isEmpty())
 }

}
