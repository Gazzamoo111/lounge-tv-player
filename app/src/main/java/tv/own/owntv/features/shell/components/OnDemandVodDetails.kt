package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageTitleArt
import tv.own.owntv.ui.theme.OnDemandDesign
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageText

/**
 * On Demand-specific title presentation. Movies and Series retain their
 * catalogue and remote-focus controllers but use a different visual hierarchy.
 */
@Composable
internal fun OnDemandVodHero(info: VodTitleInfo, modifier: Modifier = Modifier) {
    var titleArtFailed by remember(info.logoUrl) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.mpx)) {
        Text(
            text = stringResource(R.string.ondemand_welcome_kicker),
            style = stageText(16, 800),
            color = OnDemandDesign.electricLime,
        )
        Box(Modifier.fillMaxWidth().height(132.mpx), contentAlignment = Alignment.BottomStart) {
            if (!info.logoUrl.isNullOrBlank() && !titleArtFailed) {
                StageTitleArt(
                    url = info.logoUrl,
                    contentDescription = info.title,
                    onFailed = { titleArtFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    HeadlineTitle.of(info.title),
                    style = stageText(61, 800).copy(lineHeight = 66.mpxSp),
                    color = OnDemandDesign.coolWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        VodMeta(
            parts = listOfNotNull(info.year?.toString(), info.genres.firstOrNull(),
                info.runtimeSecs?.let { vodRuntime(it) }),
            rating = info.rating,
            // Playback format badges belong to the player, not the catalogue card.
            tags = emptyList(),
            size = 19,
        )
        info.plot?.takeIf { it.isNotBlank() }?.let { summary ->
            Text(
                summary,
                style = stageText(20, 450).copy(lineHeight = 30.mpxSp),
                color = OnDemandDesign.secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 780.mpx),
            )
        }
    }
}

@Composable
internal fun OnDemandVodDetailsPanel(
    info: VodTitleInfo?,
    hints: List<Pair<String, String>>,
    series: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(25.mpx))
            .background(OnDemandDesign.slateBlue.copy(alpha = 0.95f))
            .padding(20.mpx),
    ) {
        if (info != null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(13.mpx)) {
                Box(
                    Modifier.fillMaxWidth().height(292.mpx)
                        .clip(RoundedCornerShape(16.mpx))
                        .background(OnDemandDesign.midnight),
                ) {
                    val image = info.backdropUrl ?: info.posterUrl
                    if (!image.isNullOrBlank()) {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to OnDemandDesign.midnight.copy(alpha = 0.88f),
                            ),
                        ),
                    )
                    Text(
                        text = stringResource(if (series) R.string.ondemand_type_series else R.string.ondemand_type_movie),
                        style = stageText(16, 800),
                        color = OnDemandDesign.electricLime,
                        modifier = Modifier.align(Alignment.BottomStart).padding(17.mpx),
                    )
                }
                Text(
                    HeadlineTitle.of(info.title),
                    style = stageText(29, 800),
                    color = OnDemandDesign.coolWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                VodMeta(
                    parts = listOfNotNull(info.year?.toString(), info.runtimeSecs?.let { vodRuntime(it) }),
                    rating = info.rating,
                    tags = emptyList(),
                    size = 17,
                )
                if (info.genres.isNotEmpty()) {
                    Text(
                        info.genres.take(3).joinToString("  ·  "),
                        style = stageText(16, 650),
                        color = OnDemandDesign.electricLime,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                info.plot?.takeIf { it.isNotBlank() }?.let { summary ->
                    Text(
                        summary,
                        style = stageText(17, 450).copy(lineHeight = 26.mpxSp),
                        color = OnDemandDesign.secondaryText,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (info.cast.isNotEmpty()) {
                    Text(
                        info.cast.take(3).joinToString("  ·  ") { it.name },
                        style = stageText(15, 450),
                        color = OnDemandDesign.subdued,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        StageKeyHints(hints, Modifier.align(Alignment.BottomStart).padding(top = 8.mpx))
    }
}
