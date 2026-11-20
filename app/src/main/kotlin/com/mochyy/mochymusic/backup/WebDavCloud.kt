// WebDavCloud.kt

package com.mochyy.mochymusic.backup

import android.net.Uri
import android.util.Xml
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** backups in a mochy backups folder on any webdav server: nextcloud, owncloud, pcloud, koofr, yandex disk, a nas at home */
internal class WebDavCloud(account: CloudAccount) : BackupCloud {
    private val folder: HttpUrl = folderOf(requireNotNull(addressOf(account.serverUrl)) { "saved webdav address doesn't parse" })
    private val login = Credentials.basic(account.username, account.password)

    override suspend fun upload(file: File, name: String, onProgress: (Float) -> Unit) {
        // the folder may have been deleted on the server since the account was connected
        makeFolder(folder, login)
        val size = file.length()
        val reply = CloudHttp.text(
            request(fileUrl(name))
                .put(FileSliceBody(file, 0L, size) { sent -> onProgress((sent.toDouble() / size).toFloat().coerceIn(0f, 1f)) })
                .build(),
        )
        if (!reply.ok) throw CloudException(problemOf(reply.code))
    }

    override suspend fun list(): List<CloudBackupFile> {
        val reply = CloudHttp.text(request(folder).header("Depth", "1").method("PROPFIND", PropfindBody.toRequestBody(XmlType)).build())
        if (reply.code == 404) return emptyList()
        if (!reply.ok) throw CloudException(problemOf(reply.code))
        return parseListing(reply.body).filter { BackupNames.isBackup(it.name) }
    }

    override suspend fun download(backup: CloudBackupFile, into: File, onProgress: (Float) -> Unit) {
        CloudHttp.send(request(fileUrl(backup.id)).build()).use { response ->
            if (!response.isSuccessful) throw CloudException(problemOf(response.code))
            response.saveTo(into, backup.bytes, onProgress)
        }
    }

    override suspend fun delete(backup: CloudBackupFile) {
        val reply = CloudHttp.text(request(fileUrl(backup.id)).delete().build())
        if (!reply.ok && reply.code != 404) throw CloudException(problemOf(reply.code))
    }

    // the folder ends in a slash, and okhttp puts a new piece in that empty last spot
    private fun fileUrl(name: String): HttpUrl = folder.newBuilder().addPathSegment(name).build()

    private fun request(url: HttpUrl) = Request.Builder().url(url).header("Authorization", login)

    // each <response> in the folder's listing, the folder itself and anything inside that's a folder left out
    private fun parseListing(xml: String): List<CloudBackupFile> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(xml.reader())
        }
        val found = mutableListOf<CloudBackupFile>()
        var href = ""
        var bytes = 0L
        var modified = 0L
        var isFolder = false
        var reading = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "response" -> { href = ""; bytes = 0L; modified = 0L; isFolder = false }
                    "collection" -> isFolder = true
                    else -> reading = parser.name
                }
                XmlPullParser.TEXT -> when (reading) {
                    "href" -> href += parser.text.trim()
                    "getcontentlength" -> bytes = parser.text.trim().toLongOrNull() ?: 0L
                    "getlastmodified" -> modified = parseDate(parser.text.trim())
                }
                XmlPullParser.END_TAG -> {
                    reading = ""
                    if (parser.name == "response" && !isFolder) {
                        val name = Uri.decode(href.trimEnd('/').substringAfterLast('/'))
                        if (name.isNotEmpty()) found += CloudBackupFile(id = name, name = name, bytes = bytes, modifiedAt = modified)
                    }
                }
            }
        }
        return found
    }

    private fun parseDate(text: String): Long =
        runCatching { ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrDefault(0L)

    companion object {
        private const val FolderName = "Mochy Backups"
        private val XmlType = "application/xml; charset=utf-8".toMediaType()
        private const val PropfindBody = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>"""

        /** what the user typed as a server address, https added when they left it off and a slash on the end, or null when it isn't one */
        fun addressOf(typed: String): HttpUrl? {
            val trimmed = typed.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            return if (url.encodedPath.endsWith("/")) url else url.newBuilder().addPathSegment("").build()
        }

        /** checks the address and login, then makes the backup folder; fails with what went wrong */
        suspend fun connect(address: HttpUrl, username: String, password: String) {
            val login = Credentials.basic(username, password)
            val probe = CloudHttp.text(
                Request.Builder().url(address).header("Authorization", login).header("Depth", "0")
                    .method("PROPFIND", PropfindBody.toRequestBody(XmlType)).build(),
            )
            when {
                probe.code == 401 || probe.code == 403 -> throw CloudException(CloudProblem.WrongLogin)
                probe.code == 404 -> throw CloudException(CloudProblem.NotFound)
                // a plain web page answers propfind with an error or a page instead of a listing
                probe.code != 207 -> throw CloudException(CloudProblem.NotWebDav)
            }
            makeFolder(folderOf(address), login)
        }

        private fun folderOf(address: HttpUrl): HttpUrl = address.newBuilder().addPathSegment(FolderName).addPathSegment("").build()

        // 405 means it's already there, which is just as good
        private suspend fun makeFolder(folder: HttpUrl, login: String) {
            val reply = CloudHttp.text(Request.Builder().url(folder).header("Authorization", login).method("MKCOL", null).build())
            if (!reply.ok && reply.code != 405) throw CloudException(problemOf(reply.code))
        }

        private fun problemOf(code: Int): CloudProblem = when (code) {
            401, 403 -> CloudProblem.WrongLogin
            else -> CloudHttp.problemOf(code)
        }
    }
}
