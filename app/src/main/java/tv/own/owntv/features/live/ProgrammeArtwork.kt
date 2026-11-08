package tv.own.owntv.features.live

import androidx.compose.foundation.layout.size
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
internal fun ProgrammeArtwork(programme: EpgProgrammeEntity?, channelLogo: String?, modifier: Modifier = Modifier) {
    val resolver: ProgrammeArtworkResolver = koinInject()
    val poster by produceState<String?>(null, programme?.title, programme?.year, programme?.episode) {
        value = null
        val p = programme ?: return@produceState
        delay(350) // Held remote keys must not flood metadata requests.
        value = resolver.resolve(p.title, p.year, if (!p.episode.isNullOrBlank()) MetadataType.TV else null)
    }
    if (poster != null) {
        AsyncImage(model = poster, contentDescription = stringResource(R.string.lounge_v2_artwork_credit),
            contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        ChannelLogoTile(logoUrl = channelLogo, modifier = modifier) {}
    }
}
