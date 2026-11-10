// StreamFailureUi.kt

package com.mochyy.mochymusic.ui.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import kotlin.math.ceil

/** The red of a failed stream's countdown. */
val StreamFailureRed = Color(0xFFFF4D4F)

/**
 * How far a failed stream is through its timeout, 0..1, or -1 while nothing has failed. Read it
 * inside a draw or layer block so each frame redraws only that, not the composition.
 */
@Composable
fun rememberStreamFailureProgress(): State<Float> {
    val failure by (LocalPlayerConnection.current?.streamFailure
        ?: return remember { object : State<Float> { override val value = -1f } }).collectAsState()
    return produceState(initialValue = -1f, failure) {
        val f = failure
        if (f == null) {
            value = -1f
            return@produceState
        }
        while (true) {
            val p = ((SystemClock.elapsedRealtime() - f.startedAt) / f.timeoutMs.toFloat()).coerceIn(0f, 1f)
            value = p
            if (p >= 1f) break
            androidx.compose.runtime.withFrameNanos { }
        }
    }
}

/** "Error, timeout in 6s" while a stream is failing, null otherwise; changes once a second. */
@Composable
fun rememberStreamFailureLabel(progress: State<Float>): State<String?> {
    val failure by (LocalPlayerConnection.current?.streamFailure
        ?: return remember { object : State<String?> { override val value: String? = null } }).collectAsState()
    val context = LocalContext.current
    return remember(failure) {
        derivedStateOf {
            val f = failure
            val p = progress.value
            if (f == null || p < 0f) null
            else context.getString(R.string.pc_stream_error_timeout, ceil((1f - p) * f.timeoutMs / 1000f).toInt().coerceAtLeast(0))
        }
    }
}

/**
 * Paints [progress] of the width in red, growing from the right edge to the left, over whatever
 * the content drew - so every bar style keeps its own shape and only its colour changes.
 */
fun Modifier.streamFailureOverlay(progress: State<Float>): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val p = progress.value
        if (p > 0f) {
            val w = size.width * p
            drawRect(
                color = StreamFailureRed,
                topLeft = Offset(size.width - w, 0f),
                size = Size(w, size.height),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
