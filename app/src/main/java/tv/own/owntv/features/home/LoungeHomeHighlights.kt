package tv.own.owntv.features.home

import androidx.paging.PagingSource
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
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository

/** Read-only, bounded first-login shelves from the customer's own provider catalogue. */
internal data class LoungeHomeHighlights(
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val channels: List<ChannelEntity> = emptyList(),
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

        val featuredMovies = if (movieIds.isEmpty()) emptyList() else {
            val candidates = firstPage(movies.pagingAllRating(movieIds)) +
                firstPage(movies.pagingAllDateAdded(movieIds))
            candidates.asSequence()
                .filter { it.posterUrl?.isNotBlank() == true }
                .filter { movieVisibility.allows(it.categoryId, CustomizeKeys.movie(it)) }
                .distinctBy { it.canonicalTitle.ifBlank { it.name }.lowercase() }
                .take(24)
                .toList()
        }

        val popularSeries = if (seriesIds.isEmpty()) emptyList() else {
            val candidates = firstPage(series.pagingAllRating(seriesIds)) +
                firstPage(series.pagingAllDateAdded(seriesIds))
            candidates.asSequence()
                .filter { it.posterUrl?.isNotBlank() == true }
                .filter { seriesVisibility.allows(it.categoryId, CustomizeKeys.series(it)) }
                .distinctBy { it.canonicalTitle.ifBlank { it.name }.lowercase() }
                .take(24)
                .toList()
        }

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
            movies = featuredMovies,
            series = popularSeries,
            channels = highlights.distinctBy { it.id }.take(20),
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
            categoryId !in hiddenCategories && itemKey !in hiddenKeys
    }

    private suspend fun <T : Any> firstPage(source: PagingSource<Int, T>): List<T> =
        when (val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 120,
                placeholdersEnabled = false,
            ),
        )) {
            is PagingSource.LoadResult.Page -> result.data
            else -> emptyList()
        }
}
