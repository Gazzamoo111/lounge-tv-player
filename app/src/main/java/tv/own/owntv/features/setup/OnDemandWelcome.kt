package tv.own.owntv.features.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.OnDemandDesign
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * Standalone, lean-back first run. The pairing action uses the existing
 * PIN-protected LAN companion transfer. It does not imply cloud authentication.
 */
@Composable
internal fun OnDemandWelcome(
    onPairFromPhone: () -> Unit,
    onEnterManually: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstFocus.requestFocus() }
    }

    Box(modifier.fillMaxSize().background(OnDemandDesign.midnight)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to OnDemandDesign.midnight,
                    0.70f to OnDemandDesign.slateBlue.copy(alpha = 0.50f),
                    1f to OnDemandDesign.midnight,
                ),
            ),
        )
        Box(
            Modifier.align(Alignment.CenterEnd)
                .width(16.mpx)
                .height(420.mpx)
                .background(OnDemandDesign.electricLime.copy(alpha = 0.78f), RoundedCornerShape(12.mpx)),
        )
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 164.mpx, end = 140.mpx),
            verticalArrangement = Arrangement.spacedBy(20.mpx),
        ) {
            Text(
                stringResource(R.string.ondemand_welcome_brand),
                style = stageText(28, 800),
                color = OnDemandDesign.coolWhite,
            )
            Text(
                stringResource(R.string.ondemand_welcome_kicker),
                style = stageText(20, 800),
                color = OnDemandDesign.electricLime,
            )
            Spacer(Modifier.height(12.mpx))
            Text(
                stringResource(R.string.ondemand_welcome_headline),
                style = stageText(82, 850),
                color = OnDemandDesign.coolWhite,
            )
            Text(
                stringResource(R.string.ondemand_welcome_message),
                style = stageText(25, 450),
                color = OnDemandDesign.secondaryText,
            )
            Spacer(Modifier.height(38.mpx))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageButton(
                    text = stringResource(R.string.ondemand_welcome_pair),
                    onClick = { if (enabled) onPairFromPhone() },
                    modifier = Modifier.focusRequester(firstFocus),
                    tinted = true,
                )
                StageButton(
                    text = stringResource(R.string.ondemand_welcome_manual),
                    onClick = { if (enabled) onEnterManually() },
                )
            }
            Text(
                stringResource(R.string.ondemand_welcome_hint),
                style = stageText(17, 500),
                color = OnDemandDesign.subdued,
            )
        }
    }
}
