package tv.own.owntv.features.movies

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.features.shell.components.VodPosterArt
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.loungeVodTitle
import tv.own.owntv.ui.stage.StagePoster

internal fun loungeMovieBadges(item: MovieEntity, max: Int = 3): List<String> = buildList {
    val quality = when (item.qualityRank) {
        5 -> "8K"
        4 -> "4K"
        3 -> "FHD"
        2 -> "HD"
        1 -> "SD"
        else -> null
    }
    quality?.let(::add)
    item.advertisedCapabilities
        ?.split("•")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.forEach(::add)
}.distinct().take(max)

/**
 * A standard 2:3 portrait tile within the SINGLE vertical browse grid.
 * Featured, new and full-library cards use identical measured dimensions.
 * No nested LazyRow, no independent sideways scroll or mismatched focus geometry.
 */
@Composable
internal fun LoungeMoviesEditorialPoster(
    item: MovieEntity,
    width: Dp,
    title: String,
    posterUrl: String?,
    firstInSection: Boolean,
    atLeftEdge: Boolean,
    focusRequester: FocusRequester,
    onLeftToCategories: () -> Unit,
    onFocus: (MovieEntity) -> Unit,
    onOpen: (MovieEntity) -> Unit,
) {
    StagePoster(
        title = loungeVodTitle(title),
        width = width,
        height = width * 1.5f,
        badges = loungeMovieBadges(item),
        onClick = { onOpen(item) },
        modifier = Modifier
            .then(if (firstInSection) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                if (atLeftEdge && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onLeftToCategories()
                    true
                } else false
            }
            .onFocusChanged { if (it.isFocused) onFocus(item) },
    ) {
        VodPosterArt(posterUrl, OwnTVIcon.MOVIES)
    }
}
