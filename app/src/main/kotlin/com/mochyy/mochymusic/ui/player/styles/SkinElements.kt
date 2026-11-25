// the small parts a module player is built from: single buttons, plain shapes, times, and the items it adds to the chevron menu

package com.mochyy.mochymusic.ui.player.styles

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalDownloadUtil
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.FloatingLyricsKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.SpeedDialItem
import com.mochyy.mochymusic.di.ModulesEntryPoint
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import com.mochyy.mochymusic.modules.security.ModuleActionGuard
import com.mochyy.mochymusic.modules.security.SafeAppSettings
import com.mochyy.mochymusic.modules.skin.ActionTarget
import com.mochyy.mochymusic.modules.skin.ButtonKind
import com.mochyy.mochymusic.modules.skin.ProgressStyle
import com.mochyy.mochymusic.modules.skin.ShapeKind
import com.mochyy.mochymusic.modules.skin.SheetKind
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.modules.skin.SkinTarget
import com.mochyy.mochymusic.modules.skin.TimeKind
import com.mochyy.mochymusic.playback.ExoDownloadService
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.player.menu.openModuleTarget
import com.mochyy.mochymusic.ui.player.rememberMusicVolume
import com.mochyy.mochymusic.ui.player.share.song.SongShare
import com.mochyy.mochymusic.ui.screens.settings.modules.ModulePageSheetContent
import com.mochyy.mochymusic.ui.screens.settings.modules.modulePageRoute
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.makeTimeString
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** one control or custom action as a circle, with mochy's icon or the module's own picture */
@Composable
internal fun SkinButton(block: SkinBlock.Button, context: SkinContext, modifier: Modifier) {
    val on = rememberTargetOn(block.target, context)
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
    val brush = if (colors.size == 1) androidx.compose.ui.graphics.SolidColor(colors[0]) else Brush.horizontalGradient(colors)
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
    val fill = modifier.textWidth(block.text)
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

/** the module's items for the chevron menu, filtered by each item's "when" condition, its customise sheet first when the player has one */
@Composable
internal fun rememberSkinMenuExtras(context: SkinContext): List<StyleMenuExtra> {
    val run = rememberTargetRunner(context)
    val layout = context.layout
    val customiseLabel = stringResource(R.string.skin_customise_title)
    val visibleItems = layout.menu.filter { context.holds(it.condition) }
    val customise = layout.customise?.let {
        StyleMenuExtra(it.title ?: customiseLabel, R.drawable.tune) { context.openCustomise() }
    }
    return listOfNotNull(customise) + visibleItems.map { item ->
        val on = rememberTargetOn(item.target, context)
        val toggles = (item.target as? SkinTarget.Control)?.kind in Toggles ||
            (item.target as? SkinTarget.Action)?.target is ActionTarget.SetField ||
            (item.target as? SkinTarget.Action)?.target is ActionTarget.SetAppSetting
        StyleMenuExtra(
            label = item.title,
            icon = iconFor(item.target, on = on),
            picture = context.skin.resolve(item.icon),
            active = on,
            keepsOpen = toggles,
            accent = item.accent?.let(context::color),
        ) { run(item.target) }
    }
}

// whether a toggle-like control or setting is on right now
@Composable
private fun rememberTargetOn(target: SkinTarget, context: SkinContext): Boolean {
    val kind = (target as? SkinTarget.Control)?.kind ?: ((target as? SkinTarget.Action)?.target as? ActionTarget.Control)?.kind
    if (kind == null) {
        val action = (target as? SkinTarget.Action)?.target
        if (action is ActionTarget.SetField) {
            val current = context.values[action.key]?.trim()
            return if (action.value == null) current.equals("true", ignoreCase = true) else current.equals(action.value, ignoreCase = true)
        }
        return false
    }
    val connection = LocalPlayerConnection.current ?: return false
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    return when (kind) {
        ButtonKind.PlayPause, ButtonKind.Play -> connection.uiState.transportState.collectAsState().value.isPlaying
        ButtonKind.Shuffle -> connection.uiState.transportState.collectAsState().value.shuffleModeEnabled
        ButtonKind.Repeat -> connection.uiState.transportState.collectAsState().value.repeatMode != Player.REPEAT_MODE_OFF
        ButtonKind.Like -> connection.uiState.trackInfo.collectAsState().value.liked
        ButtonKind.Library -> {
            val id = connection.mediaMetadata.collectAsState().value?.id
            val dbSong by remember(database, id) { if (id != null) database.song(id) else flowOf(null) }.collectAsState(initial = null)
            dbSong?.song?.inLibrary != null
        }
        ButtonKind.Download -> {
            val id = connection.mediaMetadata.collectAsState().value?.id
            val dl by remember(downloadUtil, id) { if (id != null) downloadUtil.getDownload(id) else flowOf(null) }.collectAsState(initial = null)
            dl?.state == Download.STATE_COMPLETED || dl?.state == Download.STATE_DOWNLOADING || dl?.state == Download.STATE_QUEUED
        }
        ButtonKind.Pin -> {
            val id = connection.mediaMetadata.collectAsState().value?.id
            val pinned by remember(database, id) { if (id != null) database.speedDialDao.isPinned(id) else flowOf(false) }.collectAsState(initial = false)
            pinned
        }
        ButtonKind.FloatingLyrics -> {
            val (on) = com.mochyy.mochymusic.utils.rememberPreference(FloatingLyricsKey, defaultValue = false)
            on
        }
        else -> false
    }
}

/** the phone's music volume as a draggable line, reading its level in draw so drags never recompose the bar */
@Composable
internal fun SkinVolume(block: SkinBlock.Volume, context: SkinContext, modifier: Modifier) {
    val volume = rememberMusicVolume(listening = !context.preview)
    val interactive = LocalStyleInteractive.current
    val filled = context.color(block.color)
    val rest = context.color(block.track)
    val thickness = when (block.style) {
        ProgressStyle.Line -> 3.dp
        ProgressStyle.Thick -> 7.dp
        ProgressStyle.Pill -> 14.dp
    }
    val muted by remember(volume) { derivedStateOf { volume.shown <= 0f } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (block.icons) {
            Icon(
                painter = painterResource(if (muted) R.drawable.volume_off else R.drawable.volume_down),
                contentDescription = null,
                tint = filled.copy(alpha = filled.alpha * 0.75f),
                modifier = Modifier.size(18.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(thickness + 20.dp)
                .then(
                    if (context.preview) Modifier else Modifier
                        .pointerInput(volume) {
                            whileStyleInteractive(interactive) {
                                detectTapGestures { offset ->
                                    volume.drag((offset.x / size.width).coerceIn(0f, 1f))
                                    volume.release()
                                }
                            }
                        }
                        .pointerInput(volume) {
                            whileStyleInteractive(interactive) {
                                detectHorizontalDragGestures(
                                    onDragStart = { volume.drag((it.x / size.width).coerceIn(0f, 1f)) },
                                    onDragEnd = { volume.release() },
                                    onDragCancel = { volume.release() },
                                ) { change, _ ->
                                    change.consume()
                                    volume.drag((change.position.x / size.width).coerceIn(0f, 1f))
                                }
                            }
                        },
                )
                .drawBehind {
                    val fraction = (if (context.preview) 0.6f else volume.shown).coerceIn(0f, 1f)
                    val stroke = thickness.toPx()
                    val y = size.height / 2f
                    val start = stroke / 2f
                    val end = size.width - stroke / 2f
                    drawLine(rest, Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
                    if (fraction > 0f) drawLine(filled, Offset(start, y), Offset(start + (end - start) * fraction, y), stroke, StrokeCap.Round)
                },
        )
        if (block.icons) {
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = null,
                tint = filled.copy(alpha = filled.alpha * 0.75f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** what a tap on a button, a tappable box or a chevron item does */
@Composable
internal fun rememberTargetRunner(context: SkinContext): (SkinTarget) -> Unit {
    val connection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val android = LocalContext.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val registry = remember(android) {
        EntryPointAccessors.fromApplication(android.applicationContext, ModulesEntryPoint::class.java).moduleRegistry()
    }
    return remember(connection, database, downloadUtil, android, menuState, context.actions, context.skin, registry) {
        { target ->
            if (ModuleActionGuard.allowTrigger(context.skin.moduleId)) {
                val runBtn: (ButtonKind) -> Unit = { kind ->
                    runControl(kind, connection, database, downloadUtil, android, scope, context)
                }
                val openSheet: (SheetKind, String?) -> Unit = { kind, pageId ->
                    if (kind == SheetKind.ModulePage && pageId != null) {
                        menuState.show {
                            ModulePageSheetContent(
                                moduleId = context.skin.moduleId,
                                pageId = pageId,
                                onNavigateRoute = { route ->
                                    menuState.dismissAll()
                                    context.actions.onOpenRoute(route)
                                },
                                onDismiss = { menuState.dismiss() },
                            )
                        }
                    } else if (kind == SheetKind.Customise) {
                        context.openCustomise()
                    } else {
                        context.actions.onOpenSheet?.invoke(kind)
                    }
                }
                when (target) {
                    is SkinTarget.Control -> runBtn(target.kind)
                    is SkinTarget.Action -> openModuleTarget(
                        context = android,
                        target = target.target,
                        song = connection?.mediaMetadata?.value,
                        moduleId = context.skin.moduleId,
                        openRoute = context.actions.onOpenRoute,
                        onOpenSheet = openSheet,
                        onControl = runBtn,
                        onSetField = { key, value ->
                            val next = value ?: if (registry.fieldValues(context.skin.moduleId)[key].equals("true", true)) "false" else "true"
                            registry.setField(context.skin.moduleId, key, next)
                        },
                        onSetAppSetting = { key, value ->
                            scope.launch { applySafeAppSetting(android, key, value) }
                        },
                    )
                }
            }
        }
    }
}

internal suspend fun applySafeAppSetting(context: Context, key: String, value: String?) {
    val spec = SafeAppSettings.find(key) ?: return
    when (spec) {
        is SafeAppSettings.Toggle -> {
            // floating lyrics requires system overlay permission before turning on
            if (spec.key == FloatingLyricsKey && (value == null || value.equals("true", true))) {
                if (!Settings.canDrawOverlays(context)) return
            }
            context.dataStore.edit { prefs ->
                val rawCurrent = prefs[spec.key] ?: spec.default
                val uiCurrent = if (spec.inverted) !rawCurrent else rawCurrent
                val uiNext = value?.trim()?.lowercase()?.toBooleanStrictOrNull() ?: !uiCurrent
                prefs[spec.key] = if (spec.inverted) !uiNext else uiNext
            }
        }
        is SafeAppSettings.Choice -> {
            val wanted = value?.trim()?.uppercase()
            val match = spec.options.firstOrNull { it.first.equals(wanted, ignoreCase = true) }?.first
            context.dataStore.edit { prefs ->
                if (match != null) {
                    prefs[spec.key] = match
                } else {
                    val cur = prefs[spec.key] ?: spec.default
                    val idx = spec.options.indexOfFirst { it.first == cur }
                    val next = spec.options[(idx + 1) % spec.options.size].first
                    prefs[spec.key] = next
                }
            }
        }
        is SafeAppSettings.Slider -> {
            val parsed = value?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() } ?: return
            context.dataStore.edit { prefs ->
                prefs[spec.key] = parsed.coerceIn(spec.min, spec.max)
            }
        }
    }
}

private fun runControl(
    kind: ButtonKind,
    connection: PlayerConnection?,
    database: MusicDatabase,
    downloadUtil: com.mochyy.mochymusic.playback.DownloadUtil,
    android: Context,
    scope: CoroutineScope,
    context: SkinContext,
) {
    when (kind) {
        ButtonKind.PlayPause -> connection?.togglePlayPause()
        ButtonKind.Play -> connection?.player?.play()
        ButtonKind.Pause -> connection?.player?.pause()
        ButtonKind.Previous -> connection?.seekToPrevious()
        ButtonKind.Next -> connection?.seekToNext()
        ButtonKind.Like -> connection?.toggleLike()
        ButtonKind.Shuffle -> connection?.let { it.player.shuffleModeEnabled = !it.player.shuffleModeEnabled }
        ButtonKind.Repeat -> connection?.player?.toggleRepeatMode()
        ButtonKind.Lyrics -> context.actions.onLyrics?.invoke()
        ButtonKind.Queue -> context.actions.onQueue?.invoke()
        ButtonKind.Forward -> {
            val end = context.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
            connection?.seekTo((context.position.positionMs() + SeekStepMs).coerceAtMost(end))
        }
        ButtonKind.Rewind -> connection?.seekTo((context.position.positionMs() - SeekStepMs).coerceAtLeast(0L))
        ButtonKind.Forward30 -> {
            val end = context.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
            connection?.seekTo((context.position.positionMs() + SeekBigStepMs).coerceAtMost(end))
        }
        ButtonKind.Rewind30 -> connection?.seekTo((context.position.positionMs() - SeekBigStepMs).coerceAtLeast(0L))
        ButtonKind.Restart -> connection?.seekTo(0L)
        ButtonKind.OtherMenu -> context.actions.onOtherMenu()
        ButtonKind.Artist -> connection?.mediaMetadata?.value?.artists?.firstOrNull()?.id?.let(context.actions.onOpenArtist)
        ButtonKind.Album -> connection?.mediaMetadata?.value?.album?.id?.let(context.actions.onOpenAlbum)
        ButtonKind.Menu -> context.openMenu()
        ButtonKind.Customise -> context.openCustomise()
        ButtonKind.Close -> context.actions.onClose()
        ButtonKind.Editor -> context.style.player.editor?.let { context.actions.onOpenRoute(modulePageRoute(context.skin.moduleId, it)) } ?: context.openCustomise()
        ButtonKind.Equalizer -> context.actions.onEqualizer?.invoke() ?: context.actions.onOpenRoute("equalizer")
        ButtonKind.AudioLab -> context.actions.onOpenRoute("audio_lab")
        ButtonKind.FloatingLyrics -> scope.launch { applySafeAppSetting(android, "floatingLyrics", null) }
        ButtonKind.Library -> connection?.toggleLibrary()
        ButtonKind.Download -> {
            val song = connection?.mediaMetadata?.value ?: return
            if (song.localUri != null) return
            val existing = downloadUtil.downloads.value[song.id]?.state
            if (existing == Download.STATE_COMPLETED || existing == Download.STATE_DOWNLOADING || existing == Download.STATE_QUEUED) {
                DownloadService.sendRemoveDownload(android, ExoDownloadService::class.java, song.id, false)
            } else {
                val req = DownloadRequest.Builder(song.id, song.id.toUri())
                    .setCustomCacheKey(song.id)
                    .setData(song.title.toByteArray())
                    .build()
                DownloadService.sendAddDownload(android, ExoDownloadService::class.java, req, false)
            }
        }
        ButtonKind.Pin -> {
            val song = connection?.mediaMetadata?.value ?: return
            scope.launch(Dispatchers.IO) {
                val pinned = database.speedDialDao.isPinned(song.id).first()
                if (pinned) {
                    database.speedDialDao.delete(song.id)
                } else {
                    database.pinToSpeedDial(
                        SpeedDialItem(
                            id = song.id,
                            title = song.title,
                            subtitle = song.artists.joinToString(", ") { it.name },
                            thumbnailUrl = song.thumbnailUrl,
                            type = "SONG",
                            explicit = song.explicit,
                        ),
                    )
                }
            }
        }
        ButtonKind.Radio -> connection?.mediaMetadata?.value?.let { connection.playQueue(YouTubeQueue.radio(it)) }
        ButtonKind.Share -> connection?.mediaMetadata?.value?.let { SongShare.open(it) }
        ButtonKind.SleepTimer -> context.actions.onOpenSheet?.invoke(SheetKind.SleepTimer)
        ButtonKind.Output -> context.actions.onOpenSheet?.invoke(SheetKind.Output)
        ButtonKind.Speed -> context.actions.onOpenSheet?.invoke(SheetKind.Speed)
        ButtonKind.SongDetails -> context.actions.onOpenSheet?.invoke(SheetKind.SongDetails)
        ButtonKind.StreamInfo -> context.actions.onOpenSheet?.invoke(SheetKind.StreamInfo)
        ButtonKind.AddToPlaylist -> context.actions.onOpenSheet?.invoke(SheetKind.AddToPlaylist)
        ButtonKind.CreatePlaylist -> context.actions.onOpenSheet?.invoke(SheetKind.CreatePlaylist)
        ButtonKind.LyricsProvider -> context.actions.onOpenSheet?.invoke(SheetKind.LyricsProvider)
        ButtonKind.LyricsOffset -> context.actions.onOpenSheet?.invoke(SheetKind.LyricsOffset)
        ButtonKind.LyricsTranslation -> context.actions.onOpenSheet?.invoke(SheetKind.LyricsTranslation)
        ButtonKind.LyricsEditor -> context.actions.onOpenSheet?.invoke(SheetKind.LyricsEditor)
        ButtonKind.SongEdit -> context.actions.onOpenSheet?.invoke(SheetKind.SongEdit)
        ButtonKind.VideoQuality -> context.actions.onOpenSheet?.invoke(SheetKind.VideoQuality)
        ButtonKind.CanvasQuality -> context.actions.onOpenSheet?.invoke(SheetKind.CanvasQuality)
        ButtonKind.VideoPicker -> context.actions.onOpenSheet?.invoke(SheetKind.VideoPicker)
        ButtonKind.Devices -> context.actions.onOpenSheet?.invoke(SheetKind.Devices)
        ButtonKind.EditPlayer -> context.actions.onEditPlayer()
        ButtonKind.VolumeUp -> adjustVolume(android, AudioManager.ADJUST_RAISE)
        ButtonKind.VolumeDown -> adjustVolume(android, AudioManager.ADJUST_LOWER)
        ButtonKind.Mute -> adjustVolume(android, AudioManager.ADJUST_TOGGLE_MUTE)
        ButtonKind.OpenPlayer -> Unit
        ButtonKind.Rotate -> context.actions.turn?.toggle?.invoke()
        ButtonKind.FullVideo -> context.actions.onFullVideo?.invoke()
        ButtonKind.Settings -> context.actions.onOpenRoute("mochy_settings")
        ButtonKind.Search -> context.actions.onOpenRoute("search_input")
        ButtonKind.Home -> context.actions.onOpenRoute("home")
        ButtonKind.Back -> context.actions.onClose()
    }
}

internal fun adjustVolume(context: Context, direction: Int) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI) }
}

@DrawableRes
internal fun iconForButton(kind: ButtonKind, on: Boolean = false): Int = when (kind) {
    ButtonKind.PlayPause -> if (on) R.drawable.pause else R.drawable.play
    ButtonKind.Play -> R.drawable.play
    ButtonKind.Pause -> R.drawable.pause
    ButtonKind.Previous -> R.drawable.skip_previous
    ButtonKind.Next -> R.drawable.skip_next
    ButtonKind.Like -> if (on) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill
    ButtonKind.Shuffle -> if (on) R.drawable.shuffle_on else R.drawable.shuffle
    ButtonKind.Repeat -> if (on) R.drawable.repeat_on else R.drawable.repeat
    ButtonKind.Lyrics -> R.drawable.lyrics
    ButtonKind.Queue -> R.drawable.queue_music
    ButtonKind.Forward, ButtonKind.Forward30 -> R.drawable.forward
    ButtonKind.Rewind, ButtonKind.Rewind30, ButtonKind.Restart -> R.drawable.replay
    ButtonKind.OtherMenu -> R.drawable.more_vert
    ButtonKind.Artist -> R.drawable.artist
    ButtonKind.Album -> R.drawable.album
    ButtonKind.Menu -> R.drawable.more_horiz
    ButtonKind.Customise, ButtonKind.EditPlayer -> R.drawable.tune
    ButtonKind.Close -> R.drawable.expand_more
    ButtonKind.Editor, ButtonKind.LyricsEditor, ButtonKind.SongEdit -> R.drawable.edit
    ButtonKind.Equalizer, ButtonKind.AudioLab -> R.drawable.equalizer
    ButtonKind.FloatingLyrics -> R.drawable.lyrics
    ButtonKind.Library -> if (on) R.drawable.library_add_check else R.drawable.library_add
    ButtonKind.Download -> if (on) R.drawable.offline else R.drawable.download
    ButtonKind.Pin -> if (on) R.drawable.remove else R.drawable.add
    ButtonKind.Radio -> R.drawable.radio
    ButtonKind.Share -> R.drawable.share
    ButtonKind.SleepTimer -> R.drawable.sleep_timer
    ButtonKind.Output, ButtonKind.Devices -> R.drawable.cast_connected
    ButtonKind.Speed -> R.drawable.speed
    ButtonKind.SongDetails, ButtonKind.StreamInfo -> R.drawable.info
    ButtonKind.AddToPlaylist, ButtonKind.CreatePlaylist -> R.drawable.playlist_add
    ButtonKind.LyricsProvider, ButtonKind.LyricsOffset -> R.drawable.lyrics
    ButtonKind.LyricsTranslation -> R.drawable.translate
    ButtonKind.VideoQuality, ButtonKind.CanvasQuality, ButtonKind.VideoPicker, ButtonKind.FullVideo -> R.drawable.play
    ButtonKind.VolumeUp -> R.drawable.volume_up
    ButtonKind.VolumeDown -> R.drawable.volume_down
    ButtonKind.Mute -> R.drawable.volume_off
    ButtonKind.OpenPlayer -> R.drawable.expand_less
    ButtonKind.Rotate -> R.drawable.screen_rotation
    ButtonKind.Settings -> R.drawable.settings
    ButtonKind.Search -> R.drawable.search_filled
    ButtonKind.Home -> R.drawable.home_filled
    ButtonKind.Back -> R.drawable.arrow_back
}

@DrawableRes
private fun iconFor(target: SkinTarget, on: Boolean): Int = when (target) {
    is SkinTarget.Control -> iconForButton(target.kind, on)
    is SkinTarget.Action -> when (val a = target.target) {
        is ActionTarget.Control -> iconForButton(a.kind, on)
        is ActionTarget.Sheet -> iconForSheet(a.kind)
        is ActionTarget.Search -> R.drawable.search_filled
        is ActionTarget.Route -> R.drawable.navigate_next
        is ActionTarget.SetField, is ActionTarget.SetAppSetting -> R.drawable.tune
        is ActionTarget.Link, is ActionTarget.Page, is ActionTarget.ModuleSheet -> R.drawable.puzzle
    }
}

@DrawableRes
internal fun iconForSheet(kind: SheetKind): Int = when (kind) {
    SheetKind.PlayerMenu -> R.drawable.more_horiz
    SheetKind.SleepTimer -> R.drawable.sleep_timer
    SheetKind.Output, SheetKind.Devices -> R.drawable.cast_connected
    SheetKind.Speed -> R.drawable.speed
    SheetKind.SongDetails, SheetKind.StreamInfo -> R.drawable.info
    SheetKind.Share -> R.drawable.share
    SheetKind.AddToPlaylist, SheetKind.CreatePlaylist -> R.drawable.playlist_add
    SheetKind.LyricsProvider, SheetKind.LyricsOffset -> R.drawable.lyrics
    SheetKind.LyricsTranslation -> R.drawable.translate
    SheetKind.LyricsEditor, SheetKind.SongEdit -> R.drawable.edit
    SheetKind.Customise, SheetKind.EditPlayer -> R.drawable.tune
    SheetKind.SongMenu, SheetKind.PlaylistMenu -> R.drawable.queue_music
    SheetKind.ArtistMenu -> R.drawable.artist
    SheetKind.VideoQuality, SheetKind.CanvasQuality, SheetKind.VideoPicker -> R.drawable.play
    SheetKind.ModulePage -> R.drawable.puzzle
}

private val Toggles = setOf(
    ButtonKind.Like, ButtonKind.Shuffle, ButtonKind.Repeat, ButtonKind.PlayPause,
    ButtonKind.Library, ButtonKind.Download, ButtonKind.Pin, ButtonKind.FloatingLyrics,
)
private const val SeekStepMs = 10_000L
private const val SeekBigStepMs = 30_000L
