// CloudSignIn.kt

package com.mochyy.mochymusic.backup

import android.content.Context
import android.net.Uri
import com.mochyy.mochymusic.utils.Pkce
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** where a browser sign-in to dropbox or onedrive is, for the backup page */
sealed interface CloudLogin {
    data object Idle : CloudLogin
    data class InBrowser(val kind: CloudKind) : CloudLogin
    data class Finishing(val kind: CloudKind) : CloudLogin
    data class Failed(val problem: CloudProblem) : CloudLogin
}

/** signs in to dropbox or onedrive on their own page in the browser, then the login activity hands the answer back here */
@Singleton
class CloudSignIn @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: CloudAccountStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingFile = File(context.noBackupFilesDir, PendingName)

    private val _login = MutableStateFlow<CloudLogin>(CloudLogin.Idle)
    val login: StateFlow<CloudLogin> = _login.asStateFlow()

    fun isConfigured(kind: CloudKind): Boolean = OAuthApp.of(kind)?.isConfigured == true

    /** the page to send the user to; the check value and verifier go to disk first, since android may close mochy while the browser is in front */
    suspend fun loginUri(kind: CloudKind): Uri {
        val app = requireNotNull(OAuthApp.of(kind)) { "$kind doesn't sign in through the browser" }
        val state = Pkce.randomCode(24)
        val verifier = Pkce.randomCode(64)
        withContext(Dispatchers.IO) {
            pendingFile.writeText(JSONObject().put("kind", kind.name).put("state", state).put("verifier", verifier).toString())
        }
        _login.value = CloudLogin.InBrowser(kind)
        return app.authorizeUri(state, Pkce.challengeOf(verifier))
    }

    /** for the login activity, which closes straight away while this carries on */
    fun handleRedirect(redirect: Uri) {
        scope.launch { finish(redirect) }
    }

    /** back from the browser without an answer, the page goes back to its sign-in rows */
    fun cancelWaiting() {
        if (_login.value is CloudLogin.InBrowser) _login.value = CloudLogin.Idle
    }

    fun dismissProblem() {
        if (_login.value is CloudLogin.Failed) _login.value = CloudLogin.Idle
    }

    private suspend fun finish(redirect: Uri) {
        val pending = runCatching { JSONObject(pendingFile.readText()) }.getOrNull()
        pendingFile.delete()
        val app = pending?.optString("kind")?.let { name -> OAuthApp.entries.find { it.kind.name == name } }
        val verifier = pending?.optString("verifier").orEmpty()
        // a link that doesn't belong to the login we started is ignored, not trusted
        if (app == null || verifier.isEmpty() || redirect.getQueryParameter("state") != pending.optString("state")) {
            _login.value = CloudLogin.Failed(CloudProblem.Expired)
            return
        }
        val code = redirect.getQueryParameter("code")
        if (code == null) {
            _login.value = CloudLogin.Failed(if (redirect.getQueryParameter("error") == "access_denied") CloudProblem.Denied else CloudProblem.Busy)
            return
        }

        _login.value = CloudLogin.Finishing(app.kind)
        try {
            val tokens = app.exchange(code, verifier)
            val name = app.accountName(tokens.access)
            // the login being replaced is told to forget its token, so it doesn't linger on dropbox's side
            accounts.current()?.let { old -> OAuthApp.of(old.kind)?.revoke(old.accessToken) }
            accounts.save(
                CloudAccount(
                    kind = app.kind,
                    name = name,
                    accessToken = tokens.access,
                    refreshToken = tokens.refresh.orEmpty(),
                    expiresAt = tokens.expiresAt,
                ),
            )
            _login.value = CloudLogin.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(Tag).w(e, "${app.kind} sign-in didn't finish")
            _login.value = CloudLogin.Failed((e as? CloudException)?.problem ?: if (e is IOException) CloudProblem.Offline else CloudProblem.Busy)
        }
    }

    private companion object {
        const val Tag = "CloudBackup"
        const val PendingName = "cloud_backup_login.json"
    }
}
