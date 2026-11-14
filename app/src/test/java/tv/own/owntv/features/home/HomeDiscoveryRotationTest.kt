package tv.own.owntv.features.home
import org.junit.Assert.*
import org.junit.Test
class HomeDiscoveryRotationTest {
    @Test fun stableWithinCacheWindow() {
        val items = (0..39).toList()
        assertEquals(HomeDiscoveryRotation.rotate(items, 4, 1), HomeDiscoveryRotation.rotate(items, 4, HomeDiscoveryRotation.WINDOW_MS - 1))
        assertFalse(HomeDiscoveryRotation.expired(1, HomeDiscoveryRotation.WINDOW_MS - 1))
    }
    @Test fun rotatesAtExpiryWithoutLosingCatalogueEntries() {
        val items = (0..39).toList()
        val before = HomeDiscoveryRotation.rotate(items, 4, 0)
        val after = HomeDiscoveryRotation.rotate(items, 4, HomeDiscoveryRotation.WINDOW_MS)
        assertNotEquals(before.take(18), after.take(18))
        assertEquals(before.toSet(), after.toSet())
        assertTrue(HomeDiscoveryRotation.expired(0, HomeDiscoveryRotation.WINDOW_MS))
        assertTrue(HomeDiscoveryRotation.expired(null, 0))
    }
    @Test fun emptyAndSingletonAreSafe() {
        assertEquals(emptyList<Int>(), HomeDiscoveryRotation.rotate(emptyList<Int>(), 1, Long.MAX_VALUE))
        assertEquals(listOf(1), HomeDiscoveryRotation.rotate(listOf(1), 1, 1))
    }
}
