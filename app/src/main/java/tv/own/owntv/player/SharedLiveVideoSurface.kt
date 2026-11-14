package tv.own.owntv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** One physical video view follows this engine between the preview and the full-screen host. */
internal class SharedLiveVideoSurface(val engine: LivePreviewEngine) {
    internal data class Placement(
        val modifier: Modifier,
        val keepAwake: Boolean,
        val autoFrameRate: Boolean,
    )

    // Moving the existing LayoutNode retains its AndroidView and SurfaceHolder. A newly-created
    // SurfaceView forced Fire TV's running decoder onto a placeholder surface during each transition.
    val content: @Composable (Placement) -> Unit = movableContentOf { placement ->
        ExoPreviewSurfaceContent(
            engine, placement.modifier, placement.keepAwake, placement.autoFrameRate,
        )
    }
}

internal val LocalSharedLiveVideoSurface = staticCompositionLocalOf<SharedLiveVideoSurface?> { null }
