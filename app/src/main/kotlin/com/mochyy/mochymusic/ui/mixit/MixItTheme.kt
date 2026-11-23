// MixItTheme.kt

package com.mochyy.mochymusic.ui.mixit

import androidx.compose.ui.graphics.Color
import com.mochyy.mochymusic.constants.CrossmixMode
import com.mochyy.mochymusic.mixit.MixItFallbackMode
import kotlin.math.roundToInt

/**
 * Studio palette & lightweight formatting helpers for Mix It.
 * Warm DJ amber-gold accents over deep obsidian surfaces, matching Mochy's dark glass aesthetic.
 */
internal object MixItColors {
    val SheetSurface = Color(0xFF101013)
    val DeckBackground = Color(0xFF15151A)
    val CardSurface = Color(0xFF1B1B21)
    val CardSurfaceElevated = Color(0xFF23232B)
    val Hairline = Color.White.copy(alpha = 0.09f)

    // Signature DJ Gold from the Transition Editor concept
    val Gold = Color(0xFFF5D64B)
    val GoldBright = Color(0xFFFFE97D)
    val GoldSoft = Color(0xFFF5D64B).copy(alpha = 0.16f)
    val GoldBorder = Color(0xFFF5D64B).copy(alpha = 0.72f)
    val OnGold = Color(0xFF13120D)

    // Distinct yet cohesive waveform colors for Track 1 (Outgoing) and Track 2 (Incoming)
    val Track1Wave = Color(0xFFE9E6DC).copy(alpha = 0.72f)
    val Track1WaveActive = Color(0xFFFFF4BD)
    val Track2Wave = Color(0xFFD2DBE8).copy(alpha = 0.72f)
    val Track2WaveActive = Color(0xFFEAF3FF)

    // Cool cyan hint for beat-lock / incoming curve contrast
    val SyncAccent = Color(0xFF7BE3FF)

    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.62f)
    val TextMuted = Color.White.copy(alpha = 0.38f)
}

internal fun formatMixClock(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

internal fun formatMixDuration(durationMs: Long, beatMs: Double?): String {
    val sec = durationMs / 1_000.0
    val secLabel = if (kotlin.math.abs(sec - sec.roundToInt()) < 0.12) "${sec.roundToInt()}s" else "%.1fs".format(sec)
    if (beatMs != null && beatMs in 250.0..1_200.0) {
        val beats = (durationMs / beatMs).roundToInt().coerceAtLeast(1)
        return "$beats beats · $secLabel"
    }
    return secLabel
}

internal fun fallbackDisplayLabel(
    fallback: MixItFallbackMode,
    appMode: CrossmixMode,
    manualCrossfadeSeconds: Int,
): String = when (fallback) {
    MixItFallbackMode.CROSSMIX -> "CrossMix"
    MixItFallbackMode.AUTO_CROSSFADE -> "Auto Crossfade"
    MixItFallbackMode.OFF -> "No transition"
    MixItFallbackMode.APP_DEFAULT -> when (appMode) {
        CrossmixMode.CROSSMIX -> "CrossMix"
        CrossmixMode.AUTO_CROSSFADE -> "Auto Crossfade"
        CrossmixMode.MANUAL_CROSSFADE -> "Crossfade · ${manualCrossfadeSeconds}s"
        CrossmixMode.OFF -> "No transition"
    }
}
