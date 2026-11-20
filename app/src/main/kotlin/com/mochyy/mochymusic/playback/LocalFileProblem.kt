// LocalFileProblem.kt

package com.mochyy.mochymusic.playback

import android.content.Context
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.importer.DeviceMusicScanner
import java.io.FileNotFoundException

/** why a song that's a file on the phone couldn't play, in words for the toast; trying again never fixes any of these */
enum class LocalFileProblem(@StringRes val message: Int) {
    Gone(R.string.local_error_file_gone),
    AccessLost(R.string.local_error_access_lost),
    NoPermission(R.string.local_error_permission),
    Format(R.string.local_error_format),
    Damaged(R.string.local_error_damaged),
    ;

    companion object {
        /** the problem behind [error] when [item] is a file on the phone, or null for anything else (a decoder, the audio output, a stream) */
        fun of(context: Context, item: MediaItem?, error: PlaybackException): LocalFileProblem? {
            val uri = item?.localConfiguration?.uri
            val onPhone = item?.mediaId?.startsWith("LOCAL_") == true || (uri != null && LocalBypassDataSource.isOnPhone(uri))
            if (!onPhone) return null
            val causes = generateSequence<Throwable>(error) { it.cause }.toList()
            return when {
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND || causes.any { it is FileNotFoundException } -> Gone
                error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION || causes.any { it is SecurityException } ->
                    // a song from the phone-wide scan only opens while mochy may read the phone's music; a picked file or folder lost its own access
                    if (uri?.authority == MediaStore.AUTHORITY && !DeviceMusicScanner.hasAccess(context)) NoPermission else AccessLost
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> Format
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> Damaged
                else -> null
            }
        }
    }
}
