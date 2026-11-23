// TransferViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.importer.transfer.TransferExportStatus
import com.mochyy.mochymusic.importer.transfer.TransferExporter
import com.mochyy.mochymusic.importer.transfer.TransferImporter
import com.mochyy.mochymusic.importer.transfer.TransferManifest
import com.mochyy.mochymusic.importer.transfer.TransferRestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/** where an export or import stands, progress is null while there's no number to show yet */
sealed interface TransferStep {
    data object Idle : TransferStep
    data class Working(val progress: Float?) : TransferStep
    data object Done : TransferStep
    data class Failed(val message: String) : TransferStep
}

/** exporting everything to one zip and bringing a zip back in, for onboarding and the storage page */
@HiltViewModel
class TransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: TransferExporter,
    private val importer: TransferImporter,
) : ViewModel() {
    val export: StateFlow<TransferStep> = exporter.status
        .map { status ->
            when (status) {
                TransferExportStatus.Idle -> TransferStep.Idle
                is TransferExportStatus.Working -> TransferStep.Working(status.progress)
                TransferExportStatus.Done -> TransferStep.Done
                is TransferExportStatus.Failed -> TransferStep.Failed(status.message)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransferStep.Idle)

    private val _import = MutableStateFlow<TransferStep>(TransferStep.Idle)
    val import: StateFlow<TransferStep> = _import.asStateFlow()

    private val _manifest = MutableStateFlow<TransferManifest?>(null)

    /** what the zip being imported holds, for the progress page to show */
    val manifest: StateFlow<TransferManifest?> = _manifest.asStateFlow()

    /** the name the export suggests, so it's easy to spot in downloads later */
    fun exportFileName(): String = "mochy-export-${LocalDate.now()}.zip"

    fun exportTo(target: Uri) {
        exporter.startExport(target)
    }

    /** checks a picked zip before anything is unpacked, the failure message is ready to show */
    suspend fun check(source: Uri): Result<TransferManifest> =
        importer.peek(source).onSuccess { _manifest.value = it }

    /** true when the zip has local songs and mochy can't see the phone's music yet, so it's worth asking first */
    fun wantsAudioPermission(manifest: TransferManifest): Boolean =
        manifest.localSongs > 0 && ContextCompat.checkSelfPermission(context, AudioPermission) != PackageManager.PERMISSION_GRANTED

    /** unpacks the zip, then restarts the app so the next start swaps it in */
    fun importFrom(source: Uri) {
        if (_import.value is TransferStep.Working) return
        _import.value = TransferStep.Working(0f)
        viewModelScope.launch {
            importer.stage(source) { _import.value = TransferStep.Working(it) }
                .onSuccess {
                    _import.value = TransferStep.Done
                    // a beat on the finished page, so the restart doesn't look like a crash
                    delay(1_200)
                    TransferRestore.restartToFinish(context)
                }
                .onFailure {
                    Timber.e(it, "Import failed")
                    _import.value = TransferStep.Failed(it.message ?: "Couldn't import that file. Try again.")
                }
        }
    }

    /** back to the start after a failed import, so the page can be tried again */
    fun clearImport() {
        if (_import.value !is TransferStep.Working) _import.value = TransferStep.Idle
    }

    companion object {
        /** the permission that lets mochy find local songs again, it changed name in android 13 */
        val AudioPermission: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
