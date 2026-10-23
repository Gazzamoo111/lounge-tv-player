package tv.own.owntv.features.home

import kotlinx.coroutines.flow.first
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.metadata.TitleNormalizer
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository

/** Read-only, bounded first-login shelves from the customer's own provider catalogue. */
internal data class LoungeHomeHighlights(
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val channels: List<ChannelEntity> = emptyList(),
    val movieNames: Map<Long, String> = emptyMap(),
    val seriesNames: Map<Long, String> = emptyMap(),
)

internal class LoungeHomeHighlightsReader(
    private val movies: MovieDao,
    private val series: SeriesDao,
    private val channels: ChannelDao,
    private val categories: CategoryDao,
    private val profiles: ProfileDao,
    private val sources: SourceDao,
    private val settings: SettingsRepository,
    private val customizations: CustomizationStore,
) {
    suspend fun load(profileId: Long): LoungeHomeHighlights {
        val movieIds = activeSourceIds(settings, sources, profileId, MediaType.MOVIE).toList()
        val seriesIds = activeSourceIds(settings, sources, profileId, MediaType.SERIES).toList()
        val channelIds = activeSourceIds(settings, sources, profileId, MediaType.LIVE).toList()
        val kids = profiles.getById(profileId)?.isKids == true

        val movieVisibility = visibleFor(profileId, movieIds, MediaType.MOVIE, kids)
        val seriesVisibility = visibleFor(profileId, seriesIds, MediaType.SERIES, kids)
        val channelVisibility = visibleFor(profileId, channelIds, MediaType.LIVE, kids)

        // A provider's numeric ratings aren't a measure of popularity: their highest-rated
        // films were obscure titles on BEST. Only pick recognisable editorially curated titles,
        // and only when an exact playable match exists in the customer's own playlist.
        val moviePicks = pickFamous(
            movieIds, POPULAR_MOVIES,
            query = { title -> movies.searchListFts(phrase(title), movieIds, 15) },
            key = { item: MovieEntity -> titleKey(item.canonicalTitle.ifBlank { item.name }) },
            valid = { item: MovieEntity ->
                item.posterUrl?.isNotBlank() == true &&
                    movieVisibility.allows(item.categoryId, CustomizeKeys.movie(item))
            },
            id = { item: MovieEntity -> item.id },
            quality = { item: MovieEntity -> item.qualityRank },
        )
        val seriesPicks = pickFamous(
            seriesIds, POPULAR_SERIES,
            query = { title -> series.searchListFts(phrase(title), seriesIds, 15) },
            key = { item: SeriesEntity -> titleKey(item.canonicalTitle.ifBlank { item.name }) },
            valid = { item: SeriesEntity ->
                item.posterUrl?.isNotBlank() == true &&
                    seriesVisibility.allows(item.categoryId, CustomizeKeys.series(item))
            },
            id = { item: SeriesEntity -> item.id },
            quality = { item: SeriesEntity -> item.qualityRank },
        )

        // Every result comes from the installed provider, never a decorative remote feed.
        // Use short targeted searches rather than materialising a 16k-channel catalogue.
        val highlights = mutableListOf<ChannelEntity>()
        if (channelIds.isNotEmpty()) {
            val searches = listOf(
                "TVNZ 1", "TVNZ 2", "Three", "Sky Sport",
                "Fox League", "Fox Cricket", "TNT Sports",
                "ESPN", "BBC One", "Discovery", "National Geographic",
            )
            for (term in searches) {
                val found = channels.searchList(term, channelIds, 10)
                    .filter { channelVisibility.allows(it.categoryId, CustomizeKeys.channel(it)) }
                    .filter { it.name.contains(term, ignoreCase = true) }
                    .sortedWith(compareByDescending<ChannelEntity> {
                        it.name.contains("RAW", ignoreCase = true)
                    }.thenByDescending {
                        it.name.contains("HD", ignoreCase = true)
                    })
                highlights += found.take(if (term == "Sky Sport") 3 else 2)
                if (highlights.size >= 20) break
            }
        }

        return LoungeHomeHighlights(
            movies = moviePicks.map { it.first },
            series = seriesPicks.map { it.first },
            channels = highlights.distinctBy { it.id }.take(20),
            movieNames = moviePicks.associate { it.first.id to it.second },
            seriesNames = seriesPicks.associate { it.first.id to it.second },
        )
    }

    private suspend fun visibleFor(
        profileId: Long,
        sourceIds: List<Long>,
        type: MediaType,
        kids: Boolean,
    ): Visibility {
        if (sourceIds.isEmpty()) return Visibility()
        val customization = customizations.observe(profileId, type).first()
        val hiddenCategories = AdultCategoryClassifier.hiddenCategoryIds(
            categories.observe(sourceIds, type).first(),
            customization.hiddenCategories,
            kids,
        )
        return Visibility(hiddenCategories, customization.hiddenItems.keys)
    }

    private data class Visibility(
        val hiddenCategories: Set<Long> = emptySet(),
        val hiddenKeys: Set<String> = emptySet(),
    ) {
        fun allows(categoryId: Long?, itemKey: String): Boolean =
            (categoryId == null || categoryId !in hiddenCategories) && itemKey !in hiddenKeys
    }

    /**
     * Each title is matched exactly after provider-name normalisation; a loose substring match
     * such as "The Batman" -> "Batman vs..." never becomes a recommendation.
     */
    private suspend fun <T : Any> pickFamous(
        sourceIds: List<Long>,
        names: List<String>,
        query: suspend (String) -> List<T>,
        key: (T) -> String,
        valid: (T) -> Boolean,
        id: (T) -> Long,
        quality: (T) -> Int,
    ): List<Pair<T, String>> {
        if (sourceIds.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<T, String>>()
        val pickedNames = mutableSetOf<String>()
        for (title in names) {
            val normalized = titleKey(title)
            if (normalized in pickedNames) continue
            val match = query(title)
                .asSequence()
                .filter(valid)
                .filter { key(it) == normalized }
                .maxByOrNull(quality)
            if (match != null) {
                result += match to title
                pickedNames += normalized
            }
            if (result.size == MAX_FEATURED_ITEMS) break
        }
        return result
    }

    private fun phrase(title: String): String = "\"" + title.replace("\"", "\"\"") + "\""

    private fun titleKey(raw: String): String {
        // BEST prefixes such as "D+ ", "NF " and "AMZN " aren't part of a title.
        val stripped = raw.replace(PLATFORM_PREFIX, "")
        val clean = TitleNormalizer.normalize(stripped).query
        return clean.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    companion object {
        private const val MAX_FEATURED_ITEMS = 18
        private val PLATFORM_PREFIX = Regex(
            "^(?i)(?:(?:D\\+|NF|AMZN|APTV|HBO|DSNY|ATV|NETFLIX|DISNEY\\+|PRIME|APPLE TV)\\s+)+",
        )
        // Editorial order, not provider ratings. Missing titles are skipped, never faked.
        private val POPULAR_MOVIES = listOf(
            "Dune: Part Two", "Oppenheimer", "Top Gun: Maverick", "The Dark Knight",
            "Interstellar", "Inception", "Avatar: The Way of Water", "Barbie",
            "The Batman", "John Wick: Chapter 4", "Spider-Man: No Way Home",
            "Avengers: Endgame", "Deadpool & Wolverine", "Inside Out 2",
            "The Shawshank Redemption", "The Godfather", "The Matrix",
            "Gladiator", "The Lord of the Rings: The Fellowship of the Ring",
            "The Lord of the Rings: The Two Towers", "The Lord of the Rings: The Return of the King",
            "Pulp Fiction", "Titanic", "Forrest Gump", "The Lion King",
            "The Super Mario Bros. Movie", "Despicable Me 4", "Moana",
            "The Avengers", "The Prestige", "The Departed", "Joker",
            "Wicked", "Gladiator II", "Jurassic Park", "Saving Private Ryan",
            "Mission: Impossible – Fallout", "The Wolf of Wall Street",
        )
        private val POPULAR_SERIES = listOf(
            "Breaking Bad", "Stranger Things", "The Last of Us", "The Boys",
            "The White Lotus", "House of the Dragon", "Game of Thrones",
            "The Bear", "Severance", "Reacher", "Fallout", "Shogun",
            "Wednesday", "The Pitt", "Yellowstone", "The Sopranos",
            "Better Call Saul", "The Walking Dead", "The Office",
            "Peaky Blinders", "Succession", "Ted Lasso", "Squid Game",
            "Only Murders in the Building", "Andor", "Silo", "The Mandalorian",
            "The Penguin", "True Detective", "Ozark", "Vikings",
            "The Crown", "The Witcher", "Chernobyl", "Friends",
            "Dexter", "The Big Bang Theory", "The Rookie",
        )
    }
}
