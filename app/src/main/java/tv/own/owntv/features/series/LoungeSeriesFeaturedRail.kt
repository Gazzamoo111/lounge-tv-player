package tv.own.owntv.features.series

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
import tv.own.owntv.core.database.entity.SeriesEntity
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
internal fun LoungeSeriesEditorialPoster(
    item: SeriesEntity,
    width: Dp,
    title: String,
    posterUrl: String?,
    rank: Int? = null,
    firstInSection: Boolean,
    atLeftEdge: Boolean,
    focusRequester: FocusRequester,
    onLeftToCategories: () -> Unit,
    onFocus: (SeriesEntity) -> Unit,
    onOpen: (SeriesEntity) -> Unit,
) {
    tv.own.owntv.features.shell.components.LoungeRankedPoster(
        title = loungeVodTitle(title),
        width = width,
        posterUrl = posterUrl,
        rank = rank,
        series = true,
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
    )
}
