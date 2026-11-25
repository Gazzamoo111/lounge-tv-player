package tv.own.owntv.ui.lounge

import androidx.compose.ui.graphics.Color

/**
 * Lounge TV's neutral cinematic palette. Keep all screen-specific artwork and status colours
 * behind named tokens rather than introducing new unrelated hard-coded blues on each screen.
 * This class only owns presentation and does not depend on playback or catalogue state.
 */
internal object LoungePremiumTokens {
    val Ink = Color(0xFF071523)
    val DeepNavy = Color(0xFF0B1C30)
    val Surface = Color(0xFF14283E)
    val Elevated = Color(0xFF1B354D)
    val Edge = Color(0xFF344B61)
    val Cyan = Color(0xFF3DDCFF)
    val Body = Color(0xFFB6C8D8)
    val Text = Color(0xFFF4F7FB)
    val Live = Color(0xFFF35B70)
}
