package com.mochyy.mochymusic.ui.player.video

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.component.PopupSheetHost
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.constants.VideoModeWholeVideoKey
import com.mochyy.mochymusic.ui.menu.AddToPlaylistDialog
import com.mochyy.mochymusic.ui.player.menu.LocalMenuSheetFloating
import com.mochyy.mochymusic.ui.player.menu.SongDetailsSheet
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.ui.player.PlaybackClock
import com.mochyy.mochymusic.ui.player.SongVideo
import com.mochyy.mochymusic.ui.player.models.TrackInfo
import com.mochyy.mochymusic.ui.player.rememberSongVideoLookup
import com.mochyy.mochymusic.ui.player.styles.StyleActions
import com.mochyy.mochymusic.ui.player.styles.quarterTurn
import com.mochyy.mochymusic.ui.player.styles.sin01
import com.mochyy.mochymusic.ui.player.styles.smooth
import com.mochyy.mochymusic.ui.player.styles.turnFit
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.AppMotion
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// the whole screen up close: auto may go up to full hd here
private const val ModeAutoCap = 1080

// how long the picture takes to go between zoomed to fill and shown whole
private const val FitMs = 600

// a double tap jumps this far
private const val SeekStepMs = 10_000L

// with the lyrics open the picture dims by this much, and fades into the light behind it over this share of the pane
private const val PaneDim = 0.18f
private const val PaneFadeShare = 0.55f

// the edge colours ease from one scene's to the next over this long, and only move for a change worth seeing
private const val AmbientFadeMs = 1_400
private const val AmbientMinChange = 0.04f

private const val ControlsFadeMs = 280
private const val ControlsHideMs = 3_500L

// the pane follows the finger over this share of the screen's height
private const val PaneDragShare = 0.7f

// a pull down this far on the video closes the player
private val CloseDrag = 48.dp

// the player turned sideways around the song's youtube video, its lyrics sliding in from the right
@Composable
fun VideoPlayerStyle(actions: StyleActions, modifier: Modifier = Modifier, preview: Boolean = false) {
    if (preview) VideoModePreview(modifier) else VideoModeScreen(actions, modifier)
}

@Composable
private fun VideoModeScreen(actions: StyleActions, modifier: Modifier) {
    val connection = LocalPlayerConnection.current ?: return
    val sheet = LocalPlayerBottomSheetState.current
    val track by connection.uiState.trackInfo.collectAsState()
    val transport by connection.uiState.transportState.collectAsState()
    val metadata by connection.mediaMetadata.collectAsState()
    val isVideoSong = metadata?.id == track.mediaId && metadata?.isVideoSong == true
    val lookup = rememberSongVideoLookup(
        mediaId = track.mediaId,
        title = track.title,
        artist = track.artist,
        isVideoSong = isVideoSong,
        enabled = track.mediaId.isNotBlank(),
    )
    val scope = rememberCoroutineScope()
    val state = remember(scope) { VideoModeState(scope) }
    // collapsed into the mini player the video holds still; it's still there for the next open
    val sheetShowing by remember(sheet) { derivedStateOf { sheet == null || sheet.progress > 0f } }
    val running = transport.isPlaying && sheetShowing
    val lyricClock = remember(connection) {
        PlaybackClock({ connection.player }, { connection.lyricsOffsetMs.value.toLong() })
    }
    val seekClock = remember(connection) { PlaybackClock({ connection.player }) }
    val lyrics = rememberVideoLyrics()
    val look = rememberVideoLyricLook()
    val glass = remember { GlassState() }
    val ambient = rememberAmbient()

    val controlsFade = animateFloatAsState(
        targetValue = if (state.controlsShown) 1f else 0f,
        animationSpec = tween(ControlsFadeMs, easing = FastOutSlowInEasing),
        label = "videoControls",
    )
    val controls = remember(controlsFade) { { controlsFade.value } }
    LaunchedEffect(state.controlsShown, state.lastTouchMs, transport.isPlaying, state.menuOpen, state.paneDragging) {
        if (state.controlsShown && transport.isPlaying && !state.menuOpen && !state.paneDragging) {
            delay(ControlsHideMs)
            state.controlsShown = false
        }
    }
    // back closes the pane before it closes the player; added only while the pane is open, so it's newer than the sheet's own and wins
    if (state.paneOpen && sheetShowing) BackHandler { state.closePane() }

    // the lookup has already waited for the song to settle; the session may be the one the cover was playing
    val video = lookup.video
    // collapsed, the mini player plays this same session; the mode only holds it while any of it shows
    val session = if (sheetShowing) video?.let { rememberYouTubeVideoSession(it.videoId) } else null
    if (session != null && video != null) DriveYouTubeVideo(session, video, running, ModeAutoCap)
    // zoomed to fill by default; the menu's switch eases it out to the whole picture and back
    val (wholeVideo) = rememberPreference(VideoModeWholeVideoKey, defaultValue = false)
    val fit = animateFloatAsState(
        targetValue = if (wholeVideo) 1f else 0f,
        animationSpec = tween(FitMs, easing = FastOutSlowInEasing),
        label = "videoFit",
    )

    var showAddToPlaylist by remember { mutableStateOf(false) }
    // the lyrics, the queue and the sheets in here fling the right way, though the whole frame is turned
    com.mochyy.mochymusic.ui.player.styles.TurnedFlingFix()
    // its own sheet host, inside the turned frame, so "add to playlist" opens the right way up
    val menuSheets = remember { PopupSheetState() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        VideoTurnFrame(modifier = Modifier.fillMaxSize()) {
            PopupSheetHost(states = listOf(menuSheets)) {
                // every sheet the mode opens floats as a card the right way up, and never grows when scrolled
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalMenuState provides menuSheets,
                    LocalMenuSheetFloating provides true,
                ) {
                    VideoModeLayout(
                        state = state,
                        track = track,
                        video = video,
                        session = session,
                        searching = lookup.searching,
                        isPlaying = transport.isPlaying,
                        running = running,
                        glass = glass,
                        ambient = ambient,
                        lyrics = lyrics,
                        look = look,
                        lyricClock = lyricClock,
                        seekClock = seekClock,
                        controls = controls,
                        fit = { fit.value },
                        onClose = { sheet?.collapseSoft() },
                    )
                    if (state.menuOpen) {
                        VideoModeMenu(
                            session = session,
                            onDismiss = { state.menuOpen = false },
                            onExitVideoMode = actions.onExitVideoMode,
                            onOpenPane = { state.openPane(it) },
                            onAddToPlaylist = { showAddToPlaylist = true },
                            onSongInfo = { state.songInfoOpen = true },
                        )
                    }
                    if (state.songInfoOpen) {
                        SongDetailsSheet(
                            onDismiss = { state.songInfoOpen = false },
                            onOpenArtist = { artistId ->
                                state.songInfoOpen = false
                                actions.onOpenArtist(artistId)
                            },
                            onOpenAlbum = { albumId ->
                                state.songInfoOpen = false
                                actions.onOpenAlbum(albumId)
                            },
                        )
                    }
                    VideoAddToPlaylist(visible = showAddToPlaylist, onDismiss = { showAddToPlaylist = false })
                }
            }
        }
    }
}

// every layer of the landscape frame, back to front: light, video, pane, scrim, the line, the controls
@Composable
private fun VideoModeLayout(
    state: VideoModeState,
    track: TrackInfo,
    video: SongVideo?,
    session: YouTubeVideoSession?,
    searching: Boolean,
    isPlaying: Boolean,
    running: Boolean,
    glass: GlassState,
    ambient: VideoAmbientState,
    lyrics: VideoLyrics,
    look: VideoLyricLook,
    lyricClock: () -> Long,
    seekClock: () -> Long,
    controls: () -> Float,
    fit: () -> Float,
    onClose: () -> Unit,
) {
    val sheet = LocalPlayerBottomSheetState.current
    val connection = LocalPlayerConnection.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // the picture's own edge colours light whatever it doesn't cover
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(ambient.backLeft.value, ambient.backRight.value)))
            },
    ) {
        val frame = remember(maxWidth, maxHeight) { VideoFrame(maxWidth, maxHeight) }
        val density = LocalDensity.current
        val slidePx = with(density) { frame.videoSlide.toPx() }
        val paneShift = with(density) { frame.paneWidth.toPx() } / 2f
        val regularMounted by remember { derivedStateOf { state.pane.value < 0.999f || state.paneDragging } }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .videoGestures(
                    state = state,
                    frame = frame,
                    onCollapse = { sheet?.collapseSoft() },
                    onSeek = { forward ->
                        val player = connection?.player ?: return@videoGestures
                        val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                        val to = (player.currentPosition + if (forward) SeekStepMs else -SeekStepMs).coerceIn(0L, duration)
                        connection.seekTo(to)
                        state.flashSeek(forward)
                    },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -slidePx * state.pane.value }
                    // recorded here rather than on the video itself, so the pane blurs the picture with its fade
                    .glassRoot(glass, isActive = { state.paneMounted })
                    .drawWithContent {
                        drawContent()
                        // with the lyrics open the picture dims a touch and melts into the light behind it before
                        // its right edge, so no hard edge or corner of it ever shows inside the pane
                        val p = state.pane.value
                        if (p < 0.002f) return@drawWithContent
                        drawRect(Color.Black.copy(alpha = PaneDim * p))
                        val fadeEnd = size.width
                        val fadeStart = fadeEnd - paneShift * 2f * PaneFadeShare
                        val edge = ambient.backRight.value
                        drawRect(
                            Brush.horizontalGradient(
                                0f to edge.copy(alpha = 0f),
                                1f to edge.copy(alpha = p),
                                startX = fadeStart,
                                endX = fadeEnd,
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(fadeStart, 0f),
                        )
                    },
            ) {
                // the cover stays under the video until its first picture is up, so it never flashes black
                if (session == null || !session.hasFrame) {
                    VideoArtwork(thumbnailUrl = track.thumbnailUrl)
                }
                if (session != null) {
                    YouTubeVideoSurface(
                        session = session,
                        isPlaying = running,
                        fit = fit,
                        keepScreenOn = running,
                        onAmbient = ambient::follow,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            val loading = searching || (session != null && (!session.hasFrame || session.isStalled))
            VideoLoadingIndicator(
                loading = loading && running,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { translationX = -paneShift * state.pane.value },
            )
            // what's happening while there's no picture: still looking, or there's none to find
            val status = when {
                video != null -> null
                searching -> R.string.video_mode_finding
                else -> R.string.video_mode_no_video
            }
            if (status != null) {
                Text(
                    text = stringResource(status),
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp)
                        .graphicsLayer {
                            alpha = 1f - controls()
                            translationX = -paneShift * state.pane.value
                        },
                )
            }

            if (state.paneMounted) {
                VideoPaneBackdrop(glass = glass, frame = frame, state = state, tint = ambient.pane)
                VideoPaneContent(frame = frame, state = state, lyrics = lyrics, look = look, positionProvider = lyricClock)
            }

            // the controls dim the whole screen a little, the open pane too, and darken its top and bottom edges where they sit
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val c = controls()
                        if (c < 0.002f) return@drawBehind
                        drawRect(Color.Black.copy(alpha = 0.2f * c))
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.4f * c),
                                0.3f to Color.Transparent,
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f * c),
                            )
                        )
                    },
            )

            if (regularMounted) {
                VideoRegularLyrics(
                    lyrics = lyrics,
                    look = look,
                    frame = frame,
                    state = state,
                    controls = controls,
                    positionProvider = lyricClock,
                )
            }

            VideoSeekFlash(frame = frame, state = state)
            VideoTopBar(
                track = track,
                frame = frame,
                state = state,
                controls = controls,
                onClose = onClose,
                onSongInfo = { state.songInfoOpen = true },
            )
            VideoTransport(state = state, controls = controls, isPlaying = isPlaying)
            VideoVolumeBar(state = state, controls = controls)
            VideoSeekRow(
                state = state,
                controls = controls,
                positionProvider = seekClock,
                onChevron = { state.menuOpen = true },
            )
            VideoPaneHandle(frame = frame, state = state)
        }
    }
}

// tap for the controls (or the pane, on its bar), double tap for 10s back or on, drag up on the pane's side for it, drag down to close
private fun Modifier.videoGestures(
    state: VideoModeState,
    frame: VideoFrame,
    onCollapse: () -> Unit,
    onSeek: (forward: Boolean) -> Unit,
): Modifier = this
    .pointerInput(state, frame) {
        detectTapGestures(
            onDoubleTap = { offset ->
                // the video's own part of the screen; an open pane keeps its taps to itself
                val videoWidth = size.width - (frame.paneWidth.toPx() * state.pane.value)
                if (offset.x < videoWidth) {
                    state.touched()
                    onSeek(offset.x > videoWidth / 2f)
                }
            },
            onTap = { offset ->
                if (paneHandleZone(frame, state.paneOpen, offset.x, offset.y, this)) {
                    if (state.paneOpen) state.closePane() else state.openPane(VideoPane.LYRICS)
                } else {
                    state.toggleControls()
                }
            },
        )
    }
    .pointerInput(state, frame) {
        val travel = size.height * PaneDragShare
        val closePx = CloseDrag.toPx()
        val tracker = VelocityTracker()
        var mode = DragMode.NONE
        var pulled = 0f
        detectVerticalDragGestures(
            onDragStart = { offset ->
                tracker.resetTracking()
                pulled = 0f
                mode = when {
                    state.paneOpen && paneHandleZone(frame, true, offset.x, offset.y, this) -> {
                        state.startPaneDrag()
                        DragMode.PANE
                    }
                    // beside an open pane the video only takes taps; the pane's own list scrolls itself
                    state.paneOpen -> DragMode.NONE
                    // the pane comes up only from its own side, where its bar is; elsewhere a drag can only close the player
                    paneSide(frame, offset.x, this) -> DragMode.UNDECIDED
                    else -> DragMode.COLLAPSE
                }
            },
            onVerticalDrag = { change, dy ->
                tracker.addPosition(change.uptimeMillis, change.position)
                if (mode == DragMode.UNDECIDED) {
                    mode = if (dy < 0f) {
                        state.startPaneDrag()
                        DragMode.PANE
                    } else {
                        DragMode.COLLAPSE
                    }
                }
                when (mode) {
                    DragMode.PANE -> {
                        change.consume()
                        state.dragPaneBy(-dy / travel)
                    }
                    DragMode.COLLAPSE -> {
                        change.consume()
                        pulled += dy
                        if (pulled > closePx) {
                            mode = DragMode.NONE
                            onCollapse()
                        }
                    }
                    else -> Unit
                }
            },
            onDragEnd = {
                if (mode == DragMode.PANE) state.settlePane(-tracker.calculateVelocity().y / travel)
                mode = DragMode.NONE
            },
            onDragCancel = {
                if (mode == DragMode.PANE) state.settlePane(0f)
                mode = DragMode.NONE
            },
        )
    }

private enum class DragMode { NONE, UNDECIDED, PANE, COLLAPSE }

// the frame turns into landscape when the mode comes up, the way the sideways styles turn; built on its first frames, turned once ready
@Composable
private fun VideoTurnFrame(modifier: Modifier, content: @Composable () -> Unit) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        turn.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 150f))
    }
    Box(
        modifier = modifier
            .quarterTurn(true)
            .graphicsLayer {
                val t = turn.value
                if (t == 1f) return@graphicsLayer
                // it arrives from upright, swinging clockwise into landscape
                val angle = -90f * (1f - t)
                rotationZ = angle
                val scale = turnFit(size.width, size.height, 90f, angle) * (1f - 0.04f * sin01(t))
                scaleX = scale
                scaleY = scale
                // tipped back a touch mid-swing, so it turns in space rather than on glass
                cameraDistance = 14f * density
                rotationX = 9f * sin01(t)
                alpha = smooth(t / 0.45f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blur = 14.dp.toPx() * sin01(t)
                    renderEffect = if (blur > 0.5f && AppMotion.elementBlur) BlurEffect(blur, blur, TileMode.Clamp) else null
                }
            },
    ) {
        content()
    }
}

// no video (or none yet): the cover, blurred across the screen
@Composable
private fun VideoArtwork(thumbnailUrl: String?) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = thumbnailUrl?.resize(720, 720),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = BlurEffectCache.get(48.dp.toPx())
                },
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
    }
}

@Composable
private fun VideoAddToPlaylist(visible: Boolean, onDismiss: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val metadata = connection.mediaMetadata.collectAsState().value ?: return
    AddToPlaylistDialog(
        isVisible = visible,
        onGetSong = {
            // the playlist keeps the song by id, so it has to be in the database first
            database.transaction { insert(metadata) }
            listOf(metadata.id)
        },
        onDismiss = onDismiss,
        topSongId = metadata.id,
        topSongArtworkUrl = metadata.thumbnailUrl,
    )
}

// the picture's edge colours, eased: a dark wash of each for round the video, and the right one, deeper, for the pane
internal class VideoAmbientState(private val scope: kotlinx.coroutines.CoroutineScope) {
    val backLeft = androidx.compose.animation.Animatable(Color.Black)
    val backRight = androidx.compose.animation.Animatable(Color.Black)
    private val paneTone = androidx.compose.animation.Animatable(Color(0xFF1E1E1E))
    val pane: androidx.compose.runtime.State<Color> = paneTone.asState()

    private var seen: VideoAmbient? = null

    fun follow(next: VideoAmbient) {
        val last = seen
        if (last != null && distance(last.left, next.left) < AmbientMinChange && distance(last.right, next.right) < AmbientMinChange) return
        seen = next
        val spec = tween<Color>(AmbientFadeMs, easing = FastOutSlowInEasing)
        scope.launch { backLeft.animateTo(tone(next.left, 0.16f), spec) }
        scope.launch { backRight.animateTo(tone(next.right, 0.16f), spec) }
        scope.launch { paneTone.animateTo(tone(next.right, 0.24f), spec) }
    }

    private fun distance(a: Color, b: Color): Float =
        maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue))

    // kept dark and a little calmer than the picture, so white lyrics always read over it
    private fun tone(color: Color, maxLightness: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[1] = hsl[1] * 0.8f
        hsl[2] = hsl[2].coerceIn(0.06f, maxLightness)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}

@Composable
private fun rememberAmbient(): VideoAmbientState {
    val scope = rememberCoroutineScope()
    return remember(scope) { VideoAmbientState(scope) }
}

// the selector's card: the mode drawn small and sideways, the cover standing in for the video
@Composable
private fun VideoModePreview(modifier: Modifier) {
    val connection = LocalPlayerConnection.current
    val thumbnail = connection?.uiState?.trackInfo?.collectAsState()?.value?.thumbnailUrl
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.fillMaxSize().quarterTurn(true)) {
            AsyncImage(
                model = thumbnail?.resize(480, 480),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.85f },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .size(16.dp)
                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 18.dp)) {
                Box(modifier = Modifier.size(64.dp, 5.dp).background(Color.White.copy(alpha = 0.7f), CircleShape))
                Spacer(modifier = Modifier.height(5.dp))
                Box(modifier = Modifier.size(120.dp, 9.dp).background(Color.White, CircleShape))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = 8.dp)
                    .size(60.dp, 3.dp)
                    .background(Color.White.copy(alpha = 0.6f), CircleShape),
            )
        }
    }
}
