// MoveToNewMochyViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.ApkVerifier
import com.mochyy.mochymusic.core.updater.apkFileFor
import com.mochyy.mochymusic.core.updater.downloadApk
import com.mochyy.mochymusic.core.updater.getNewMochyRelease
import com.mochyy.mochymusic.core.updater.installApk
import com.mochyy.mochymusic.core.updater.updateChannel
import com.mochyy.mochymusic.importer.transfer.TransferExportStatus
import com.mochyy.mochymusic.importer.transfer.TransferExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** the move page: export everything, then fetch the new mochy and hand it to the installer */
@HiltViewModel
class MoveToNewMochyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: TransferExporter,
) : ViewModel() {
    // an export from an earlier visit still counts, the zip is wherever it was saved
    val export: StateFlow<TransferStep> = exporter.status
        .map { status -> if (status == TransferExportStatus.Idle && TransferExporter.lastExportedAt(context) > 0L) TransferStep.Done else status.asStep() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransferStep.Idle)

    private val _app = MutableStateFlow<TransferStep>(TransferStep.Idle)
    val app: StateFlow<TransferStep> = _app.asStateFlow()

    private val _newMochyInstalled = MutableStateFlow(installedNewMochy() != null)
    val newMochyInstalled: StateFlow<Boolean> = _newMochyInstalled.asStateFlow()

    /** the name the export suggests, so it's easy to spot in downloads later */
    fun exportFileName(): String = "mochy-move-${LocalDate.now()}.zip"

    fun exportTo(target: Uri) {
        exporter.startExport(target)
    }

    /** downloads the newest new mochy, keeps a copy in downloads and opens the installer */
    fun getNewMochy() {
        if (_app.value is TransferStep.Working) return
        if (_newMochyInstalled.value) {
            _app.value = TransferStep.Done
            return
        }
        _app.value = TransferStep.Working(null)
        viewModelScope.launch {
            runCatching { fetchNewMochy() }
                .onSuccess { apk ->
                    _app.value = if (installApk(context, apk, ApkVerifier.NewMochy)) {
                        TransferStep.Idle
                    } else {
                        TransferStep.Failed(context.getString(R.string.move_app_allow_installs))
                    }
                }
                .onFailure {
                    Timber.e(it, "Getting the new Mochy failed")
                    _app.value = TransferStep.Failed((it as? NotOutYet)?.message ?: context.getString(R.string.move_app_download_failed))
                }
        }
    }

    /** looks again for the new mochy, called whenever the page comes back from the installer */
    fun refreshInstalled() {
        val installed = installedNewMochy() != null
        _newMochyInstalled.value = installed
        if (installed) _app.value = TransferStep.Done
    }

    /** opens the new mochy, false when it isn't there to open */
    fun openNewMochy(): Boolean {
        val pkg = installedNewMochy() ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    // only a release from 9.0.0 on is the new app, and the download is checked against its own id and key
    private suspend fun fetchNewMochy(): File {
        val release = getNewMochyRelease(updateChannel(context), force = true).getOrThrow()
            ?: throw NotOutYet(context.getString(R.string.move_app_not_out))
        if (release.apkUrl == null) throw NotOutYet(context.getString(R.string.move_app_no_apk))
        val apk = downloadApk(context, release, ApkVerifier.NewMochy) { _app.value = TransferStep.Working(it) }.getOrThrow()
        withContext(Dispatchers.IO) { saveToDownloads(apk, release.apkName ?: apkFileFor(context, release).name) }
        return apk
    }

    private fun installedNewMochy(): String? = NewMochyPackages.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    // a copy in downloads, so the new app can be installed again later without this one
    private fun saveToDownloads(apk: File, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching { copyIntoDownloads(apk, name) }.onFailure { Timber.w(it, "Couldn't keep a copy of the new Mochy in Downloads") }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyIntoDownloads(apk: File, name: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out -> apk.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }

    // a failure whose message is already the friendly one to show
    private class NotOutYet(message: String) : Exception(message)

    companion object {
        /** the new mochy's app id, and its debug build so a test phone finds it too */
        val NewMochyPackages = listOf("com.mochy.mochymusic", "com.mochy.mochymusic.debug")
    }
}
