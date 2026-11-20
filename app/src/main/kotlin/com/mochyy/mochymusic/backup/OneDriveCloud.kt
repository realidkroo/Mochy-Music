// OneDriveCloud.kt

package com.mochyy.mochymusic.backup

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** backups in mochy's app folder on onedrive (apps/<app name>), the only folder it asked for */
internal class OneDriveCloud(private val session: OAuthSession) : BackupCloud {
    // anything past a few mb has to go through an upload session, so every backup does, in pieces
    override suspend fun upload(file: File, name: String, onProgress: (Float) -> Unit) {
        val size = file.length()
        val created = session.text { token ->
            Request.Builder()
                .url("$AppFolder:/$name:/createUploadSession")
                .header("Authorization", "Bearer $token")
                .post(
                    JSONObject().put("item", JSONObject().put("@microsoft.graph.conflictBehavior", "rename"))
                        .toString().toRequestBody(CloudHttp.Json),
                )
                .build()
        }
        if (!created.ok) CloudHttp.fail(created.code)
        val uploadUrl = JSONObject(created.body).getString("uploadUrl")
        val report = { sent: Long -> onProgress((sent.toDouble() / size).toFloat().coerceIn(0f, 1f)) }
        var offset = 0L
        while (offset < size) {
            val length = minOf(Chunk, size - offset)
            // the upload link carries its own permission, a login header on it gets turned down
            val reply = CloudHttp.text(
                Request.Builder()
                    .url(uploadUrl)
                    .header("Content-Range", "bytes $offset-${offset + length - 1}/$size")
                    .put(FileSliceBody(file, offset, length, report))
                    .build(),
            )
            if (!reply.ok) CloudHttp.fail(reply.code)
            offset += length
        }
    }

    override suspend fun list(): List<CloudBackupFile> {
        val found = mutableListOf<CloudBackupFile>()
        var next: String? = "$AppFolder/children".toHttpUrl().newBuilder()
            .addQueryParameter("\$select", "id,name,size,lastModifiedDateTime,file")
            .addQueryParameter("\$top", "200")
            .build()
            .toString()
        while (next != null) {
            val url = next
            val reply = session.text { token -> Request.Builder().url(url).header("Authorization", "Bearer $token").build() }
            // a brand new app folder only appears with the first upload, until then there's simply nothing in it
            if (reply.code == 404) return found
            if (!reply.ok) CloudHttp.fail(reply.code)
            val page = JSONObject(reply.body)
            val items = page.optJSONArray("value")
            for (i in 0 until (items?.length() ?: 0)) {
                val item = items!!.getJSONObject(i)
                if (!item.has("file") || !BackupNames.isBackup(item.optString("name"))) continue
                found += CloudBackupFile(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    bytes = item.optLong("size"),
                    modifiedAt = runCatching { Instant.parse(item.optString("lastModifiedDateTime")).toEpochMilli() }.getOrDefault(0L),
                )
            }
            next = page.optString("@odata.nextLink").ifEmpty { null }
        }
        return found
    }

    // onedrive answers with a redirect to a short-lived link, okhttp follows it and drops the login header on the way
    override suspend fun download(backup: CloudBackupFile, into: File, onProgress: (Float) -> Unit) {
        session.send { token ->
            Request.Builder().url("$Drive/items/${backup.id}/content").header("Authorization", "Bearer $token").build()
        }.use { response ->
            if (!response.isSuccessful) CloudHttp.fail(response.code)
            response.saveTo(into, backup.bytes, onProgress)
        }
    }

    override suspend fun delete(backup: CloudBackupFile) {
        val reply = session.text { token ->
            Request.Builder().url("$Drive/items/${backup.id}").header("Authorization", "Bearer $token").delete().build()
        }
        if (!reply.ok && reply.code != 404) CloudHttp.fail(reply.code)
    }

    private companion object {
        const val Drive = "https://graph.microsoft.com/v1.0/me/drive"
        const val AppFolder = "$Drive/special/approot"
        // microsoft wants every piece but the last in steps of 320 kib, this is 32 of them
        const val Chunk = 32L * 320 * 1024
    }
}
