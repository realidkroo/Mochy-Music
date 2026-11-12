// ZoomedCover.kt

package com.mochyy.mochymusic.ui.player.customize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlin.math.max
import kotlin.math.min

/**
 * A cover that sits between showing the whole picture ([zoom] 0) and filling its box like
 * ContentScale.Crop ([zoom] 1). Drawn fitted and scaled up, so [zoom] can move every frame.
 */
@Composable
fun ZoomedCover(model: Any?, zoom: () -> Float, modifier: Modifier = Modifier) {
    var picture by remember(model) { mutableStateOf(Size.Unspecified) }
    Box(modifier = modifier.clipToBounds()) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { picture = it.painter.intrinsicSize },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f + (fillScale(picture, size) - 1f) * zoom().coerceIn(0f, 1f)
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/** how much bigger the cropped picture is than the fitted one, 1 when it can't be known yet */
internal fun fillScale(picture: Size, box: Size): Float {
    if (!picture.isSpecified || picture.width <= 0f || picture.height <= 0f) return 1f
    if (box.width <= 0f || box.height <= 0f) return 1f
    val fit = min(box.width / picture.width, box.height / picture.height)
    val crop = max(box.width / picture.width, box.height / picture.height)
    return crop / fit
}
