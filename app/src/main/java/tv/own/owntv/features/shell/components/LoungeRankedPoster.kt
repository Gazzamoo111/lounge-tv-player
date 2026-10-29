package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** Original feed rank, never a number assigned to a shuffled Featured selection. */
@Composable
internal fun LoungeRankedPoster(
    title: String,
    posterUrl: String?,
    width: Dp,
    rank: Int?,
    series: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(Modifier.width(width)) {
        if (rank != null) {
            BasicText(
                java.text.NumberFormat.getIntegerInstance().format(rank),
                style = stageText(if (rank < 10) 176 else 120, 900).copy(color = StageColors.Text.copy(alpha = .9f), drawStyle = Stroke(width = 3f)),
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 38.mpx),
                maxLines = 1,
            )
        }
        val artWidth = if (rank == null) width else width * .82f
        StagePoster(
            title = title, onClick = onClick, width = artWidth, height = artWidth * 1.5f,
            modifier = modifier.align(Alignment.TopEnd),
        ) { VodPosterArt(posterUrl, if (series) OwnTVIcon.SERIES else OwnTVIcon.MOVIES) }
    }
}
