// LocalMissingFiles.kt

package com.mochyy.mochymusic.importer

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.mochyy.mochymusic.db.entities.SongEntity
import java.io.File

/** local songs whose file is gone for certain; anything that might only be out of reach for now (a memory card taken out, a folder that didn't read) counts as still there */
object LocalMissingFiles {

    /** the songs in [songs] whose file a re-scan ([scan]) shows is really gone */
    fun find(songs: List<SongEntity>, scan: FolderScan): List<SongEntity> {
        val found = scan.files.mapNotNullTo(HashSet()) { LocalFolders.documentKey(it.uri) }
        return songs.filter { song ->
            val uri = song.localUri?.toUri() ?: return@filter false
            runCatching { isGone(uri, scan, found) }.getOrDefault(false)
        }
    }

    private fun isGone(uri: Uri, scan: FolderScan, found: Set<String>): Boolean = when (uri.scheme) {
        // a shared file's own copy: gone when its folder is there and it isn't
        "file" -> {
            val file = File(uri.path ?: return false)
            !file.exists() && file.parentFile?.exists() == true
        }
        // a song that came through a picked folder, when that whole folder was just read and it wasn't in it; single picks and the phone-wide scan can't be told apart from a card taken out, so they're left alone
        "content" -> {
            val tree = LocalFolders.treeKey(uri)
            // a wma or aiff that came in before the scan left those out is still there, only not looked at
            val name = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()?.substringAfterLast('/').orEmpty()
            tree != null && tree in scan.readTrees && LocalFolders.documentKey(uri) !in found && LocalFileNames.canPlay(name)
        }
        else -> false
    }
}
