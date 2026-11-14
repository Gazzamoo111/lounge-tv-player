package tv.own.owntv.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import tv.own.owntv.ui.theme.stageText
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.theme.mpx

@Composable
internal fun ProgrammeArtwork(programme: EpgProgrammeEntity?, channelLogo: String?, modifier: Modifier = Modifier,
    channelName: String? = null,
    loadArtwork: (suspend (String, Int?, MetadataType?) -> String?)? = null) {
    val resolver: ProgrammeArtworkResolver? = if (loadArtwork == null) koinInject() else null
    val lookup = loadArtwork ?: resolver!!::resolve
    val channelTheme = ProgrammeArtworkMatch.channelThemeTitle(channelName,
        synthetic247Programme = ProgrammeArtworkMatch.isSynthetic(programme?.title))
    val artwork by produceState<Pair<String?, Boolean>>(null to false, programme?.title, programme?.year, programme?.episode, channelTheme) {
        value = null to false
        delay(350) // Held remote keys must not flood metadata requests.
        val p = programme
        if (p != null && !ProgrammeArtworkMatch.isSynthetic(p.title)) {
            value = lookup(p.title, p.year, if (!p.episode.isNullOrBlank()) MetadataType.TV else null) to false
        }
        // A continuous 24/7 feed often has a synthetic, repeated EPG label. When an actual
        // programme cannot be identified, display only an exact channel-themed match.
        if (value.first == null && channelTheme != null) value = lookup(channelTheme, null, null) to true
    }
    Box(modifier.clip(RoundedCornerShape(12.mpx))) {
        ChannelLogoTile(logoUrl = channelLogo, modifier = Modifier.fillMaxSize()) {}
        if (artwork.first != null) {
            AsyncImage(model = artwork.first, contentDescription = stringResource(R.string.lounge_v2_artwork_credit),
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (artwork.second) Text(stringResource(R.string.m4_channel_artwork), style = stageText(14, 600),
                color = Color.White, modifier = Modifier.align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f)).padding(4.mpx))
        }
    }
}
