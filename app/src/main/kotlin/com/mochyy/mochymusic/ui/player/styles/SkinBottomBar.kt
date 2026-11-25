// a module player's bottom bar: the sung line and what's next on two cards, two pills, or mochy's own volume bar, each opening the lyrics or the queue (docs/wiki, blocks)

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.skin.BottomBarKind
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.modules.skin.SkinBlurDirection
import com.mochyy.mochymusic.modules.skin.SkinGlassConfig
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.player.PlayerVolumeBar
import com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction

@Composable
internal fun SkinBottomBar(block: SkinBlock.BottomBar, context: SkinContext, modifier: Modifier) {
    val interactive = LocalStyleInteractive.current
    // the selector's card and the editor stand still; everywhere else a tap opens the page
    val open: (PlayerMenuAction) -> Unit = { action ->
        if (!context.preview && interactive()) {
            when (action) {
                PlayerMenuAction.LYRICS -> context.actions.onLyrics?.invoke()
                PlayerMenuAction.QUEUE -> context.actions.onQueue?.invoke()
                else -> Unit
            }
        }
    }
    when (block.kind) {
        BottomBarKind.Volume -> PlayerVolumeBar(isActive = { false }, onAction = open, modifier = modifier.fillMaxWidth(), preview = context.preview)
        BottomBarKind.Cards -> Row(modifier.fillMaxWidth().height(block.height), horizontalArrangement = Arrangement.spacedBy(block.gap)) {
            LyricsCard(block, context) { open(PlayerMenuAction.LYRICS) }
            NextCard(block, context) { open(PlayerMenuAction.QUEUE) }
        }
        BottomBarKind.Pills -> Row(modifier.fillMaxWidth().height(block.height), horizontalArrangement = Arrangement.spacedBy(block.gap)) {
            BarPill(block, context, R.drawable.lyrics, stringResource(R.string.lyrics)) { open(PlayerMenuAction.LYRICS) }
            BarPill(block, context, R.drawable.queue_music, stringResource(R.string.queue)) { open(PlayerMenuAction.QUEUE) }
        }
    }
}

/** the sung line, with the lyrics' name over it; it only changes when the line does */
@Composable
private fun RowScope.LyricsCard(block: SkinBlock.BottomBar, context: SkinContext, onClick: () -> Unit) {
    val clock = context.lyricClock
    val line by remember(clock) { derivedStateOf { clock.line() } }
    val lines = clock.lines
    // before the first line it shows the first one coming, after the last the last one sung
    val shown = lines.getOrNull(line.coerceIn(0, (lines.size - 1).coerceAtLeast(0)))?.text
    BarSurface(block, context, Modifier.weight(1f).fillMaxHeight(), onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.lyrics), color = soft(block, context), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                shown ?: stringResource(R.string.skin_bar_no_lyrics),
                color = ink(block, context), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** the next song in the queue with its cover, or a note that nothing's queued */
@Composable
private fun RowScope.NextCard(block: SkinBlock.BottomBar, context: SkinContext, onClick: () -> Unit) {
    val next = context.queue?.itemAt(1)
    BarSurface(block, context, Modifier.weight(1f).fillMaxHeight(), onClick) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (next?.artworkUri != null) {
                AsyncImage(
                    model = next.artworkUri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.skin_bar_up_next), color = soft(block, context), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(
                    next?.title ?: stringResource(R.string.skin_bar_nothing_next),
                    color = ink(block, context), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                next?.artist?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = soft(block, context), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RowScope.BarPill(block: SkinBlock.BottomBar, context: SkinContext, icon: Int, label: String, onClick: () -> Unit) {
    BarSurface(block, context, Modifier.weight(1f).fillMaxHeight(), onClick) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = ink(block, context), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = ink(block, context), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** a card or pill's ground: frosted glass over the player's backdrop, the module's colours, or the player's faint chip */
@Composable
private fun BarSurface(block: SkinBlock.BottomBar, context: SkinContext, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(block.corner)
    val colors = block.fill.map(context::color)
    val cfg = block.glassConfig ?: block.glass?.let { SkinGlassConfig(blur = it) }
    val glass = context.glass.takeIf { cfg != null }
    Box(modifier.clip(shape).clickable(onClick = onClick), contentAlignment = Alignment.CenterStart) {
        if (glass != null && cfg != null) {
            val radius = with(LocalDensity.current) { cfg.blur.dp.toPx() }
            val progressive = when (cfg.progressive) {
                SkinBlurDirection.TopToBottom -> BlurDirection.TopToBottom
                SkinBlurDirection.BottomToTop -> BlurDirection.BottomToTop
                else -> null
            }
            GlassPillBackground(
                state = glass,
                blurRadius = { radius },
                tint = cfg.tint?.let(context::color) ?: colors.singleOrNull() ?: Color.Transparent,
                tileMode = android.graphics.Shader.TileMode.CLAMP,
                bleed = cfg.bleed,
                progressive = progressive,
                progressiveRamp = cfg.ramp,
                fallbackBlur = true,
                modifier = Modifier.matchParentSize(),
            )
            cfg.shader?.let { prog -> SkinShaderSurface(prog, context, Modifier.matchParentSize()) {} }
            if (cfg.outline) Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.2f), shape))
        } else {
            val fill = colors.ifEmpty { listOf(context.tones.chip) }
            Box(Modifier.matchParentSize().background(if (fill.size == 1) androidx.compose.ui.graphics.SolidColor(fill[0]) else Brush.horizontalGradient(fill)))
        }
        content()
    }
}

private fun ink(block: SkinBlock.BottomBar, context: SkinContext): Color = block.color?.let(context::color) ?: context.tones.ink

private fun soft(block: SkinBlock.BottomBar, context: SkinContext): Color = ink(block, context).let { it.copy(alpha = it.alpha * 0.6f) }
