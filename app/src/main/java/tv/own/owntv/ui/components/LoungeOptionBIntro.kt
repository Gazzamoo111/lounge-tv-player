package tv.own.owntv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import tv.own.owntv.R

/** Original user-supplied couch + LOUNGE TV logo. No sound, LTV symbol or extra effects. */
@Composable
fun LoungeOptionBIntroScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = stringResource(R.string.app_name),
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(2300)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF020714))) {
        Image(
            painter = painterResource(R.drawable.loungetv_original_full_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.315f).alpha(opacity),
            contentScale = ContentScale.Fit,
        )
    }
}
