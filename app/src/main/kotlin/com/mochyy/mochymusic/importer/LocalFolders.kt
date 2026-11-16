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

/** music folders the user picked themselves (like salt player), picking one keeps access to everything inside it so mochy can read those songs without seeing all the music on the phone, re-scan reads them again */
object LocalFolders {
    private const val FILE_NAME = "local_music_folders.txt"
    private const val MAX_FILES = 5_000
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

    /** the MediaStore uri of a file picked through a folder, or null if MediaStore doesn't have it */
    fun mediaStoreUri(context: Context, document: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { MediaStore.getMediaUri(context, document) }.getOrNull()
    }

    /** every song in every saved folder, with the lyrics and covers beside it */
    fun scanSaved(context: Context): List<LocalFile> = read(context).flatMap { scan(context, it) }.distinctBy { it.uri }

    /** every audio file under tree with the lyrics and covers beside it, folders inside it included. hidden folders are skipped */
    fun scan(context: Context, tree: Uri): List<LocalFile> {
        val found = ArrayList<LocalFile>()
        val pending = ArrayDeque<String>()
        pending += runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return found
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
                context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
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
                        if (kind != LocalFileNames.Kind.Other) inFolder += NamedFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, kind)
                    }
                }
                found += LocalSidecars.pair(inFolder, picked = false)
            } catch (e: Exception) {
                // a folder that was deleted or lost its access: skip it, keep the rest
                Timber.w(e, "Couldn't read a music folder")
            }
        }
        return found
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
