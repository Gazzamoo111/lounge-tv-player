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
