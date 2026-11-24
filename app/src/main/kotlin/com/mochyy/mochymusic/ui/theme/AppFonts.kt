package com.mochyy.mochymusic.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.constants.AppFontModeKey
import com.mochyy.mochymusic.constants.CustomFontIdKey
import com.mochyy.mochymusic.constants.CustomFontNameKey
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

internal object AppFonts {
    data class Selection(
        val mode: AppFontMode = AppFontMode.DEFAULT,
        val fileName: String? = null,
        val family: FontFamily? = null,
        val missing: Boolean = false,
        val ready: Boolean = false,
    )

    enum class Error { InvalidFont, TooLarge, Storage }

    var selection by mutableStateOf(Selection())
        private set

    private data class Stored(val mode: AppFontMode, val id: String?, val name: String?)
    private data class Loaded(val id: String, val family: FontFamily)

    private val lock = Mutex()
    private var loaded: Loaded? = null

    fun start(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val store = context.dataStore
            store.data.map { it.fontSettings() }.distinctUntilChanged()
                .onEach {
                    lock.withLock {
                        // A notification may have waited behind an import. Read the committed snapshot.
                        val prefs = store.data.first().fontSettings()
                        publish(context, prefs)
                        cleanFiles(context, prefs.id)
                    }
                }
                .retryWhen { error, _ ->
                    if (error !is IOException) return@retryWhen false
                    delay(1_000)
                    true
                }
                .collect {}
        }
    }

    suspend fun select(context: Context, mode: AppFontMode): Error? = operate {
        val store = context.dataStore
        val current = store.data.first().fontSettings()
        if (mode == AppFontMode.CUSTOM && load(context, current.id) == null) {
            throw InvalidFontException()
        }
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            store.edit { it[AppFontModeKey] = mode.name }
            publish(context, current.copy(mode = mode))
        }
    }

    suspend fun importFont(context: Context, uri: Uri): Error? = operate {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: throw InvalidFontException()
        if (!isTtfName(name)) throw InvalidFontException()

        val directory = directory(context)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create font directory")
        val id = UUID.randomUUID().toString()
        val destination = File(directory, "$id.ttf")
        val temporary = File.createTempFile("import-", ".tmp", directory)
        var committed = false
        try {
            val jobContext = currentCoroutineContext()
            resolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    input.copyFontTo(output, checkCancellation = { jobContext.ensureActive() })
                    output.fd.sync()
                }
            } ?: throw IOException("Could not open selected font")

            val typeface = readTypeface(temporary)
            if (!temporary.renameTo(destination)) throw IOException("Could not finalize font")
            val family = FontFamily(ImportedFont(typeface))
            val displayName = name.filterNot { it.isISOControl() }.take(200)
            jobContext.ensureActive()
            withContext(NonCancellable) {
                context.dataStore.edit {
                    it[AppFontModeKey] = AppFontMode.CUSTOM.name
                    it[CustomFontIdKey] = id
                    it[CustomFontNameKey] = displayName
                }
                committed = true
                loaded = Loaded(id, family)
                selection = Selection(AppFontMode.CUSTOM, displayName, family, ready = true)
                cleanFiles(context, id)
            }
        } finally {
            temporary.delete()
            if (!committed) destination.delete()
        }
    }

    suspend fun delete(context: Context): Error? = operate {
        val store = context.dataStore
        val previous = store.data.first().fontSettings()
        val mode = if (previous.mode == AppFontMode.CUSTOM) AppFontMode.DEFAULT else previous.mode
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            store.edit {
                it[AppFontModeKey] = mode.name
                it.remove(CustomFontIdKey)
                it.remove(CustomFontNameKey)
            }
            loaded = null
            selection = Selection(mode = mode, ready = true)
            cleanFiles(context, keepId = null)
        }
    }

    private suspend fun operate(action: suspend () -> Unit): Error? = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                action()
                null
            } catch (error: CancellationException) {
                throw error
            } catch (_: FontTooLargeException) {
                Error.TooLarge
            } catch (_: InvalidFontException) {
                Error.InvalidFont
            } catch (_: IOException) {
                Error.Storage
            } catch (_: SecurityException) {
                Error.Storage
            } catch (_: IllegalArgumentException) {
                Error.Storage
            }
        }
    }

    private fun publish(context: Context, stored: Stored) {
        val file = load(context, stored.id)
        val mode = if (file == null && stored.mode == AppFontMode.CUSTOM) AppFontMode.DEFAULT else stored.mode
        selection = Selection(
            mode = mode,
            fileName = file?.let { stored.name.orEmpty().ifBlank { "TTF" } },
            family = file?.family,
            missing = file == null && (stored.id != null || stored.mode == AppFontMode.CUSTOM),
            ready = true,
        )
    }

    private fun load(context: Context, id: String?): Loaded? {
        val previous = loaded
        if (id != null && previous?.id == id) return previous
        loaded = id?.takeIf(FontId::matches)?.let { validId ->
            val file = File(directory(context), "$validId.ttf")
            try {
                Loaded(validId, FontFamily(ImportedFont(readTypeface(file))))
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
        return loaded
    }

    private fun readTypeface(file: File): Typeface {
        if (!file.isFile || file.length() > MaxCustomFontBytes ||
            !file.inputStream().buffered().use(::hasTrueTypeHeader)
        ) throw InvalidFontException()

        return try {
            // A builder without a fallback reports a rejected font as null on API 26+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Typeface.Builder(file).build() ?: throw InvalidFontException()
            } else {
                Typeface.createFromFile(file)
            }
        } catch (_: RuntimeException) {
            throw InvalidFontException()
        }
    }

    private fun cleanFiles(context: Context, keepId: String?) {
        val keep = "$keepId.ttf"
        directory(context).listFiles()?.forEach { file ->
            if (file.name != keep && file.isFile) file.delete()
        }
    }

    private fun directory(context: Context) = File(context.noBackupFilesDir, "custom-font")

    private fun Preferences.fontSettings() = Stored(
        AppFontMode.fromStoredValue(this[AppFontModeKey]), this[CustomFontIdKey], this[CustomFontNameKey],
    )

    // Serve the validated Typeface from memory. Keeping it in a FontListFontFamily
    // lets Compose synthesize styles even after the original file has been replaced.
    private class ImportedFont(private val typeface: Typeface) : AndroidFont(
        FontLoadingStrategy.Blocking, Loader, FontVariation.Settings(),
    ) {
        override val weight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            FontWeight(typeface.weight)
        } else if (typeface.isBold) {
            FontWeight.Bold
        } else {
            FontWeight.Normal
        }
        override val style = if (typeface.isItalic) FontStyle.Italic else FontStyle.Normal

        private object Loader : TypefaceLoader {
            override fun loadBlocking(context: Context, font: AndroidFont) = (font as ImportedFont).typeface
            override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
                error("Imported fonts are already loaded")
        }
    }

    private class InvalidFontException : IOException()

    private val FontId = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
}
