// the small parts a module player is built from: single buttons, plain shapes, times, and the items it adds to the chevron menu

package com.mochyy.mochymusic.ui.player.styles

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import com.mochyy.mochymusic.modules.skin.ButtonKind
import com.mochyy.mochymusic.modules.skin.ShapeKind
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.modules.skin.SkinTarget
import com.mochyy.mochymusic.modules.skin.TimeKind
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.ui.player.menu.openModuleTarget
import com.mochyy.mochymusic.utils.makeTimeString
import androidx.media3.common.Player

/** one control or custom action as a circle, with mochy's icon or the module's own picture */
@Composable
internal fun SkinButton(block: SkinBlock.Button, context: SkinContext, modifier: Modifier) {
    val on = rememberTargetOn(block.target)
    val run = rememberTargetRunner(context)
    val picture = (if (on) block.imageOn ?: block.image else block.image)?.let(context.skin::resolve)
    val fill = block.fill?.let(context::color) ?: context.tones.chip
    val tint = block.color?.let(context::color) ?: context.tones.ink
    val tones = remember(context.tones, fill, tint) { context.tones.copy(chip = fill, ink = tint) }
    StyleCircle(
        icon = iconFor(block.target, on),
        tones = tones,
        size = block.size,
        onClick = { run(block.target) },
        modifier = modifier,
        active = on && picture == null && block.fill == null,
        picture = picture,
    )
}

/** a box, a circle, a pill or a line, filled with one colour or a gradient, or only outlined */
@Composable
internal fun SkinShape(block: SkinBlock.Shape, context: SkinContext, modifier: Modifier) {
    val shape = when (block.kind) {
        ShapeKind.Rect -> if (block.corner.value > 0f) RoundedCornerShape(block.corner) else RectangleShape
        ShapeKind.Circle -> CircleShape
        ShapeKind.Pill, ShapeKind.Line -> RoundedCornerShape(percent = 50)
    }
    val colors = block.colors.map(context::color)
    val brush = if (colors.size == 1) Brush.linearGradient(listOf(colors[0], colors[0])) else Brush.horizontalGradient(colors)
    val sized = modifier
        .then(
            when {
                block.kind == ShapeKind.Circle -> Modifier.size(block.width ?: block.height)
                block.width != null -> Modifier.width(block.width)
                else -> Modifier.fillMaxWidth()
            },
        )
        .then(if (block.kind == ShapeKind.Circle) Modifier else Modifier.height(block.height))
    Box(if (block.stroke != null) sized.border(block.stroke, brush, shape) else sized.background(brush, shape))
}

/** how far into the song, how much is left, or how long it is */
@Composable
internal fun SkinTime(block: SkinBlock.Time, context: SkinContext, modifier: Modifier) {
    val fill = modifier.fillMaxWidth()
    when (block.kind) {
        TimeKind.Elapsed -> {
            val seconds = rememberPlayedSeconds(context.position)
            TextLine(makeTimeString(seconds * 1_000L), block.text, context, fill)
        }
        TimeKind.Remaining -> {
            val seconds = rememberPlayedSeconds(context.position)
            val left by remember(context.duration) { derivedStateOf { (context.duration - seconds * 1_000L).coerceAtLeast(0L) } }
            TextLine("-" + makeTimeString(left), block.text, context, fill)
        }
        TimeKind.Length -> TextLine(makeTimeString(context.duration), block.text, context, fill)
    }
}

/** the module's items for the chevron menu, its customise sheet first when the player has one */
@Composable
internal fun rememberSkinMenuExtras(context: SkinContext): List<StyleMenuExtra> {
    val run = rememberTargetRunner(context)
    val layout = context.layout
    val customiseLabel = stringResource(R.string.skin_customise_title)
    return remember(layout, context.skin, run, customiseLabel) {
        val customise = layout.customise?.let {
            StyleMenuExtra(it.title ?: customiseLabel, R.drawable.tune) { context.openCustomise() }
        }
        listOfNotNull(customise) + layout.menu.map { item ->
            val toggles = (item.target as? SkinTarget.Control)?.kind in Toggles
            StyleMenuExtra(
                label = item.title,
                icon = iconFor(item.target, on = false),
                picture = context.skin.resolve(item.icon),
                keepsOpen = toggles,
            ) { run(item.target) }
        }
    }
}

// whether a toggle-like control is on right now: playing, liked, shuffling, repeating
@Composable
private fun rememberTargetOn(target: SkinTarget): Boolean {
    val kind = (target as? SkinTarget.Control)?.kind ?: return false
    val connection = LocalPlayerConnection.current ?: return false
    return when (kind) {
        ButtonKind.PlayPause -> connection.uiState.transportState.collectAsState().value.isPlaying
        ButtonKind.Shuffle -> connection.uiState.transportState.collectAsState().value.shuffleModeEnabled
        ButtonKind.Repeat -> connection.uiState.transportState.collectAsState().value.repeatMode != Player.REPEAT_MODE_OFF
        ButtonKind.Like -> connection.uiState.trackInfo.collectAsState().value.liked
        else -> false
    }
}

/** what a tap on a button or a chevron item does */
@Composable
private fun rememberTargetRunner(context: SkinContext): (SkinTarget) -> Unit {
    val connection = LocalPlayerConnection.current
    val android = LocalContext.current
    return remember(connection, android, context.actions, context.skin) {
        { target ->
            when (target) {
                is SkinTarget.Control -> connection?.let { runControl(target.kind, it, context) }
                is SkinTarget.Action -> openModuleTarget(android, target.target, connection?.mediaMetadata?.value, context.skin.moduleId, context.actions.onOpenRoute)
            }
        }
    }
}

private fun runControl(kind: ButtonKind, connection: PlayerConnection, context: SkinContext) {
    when (kind) {
        ButtonKind.PlayPause -> connection.togglePlayPause()
        ButtonKind.Previous -> connection.seekToPrevious()
        ButtonKind.Next -> connection.seekToNext()
        ButtonKind.Like -> connection.toggleLike()
        ButtonKind.Shuffle -> connection.player.shuffleModeEnabled = !connection.player.shuffleModeEnabled
        ButtonKind.Repeat -> connection.player.toggleRepeatMode()
        ButtonKind.Lyrics -> context.actions.onLyrics?.invoke()
        ButtonKind.Menu -> context.openMenu()
        ButtonKind.Customise -> context.openCustomise()
        ButtonKind.Close -> context.actions.onClose()
    }
}

@DrawableRes
private fun iconFor(target: SkinTarget, on: Boolean): Int = when ((target as? SkinTarget.Control)?.kind) {
    ButtonKind.PlayPause -> if (on) R.drawable.pause else R.drawable.play
    ButtonKind.Previous -> R.drawable.skip_previous
    ButtonKind.Next -> R.drawable.skip_next
    ButtonKind.Like -> if (on) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill
    ButtonKind.Shuffle -> if (on) R.drawable.shuffle_on else R.drawable.shuffle
    ButtonKind.Repeat -> if (on) R.drawable.repeat_on else R.drawable.repeat
    ButtonKind.Lyrics -> R.drawable.lyrics
    ButtonKind.Menu -> R.drawable.more_horiz
    ButtonKind.Customise -> R.drawable.tune
    ButtonKind.Close -> R.drawable.expand_more
    null -> R.drawable.puzzle
}

private val Toggles = setOf(ButtonKind.Like, ButtonKind.Shuffle, ButtonKind.Repeat, ButtonKind.PlayPause)
