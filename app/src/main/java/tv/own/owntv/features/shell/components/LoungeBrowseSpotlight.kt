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
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageText

/**
 * Shared Movies / Series TV spotlight: left-side title or TMDB logo, concise metadata
 * and synopsis. The existing VodCinematicBackdrop displays wide artwork to the right.
 * Designed for a 350px hero, keeping title, facts and synopsis legible at TV distance.
 */
@Composable
internal fun LoungeBrowseSpotlight(info: VodTitleInfo, modifier: Modifier = Modifier) {
    var logoFailed by remember(info.logoUrl) { mutableStateOf(false) }
    Column(
        modifier = modifier.padding(top = 12.mpx),
        verticalArrangement = Arrangement.spacedBy(15.mpx),
    ) {
        if (!info.logoUrl.isNullOrBlank() && !logoFailed) {
            StageTitleArt(
                url = info.logoUrl,
                contentDescription = null,
                modifier = Modifier.width(720.mpx).height(126.mpx),
                onFailed = { logoFailed = true },
            )
        } else {
            Text(
                text = info.title,
                style = stageText(68, 800).copy(lineHeight = 70.mpxSp),
                color = StageColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VodMeta(
            parts = listOfNotNull(info.year?.toString(), info.genres.firstOrNull(), info.runtimeSecs?.let { vodRuntime(it) }),
            rating = info.rating,
            tags = info.tags.take(2),
            size = 21,
        )
        info.plot?.takeIf(String::isNotBlank)?.let { plot ->
            Text(
                text = plot,
                style = stageText(23, 400).copy(lineHeight = 33.mpxSp),
                color = StageColors.Body,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(850.mpx),
            )
        }
    }
}
