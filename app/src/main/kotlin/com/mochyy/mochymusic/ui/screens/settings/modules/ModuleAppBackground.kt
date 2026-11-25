// app-wide custom background for look modules, drawn behind non-sensitive screens and captured by the frosted bars

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.modules.security.SafeModuleRoutes
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.GradientKind
import com.mochyy.mochymusic.modules.skin.ModuleSkin
import com.mochyy.mochymusic.modules.skin.PageBackground
import com.mochyy.mochymusic.ui.component.onChrome
import com.mochyy.mochymusic.ui.player.rememberFallbackBlurredImage
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.flow.MutableStateFlow

/** draws the active look module's app-wide background behind non-sensitive screens */
@Composable
internal fun ModuleAppBackground(currentRoute: String?, modifier: Modifier = Modifier) {
    val active = ActiveSkin.appBackground ?: return
    if (SafeModuleRoutes.isSensitiveRoute(currentRoute)) return
    val allowed = active.background.screens
    if (allowed.isNotEmpty()) {
        val name = currentRoute?.let(::takeoverName).orEmpty()
        if (name !in allowed) return
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = active.background.opacity },
    ) {
        PageBackgroundLayer(active.background.background, active.skin, active.values, subjectCover = null)
        active.background.layers.forEach { layer ->
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = layer.opacity }) {
                PageBackgroundLayer(layer.background, active.skin, active.values, subjectCover = null)
            }
        }
        if (active.background.dim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = active.background.dim)))
        }
    }
}

/** renders one [PageBackground] layer for either a module page or the app-wide background */
@Composable
internal fun PageBackgroundLayer(
    background: PageBackground,
    skin: ModuleSkin,
    values: Map<String, String>,
    subjectCover: String?,
) {
    val accent = MaterialTheme.colorScheme.primary
    val disableBlur by rememberPreference(DisableBlurKey, false)
    when (background) {
        is PageBackground.Fill -> {
            val brush = remember(background.colors, background.gradient, accent, values) {
                pageBackgroundBrush(background.colors.map { it.onChrome(accent, values) }, background.gradient)
            }
            Box(Modifier.fillMaxSize().background(brush))
        }
        is PageBackground.Picture -> {
            val wantBlur = !disableBlur && background.blur > 0.5f
            val nativeBlur = wantBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val rawModel = skin.resolve(background.image)
            val model = if (wantBlur && !nativeBlur) rememberFallbackBlurredImage(rawModel, background.blur) else rawModel
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(
                    if (nativeBlur) Modifier.graphicsLayer { scaleX = 1.1f; scaleY = 1.1f }.blur(background.blur.dp) else Modifier,
                ),
            )
            if (background.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
        }
        is PageBackground.SubjectCover -> {
            val url = subjectCover ?: return
            val wantBlur = !disableBlur && background.blur > 0.5f
            val nativeBlur = wantBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val rawModel = if (wantBlur) url.resize(256, 256) else url
            val model = if (wantBlur && !nativeBlur) rememberFallbackBlurredImage(rawModel, background.blur) else rawModel
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(
                    if (nativeBlur) Modifier.graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }.blur(background.blur.dp) else Modifier,
                ),
            )
            if (background.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
        }
        is PageBackground.PlayingCover -> {
            val connection = LocalPlayerConnection.current
            val playing by (connection?.mediaMetadata ?: EmptySongFlow).collectAsState()
            val url = playing?.thumbnailUrl ?: subjectCover
            if (url != null) {
                val wantBlur = !disableBlur && background.blur > 0.5f
                val nativeBlur = wantBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val rawModel = if (wantBlur) url.resize(256, 256) else url
                val model = if (wantBlur && !nativeBlur) rememberFallbackBlurredImage(rawModel, background.blur) else rawModel
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(
                        if (nativeBlur) Modifier.graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }.blur(background.blur.dp) else Modifier,
                    ),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
            if (background.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
        }
        is PageBackground.Ambient -> {
            val art = LocalArtworkColor.current
            val brush = remember(art, accent) {
                val primary = if (art == Color.Unspecified || art.alpha < 0.05f) accent else art
                Brush.verticalGradient(
                    listOf(
                        primary.copy(alpha = 0.55f),
                        primary.copy(alpha = 0.22f),
                        Color(0xFF0A0A0C),
                    )
                )
            }
            Box(Modifier.fillMaxSize().background(brush))
            if (background.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
        }
        is PageBackground.Shader -> {
            val fallbackBrush = remember(background.fallback, accent, values) {
                pageBackgroundBrush(background.fallback.map { it.onChrome(accent, values) }, GradientKind.Vertical)
            }
            val program = (rememberPageProgram(skin, background.program) as? PageProgram.Ready)?.program
            var failed by remember(program) { mutableStateOf(false) }
            if (program != null && !failed) {
                PageShaderFill(program, background.program, Modifier.fillMaxSize(), touchable = false, onFailed = { failed = true })
            } else {
                Box(Modifier.fillMaxSize().background(fallbackBrush))
            }
        }
    }
}

internal fun pageBackgroundBrush(colors: List<Color>, gradient: GradientKind = GradientKind.Vertical): Brush {
    if (colors.isEmpty()) return SolidColor(Color.Black)
    if (colors.size == 1) return SolidColor(colors[0])
    return when (gradient) {
        GradientKind.Vertical -> Brush.verticalGradient(colors)
        GradientKind.Horizontal -> Brush.horizontalGradient(colors)
        GradientKind.Diagonal -> Brush.linearGradient(colors)
        GradientKind.Radial -> Brush.radialGradient(colors)
    }
}

private val EmptySongFlow = MutableStateFlow<MediaMetadata?>(null)
