package tv.own.owntv.ui.components

import tv.own.owntv.R

/**
 * Reversible, source-scoped customer-facing cleanup for the audited BEST VOD catalogue.
 * Matches actual provider category names, not Room row IDs or playlist-local IDs.
 * Do not apply these rules to a different provider merely because it has a category
 * named "Action", "Asia" or "Movies".
 */
internal object LoungeVodCatalogueCleanup {
    enum class Kind { MOVIE, SERIES }

    data class Folder(
        val id: Long,
        val sourceId: Long,
        val key: String,
        val name: String,
        /** Null means a count has not yet been observed; never treat it as zero. */
        val count: Int?,
    )

    data class Plan(
        val hiddenIds: Set<Long> = emptySet(),
        val hiddenKeys: Set<String> = emptySet(),
        val names: Map<String, String> = emptyMap(),
        val priorityKeys: List<String> = emptyList(),
    )

    private fun canonical(name: String) = name.trim().uppercase()
        .replace(Regex("""\s+"""), " ")

    // Distinctive folder names present in the uploaded 27-Oct audit. Recognise a
    // source only after several independent matches, rather than guessing its ID.
    private val movieSignatures = setOf(
        "|EN| DRAMA", "|EN| NEW RELEASED", "|EN| CLASSIC MOVIES COLLECTION",
        "|EN| SCIENCE FICTION/FANTASTY", "|EN| ACTORS & DIRECTORS",
        "|EN| DRAMA/CRIME", "|EN| HORROR/THRILLER",
        "|EN| CHILDREN/FAMILY", "|EN| ADVENTURE MOVIES",
        "|EN| GANGSTER & MAFIA", "|EN| MOVIES 2018-2021",
        "|EN| FRENCH MOVIES / ENG SUB", "|EN| ITALIAN MOVIES / ENG SUB",
    )
    private val seriesSignatures = setOf(
        "|EN| ENGLISH SERIES", "|EN| LATEST RELEASES",
        "|EN| GANGSTER & MAFIA", "|EN| REALITY SHOWS",
        "|EN| TURKISH SERIES SUB", "|EN| ASIAN SERIES",
        "|EN| DOCUMENTARIES", "|EN| CLASSIC SERIES",
        "|MULTI| NETFLIX SERIES", "|MULTI| NETFLIX ASIA",
        "|EN| ENGLISH KIDS", "|EN| COMEDY SERIES",
    )
    private val explicitForeignCategory = Regex(
        """(?i)\b(?:turkish|asian|asia|indian|hindi|tamil|telugu|malayalam|bengali|punjabi|french|italian|foreign|spanish|german|portuguese|arabic|russian|korean|japanese|chinese|mandarin|urdu|persian|thai|vietnamese|indonesian|polish|subtitled)\b|\beng\s+sub\b"""
    )
    private val excludedCategory = Regex(
        """(?i)\b(?:adult|xxx|18\+|erotic|biblical|religious|church|sermons|gospel)\b"""
    )
    private val nonEnglishTag = Regex(
        """(?i)^\s*\|(?:TR|TUR|AS|HI|HIN|TA|TE|ML|BN|PA|ES|ESP|PT|POR|FR|FRA|DE|GER|IT|ITA|RU|RUS|AR|ARA|JA|JP|JPN|KO|KOR|ZH|CN|CHN|TH|VI|ID|PL)\|"""
    )
    // Explicit provider markers only. Never infer language from a country's name in
    // a genuine title, poster, cast, or filming location.
    private val foreignTitlePrefix = Regex(
        """(?i)^\s*(?:\[(?:TR|TUR|HI|HIN|TA|TE|ES|ESP|PT|POR|FR|FRA|DE|GER|IT|ITA|RU|RUS|AR|ARA|JA|JPN|KO|KOR|ZH|CHN|TH|VI|ID)\]\s*|(?:TR|TUR|HI|HIN|TA|TE|ES|ESP|PT|POR|FR|FRA|DE|GER|IT|ITA|RU|RUS|AR|ARA|JA|JPN|KO|KOR|ZH|CHN|TH|VI|ID)\s*[-:|]\s*)"""
    )

    fun explicitlyNonEnglishTitle(providerName: String): Boolean =
        foreignTitlePrefix.containsMatchIn(providerName)

    /** Provider-supplied audio language is authoritative where present. Unknown is not
     *  inferred from a title's nationality; MULTI without English proof is excluded. */
    fun visibleEnglish(providerName: String, providerLanguage: String?): Boolean {
        if (explicitlyNonEnglishTitle(providerName)) return false
        val language = providerLanguage?.trim()?.lowercase().orEmpty()
        if (language.isBlank() || language in setOf("unknown", "und", "n/a")) return true
        val english = setOf("en", "eng", "english")
        val tokens = language.split(Regex("""[,;/|+]""")).map { it.trim().substringBefore('-').substringBefore('_') }
        if (tokens.all { it in english }) return true
        val foreign = setOf(
            "multi", "multilingual", "es", "spa", "spanish", "pt", "por", "portuguese",
            "fr", "fra", "fre", "french", "de", "deu", "ger", "german",
            "it", "ita", "italian", "ru", "rus", "russian", "tr", "tur", "turkish",
            "ar", "ara", "arabic", "hi", "hin", "hindi", "ta", "tam", "tamil",
            "te", "tel", "telugu", "ml", "mal", "bn", "ben", "pa", "pan", "urdu", "ur", "urd",
            "ja", "jpn", "jp", "japanese", "ko", "kor", "korean", "zh", "zho", "chi", "chinese",
            "th", "tha", "thai", "vi", "vie", "vietnamese", "id", "ind", "indonesian",
            "pl", "pol", "polish", "fa", "fas", "persian",
        )
        return tokens.none { it in foreign }
    }

    private val movieLabels = mapOf(
        "|EN| NEW RELEASED" to R.string.lounge_movies_new_releases,
        "|EN| ACTION/THRILLER" to R.string.catalogue_action_thrillers,
        "|EN| HORROR/THRILLER" to R.string.catalogue_horror_thrillers,
        "|EN| SCIENCE FICTION/FANTASTY" to R.string.catalogue_sci_fi_fantasy,
        "|EN| ADVENTURE MOVIES" to R.string.catalogue_adventure,
        "|EN| CHILDREN/FAMILY" to R.string.catalogue_family,
        "|EN| CLASSIC MOVIES COLLECTION" to R.string.catalogue_classics,
        "|EN| DRAMA/ROMANCE" to R.string.catalogue_romance_drama,
        "|EN| DRAMA/CRIME" to R.string.catalogue_crime_drama,
        "|EN| DRAMA/COMEDY" to R.string.catalogue_comedy_drama,
        "|EN| DOCUMENTARY" to R.string.catalogue_documentary,
        "|EN| GANGSTER & MAFIA" to R.string.catalogue_crime_gangster,
        "|EN| CHRISTMAS MOVIES" to R.string.catalogue_christmas,
        "|EN| NETFLIX" to R.string.catalogue_netflix_picks,
        "NETFLIX MOVIES" to R.string.catalogue_netflix_movies,
        "|EN| DISNEY+" to R.string.catalogue_disney_plus_picks,
        "|EN| PRIME+" to R.string.catalogue_prime_video,
        "|EN| HBO MAX" to R.string.catalogue_hbo_max,
        "|EN| 4K MOVIES" to R.string.catalogue_four_k_movies,
        "|EN| ANIME" to R.string.catalogue_anime,
        "|EN| CLASSIC CARTOONS" to R.string.catalogue_classic_animation,
        "|EN| ACTORS & DIRECTORS" to R.string.catalogue_actors_directors,
        "|EN| MOVIES 2018-2021" to R.string.catalogue_movies_2018_2021,
        "TOP IMDB/OSCAR MOVIES" to R.string.catalogue_award_winners,
        "|EN| PRE-RELEASES/SD CAM" to R.string.catalogue_pre_releases,
        "|EN| BROADWAY/MUSICAL" to R.string.catalogue_musicals,
        "|EN| MUSIC/CONCERTS" to R.string.catalogue_music_concerts,
        "|EN| BET/ALLBLK MOVIES" to R.string.catalogue_bet_allblk,
        "|EN| TV MOVIES" to R.string.catalogue_tv_movies,
    )
    private val seriesLabels = mapOf(
        "|EN| ENGLISH SERIES" to R.string.catalogue_english_series,
        "|EN| LATEST RELEASES" to R.string.lounge_series_new_releases,
        "|EN| TOP SERIES" to R.string.catalogue_top_series,
        "|EN| REALITY SHOWS" to R.string.catalogue_reality,
        "|EN| DOCUMENTARIES" to R.string.catalogue_documentary,
        "|EN| COMEDY SERIES" to R.string.catalogue_comedy,
        "|EN| GANGSTER & MAFIA" to R.string.catalogue_crime_gangster,
        "|EN| CLASSIC SERIES" to R.string.catalogue_classics,
        "|EN| ENGLISH KIDS" to R.string.catalogue_kids_family,
        "|EN| DISNEY+ ANIMATION" to R.string.catalogue_animation,
        "|EN| CLASSIC CARTOONS" to R.string.catalogue_classic_animation,
        "|EN| AMAZON PRIME" to R.string.catalogue_prime_video,
        "|EN| APPLE TV+" to R.string.catalogue_apple_tv_plus,
        "|EN| AMC+/MGM+" to R.string.catalogue_amc_plus_mgm_plus,
        "|EN| NBC/PEACOCK" to R.string.catalogue_nbc_peacock,
        "|EN| CBS/PARAMOUNT+" to R.string.catalogue_cbs_paramount_plus,
        "|EN| HULU/FX/FOX/ABC" to R.string.catalogue_hulu_fx_fox_abc,
        "|EN| BET/ALLBLK SERIES" to R.string.catalogue_bet_allblk,
    )
    private val moviePriority = listOf(
        "|EN| NEW RELEASED", "|EN| ACTION/THRILLER", "|EN| COMEDY", "|EN| DRAMA",
        "|EN| HORROR/THRILLER", "|EN| SCIENCE FICTION/FANTASTY",
        "|EN| ADVENTURE MOVIES", "|EN| CHILDREN/FAMILY", "|EN| DOCUMENTARY",
        "|EN| DRAMA/CRIME", "|EN| DRAMA/ROMANCE", "|EN| CLASSIC MOVIES COLLECTION",
    )
    private val seriesPriority = listOf(
        "|EN| LATEST RELEASES", "|EN| ENGLISH SERIES", "|EN| COMEDY SERIES",
        "|EN| GANGSTER & MAFIA", "|EN| DOCUMENTARIES", "|EN| REALITY SHOWS",
        "|EN| ENGLISH KIDS", "|EN| UK SERIES", "|EN| TOP SERIES",
    )

    private fun displayName(name: String, kind: Kind, localize: (Int) -> String): String {
        val original = canonical(name)
        (if (kind == Kind.MOVIE) movieLabels else seriesLabels)[original]?.let { return localize(it) }
        val plain = name.trim()
            .replace(Regex("""(?i)^\s*\|(?:EN|MULTI)\|\s*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '|')
        return plain.split(' ').joinToString(" ") { token ->
            if (token.length <= 3 && token.all { it.isLetter() } && token == token.uppercase()) token
            else token.lowercase().replaceFirstChar { it.titlecase() }
        }.replace("Netflix", "Netflix").replace("Disney+", "Disney+")
    }

    fun plan(kind: Kind, folders: List<Folder>, localize: (Int) -> String = { it.toString() }): Plan {
        val signatures = if (kind == Kind.MOVIE) movieSignatures else seriesSignatures
        val qualifiedSources = folders.groupBy { it.sourceId }
            .filterValues { sourceFolders ->
                sourceFolders.count { canonical(it.name) in signatures } >= 6
            }.keys
        if (qualifiedSources.isEmpty()) return Plan()

        val ours = folders.filter { it.sourceId in qualifiedSources }
        val hidden = ours.filter { folder ->
            val raw = canonical(folder.name)
            (folder.count != null && folder.count < 10) ||
                raw.startsWith("|MULTI|") ||
                nonEnglishTag.containsMatchIn(raw) ||
                explicitForeignCategory.containsMatchIn(raw) ||
                excludedCategory.containsMatchIn(raw)
        }
        val hiddenKeys = hidden.mapTo(mutableSetOf()) { it.key }
        val visible = ours.filterNot { it.key in hiddenKeys }
        val labels = visible.associate { it.key to displayName(it.name, kind, localize) }
        val prioritized = (if (kind == Kind.MOVIE) moviePriority else seriesPriority)
            .mapNotNull { desired ->
                visible.firstOrNull { canonical(it.name) == desired }?.key
            }
        return Plan(hidden.mapTo(mutableSetOf()) { it.id }, hiddenKeys, labels, prioritized)
    }
}
