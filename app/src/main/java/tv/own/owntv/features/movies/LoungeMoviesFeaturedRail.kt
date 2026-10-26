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
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.Composable
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.features.shell.components.VodPosterArt
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
) {
    if (movies.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Four large 16:9 highlights fit the available TV pane, with the next card peeking
        // through at the edge. The original seven-across poster grid is unchanged below.
        val cardWidth = (maxWidth - 54.mpx) / 4.13f
        Column(Modifier.fillMaxWidth()) {
        Text(heading, style = stageText(27, 700), color = StageColors.Text)
        Spacer(Modifier.height(13.mpx))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            contentPadding = PaddingValues(end = 24.mpx),
            modifier = Modifier.fillMaxWidth().focusGroup(),
        ) {
            items(movies, key = { it.id }) { movie ->
                StagePoster(
                    title = loungeVodTitle(displayNames[movie.id] ?: movie.canonicalTitle.ifBlank { movie.name }),
                    width = cardWidth,
                    height = cardWidth * (9f / 16f),
                    compact = true,
                    onClick = { onPlay(movie) },
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused(movie) },
                ) {
                    VodPosterArt(movie.backdropUrl?.takeIf { it.isNotBlank() } ?: posterUrl(movie), OwnTVIcon.MOVIES)
                }
            }
        }
        }
    }
}
