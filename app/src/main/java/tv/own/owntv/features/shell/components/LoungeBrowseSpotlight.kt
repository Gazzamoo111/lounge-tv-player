package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.ui.stage.StageTitleArt
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * Shared Movies / Series TV spotlight: left-side title or TMDB logo, concise metadata
 * and synopsis. The existing VodCinematicBackdrop displays wide artwork to the right.
 * Fits within the shorter spotlight so shelves keep their labels while focused.
 */
@Composable
internal fun LoungeBrowseSpotlight(info: VodTitleInfo, modifier: Modifier = Modifier) {
    var logoFailed by remember(info.logoUrl) { mutableStateOf(false) }
    Column(
        modifier = modifier.padding(top = 8.mpx),
        verticalArrangement = Arrangement.spacedBy(9.mpx),
    ) {
        if (!info.logoUrl.isNullOrBlank() && !logoFailed) {
            StageTitleArt(
                url = info.logoUrl,
                contentDescription = null,
                modifier = Modifier.width(570.mpx).height(90.mpx),
                onFailed = { logoFailed = true },
            )
        } else {
            Text(
                text = info.title,
                style = stageText(46, 800),
                color = StageColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VodMeta(
            parts = listOfNotNull(info.year?.toString(), info.genres.firstOrNull()),
            rating = info.rating,
            tags = info.tags.take(2),
            size = 17,
        )
        info.plot?.takeIf(String::isNotBlank)?.let { plot ->
            Text(
                text = plot,
                style = stageText(18, 400),
                color = StageColors.Body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(780.mpx),
            )
        }
    }
}
