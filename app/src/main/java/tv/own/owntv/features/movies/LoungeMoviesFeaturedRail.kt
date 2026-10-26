package tv.own.owntv.features.movies

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
 * Read-only category highlight row. Five full-height portrait covers fit the pane, rather
 * than squeezing a 2:3 poster into a 16:9 backdrop tile. Side-scrolls up to 20 titles.
 * This is independent of the paginated full-library grid that follows it.
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
        // Showcase covers are intentionally larger than the 196px full-library tiles.
        // A four-across shelf in Separate mode and around five in Cinematic, with
        // a cap so focus never makes a poster taller than the Movies browse pane.
        val cardWidth = minOf((maxWidth - 72.mpx) / 3.9f, 340.mpx)
        Column(Modifier.fillMaxWidth()) {
            Text(heading, style = stageText(29, 800), color = StageColors.Text)
            Spacer(Modifier.height(14.mpx))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(20.mpx),
                contentPadding = PaddingValues(end = 24.mpx),
                modifier = Modifier.fillMaxWidth().focusGroup(),
            ) {
                itemsIndexed(movies, key = { _, movie -> movie.id }) { index, movie ->
                    StagePoster(
                        title = loungeVodTitle(displayNames[movie.id] ?: movie.canonicalTitle.ifBlank { movie.name }),
                        width = cardWidth,
                        height = cardWidth * 1.5f,
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
                        // Use the film's poster as a normal 2:3 cover, not its 16:9 backdrop.
                        VodPosterArt(posterUrl(movie), OwnTVIcon.MOVIES)
                    }
                }
            }
        }
    }
}
