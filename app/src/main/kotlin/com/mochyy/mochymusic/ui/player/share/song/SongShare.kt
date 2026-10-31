// SongShare.kt

package com.mochyy.mochymusic.ui.player.share.song

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.ElementBlurKey
import com.mochyy.mochymusic.constants.LyricsHighBloomKey
import com.mochyy.mochymusic.constants.LyricsMotionStyle
import com.mochyy.mochymusic.constants.LyricsMotionStyleKey
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.component.PopupSheetHost
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.player.share.LyricsShareSession
import com.mochyy.mochymusic.ui.player.share.ShareBackground
import com.mochyy.mochymusic.ui.player.share.ShareFlight
import com.mochyy.mochymusic.ui.player.share.showLinePicker
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// "share song" from any menu: the menu calls open, and the host in MainActivity shows the share over everything, the player included
object SongShare {
    internal var song by mutableStateOf<MediaMetadata?>(null)
        private set

    fun open(song: MediaMetadata) {
        this.song = song
    }

    internal fun close() {
        song = null
    }
}

@Composable
fun SongShareHost() {
    val song = SongShare.song ?: return
    key(song.id) { SongShareDialog(song, onDismiss = SongShare::close) }
}

// holding a lyric line: the same share, opening on the line picker with the held line flying in, and no link page
@Composable
internal fun LyricsSongShare(song: MediaMetadata, pick: LyricsShareSession, lyrics: List<LyricsEntry>, onDismiss: () -> Unit) {
    SongShareDialog(song, onDismiss, pick = pick, heldLyrics = lyrics)
}

@Composable
private fun SongShareDialog(
    song: MediaMetadata,
    onDismiss: () -> Unit,
    pick: LyricsShareSession? = null,
    heldLyrics: List<LyricsEntry>? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            // the sheets handle back themselves, one sheet at a time
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        EdgeToEdgeDialogWindow()
        val context = LocalContext.current
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current
        val session = remember {
            SongShareSession(song).also { started ->
                if (pick != null && heldLyrics != null) {
                    started.kind = SongShareKind.Lyrics
                    started.pick = pick
                    started.lyrics = Fetched.Found(heldLyrics)
                }
            }
        }
        val sheets = remember { PopupSheetState() }

        ReadLyricsLook(session)

        LaunchedEffect(Unit) {
            if (pick != null) {
                // the held line's own picker comes first; closing it plays the opening backwards, back into the lyrics
                pick.onLinesPicked = { popup -> session.choseLines(popup) }
                val picker = sheets.showLinePicker(pick)
                snapshotFlow { picker.isClosing }.first { it }
                pick.choreography.playBackward()
            } else {
                val first = sheets.showKindPage(session)
                snapshotFlow { first.isClosing }.first { it }
            }
            snapshotFlow { sheets.isVisible }.first { !it }
            onDismiss()
        }
        LaunchedEffect(session) {
            launch { loadShareArt(context, session) }
            launch { loadAnimatedCover(session) }
            launch { findMusicVideo(session) }
            if (heldLyrics == null) {
                loadShareLyrics(session, database, playerConnection?.service?.lyricsHelper)
            } else {
                loadLyricsOffset(session, database)
            }
            val playing = playerConnection?.player?.takeIf { it.currentMediaItem?.mediaId == song.id }?.currentPosition
            pickStartingMoment(session, playing)
        }
        // the line picker is tinted with the cover's colours too
        LaunchedEffect(session) { snapshotFlow { session.palette }.collect { session.pick?.palette = it } }
        // your own picture is read again whenever a different one is picked
        LaunchedEffect(session) {
            snapshotFlow { ((session.background as? SongBackground.Still)?.background as? ShareBackground.Picture)?.uri }
                .collect { uri -> if (uri != null) loadPictureArt(context, session, uri) }
        }
        // lining the music video up means listening to both; it only happens once the music video is picked
        LaunchedEffect(session) {
            snapshotFlow { (session.background as? SongBackground.Video)?.source == ShareVideoSource.MusicVideo }.first { it }
            matchMusicVideo(session, playerConnection?.service)
        }
        MovesThePlayhead(session)
        SongPreviewAudio(session)

        Box(Modifier.fillMaxSize()) {
            PopupSheetHost(states = listOf(sheets)) {}
            if (pick != null) ShareFlight(pick)
            // never drawn by the window: export frames are drawn from here by hand, at full size
            AndroidView(
                factory = { FrameLayout(it).apply { visibility = View.INVISIBLE }.also { host -> session.offscreenHost = host } },
                modifier = Modifier.size(1.dp),
            )
        }
    }
}

// one clock for every picture in the share, and only while a video picture is up; a still share draws once and rests
@Composable
private fun MovesThePlayhead(session: SongShareSession) {
    LaunchedEffect(session) {
        snapshotFlow { session.isVideo && session.previewWanted }.collectLatest { moving ->
            if (!moving) {
                session.playhead.longValue = session.windowStartMs
                return@collectLatest
            }
            while (true) withFrameMillis { session.playhead.longValue = session.previewPositionMs() }
        }
    }
}

// the lyrics page's look, read here once so the card's lyrics and the exports use the same one
@Composable
private fun ReadLyricsLook(session: SongShareSession) {
    val (motionStyle) = rememberEnumPreference(LyricsMotionStyleKey, defaultValue = LyricsMotionStyle.MOTION)
    val (highBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)
    val (lineBlur) = rememberPreference(ElementBlurKey, defaultValue = true)
    LaunchedEffect(motionStyle, highBloom, lineBlur) {
        session.lyricsLook = ShareLyricsLook(motionStyle, highBloom, lineBlur)
    }
}

// the share's own player for the song; it stays quiet until a video share plays, and only then pauses the app's music, which carries on once the share closes
@Composable
private fun SongPreviewAudio(session: SongShareSession) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    WatchesTheApp(session)
    // set once the share's song has paused the app's own, so closing the share plays it again
    val pausedTheApp = remember { booleanArrayOf(false) }
    DisposableEffect(session) {
        val preview = SongPreviewPlayer(context, playerConnection.service.shareDataSourceFactory())
        session.preview = preview
        onDispose {
            session.preview = null
            preview.release()
            if (pausedTheApp[0]) playerConnection.player.play()
        }
    }
    LaunchedEffect(session) {
        val item = session.song.toMediaItem()
        // nothing loads until the song first plays, so a picture never fetches it at all
        var loaded: Pair<Long, Long>? = null
        snapshotFlow { Triple(session.previewPlays, session.windowStartMs, session.windowLengthMs) }.collect { (plays, start, length) ->
            val preview = session.preview ?: return@collect
            if (plays && playerConnection.player.isPlaying) {
                // the two would play over each other
                playerConnection.player.pause()
                pausedTheApp[0] = true
            }
            if (loaded == null && !plays) return@collect
            // the window it loops follows the timeline once the finger lets go
            val window = start to length
            if (loaded != window) {
                preview.load(item, start, length, play = plays)
                loaded = window
            } else {
                preview.setPlaying(plays)
            }
        }
    }
}

// the share's song stops the moment you leave mochy, say for instagram, so it never loops on under the app you post from
@Composable
private fun WatchesTheApp(session: SongShareSession) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, _ -> session.onScreen = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

// the share starts on what's playing when it's this song, otherwise on the first sung line
private fun pickStartingMoment(session: SongShareSession, playingAtMs: Long?) {
    if (session.windowTouched || session.pick != null) return
    val lines = session.lyrics.found.orEmpty()
    val start = when {
        playingAtMs != null && lines.isNotEmpty() -> {
            val current = LyricsUtils.findCurrentLineIndex(lines, playingAtMs).coerceIn(0, lines.lastIndex)
            lines[current].time - session.lyricsOffsetMs
        }
        playingAtMs != null -> playingAtMs
        else -> (lines.firstOrNull { it.time > 0L }?.time ?: 0L) - session.lyricsOffsetMs
    }
    session.startAt(start)
}

// lines picked: the window moves to them, and the styles come next
internal fun SongShareSession.choseLines(popup: PopupSheetState) {
    fitWindowToPick()
    popup.showStylePage(this)
}

// the dialog's window drawn edge to edge, see-through and without its own dim or animation, so its coordinates are the app's and the held line flies from exactly where it sits
@Composable
private fun EdgeToEdgeDialogWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        window.run {
            setWindowAnimations(0)
            setDimAmount(0f)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            @Suppress("DEPRECATION")
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            @Suppress("DEPRECATION")
            statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 28) {
                attributes = attributes.also {
                    it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
            if (Build.VERSION.SDK_INT >= 30) {
                attributes = attributes.also { it.fitInsetsTypes = 0 }
            }
        }
    }
}
