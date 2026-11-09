package tv.own.owntv.features.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
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
    channelName: String? = null) {
    val resolver: ProgrammeArtworkResolver = koinInject()
    val channelTheme = ProgrammeArtworkMatch.channelThemeTitle(channelName,
        synthetic247Programme = programme?.title?.trim()?.startsWith("24/7", ignoreCase = true) == true)
    val poster by produceState<String?>(null, programme?.title, programme?.year, programme?.episode, channelTheme) {
        value = null
        delay(350) // Held remote keys must not flood metadata requests.
        val p = programme
        if (p != null) value = resolver.resolve(p.title, p.year, if (!p.episode.isNullOrBlank()) MetadataType.TV else null)
        // A continuous 24/7 feed often has a synthetic, repeated EPG label. When an actual
        // programme cannot be identified, display only an exact channel-themed match.
        if (value == null && channelTheme != null) value = resolver.resolve(channelTheme)
    }
    Box(modifier) {
        ChannelLogoTile(logoUrl = channelLogo, modifier = Modifier.fillMaxSize()) {}
        if (poster != null) {
            AsyncImage(model = poster, contentDescription = stringResource(R.string.lounge_v2_artwork_credit),
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}
