package tv.own.owntv.ui.components

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
        "|EN| NEW RELEASED", "|EN| CLASSIC MOVIES COLLECTION",
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

    private val movieLabels = mapOf(
        "|EN| NEW RELEASED" to "New Releases",
        "|EN| ACTION/THRILLER" to "Action & Thrillers",
        "|EN| HORROR/THRILLER" to "Horror & Thrillers",
        "|EN| SCIENCE FICTION/FANTASTY" to "Sci-Fi & Fantasy",
        "|EN| ADVENTURE MOVIES" to "Adventure",
        "|EN| CHILDREN/FAMILY" to "Family",
        "|EN| CLASSIC MOVIES COLLECTION" to "Classics",
        "|EN| DRAMA/ROMANCE" to "Romance & Drama",
        "|EN| DRAMA/CRIME" to "Crime Drama",
        "|EN| DRAMA/COMEDY" to "Comedy-Drama",
        "|EN| DOCUMENTARY" to "Documentary",
        "|EN| GANGSTER & MAFIA" to "Crime & Gangster",
        "|EN| CHRISTMAS MOVIES" to "Christmas",
        "|EN| NETFLIX" to "Netflix Picks",
        "NETFLIX MOVIES" to "Netflix Movies",
        "|EN| DISNEY+" to "Disney+ Picks",
        "|EN| PRIME+" to "Prime Video",
        "|EN| HBO MAX" to "HBO / Max",
        "|EN| 4K MOVIES" to "4K Movies",
        "|EN| ANIME" to "Anime",
        "|EN| CLASSIC CARTOONS" to "Classic Animation",
        "|EN| ACTORS & DIRECTORS" to "Actors & Directors",
        "|EN| MOVIES 2018-2021" to "Movies 2018–2021",
        "TOP IMDB/OSCAR MOVIES" to "Award Winners",
        "|EN| PRE-RELEASES/SD CAM" to "Pre-Releases",
        "|EN| BROADWAY/MUSICAL" to "Musicals",
        "|EN| MUSIC/CONCERTS" to "Music & Concerts",
        "|EN| BET/ALLBLK MOVIES" to "BET / ALLBLK",
        "|EN| TV MOVIES" to "TV Movies",
    )
    private val seriesLabels = mapOf(
        "|EN| ENGLISH SERIES" to "English Series",
        "|EN| LATEST RELEASES" to "New Series",
        "|EN| TOP SERIES" to "Top Series",
        "|EN| REALITY SHOWS" to "Reality",
        "|EN| DOCUMENTARIES" to "Documentary",
        "|EN| COMEDY SERIES" to "Comedy",
        "|EN| GANGSTER & MAFIA" to "Crime & Gangster",
        "|EN| CLASSIC SERIES" to "Classics",
        "|EN| ENGLISH KIDS" to "Kids & Family",
        "|EN| DISNEY+ ANIMATION" to "Animation",
        "|EN| CLASSIC CARTOONS" to "Classic Animation",
        "|EN| AMAZON PRIME" to "Prime Video",
        "|EN| APPLE TV+" to "Apple TV+",
        "|EN| AMC+/MGM+" to "AMC+ / MGM+",
        "|EN| NBC/PEACOCK" to "NBC / Peacock",
        "|EN| CBS/PARAMOUNT+" to "CBS / Paramount+",
        "|EN| HULU/FX/FOX/ABC" to "Hulu / FX / FOX / ABC",
        "|EN| BET/ALLBLK SERIES" to "BET / ALLBLK",
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

    private fun displayName(name: String, kind: Kind): String {
        val original = canonical(name)
        (if (kind == Kind.MOVIE) movieLabels else seriesLabels)[original]?.let { return it }
        val plain = name.trim()
            .replace(Regex("""(?i)^\s*\|(?:EN|MULTI)\|\s*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '|')
        return plain.split(' ').joinToString(" ") { token ->
            if (token.length <= 3 && token.all { it.isLetter() } && token == token.uppercase()) token
            else token.lowercase().replaceFirstChar { it.titlecase() }
        }.replace("Netflix", "Netflix").replace("Disney+", "Disney+")
    }

    fun plan(kind: Kind, folders: List<Folder>): Plan {
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
        val labels = visible.associate { it.key to displayName(it.name, kind) }
        val prioritized = (if (kind == Kind.MOVIE) moviePriority else seriesPriority)
            .mapNotNull { desired ->
                visible.firstOrNull { canonical(it.name) == desired }?.key
            }
        return Plan(hidden.mapTo(mutableSetOf()) { it.id }, hiddenKeys, labels, prioritized)
    }
}
