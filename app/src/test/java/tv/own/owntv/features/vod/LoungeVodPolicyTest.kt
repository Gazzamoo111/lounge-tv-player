package tv.own.owntv.features.vod

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoungeVodPolicyTest {
    @Test fun recognisedEnglishMoviesAppear() {
        assertTrue(LoungeVodPolicy.allowsMovieName("Inside Out 2"))
        assertTrue(LoungeVodPolicy.allowsMovieName("Top Gun: Maverick"))
        assertTrue(LoungeVodPolicy.allowsMovieName("Dune: Part Two"))
    }

    @Test fun recognisedEnglishSeriesAppear() {
        assertTrue(LoungeVodPolicy.allowsSeriesName("Friends"))
        assertTrue(LoungeVodPolicy.allowsSeriesName("The Office"))
        assertTrue(LoungeVodPolicy.allowsSeriesName("Wednesday"))
    }

    @Test fun providerServicePrefixesDoNotHideApprovedTitles() {
        assertTrue(LoungeVodPolicy.allowsMovieName("D+ Inside Out 2"))
        assertTrue(LoungeVodPolicy.allowsSeriesName("NF Stranger Things"))
    }

    @Test fun obscureOrUnreviewedTitlesAreNotShown() {
        assertFalse(LoungeVodPolicy.allowsMovieName("Random Unreviewed Film"))
        assertFalse(LoungeVodPolicy.allowsSeriesName("D+ Bug Juice"))
        assertFalse(LoungeVodPolicy.allowsMovieName("Oppenheimer"))
        assertFalse(LoungeVodPolicy.allowsSeriesName("Squid Game"))
    }

    @Test fun foreignAndAdultProviderBadgesOverrideAnApprovedName() {
        assertFalse(LoungeVodPolicy.allowsMovieName("Inside Out 2", "ES - Inside Out 2"))
        assertFalse(LoungeVodPolicy.allowsMovieName("Inside Out 2", "18+ Inside Out 2"))
        assertFalse(LoungeVodPolicy.allowsMovieName("Inside Out 2", "Inside Out 2 (Spanish)"))
        assertFalse(LoungeVodPolicy.allowsSeriesName("Friends", "XXX Friends"))
        assertFalse(LoungeVodPolicy.allowsMovieName("The Matrix"))
    }

    @Test fun providerLanguageCodesAreEnforced() {
        assertTrue(LoungeVodPolicy.allowsProviderLanguage(null))
        assertTrue(LoungeVodPolicy.allowsProviderLanguage("EN"))
        assertTrue(LoungeVodPolicy.allowsProviderLanguage("en"))
        assertFalse(LoungeVodPolicy.allowsProviderLanguage("ES"))
        assertFalse(LoungeVodPolicy.allowsProviderLanguage("FR"))
        assertFalse(LoungeVodPolicy.allowsProviderLanguage("HI"))
    }

    @Test fun restrictedAndForeignFoldersAreHidden() {
        assertTrue(LoungeVodPolicy.excludesCategory("18+ Movies"))
        assertTrue(LoungeVodPolicy.excludesCategory("R18 Movies"))
        assertTrue(LoungeVodPolicy.excludesCategory("ES | Spanish Movies"))
        assertTrue(LoungeVodPolicy.excludesCategory("Adult Movies"))
        assertFalse(LoungeVodPolicy.excludesCategory("New Releases"))
        assertFalse(LoungeVodPolicy.excludesCategory("Action & Adventure"))
    }
}
