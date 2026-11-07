// TvMotion.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// the tv's one easing: quick out of the gate, a long soft landing
internal val TvEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// a row or card that rises and fades in the first time it shows, and never again; seen holds the keys already shown
@Composable
internal fun Modifier.tvReveal(key: Any, seen: MutableSet<Any>, delayMillis: Int = 0): Modifier {
    val first = remember(key) { seen.add(key) }
    val progress = remember(key) { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(key) {
        if (progress.value < 1f) progress.animateTo(1f, tween(520, delayMillis = delayMillis, easing = TvEase))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 28.dp.toPx()
    }
}

// a menu or dialog that grows out of the middle instead of popping in
@Composable
internal fun Modifier.tvPopIn(): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(260, easing = TvEase)) }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        val scale = 0.92f + 0.08f * p
        scaleX = scale
        scaleY = scale
    }
}
