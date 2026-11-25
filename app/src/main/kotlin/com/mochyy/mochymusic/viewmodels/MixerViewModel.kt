package com.mochyy.mochymusic.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.mochyy.mochymusic.eq.mixer.MixerStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** the mixer pages' way to the mixer, plus reading and writing script files */
@HiltViewModel
class MixerViewModel @Inject constructor(
    val store: MixerStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state = store.state
    val presets = store.presets

    /** a script file's text and its name without the extension; null when it isn't readable text or is far too big for one */
    suspend fun readScript(uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(MaxScriptBytes + 1)
                if (bytes.size > MaxScriptBytes) return@runCatching null
                bytes.decodeToString()
            } ?: return@runCatching null
            if (text.contains('\u0000')) return@runCatching null
            text to (displayName(uri) ?: "Liveprog").substringBeforeLast('.')
        }.getOrNull()
    }

    suspend fun writeScript(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        // the biggest jamesdsp scripts are a few dozen kilobytes; anything past this is something else
        private const val MaxScriptBytes = 512 * 1024
    }
}
