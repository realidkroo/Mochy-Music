// DiscordAuth.kt

package com.mochyy.mochymusic.discord

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.constants.DiscordAccessTokenKey
import com.mochyy.mochymusic.constants.DiscordLoginStateKey
import com.mochyy.mochymusic.constants.DiscordLoginVerifierKey
import com.mochyy.mochymusic.constants.DiscordProfileKey
import com.mochyy.mochymusic.constants.DiscordRefreshTokenKey
import com.mochyy.mochymusic.constants.DiscordTokenExpiresAtKey
import com.mochyy.mochymusic.utils.Pkce
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Where a Discord sign-in is, for the Discord page. */
sealed interface DiscordLogin {
    data object Idle : DiscordLogin
    /** Discord's page is open in the browser */
    data object InBrowser : DiscordLogin
    /** back from the browser, swapping the code for a token */
    data object Finishing : DiscordLogin
    data class Failed(val message: String) : DiscordLogin
}

/**
 * Signing in to Discord on Discord's own page, in the browser. It's OAuth with PKCE, so no secret
 * ships inside Mochy, and the password never passes through it. Discord sends the browser back
 * to discord-<id>:/authorize/callback, and [DiscordLoginActivity] hands that to [finish].
 */
@Singleton
class DiscordAuth @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _login = MutableStateFlow<DiscordLogin>(DiscordLogin.Idle)
    val login: StateFlow<DiscordLogin> = _login.asStateFlow()

    val isConfigured: Boolean get() = ClientId.isNotBlank()

    val signedIn: Flow<Boolean> = context.dataStore.data
        .map { it[DiscordAccessTokenKey] != null }
        .distinctUntilChanged()

    val profile: Flow<DiscordProfile?> = context.dataStore.data
        .map { it[DiscordProfileKey] }
        .distinctUntilChanged()
        .map { DiscordProfile.fromJson(it) }

    /**
     * The page to send the user to. The check value and the PKCE verifier are saved first, since
     * Android may close Mochy while the browser is in front.
     */
    suspend fun loginUri(): Uri {
        val state = Pkce.randomCode(24)
        val verifier = Pkce.randomCode(64)
        context.dataStore.edit {
            it[DiscordLoginStateKey] = state
            it[DiscordLoginVerifierKey] = verifier
        }
        _login.value = DiscordLogin.InBrowser
        return Uri.parse("https://discord.com/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", ClientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", RedirectUri)
            .appendQueryParameter("scope", Scopes)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", Pkce.challengeOf(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    /** For [DiscordLoginActivity], which closes straight away while this carries on. */
    fun handleRedirect(redirect: Uri) {
        scope.launch { finish(redirect) }
    }

    /** The user came back from the browser, ready or not. */
    suspend fun finish(redirect: Uri) {
        val prefs = context.dataStore.data.first()
        val expectedState = prefs[DiscordLoginStateKey]
        val verifier = prefs[DiscordLoginVerifierKey]
        // a link that doesn't belong to the login we started is ignored, not trusted
        if (expectedState == null || verifier == null || redirect.getQueryParameter("state") != expectedState) {
            _login.value = DiscordLogin.Failed("That sign-in link had expired. Try logging in again.")
            return
        }
        context.dataStore.edit {
            it.remove(DiscordLoginStateKey)
            it.remove(DiscordLoginVerifierKey)
        }
        val code = redirect.getQueryParameter("code")
        if (code == null) {
            _login.value = DiscordLogin.Failed(
                if (redirect.getQueryParameter("error") == "access_denied") {
                    "You said no on Discord's page, so Mochy isn't connected."
                } else {
                    "Discord didn't let the sign-in through. Try again."
                },
            )
            return
        }

        _login.value = DiscordLogin.Finishing
        try {
            val reply = DiscordHttp.postForm(
                "/oauth2/token",
                mapOf(
                    "client_id" to ClientId,
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to RedirectUri,
                    "code_verifier" to verifier,
                ),
            )
            check(reply.ok) { "token exchange failed: HTTP ${reply.code} ${reply.body.take(200)}" }
            val token = saveTokens(reply.body)
            val profile = DiscordProfile.fetch(token)
            context.dataStore.edit { it[DiscordProfileKey] = profile.toJson() }
            _login.value = DiscordLogin.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(LogTag).w(e, "Discord sign-in didn't finish")
            _login.value = DiscordLogin.Failed("Couldn't finish signing in to Discord. Check your connection and try again.")
        }
    }

    /** The user closed the browser without finishing; the page goes back to its button. */
    fun cancelWaiting() {
        if (_login.value == DiscordLogin.InBrowser) _login.value = DiscordLogin.Idle
    }

    fun dismissProblem() {
        if (_login.value is DiscordLogin.Failed) _login.value = DiscordLogin.Idle
    }

    /** A token that still works, renewed first when it's about to run out. Null when signed out. */
    suspend fun accessToken(): String? {
        val prefs = context.dataStore.data.first()
        val token = prefs[DiscordAccessTokenKey] ?: return null
        val expiresAt = prefs[DiscordTokenExpiresAtKey] ?: 0L
        if (expiresAt == 0L || System.currentTimeMillis() < expiresAt - RenewEarlyMs) return token
        return renew(token)
    }

    /**
     * Discord turned [rejected] down. Renew it once; if Discord says the login itself is gone
     * (the user removed Mochy from their authorised apps), sign out so the page says so.
     */
    suspend fun renew(rejected: String): String? = refreshing.withLock {
        val prefs = context.dataStore.data.first()
        val current = prefs[DiscordAccessTokenKey] ?: return@withLock null
        // someone else renewed it while this waited
        if (current != rejected) return@withLock current
        val refreshToken = prefs[DiscordRefreshTokenKey] ?: return@withLock null.also { signOutHere() }
        val reply = try {
            DiscordHttp.postForm(
                "/oauth2/token",
                mapOf(
                    "client_id" to ClientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline: keep the old one and try again next time
            return@withLock current
        }
        when {
            reply.ok -> saveTokens(reply.body)
            reply.code in 400..401 -> null.also { signOutHere() }
            else -> current
        }
    }

    /** The name, picture and bio again, for when the page opens: people change them. */
    suspend fun refreshProfile() {
        val token = accessToken() ?: return
        runCatching { DiscordProfile.fetch(token) }
            .onSuccess { profile -> context.dataStore.edit { it[DiscordProfileKey] = profile.toJson() } }
    }

    /** Signs out here and tells Discord to forget the token too. */
    suspend fun logOut() {
        val token = context.dataStore.data.first()[DiscordAccessTokenKey]
        signOutHere()
        if (token != null) {
            runCatching {
                DiscordHttp.postForm(
                    "/oauth2/token/revoke",
                    mapOf("client_id" to ClientId, "token" to token, "token_type_hint" to "access_token"),
                )
            }
        }
    }

    /**
     * Older Mochy builds logged in by reading the account's own login token out of a web page.
     * That token can do anything the account can, so it's wiped rather than left lying around.
     */
    suspend fun forgetOldLogin() {
        val prefs = context.dataStore.data.first()
        if (LegacyKeys.none { prefs.contains(it) }) return
        context.dataStore.edit { stored -> LegacyKeys.forEach { stored.remove(it) } }
    }

    private suspend fun signOutHere() {
        context.dataStore.edit {
            it.remove(DiscordAccessTokenKey)
            it.remove(DiscordRefreshTokenKey)
            it.remove(DiscordTokenExpiresAtKey)
            it.remove(DiscordProfileKey)
        }
    }

    /** Stores what Discord's token endpoint sent and returns the new access token. */
    private suspend fun saveTokens(body: String): String {
        val reply = json.parseToJsonElement(body).jsonObject
        val access = reply["access_token"]?.jsonPrimitive?.contentOrNull ?: error("no access token in Discord's reply")
        val refresh = reply["refresh_token"]?.jsonPrimitive?.contentOrNull
        val expiresIn = reply["expires_in"]?.jsonPrimitive?.longOrNull ?: 0L
        context.dataStore.edit {
            it[DiscordAccessTokenKey] = access
            if (refresh != null) it[DiscordRefreshTokenKey] = refresh
            it[DiscordTokenExpiresAtKey] = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L
        }
        return access
    }

    companion object {
        private const val LogTag = "DiscordAuth"

        val ClientId: String get() = BuildConfig.DISCORD_CLIENT_ID
        val RedirectUri: String get() = "discord-$ClientId:/authorize/callback"

        /**
         * sdk.social_layer_presence is Discord's own bundle for apps that show what the user is
         * doing. Any app can turn it on in the Developer Portal (Social SDK), unlike
         * activities.write on its own, which needs Discord's approval.
         */
        private const val Scopes = "openid identify sdk.social_layer_presence"

        /** renew a little before Discord's deadline, so a long song never meets an expired token */
        private const val RenewEarlyMs = 10 * 60_000L

        private val LegacyKeys: List<Preferences.Key<*>> = listOf(
            "discordToken", "discordUsername", "discordName", "discordAvatar", "discordStatus",
            "discordButton1Text", "discordButton2Text", "discordActivityName",
        ).map { stringPreferencesKey(it) } + listOf(
            "discordInfoDismissed", "discordUseDetails", "discordButton1Visible", "discordButton2Visible",
            "discordAdvancedMode",
        ).map { booleanPreferencesKey(it) }
    }
}
