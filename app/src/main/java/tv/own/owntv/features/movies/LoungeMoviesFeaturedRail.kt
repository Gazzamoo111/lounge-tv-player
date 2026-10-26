package tv.own.owntv.features.movies

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import coil3.compose.AsyncImage
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.Composable
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.loungeVodTitle
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * Read-only editorial shelf. Its selection already comes from Home's catalogue-wide
 * exact-title matching, not the first page of Movies' PagingSource.
 *
 * Never modifies the provider catalogue or the existing full-library pager.
 */
@Composable
internal fun LoungeMoviesFeaturedRail(
    heading: String,
    movies: List<MovieEntity>,
    displayNames: Map<Long, String>,
    posterUrl: (MovieEntity) -> String?,
    onFocused: (MovieEntity) -> Unit,
    onPlay: (MovieEntity) -> Unit,
    onLeftFromFirstCard: () -> Unit,
    firstCardFocusRequester: FocusRequester? = null,
) {
    if (movies.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Four larger widescreen cards visible, with focus lift room between cards.
        val cardWidth = (maxWidth - 78.mpx) / 4.2f
        Column(Modifier.fillMaxWidth()) {
            Text(heading, style = stageText(27, 700), color = StageColors.Text)
            Spacer(Modifier.height(13.mpx))
            LazyRow(
            horizontalArrangement = Arrangement.spacedBy(22.mpx),
            contentPadding = PaddingValues(end = 24.mpx),
            modifier = Modifier.fillMaxWidth().focusGroup(),
        ) {
            itemsIndexed(movies, key = { _, movie -> movie.id }) { index, movie ->
                StagePoster(
                    title = loungeVodTitle(displayNames[movie.id] ?: movie.canonicalTitle.ifBlank { movie.name }),
                    width = cardWidth,
                    height = cardWidth * (9f / 16f),
                    compact = true,
                    onClick = { onPlay(movie) },
                    modifier = Modifier
                        .then(if (index == 0 && firstCardFocusRequester != null) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (index == 0 && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                onLeftFromFirstCard()
                                true
                            } else false
                        }
                        .onFocusChanged { if (it.isFocused) onFocused(movie) },
                ) {
                    // Unlike full-library posters, a highlight's 16:9 artwork must be
                    // contained in its landscape tile, not enlarged/cropped past the box.
                    val art = movie.backdropUrl?.takeIf(String::isNotBlank) ?: posterUrl(movie)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
                        if (art != null) {
                            AsyncImage(
                                model = art,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                OwnTVIcon(OwnTVIcon.MOVIES, StageColors.Dim, Modifier.height(42.mpx))
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
