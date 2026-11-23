package com.mochyy.mochymusic.core.updater.downloadmanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.ApkVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val apkUrl = inputData.getString("apk_url") ?: return@withContext Result.failure()
        val version = inputData.getString("version") ?: "unknown"
        val fileSize = inputData.getString("file_size") ?: ""

        DownloadNotificationManager.showDownloadStarting(version, fileSize)

        val isZip = apkUrl.contains("nightly.link") || apkUrl.endsWith(".zip")
        try {
            val connection = ApkVerifier.openVerifiedConnection(apkUrl, allowNightlyLink = isZip)
            val fileLength = connection.contentLengthLong
            if (fileLength > ApkVerifier.MaxApkBytes) {
                connection.disconnect()
                DownloadNotificationManager.showDownloadFailed(version, "Update file exceeds maximum allowed size")
                return@withContext Result.failure()
            }

            val downloadDir = ApkVerifier.updatesDir(context)
            val downloadFile = if (isZip) File(downloadDir, "mochy_temp.zip") else File(downloadDir, "mochy.apk")
            if (downloadFile.exists()) downloadFile.delete()

            try {
                connection.inputStream.use { inputStream ->
                    FileOutputStream(downloadFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (isStopped) {
                                downloadFile.delete()
                                return@withContext Result.retry()
                            }
                            totalBytesRead += bytesRead
                            if (totalBytesRead > ApkVerifier.MaxApkBytes) {
                                downloadFile.delete()
                                DownloadNotificationManager.showDownloadFailed(version, "Update file exceeded maximum allowed size")
                                return@withContext Result.failure()
                            }
                            outputStream.write(buffer, 0, bytesRead)

                            if (fileLength > 0) {
                                val progress = (totalBytesRead.toFloat() / fileLength.toFloat() * 100).toInt().coerceIn(0, 100)
                                DownloadNotificationManager.updateDownloadProgress(progress, version)
                                setProgress(workDataOf("progress" to progress.toFloat() / 100f))
                            }
                        }
                        outputStream.flush()
                    }
                }
            } finally {
                connection.disconnect()
            }

            val finalFile = if (isZip) {
                val targetApkFile = File(downloadDir, "mochy.apk")
                if (targetApkFile.exists()) targetApkFile.delete()
                var extracted = false
                try {
                    ZipInputStream(downloadFile.inputStream()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                                FileOutputStream(targetApkFile).use { fos ->
                                    val buf = ByteArray(16 * 1024)
                                    var extractedBytes = 0L
                                    while (true) {
                                        val n = zis.read(buf)
                                        if (n < 0) break
                                        extractedBytes += n
                                        if (extractedBytes > ApkVerifier.MaxApkBytes) {
                                            throw SecurityException("Extracted APK exceeded maximum allowed size")
                                        }
                                        fos.write(buf, 0, n)
                                    }
                                }
                                extracted = true
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    targetApkFile.delete()
                    DownloadNotificationManager.showDownloadFailed(
                        version,
                        e.message ?: "Failed to extract zip file"
                    )
                    return@withContext Result.failure()
                } finally {
                    if (downloadFile.exists()) {
                        downloadFile.delete()
                    }
                }
                if (!extracted) {
                    DownloadNotificationManager.showDownloadFailed(
                        version,
                        "Could not find APK in zip"
                    )
                    return@withContext Result.failure()
                }
                targetApkFile
            } else {
                downloadFile
            }

            if (!ApkVerifier.verifyUpdateApk(context, finalFile)) {
                finalFile.delete()
                DownloadNotificationManager.showDownloadFailed(
                    version,
                    "Update APK signature verification failed"
                )
                return@withContext Result.failure()
            }

            if (version.startsWith("nightly-r")) {
                val runNumberString = version.removePrefix("nightly-r")
                val runNumber = runNumberString.toIntOrNull()
                if (runNumber != null) {
                    val sharedPreferences = context.getSharedPreferences("update_settings", Context.MODE_PRIVATE)
                    sharedPreferences.edit().putInt("last_installed_nightly_run", runNumber).apply()
                }
            }

            DownloadNotificationManager.showDownloadComplete(version, finalFile.absolutePath)

            Result.success(workDataOf("file_path" to finalFile.absolutePath))
        } catch (e: Exception) {
            DownloadNotificationManager.showDownloadFailed(
                version,
                e.message ?: context.getString(R.string.download_failed)
            )
            Result.failure()
        }
    }
}
