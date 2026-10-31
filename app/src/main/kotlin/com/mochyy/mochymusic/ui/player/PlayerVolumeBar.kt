package com.mochyy.mochymusic.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.SeekBarStyle
import com.mochyy.mochymusic.ui.component.SeekBarTrack
import com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction
import kotlin.math.roundToInt

/** the bar's own height, from the volume line to the bottom of its buttons */
internal val PlayerVolumeBarHeight = 76.dp

/** the room under the bar, above where the navigation bar comes back */
private val VolumeBarBottomGap = 10.dp

/** the buttons under the volume line, each centred in its half */
private val VolumeBarButtons = listOf(PlayerMenuAction.LYRICS, PlayerMenuAction.QUEUE)

/** The caller pads the bar 26dp in; this brings the speakers out to the seek bar's 32dp edges. */
private val VolumeRowInset = 6.dp

/**
 * How far up from the screen's bottom the volume bar reaches. The player hides the navigation bar
 * while it's open, so its size is read as if it were showing: the bar stays clear of the swipe
 * that brings it back, and doesn't jump when it comes and goes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun playerVolumeBarFootprint(): Dp =
    WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() +
        VolumeBarBottomGap + PlayerVolumeBarHeight

/**
 * The bottom bar's second style, in place of the card deck: the phone's music volume as a line
 * like the seek bar's, and under it the lyrics and queue buttons. Place it with
 * [playerVolumeBarFootprint] of room under the controls.
 */
@Composable
internal fun PlayerVolumeBar(
    isActive: (PlayerMenuAction) -> Boolean,
    onAction: (PlayerMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    /** the player sheet's open progress: like the card deck, the bar only shows on the open player */
    progressProvider: () -> Float = { 1f },
    /** the selector's still picture of it: it doesn't follow the volume keys */
    preview: Boolean = false,
) {
    if (LocalPlayerConnection.current == null) return
    val visible by remember { derivedStateOf { progressProvider() > 0.98f } }
    if (!visible) return
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerVolumeBarHeight),
    ) {
        VolumeRow(listening = !preview)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VolumeRowInset),
        ) {
            VolumeBarButtons.forEach { action ->
                val on = isActive(action)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                    PressScaleActionButton(
                        icon = action.icon,
                        tint = if (on) Color.White else Color.White.copy(alpha = 0.85f),
                        containerColor = if (on) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
                        onClick = { onAction(action) },
                        hasShadow = false,
                    )
                }
            }
        }
    }
}

/**
 * The volume line, quiet speaker on the left and loud on the right, its ends lined up with the seek
 * bar's above. While it's held the line thickens and both speakers light up and grow a little, and
 * the quiet one turns into a muted speaker at zero.
 */
@Composable
private fun VolumeRow(listening: Boolean) {
    val volume = rememberMusicVolume(listening)
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val held = animateFloatAsState(
        targetValue = if (dragged || pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "volumeHeld",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = VolumeRowInset),
    ) {
        Icon(
            painter = painterResource(if (volume.shown <= 0f) R.drawable.volume_off else R.drawable.volume_down),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(18.dp)
                .speakerLight(held),
        )
        Slider(
            value = volume.shown,
            onValueChange = volume::drag,
            onValueChangeFinished = volume::release,
            interactionSource = interactionSource,
            thumb = { Spacer(Modifier.size(0.dp)) },
            track = {
                SeekBarTrack(
                    style = SeekBarStyle.DEFAULT,
                    fraction = volume.shown,
                    activeColor = Color.White,
                    inactiveColor = Color.White.copy(alpha = 0.24f),
                    animateWave = false,
                    active = dragged || pressed,
                )
            },
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .padding(horizontal = 10.dp),
        )
        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(18.dp)
                .speakerLight(held),
        )
    }
}

/** a speaker at 70% that brightens and grows a touch as the line is held; read while drawing only */
private fun Modifier.speakerLight(held: State<Float>): Modifier = graphicsLayer {
    val h = held.value
    alpha = 0.7f + 0.3f * h.coerceIn(0f, 1f)
    val scale = 1f + 0.14f * h
    scaleX = scale
    scaleY = scale
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/**
 * The phone's music volume as 0..1. A drag shows exactly where the finger is (the phone only has
 * a few steps); letting go settles on the step the phone actually took.
 */
internal class MusicVolume(private val audio: AudioManager) {
    private val steps = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var level by mutableFloatStateOf(read())
    private var dragging by mutableStateOf<Float?>(null)

    val shown: Float get() = dragging ?: level

    val isDragging: Boolean get() = dragging != null

    fun read(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / steps

    fun refresh() {
        level = read()
    }

    fun drag(fraction: Float) {
        dragging = fraction
        // some phones refuse volume changes in Do Not Disturb; the line just stays where it is
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * steps).roundToInt(), 0) }
    }

    fun release() {
        dragging = null
        refresh()
    }
}

/** the music volume, following the volume keys while [listening] */
@Composable
internal fun rememberMusicVolume(listening: Boolean): MusicVolume {
    val context = LocalContext.current
    val volume = remember(context) {
        MusicVolume(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    }
    DisposableEffect(volume, listening) {
        if (!listening) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (stream == AudioManager.STREAM_MUSIC) volume.refresh()
            }
        }
        // the volume keys may have moved it while the bar was away
        volume.refresh()
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return volume
}
