// DiscordViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.discord.DiscordAuth
import com.mochyy.mochymusic.discord.DiscordLogin
import com.mochyy.mochymusic.discord.DiscordPresence
import com.mochyy.mochymusic.discord.DiscordProfile
import com.mochyy.mochymusic.discord.NowPlaying
import com.mochyy.mochymusic.discord.PresenceConnection
import com.mochyy.mochymusic.discord.PresenceImage
import com.mochyy.mochymusic.discord.PresenceSettings
import com.mochyy.mochymusic.discord.savePresencePicture
import com.mochyy.mochymusic.utils.PreferencesCache
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings → Integrations → Discord: the account, the switch, and every customisation. */
@HiltViewModel
class DiscordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: DiscordAuth,
    private val presence: DiscordPresence,
) : ViewModel() {

    val isConfigured: Boolean get() = auth.isConfigured
    val login: StateFlow<DiscordLogin> = auth.login
    val connection: StateFlow<PresenceConnection> = presence.connection
    val nowPlaying: StateFlow<NowPlaying?> = presence.nowPlaying

    val signedIn: StateFlow<Boolean> = auth.signedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val profile: StateFlow<DiscordProfile?> = auth.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The page edits this copy directly, so typing and the preview never wait on the disk;
     * every change is saved behind it.
     */
    private val _settings = MutableStateFlow(PresenceSettings.from(PreferencesCache.current(context.dataStore)))
    val settings: StateFlow<PresenceSettings> = _settings.asStateFlow()

    fun edit(change: PresenceSettings.() -> PresenceSettings) {
        val edited = _settings.value.change()
        _settings.value = edited
        presence.save(edited)
    }

    // a picture picked for "your own picture" in the big or small slot; false when it couldn't be read
    suspend fun pickCustomPicture(large: Boolean, picked: Uri): Boolean {
        val saved = savePresencePicture(context, picked, if (large) "large" else "small") ?: return false
        edit {
            if (large) copy(largeImage = PresenceImage.CUSTOM, largeCustomImage = saved)
            else copy(smallImage = PresenceImage.CUSTOM, smallCustomImage = saved)
        }
        return true
    }

    suspend fun loginUri(): Uri = auth.loginUri()

    fun cancelWaiting() = auth.cancelWaiting()

    fun dismissProblem() = auth.dismissProblem()

    fun refreshProfile() {
        viewModelScope.launch { auth.refreshProfile() }
    }

    /** finishes even if the user leaves the page straight away, so Discord forgets the token too */
    fun logOut() {
        viewModelScope.launch(NonCancellable) { auth.logOut() }
    }
}
