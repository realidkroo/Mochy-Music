// SongShareSendPage.kt

package com.mochyy.mochymusic.ui.player.share.song

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.datasource.DataSource
import androidx.media3.transformer.ExportException
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.player.share.ShareMuted
import com.mochyy.mochymusic.ui.player.share.song.export.ShareApp
import com.mochyy.mochymusic.ui.player.share.song.export.ShareEncoders
import com.mochyy.mochymusic.ui.player.share.song.export.ShareExportJob
import com.mochyy.mochymusic.ui.player.share.song.export.ShareQuality
import com.mochyy.mochymusic.ui.player.share.song.export.ShareRequest
import com.mochyy.mochymusic.ui.player.share.song.export.ShareTheme
import com.mochyy.mochymusic.ui.player.share.song.export.exportAndShare
import com.mochyy.mochymusic.ui.player.share.song.export.installedPackage
import com.mochyy.mochymusic.ui.player.share.song.export.sendTo
import com.mochyy.mochymusic.ui.player.share.song.export.takesSticker
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

// "Share to": the finished picture, the three apps, and export and share for everything else
internal fun PopupSheetState.showSendPage(session: SongShareSession) {
    val popup = this
    val send = SendState(session)
    show(
        buttonBar = { SendButton(send) },
        // leaving the page stops an export that's still running
        onClosed = send::cancel,
    ) {
        SendContent(send, live = popup.rememberIsFront())
    }
}

// one export at a time, shared by the page and its button
@Stable
private class SendState(val session: SongShareSession) {
    var quality by mutableStateOf<ShareQuality?>(null)
    var job by mutableStateOf<ShareExportJob?>(null)
    var progress by mutableFloatStateOf(0f)
    var note by mutableStateOf<Int?>(null)
    var scope: CoroutineScope? = null
    var context: Context? = null
    var theme: ShareTheme? = null
    var songSource: DataSource.Factory? = null
    private var running: Job? = null

    val working: Boolean get() = job != null

    fun start(work: suspend ShareRequest.() -> Int?) {
        if (working) return
        val scope = scope ?: return
        val request = ShareRequest(
            context = context ?: return,
            session = session,
            theme = theme ?: return,
            quality = quality ?: ShareQuality.FullHd,
            songSource = songSource ?: return,
            job = ShareExportJob(),
        )
        job = request.job
        note = null
        // the song stops while the export reads it, so the two don't fight over the phone, and stays stopped once it's handed to another app
        val wasPlaying = session.previewWanted
        session.previewWanted = false
        running = scope.launch {
            var handedOff = false
            try {
                note = request.work()
                handedOff = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // kept in release builds, where timber logs nothing
                Log.e("SongShare", "Share export failed", e)
                note = if (e.isConnectionProblem()) R.string.song_share_failed else R.string.song_share_failed_retry
            } finally {
                job = null
                if (!handedOff) session.previewWanted = wasPlaying
            }
        }
    }

    fun cancel() {
        job?.cancelled = true
        running?.cancel()
    }
}

// only a failure to load something is worth blaming on the connection; a picture is made entirely on the phone
private fun Throwable.isConnectionProblem(): Boolean = generateSequence(this) { it.cause }.any { cause ->
    cause is IOException && cause !is FileNotFoundException ||
        cause is ExportException && cause.errorCode in ExportException.ERROR_CODE_IO_UNSPECIFIED..ExportException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
}

@Composable
private fun SendContent(send: SendState, live: Boolean) {
    val session = send.session
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val playerConnection = LocalPlayerConnection.current
    // made once: each one builds its own http client
    val songSource = remember(playerConnection) { playerConnection?.service?.shareDataSourceFactory() }
    val video = session.isVideo
    SideEffect {
        send.scope = scope
        send.context = context
        send.theme = ShareTheme(colors, typography)
        send.songSource = songSource
    }
    // the sharpest the phone can make: 4k pictures always, 4k video when its encoder takes it
    val canUltraHd = remember(video) { !video || ShareEncoders.canEncode(ShareQuality.UltraHd, 30) }
    LaunchedEffect(canUltraHd) {
        if (send.quality == null || (!canUltraHd && send.quality == ShareQuality.UltraHd)) {
            send.quality = if (canUltraHd) ShareQuality.UltraHd else ShareQuality.FullHd
        }
    }
    // the export's progress lives off the main thread; it's copied in a few times a second
    LaunchedEffect(send.job) {
        val job = send.job ?: return@LaunchedEffect
        while (true) {
            send.progress = job.progress
            delay(100)
        }
    }
    val previewHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.song_share_send_title),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = (-0.8).sp,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        Box(Modifier.fillMaxWidth().height(previewHeight)) {
            SongSharePreview(
                session = session,
                live = live,
                modifier = Modifier.fillMaxSize(),
                movable = false,
            )
            if (video) {
                PreviewPill(
                    playing = session.previewWanted,
                    enabled = !send.working,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                ) { session.previewWanted = !session.previewWanted }
            }
        }
        // a still background makes a picture; this turns it into a video with the song, the lyrics flowing through the picked lines
        if (!session.hasVideoBackground) {
            Spacer(Modifier.height(14.dp))
            ShareAsVideoRow(session, enabled = !send.working)
        }
        Divider()
        val stickers = ShareApp.entries.any { it.takesSticker(context) }
        Caption(stringResource(if (stickers) R.string.song_share_send_apps_sticker else R.string.song_share_send_apps))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ShareApp.entries.forEach { app ->
                AppTile(app, enabled = !send.working) { send.start { sendTo(app); null } }
            }
        }
        Spacer(Modifier.height(16.dp))
        QualityPicker(send, canUltraHd, video)
        Divider()
        when {
            send.working -> ExportProgress(send.progress, video)
            send.note != null -> Caption(stringResource(send.note!!))
            else -> Caption(stringResource(R.string.song_share_send_or))
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ShareAsVideoRow(session: SongShareSession, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.song_share_as_video),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White,
            )
            Text(
                stringResource(R.string.song_share_as_video_hint),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = ShareMuted,
            )
        }
        AppSwitch(
            checked = session.shareAsVideo,
            onCheckedChange = { on ->
                session.shareAsVideo = on
                // the lyrics play the lines that were picked, unless the part was moved by hand
                if (on && session.pick != null && !session.windowTouched) session.fitWindowToPick()
            },
            enabled = enabled,
        )
    }
}

@Composable
private fun SendButton(send: SendState) {
    if (send.working) {
        SheetPillButton(text = stringResource(R.string.song_share_cancel), onClick = send::cancel)
    } else {
        SheetPillButton(
            text = stringResource(R.string.song_share_export_button),
            onClick = { send.start { if (exportAndShare()) R.string.song_share_saved else null } },
        )
    }
}

@Composable
private fun PreviewPill(playing: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        stringResource(if (playing) R.string.song_share_pause else R.string.song_share_preview),
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = Color.White,
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 7.dp),
    )
}

// the app's own icon from the phone; a missing app shows greyed out and says so
@Composable
private fun AppTile(app: ShareApp, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val target by produceState<String?>(null, app) { value = withContext(Dispatchers.IO) { app.installedPackage(context) } }
    val icon by produceState<ImageBitmap?>(null, target) {
        val name = target ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(name).toBitmap(144, 144).asImageBitmap() }.getOrNull()
        }
    }
    val installed = target != null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .graphicsLayer { alpha = if (installed && enabled) 1f else 0.4f }
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = installed && enabled, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color.White),
        ) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (installed) app.label else R.string.song_share_app_missing),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// a video is named the way videos are (4k, 1080p); a picture just says which one is sharper
@Composable
private fun QualityPicker(send: SendState, canUltraHd: Boolean, video: Boolean) {
    val labels = if (video) {
        listOf(ShareQuality.UltraHd to R.string.song_share_quality_4k, ShareQuality.FullHd to R.string.song_share_quality_1080)
    } else {
        listOf(ShareQuality.UltraHd to R.string.song_share_quality_best, ShareQuality.FullHd to R.string.song_share_quality_standard)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Caption(stringResource(R.string.song_share_quality))
        labels.forEach { (quality, label) ->
            val on = send.quality == quality
            val enabled = !send.working && (quality != ShareQuality.UltraHd || canUltraHd)
            Text(
                stringResource(label),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier
                    .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
                    .clip(CircleShape)
                    .background(if (on) MenuRowSurface else Color.Transparent)
                    .border(1.dp, Color.White.copy(alpha = if (on) 0.5f else 0.15f), CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled) { send.quality = quality }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ExportProgress(progress: Float, video: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Caption(
            if (video) {
                stringResource(R.string.song_share_making_video, (progress * 100).toInt())
            } else {
                stringResource(R.string.song_share_making_picture)
            },
        )
        Spacer(Modifier.height(10.dp))
        Spacer(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(6.dp)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(MenuRowSurface, cornerRadius = radius)
                    drawRoundRect(Color.White, size = Size(size.width * progress.coerceIn(0.02f, 1f), size.height), cornerRadius = radius)
                },
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = ShareMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun Divider() {
    Spacer(
        Modifier
            .padding(horizontal = 6.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.1f)),
    )
}
