package tv.own.owntv.features.customize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouriteFoldersTest {
    @Test fun normalisesRemoteKeyboardNames() {
        assertEquals("Favourites · Rugby", FavouriteFolders.canonicalName("   Rugby  "))
        assertEquals("Favourites · Family Sports", FavouriteFolders.canonicalName("Family   Sports"))
        assertEquals("Favourites · Rugby", FavouriteFolders.canonicalName("favourites · Rugby"))
        assertNull(FavouriteFolders.canonicalName("   "))
        assertNull(FavouriteFolders.canonicalName("Favourites · "))
    }

    @Test fun onlyTaggedProfileCategoriesAreFolders() {
        assertTrue(FavouriteFolders.isFolder("Favourites · Sport"))
        assertTrue(FavouriteFolders.isFolder("favourites · Family"))
        assertFalse(FavouriteFolders.isFolder("Favourites ·  "))
        assertFalse(FavouriteFolders.isFolder("Sports"))
        assertEquals("Sport", FavouriteFolders.displayName("Favourites · Sport"))
        assertEquals("Sports", FavouriteFolders.displayName("Sports"))
    }

    @Test fun namesAreCaseInsensitiveButUnrelatedCategoriesStaySeparate() {
        assertTrue(FavouriteFolders.sameFolder("Favourites · Sport", "favourites · SPORT"))
        assertFalse(FavouriteFolders.sameFolder("Favourites · Sport", "Favourites · News"))
        assertFalse(FavouriteFolders.sameFolder("Sport", "Favourites · Sport"))
    }

    @Test fun titlesFitTvCategoryRails() {
        val named = FavouriteFolders.canonicalName("a".repeat(90))!!
        assertEquals(FavouriteFolders.PREFIX.length + 48, named.length)
        assertEquals("a".repeat(48), FavouriteFolders.displayName(named))
    }
}
