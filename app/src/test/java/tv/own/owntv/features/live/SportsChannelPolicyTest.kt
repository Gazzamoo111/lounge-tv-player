package tv.own.owntv.features.live
import org.junit.Assert.*
import org.junit.Test

class SportsChannelPolicyTest {
 @Test fun excludesKnownIdsAndAliasesOnly() {
  assertTrue(SportsChannelPolicy.excludedBest("646974","Renamed"))
  assertTrue(SportsChannelPolicy.excludedBest("1520168","Renamed"))
  assertTrue(SportsChannelPolicy.excludedBest(null,"AU| KAYO - ESPN 1"))
  for(n in listOf("AU| ESPN 2 HD","AU| ESPN 10","AU| ESPN 11","US| ESPN 1"))assertFalse(n,SportsChannelPolicy.excludedBest(null,n))
 }
 @Test fun suppressesOnlyObservedBadBeinGuideAliases() {
  assertTrue(SportsChannelPolicy.guideUntrusted("best:646977","Renamed"))
  assertTrue(SportsChannelPolicy.guideUntrusted(null,"AU| BEIN SPORTS 3 HD"))
  assertFalse(SportsChannelPolicy.guideUntrusted(null,"UK| BEIN SPORTS 1 EN FHD"))
  assertFalse(SportsChannelPolicy.guideUntrusted(null,"AU| FOX SPORTS 503 HD"))
 }
 @Test fun mapsFox501To507GuideIds() {
  assertEquals("FOXCricket.au", SportsChannelPolicy.foxGuideId("best:646971","Renamed"))
  assertEquals("FoxLeague.au", SportsChannelPolicy.foxGuideId(null,"AU| KAYO - FOX SPORTS 502 LEAUGE"))
  assertEquals("FoxSports3.au", SportsChannelPolicy.foxGuideId("646969","AU| FOX SPORTS 503 HD"))
  assertEquals("FoxFooty.au", SportsChannelPolicy.foxGuideId("1520170","Renamed"))
  assertEquals("FoxSports5.au", SportsChannelPolicy.foxGuideId("646967","Renamed"))
  assertEquals("FoxSports6.au", SportsChannelPolicy.foxGuideId("646966","Renamed"))
  assertEquals("FoxSportsMore.au", SportsChannelPolicy.foxGuideId("646965","Renamed"))
  assertNull(SportsChannelPolicy.foxGuideId(null,"AU| ESPN 2 HD"))
 }
}
