// ShareTargets.kt

package com.mochyy.mochymusic.ui.player.share.song.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.media3.datasource.DataSource
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.player.share.song.ShareLayers
import com.mochyy.mochymusic.ui.player.share.song.SongShareKind
import com.mochyy.mochymusic.ui.player.share.song.SongShareSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// the only three apps the share sends to directly
internal enum class ShareApp(@StringRes val label: Int, val packages: List<String>) {
    Instagram(R.string.song_share_app_instagram, listOf("com.instagram.android")),
    WhatsApp(R.string.song_share_app_whatsapp, listOf("com.whatsapp", "com.whatsapp.w4b")),
    Facebook(R.string.song_share_app_facebook, listOf("com.facebook.katana")),
}

internal fun ShareApp.installedPackage(context: Context): String? = packages.firstOrNull { name ->
    runCatching { context.packageManager.getApplicationInfo(name, 0) }.isSuccess
}

// meta only takes a story from an app that names its own app id; without one the finished video goes to the app as a normal share
internal fun metaAppId(context: Context): String? = context.getString(R.string.meta_app_id).takeIf { it.isNotBlank() }

// instagram and facebook put the card on as a sticker people can move, with the song's link on it
internal fun ShareApp.takesSticker(context: Context): Boolean = this != ShareApp.WhatsApp && metaAppId(context) != null

// what one export produced, ready to hand to another app
internal class ShareFile(val file: File, val mimeType: String)

internal class ShareRequest(
    val context: Context,
    val session: SongShareSession,
    val theme: ShareTheme,
    val quality: ShareQuality,
    val songSource: DataSource.Factory,
    val job: ShareExportJob,
)

// the whole picture, or a layer of it, as a video or an image depending on the share's mode
private suspend fun ShareRequest.export(layers: ShareLayers): ShareFile =
    if (session.isVideo) {
        ShareFile(exportVideo(context, session, theme, quality, layers, songSource, job), "video/mp4")
    } else {
        ShareFile(exportStill(context, session, theme, quality, layers), "image/png")
    }

// a lyrics video's words move with the song, so they can't be a sticker; they stay in the video instead
private val ShareRequest.cardMoves: Boolean
    get() = session.isVideo && session.kind == SongShareKind.Lyrics && session.lyricsSynced

internal suspend fun ShareRequest.sendTo(app: ShareApp) {
    val target = app.installedPackage(context) ?: return
    if (!session.isCardOnly && app.takesSticker(context) && openStory(app, target)) return
    val baked = export(ShareLayers.Everything)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = baked.mimeType
        putExtra(Intent.EXTRA_STREAM, uriOf(context, baked.file))
        putExtra(Intent.EXTRA_TEXT, caption(context, session))
        setPackage(target)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(send)
}

// meta's add to story: the background as the story, the card as a sticker on it, the link behind the sticker
private suspend fun ShareRequest.openStory(app: ShareApp, target: String): Boolean {
    val appId = metaAppId(context) ?: return false
    val action = if (app == ShareApp.Instagram) "com.instagram.share.ADD_TO_STORY" else "com.facebook.stories.ADD_TO_STORY"
    if (context.packageManager.queryIntentActivities(Intent(action).setPackage(target), PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) return false

    val background = if (cardMoves) export(ShareLayers.Everything) else export(ShareLayers.BackgroundOnly)
    val sticker = if (cardMoves) null else exportStill(context, session, theme, quality, ShareLayers.Sticker, cropToCard = true)
    val backgroundUri = uriOf(context, background.file)
    val story = Intent(action).apply {
        setDataAndType(backgroundUri, background.mimeType)
        setPackage(target)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (app == ShareApp.Instagram) putExtra("source_application", appId) else putExtra("com.facebook.platform.extra.APPLICATION_ID", appId)
        session.link?.let { putExtra("content_url", it) }
        sticker?.let { file ->
            val stickerUri = uriOf(context, file)
            putExtra("interactive_asset_uri", stickerUri)
            context.grantUriPermission(target, stickerUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(story)
    return true
}

// keeps a copy in the gallery, then opens the phone's own share sheet with it and the link
internal suspend fun ShareRequest.exportAndShare(): Boolean {
    val baked = export(ShareLayers.Everything)
    val saved = saveToGallery(context, baked)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = baked.mimeType
        putExtra(Intent.EXTRA_STREAM, uriOf(context, baked.file))
        putExtra(Intent.EXTRA_TEXT, caption(context, session))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.song_share_chooser_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return saved
}

internal fun copySongLink(context: Context, session: SongShareSession): Boolean {
    val link = session.link ?: return false
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(session.share.title, link))
    return true
}

private fun caption(context: Context, session: SongShareSession): String {
    val line = context.getString(R.string.song_share_caption, session.share.title, session.share.artist)
    return session.link?.let { "$line\n$it" } ?: line
}

private fun uriOf(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)

// the shared folders only take files without a permission from android 10 on
private suspend fun saveToGallery(context: Context, file: ShareFile): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
    runCatching {
        val video = file.mimeType.startsWith("video")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Mochy")
        }
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, values) ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { out -> file.file.inputStream().use { it.copyTo(out) } } != null
    }.getOrDefault(false)
}
