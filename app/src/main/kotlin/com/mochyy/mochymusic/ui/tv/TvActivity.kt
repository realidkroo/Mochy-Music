// TvActivity.kt

package com.mochyy.mochymusic.ui.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.link.tv.TvControl
import com.mochyy.mochymusic.link.tv.TvPairing
import com.mochyy.mochymusic.link.tv.TvPhoneSync
import com.mochyy.mochymusic.link.tv.TvProfile
import com.mochyy.mochymusic.link.tv.TvProfiles
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.playback.MusicService
import com.mochyy.mochymusic.playback.MusicService.MusicBinder
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.ui.launch.LaunchGate
import com.mochyy.mochymusic.ui.theme.MochyTheme
import com.mochyy.mochymusic.utils.SyncUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.system.exitProcess

// the big-screen app: launched from the tv's home screen, everything inside it is laid out for a remote
@AndroidEntryPoint
class TvActivity : ComponentActivity() {
    @Inject lateinit var database: MusicDatabase
    @Inject lateinit var downloadUtil: DownloadUtil
    @Inject lateinit var syncUtils: SyncUtils
    @Inject lateinit var phoneSync: TvPhoneSync
    @Inject lateinit var pairing: TvPairing

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)

    // set once someone is picked, so the phone is only looked for while a profile is actually open
    private var profileOpen = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service !is MusicBinder) return
            val previous = playerConnection
            playerConnection = PlayerConnection(this@TvActivity, service, database, lifecycleScope).also { TvControl.player = it }
            previous?.dispose()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            TvControl.player = null
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the phone's launch intro never plays here; work that waits for it (the youtube token) starts now
        LaunchGate.skip()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        TvProfiles.load(this)
        val skipPicker = TvProfiles.takePickedBeforeRestart(this) && TvProfiles.active() != null
        setContent {
            MochyTheme(darkTheme = true, pureBlack = true) {
                CompositionLocalProvider(LocalTvLink provides remember { TvLink(phoneSync, pairing) }) {
                    TvRoot(
                        player = playerConnection,
                        database = database,
                        downloadUtil = downloadUtil,
                        syncUtils = syncUtils,
                        skipPicker = skipPicker,
                        onProfileOpen = ::openProfile,
                        onProfileClosed = {
                            profileOpen = false
                            phoneSync.pause()
                        },
                    )
                }
            }
        }
    }

    // a profile on another library than the one open means starting over into it; otherwise it opens right away
    private fun openProfile(profile: TvProfile): Boolean {
        TvProfiles.setActive(this, profile.id)
        if (TvProfiles.needsRestartFor(profile.id)) {
            TvProfiles.markPickedBeforeRestart(this)
            restartInto(this)
            return false
        }
        profileOpen = true
        TvControl.start(this)
        phoneSync.resume()
        return true
    }

    override fun onStart() {
        super.onStart()
        playerConnection?.isUiVisible?.value = true
        bindService(Intent(this, MusicService::class.java), serviceConnection, BIND_AUTO_CREATE)
        if (profileOpen) phoneSync.resume()
    }

    override fun onStop() {
        playerConnection?.isUiVisible?.value = false
        unbindService(serviceConnection)
        phoneSync.pause()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        TvControl.player = null
        TvControl.stop()
        playerConnection?.dispose()
        playerConnection = null
    }

    companion object {
        // the player stops and the app comes back up on the picked profile's library
        fun restartInto(context: Context) {
            context.stopService(Intent(context, MusicService::class.java))
            context.startActivity(Intent(context, TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            exitProcess(0)
        }
    }
}

private sealed interface TvStage {
    data object Hello : TvStage
    data object Welcome : TvStage
    data class Pair(val firstRun: Boolean) : TvStage
    data class Name(val firstRun: Boolean) : TvStage
    data object Picker : TvStage
    data class Main(val profile: TvProfile) : TvStage
}

// which screen the tv is on before the app itself: hii, the first-run welcome, the profile picker
@Composable
private fun TvRoot(
    player: PlayerConnection?,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    syncUtils: SyncUtils,
    skipPicker: Boolean,
    onProfileOpen: (TvProfile) -> Boolean,
    onProfileClosed: () -> Unit,
) {
    val context = LocalContext.current
    var stage by remember { mutableStateOf<TvStage>(TvStage.Hello) }

    fun open(profile: TvProfile) {
        if (onProfileOpen(profile)) stage = TvStage.Main(profile)
    }

    fun afterHello() {
        val profiles = TvProfiles.profiles.value
        val active = TvProfiles.active()
        stage = when {
            profiles.isEmpty() -> TvStage.Welcome
            skipPicker && active != null -> TvStage.Main(active).also { onProfileOpen(active) }
            else -> TvStage.Picker
        }
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        contentKey = { it::class },
        label = "tvStage",
    ) { shown ->
        when (shown) {
            TvStage.Hello -> TvHello(onDone = ::afterHello)
            TvStage.Welcome -> TvWelcome(onNext = { stage = TvStage.Pair(firstRun = true) })
            is TvStage.Pair -> {
                BackHandler(enabled = !shown.firstRun) { stage = TvStage.Picker }
                TvPairWelcome(
                    pairing = LocalTvLink.current.pairing,
                    onPaired = ::open,
                    onSkip = { stage = TvStage.Name(shown.firstRun) },
                )
            }
            is TvStage.Name -> {
                BackHandler { stage = TvStage.Pair(shown.firstRun) }
                TvNameEntry(onDone = { name -> open(TvProfiles.create(context, name, avatarPath = null, phone = null)) })
            }
            TvStage.Picker -> TvProfilePicker(onPick = ::open, onAdd = { stage = TvStage.Pair(firstRun = false) })
            is TvStage.Main -> {
                val profiles by TvProfiles.profiles.collectAsState()
                val profile = profiles.firstOrNull { it.id == shown.profile.id } ?: shown.profile
                TvShell(
                    player = player,
                    database = database,
                    downloadUtil = downloadUtil,
                    syncUtils = syncUtils,
                    profile = profile,
                    onSwitchProfile = {
                        onProfileClosed()
                        stage = TvStage.Picker
                    },
                )
            }
        }
    }
}
