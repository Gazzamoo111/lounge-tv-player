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
}
