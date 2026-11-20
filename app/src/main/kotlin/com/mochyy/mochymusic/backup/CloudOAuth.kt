// CloudOAuth.kt

package com.mochyy.mochymusic.backup

import android.net.Uri
import com.mochyy.mochymusic.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/** what a cloud's token endpoint sent back */
internal class OAuthTokens(val access: String, val refresh: String?, val expiresAt: Long)

/** a cloud mochy signs in to on the cloud's own page in the browser, with pkce, so no secret ships inside mochy */
internal enum class OAuthApp(
    val kind: CloudKind,
    private val authorizeUrl: String,
    private val tokenUrl: String,
    // where the cloud sends the browser back to; it's registered with the cloud's app and the login activity catches it
    val redirectUri: String,
) {
    Dropbox(
        CloudKind.Dropbox,
        authorizeUrl = "https://www.dropbox.com/oauth2/authorize",
        tokenUrl = "https://api.dropboxapi.com/oauth2/token",
        redirectUri = "mochy-backup://dropbox",
    ),
    OneDrive(
        CloudKind.OneDrive,
        authorizeUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
        tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
        redirectUri = "mochy-backup://onedrive",
    ),
    ;

    val clientId: String
        get() = when (this) {
            Dropbox -> BuildConfig.DROPBOX_APP_KEY
            OneDrive -> BuildConfig.ONEDRIVE_CLIENT_ID
        }

    val isConfigured: Boolean get() = clientId.isNotBlank()

    fun authorizeUri(state: String, challenge: String): Uri {
        val builder = Uri.parse(authorizeUrl).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
        when (this) {
            // offline hands out a refresh token, so backups keep going after the 4 hour access token runs out
            Dropbox -> builder.appendQueryParameter("token_access_type", "offline")
            OneDrive -> builder.appendQueryParameter("scope", OneDriveScope).appendQueryParameter("prompt", "select_account")
        }
        return builder.build()
    }

    suspend fun exchange(code: String, verifier: String): OAuthTokens = token(
        rejected = CloudProblem.Expired,
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to redirectUri,
        "code_verifier" to verifier,
    )

    // a refresh token the cloud turns down means the user removed mochy there or changed their password
    suspend fun refresh(refreshToken: String): OAuthTokens = token(
        rejected = CloudProblem.SignedOut,
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
    )

    /** the email or name to show for the account that just signed in, the cloud's own name when it won't say */
    suspend fun accountName(accessToken: String): String = runCatching {
        val request = when (this) {
            Dropbox -> Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_current_account")
                .header("Authorization", "Bearer $accessToken")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            OneDrive -> Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me")
                .header("Authorization", "Bearer $accessToken")
                .build()
        }
        val reply = CloudHttp.text(request)
        check(reply.ok)
        val json = JSONObject(reply.body)
        when (this) {
            Dropbox -> json.optString("email").ifEmpty { json.optJSONObject("name")?.optString("display_name").orEmpty() }
            OneDrive -> json.optString("mail").ifEmpty { json.optString("userPrincipalName") }.ifEmpty { json.optString("displayName") }
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: kind.label

    /** tells dropbox to forget the token on sign out; microsoft has nothing like it, its tokens just run out */
    suspend fun revoke(accessToken: String) {
        if (this != Dropbox || accessToken.isEmpty()) return
        runCatching {
            CloudHttp.text(
                Request.Builder()
                    .url("https://api.dropboxapi.com/2/auth/token/revoke")
                    .header("Authorization", "Bearer $accessToken")
                    .post(ByteArray(0).toRequestBody(null))
                    .build(),
            )
        }
    }

    private suspend fun token(rejected: CloudProblem, vararg fields: Pair<String, String>): OAuthTokens {
        val form = FormBody.Builder().add("client_id", clientId)
        fields.forEach { (name, value) -> form.add(name, value) }
        if (this == OneDrive) form.add("scope", OneDriveScope)
        val reply = CloudHttp.text(Request.Builder().url(tokenUrl).post(form.build()).build())
        if (reply.code == 400 || reply.code == 401) throw CloudException(rejected)
        if (!reply.ok) CloudHttp.fail(reply.code)
        val json = JSONObject(reply.body)
        val access = json.optString("access_token").ifEmpty { throw CloudException(CloudProblem.Busy) }
        val expiresIn = json.optLong("expires_in")
        return OAuthTokens(
            access = access,
            refresh = json.optString("refresh_token").ifEmpty { null },
            expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L,
        )
    }

    companion object {
        // the app folder only: mochy sees its own backups and nothing else in the user's onedrive
        private const val OneDriveScope = "offline_access User.Read Files.ReadWrite.AppFolder"

        fun of(kind: CloudKind): OAuthApp? = entries.find { it.kind == kind }
    }
}

/** keeps a signed-in cloud's access token fresh: renewed a little before it runs out, and once more if the cloud turns it down */
internal class OAuthSession(private val app: OAuthApp, private val store: CloudAccountStore) {
    suspend fun send(build: (token: String) -> Request): Response {
        val token = token()
        val response = CloudHttp.send(build(token))
        if (response.code != 401) return response
        response.close()
        return CloudHttp.send(build(renew(token)))
    }

    suspend fun text(build: (token: String) -> Request): CloudHttp.Reply =
        send(build).use { CloudHttp.Reply(it.code, it.body.string()) }

    private suspend fun token(): String {
        val account = store.current() ?: throw CloudException(CloudProblem.SignedOut)
        if (account.expiresAt == 0L || System.currentTimeMillis() < account.expiresAt - RenewEarlyMs) return account.accessToken
        return renew(account.accessToken)
    }

    private suspend fun renew(rejected: String): String = Renewing.withLock {
        val account = store.current() ?: throw CloudException(CloudProblem.SignedOut)
        // another backup renewed it while this one waited
        if (account.accessToken != rejected) return@withLock account.accessToken
        val refresh = account.refreshToken.ifEmpty { throw CloudException(CloudProblem.SignedOut) }
        val tokens = app.refresh(refresh)
        store.update {
            it.copy(accessToken = tokens.access, refreshToken = tokens.refresh ?: it.refreshToken, expiresAt = tokens.expiresAt)
        }
        tokens.access
    }

    private companion object {
        const val RenewEarlyMs = 5 * 60_000L
        val Renewing = Mutex()
    }
}
