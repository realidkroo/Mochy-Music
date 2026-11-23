package com.mochyy.mochymusic.core.updater.downloadmanager

import android.content.Context
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.ApkVerifier
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class CustomDownloadManager {
    private var downloadJob: Job? = null
    private var isPaused = false

    fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit,
        onDownloadComplete: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        downloadJob?.cancel()
        isPaused = false

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = ApkVerifier.openVerifiedConnection(apkUrl, allowNightlyLink = false)
                val fileLength = connection.contentLengthLong
                if (fileLength > ApkVerifier.MaxApkBytes) {
                    connection.disconnect()
                    withContext(Dispatchers.Main) { onError("Update file exceeds maximum allowed size") }
                    return@launch
                }

                val downloadDir = ApkVerifier.updatesDir(context)
                val outputFile = File(downloadDir, "mochy.apk")
                if (outputFile.exists()) outputFile.delete()

                try {
                    connection.inputStream.use { inputStream ->
                        FileOutputStream(outputFile).use { outputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                if (isPaused) {
                                    outputFile.delete()
                                    return@launch
                                }
                                totalBytesRead += bytesRead
                                if (totalBytesRead > ApkVerifier.MaxApkBytes) {
                                    outputFile.delete()
                                    withContext(Dispatchers.Main) { onError("Update file exceeded maximum allowed size") }
                                    return@launch
                                }
                                outputStream.write(buffer, 0, bytesRead)

                                if (fileLength > 0) {
                                    val progress = (totalBytesRead.toFloat() / fileLength.toFloat()).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) {
                                        onProgress(progress)
                                    }
                                }
                            }
                            outputStream.flush()
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                if (!ApkVerifier.verifyUpdateApk(context, outputFile)) {
                    outputFile.delete()
                    withContext(Dispatchers.Main) { onError("Update APK signature verification failed") }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    onDownloadComplete(outputFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: context.getString(R.string.download_failed))
                }
            }
        }
    }

    fun pauseDownload() {
        isPaused = true
        downloadJob?.cancel()
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }
}
