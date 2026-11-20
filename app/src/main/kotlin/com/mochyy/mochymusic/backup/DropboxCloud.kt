// DropboxCloud.kt

package com.mochyy.mochymusic.backup

import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** backups in mochy's app folder on dropbox (apps/<app name>), the only folder the app can see */
internal class DropboxCloud(private val session: OAuthSession) : BackupCloud {
    // dropbox takes at most 150 mb in one request, so every backup goes up in pieces through an upload session
    override suspend fun upload(file: File, name: String, onProgress: (Float) -> Unit) {
        val size = file.length()
        val report = { sent: Long -> onProgress((sent.toDouble() / size).toFloat().coerceIn(0f, 1f)) }
        var sessionId = ""
        var offset = 0L
        while (offset < size) {
            val length = minOf(Chunk, size - offset)
            val body = FileSliceBody(file, offset, length, report)
            if (sessionId.isEmpty()) {
                val reply = content("upload_session/start", JSONObject().put("close", false), body)
                sessionId = JSONObject(reply).getString("session_id")
            } else {
                content("upload_session/append_v2", JSONObject().put("cursor", cursor(sessionId, offset)).put("close", false), body)
            }
            offset += length
        }
        val commit = JSONObject().put("path", "/$name").put("mode", "add").put("autorename", true).put("mute", true)
        content("upload_session/finish", JSONObject().put("cursor", cursor(sessionId, size)).put("commit", commit), ByteArray(0).toRequestBody(CloudHttp.Binary))
    }

    override suspend fun list(): List<CloudBackupFile> {
        val found = mutableListOf<CloudBackupFile>()
        var page = JSONObject(rpc("files/list_folder", JSONObject().put("path", "")))
        while (true) {
            val entries = page.optJSONArray("entries")
            for (i in 0 until (entries?.length() ?: 0)) {
                val entry = entries!!.getJSONObject(i)
                if (entry.optString(".tag") != "file" || !BackupNames.isBackup(entry.optString("name"))) continue
                found += CloudBackupFile(
                    id = entry.getString("id"),
                    name = entry.getString("name"),
                    bytes = entry.optLong("size"),
                    modifiedAt = runCatching { Instant.parse(entry.optString("server_modified")).toEpochMilli() }.getOrDefault(0L),
                )
            }
            if (!page.optBoolean("has_more")) break
            page = JSONObject(rpc("files/list_folder/continue", JSONObject().put("cursor", page.getString("cursor"))))
        }
        return found
    }

    override suspend fun download(backup: CloudBackupFile, into: File, onProgress: (Float) -> Unit) {
        session.send { token ->
            Request.Builder()
                .url("$ContentApi/files/download")
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", JSONObject().put("path", backup.id).toString())
                .post(ByteArray(0).toRequestBody(null))
                .build()
        }.use { response ->
            if (!response.isSuccessful) throw CloudException(problemOf(response.code, response.body.string()))
            response.saveTo(into, backup.bytes, onProgress)
        }
    }

    override suspend fun delete(backup: CloudBackupFile) {
        rpc("files/delete_v2", JSONObject().put("path", backup.id))
    }

    // the calls that take json and answer json
    private suspend fun rpc(path: String, args: JSONObject): String {
        val reply = session.text { token ->
            Request.Builder()
                .url("$Api/$path")
                .header("Authorization", "Bearer $token")
                .post(args.toString().toRequestBody(CloudHttp.Json))
                .build()
        }
        if (!reply.ok) throw CloudException(problemOf(reply.code, reply.body))
        return reply.body
    }

    // the calls that carry file bytes, with their arguments in a header instead
    private suspend fun content(path: String, args: JSONObject, body: RequestBody): String {
        val reply = session.text { token ->
            Request.Builder()
                .url("$ContentApi/$path")
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", args.toString())
                .post(body)
                .build()
        }
        if (!reply.ok) throw CloudException(problemOf(reply.code, reply.body))
        return reply.body
    }

    private fun cursor(sessionId: String, offset: Long) = JSONObject().put("session_id", sessionId).put("offset", offset)

    // dropbox answers most trouble with 409 and says what it was in the body
    private fun problemOf(code: Int, body: String): CloudProblem = when {
        code == 409 && "insufficient_space" in body -> CloudProblem.Full
        code == 409 && "not_found" in body -> CloudProblem.NotFound
        code == 409 -> CloudProblem.Busy
        else -> CloudHttp.problemOf(code)
    }

    private companion object {
        const val Api = "https://api.dropboxapi.com/2"
        const val ContentApi = "https://content.dropboxapi.com/2"
        const val Chunk = 32L * 1024 * 1024
    }
}
