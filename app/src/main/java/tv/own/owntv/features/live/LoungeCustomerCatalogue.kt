package tv.own.owntv.features.live

/** Presentation policy only. Sports readers keep the original database and source URLs. */
internal object LoungeCustomerCatalogue {
    private val ppv = Regex("""\bppv\b|\bpay[ -]+per[ -]+view\b""", RegexOption.IGNORE_CASE)
    fun internalOnly(category: String?): Boolean = category != null &&
        (ppv.containsMatchIn(category) || loungeLiveSectionOf(category) == LoungeLiveSection.PPV)
}
