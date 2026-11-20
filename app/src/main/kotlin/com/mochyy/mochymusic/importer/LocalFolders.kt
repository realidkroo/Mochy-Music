// LocalFolders.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import timber.log.Timber
import java.io.File

/** a look through music folders: the songs found, the files left out for a format mochy can't play, and how much of the folders could really be read */
data class FolderScan(
    val files: List<LocalFile>,
    val unplayable: Int = 0,
    /** the file cap stopped it before every folder was read */
    val truncated: Boolean = false,
    /** the folders read all the way through, so a song of theirs that wasn't found really is gone */
    val readTrees: Set<String> = emptySet(),
) {
    operator fun plus(other: FolderScan) = FolderScan(
        files = (files + other.files).distinctBy { it.uri },
        unplayable = unplayable + other.unplayable,
        truncated = truncated || other.truncated,
        readTrees = readTrees + other.readTrees,
    )
}

/** music folders the user picked themselves (like salt player), picking one keeps access to everything inside it so mochy can read those songs without seeing all the music on the phone, re-scan reads them again */
object LocalFolders {
    private const val FILE_NAME = "local_music_folders.txt"
    const val MAX_FILES = 5_000
    private val lock = Any()

    fun read(context: Context): List<Uri> = synchronized(lock) {
        runCatching {
            file(context).takeIf { it.exists() }?.readLines()
                ?.map { it.trim() }?.filter { it.isNotEmpty() }?.map { it.toUri() }
        }.getOrNull().orEmpty()
    }

    /** keeps access to tree for good and remembers it for the next re-scan */
    fun add(context: Context, tree: Uri) {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        synchronized(lock) {
            val folders = (read(context) + tree).distinct()
            runCatching { file(context).writeText(folders.joinToString("\n")) }
        }
    }

    /** the files that aren't in the library yet, known are the library's file uris, on android 10+ each file's MediaStore uri is checked too since the phone-wide scan stores another uri */
    fun onlyNew(context: Context, files: List<LocalFile>, known: Set<String>): List<LocalFile> {
        val knownDocuments = known.mapNotNullTo(HashSet()) { documentKey(it.toUri()) }
        val checkMediaStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            known.any { it.startsWith("content://media/") }
        return files.filter { file ->
            if (file.uri.toString() in known || documentKey(file.uri) in knownDocuments) return@filter false
            !checkMediaStore || mediaStoreUri(context, file.uri)?.toString() !in known
        }
    }

    /** a file picked on its own and the same file found through its folder have different links but one document, this is that document */
    fun documentKey(uri: Uri): String? =
        runCatching { "${uri.authority}/${DocumentsContract.getDocumentId(uri)}" }.getOrNull()

    /** the picked folder a song's link came through, null for a file picked on its own or found by the phone-wide scan */
    fun treeKey(uri: Uri): String? =
        runCatching { "${uri.authority}/${DocumentsContract.getTreeDocumentId(uri)}" }.getOrNull()

    /** the MediaStore uri of a file picked through a folder, or null if MediaStore doesn't have it */
    fun mediaStoreUri(context: Context, document: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { MediaStore.getMediaUri(context, document) }.getOrNull()
    }

    /** every song in every saved folder, with the lyrics and covers beside it */
    fun scanSaved(context: Context): FolderScan =
        read(context).map { scan(context, it) }.fold(FolderScan(emptyList())) { all, one -> all + one }

    /** every audio file under tree with the lyrics and covers beside it, folders inside it included. hidden folders are skipped */
    fun scan(context: Context, tree: Uri): FolderScan {
        val found = ArrayList<LocalFile>()
        var unplayable = 0
        var everyFolderRead = true
        val pending = ArrayDeque<String>()
        pending += runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return FolderScan(found)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        while (pending.isNotEmpty() && found.size < MAX_FILES) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, pending.removeAt(pending.lastIndex))
            try {
                // one folder at a time, so a song only pairs with the lyrics and pictures beside it
                val inFolder = ArrayList<NamedFile>()
                val cursor = context.contentResolver.query(children, columns, null, null, null)
                if (cursor == null) {
                    everyFolderRead = false
                    continue
                }
                cursor.use {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0) ?: continue
                        val mime = cursor.getString(1).orEmpty()
                        val name = cursor.getString(2).orEmpty()
                        if (name.startsWith(".")) continue
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            pending += id
                            continue
                        }
                        val kind = LocalFileNames.kind(name, mime)
                        if (kind == LocalFileNames.Kind.Audio && !LocalFileNames.canPlay(name, mime)) {
                            unplayable++
                            continue
                        }
                        if (kind != LocalFileNames.Kind.Other) inFolder += NamedFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, kind)
                    }
                }
                found += LocalSidecars.pair(inFolder, picked = false)
            } catch (e: Exception) {
                // a folder that was deleted or lost its access: skip it, keep the rest
                everyFolderRead = false
                Timber.w(e, "Couldn't read a music folder")
            }
        }
        // the cap is the only thing that leaves folders unread
        val truncated = pending.isNotEmpty()
        val readAll = everyFolderRead && !truncated
        return FolderScan(
            files = found,
            unplayable = unplayable,
            truncated = truncated,
            readTrees = if (readAll) setOfNotNull(treeKey(tree)) else emptySet(),
        )
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
