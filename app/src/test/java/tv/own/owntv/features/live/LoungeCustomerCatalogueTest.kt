package tv.own.owntv.features.live
import org.junit.Assert.*
import org.junit.Test
class LoungeCustomerCatalogueTest {
    @Test fun ppvHiddenAcrossCanonicalAndProviderFolders() {
        listOf("PPV · Boxing", "US| ESPN+ PPV VIP", "Pay-per-view").forEach { assertTrue(it, LoungeCustomerCatalogue.internalOnly(it)) }
        listOf("Sports · US Sports", "Kids & Family · Movies", "New Zealand · Freeview").forEach { assertFalse(it, LoungeCustomerCatalogue.internalOnly(it)) }
    }
    @Test fun originalClassificationRetainsInternalSportsIdentity() {
        assertEquals(LoungeLiveSection.PPV, loungeLiveSectionOf("US| ESPN+ PPV VIP"))
    }
    @Test fun editorialChildrenHaveStablePriorities() {
        assertEquals(0, LoungeEditorialOrder.categoryPriority(LoungeLiveSection.SPORTS, "Sports · US Sports"))
        assertEquals(1, LoungeEditorialOrder.categoryPriority(LoungeLiveSection.SPORTS, "Sports · UK Sports"))
        assertEquals(2, LoungeEditorialOrder.categoryPriority(LoungeLiveSection.SPORTS, "Sports · Tennis"))
        assertEquals(0, LoungeEditorialOrder.categoryPriority(LoungeLiveSection.KIDS, "Movies"))
    }
}
