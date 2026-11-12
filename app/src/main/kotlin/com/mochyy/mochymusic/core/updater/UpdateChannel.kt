package com.mochyy.mochymusic.core.updater

import android.content.Context
import com.mochyy.mochymusic.BuildConfig

// release only sees normal github releases, nightly sees the prereleases too and takes whichever is newest
enum class UpdateChannel { Release, Nightly }

private const val KeyUpdateChannel = "update_channel"

// a nightly is marked when it's built, or carries a fourth number in its version (8.3.2.4)
val installedIsNightly: Boolean by lazy {
    BuildConfig.IS_NIGHTLY || parseFullVersion(BuildConfig.VERSION_NAME).nightly > 0
}

// a nightly build starts out on the nightly channel, everything else on release, until someone switches
fun updateChannel(context: Context): UpdateChannel {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KeyUpdateChannel, null)
    return UpdateChannel.entries.firstOrNull { it.name == saved }
        ?: if (installedIsNightly) UpdateChannel.Nightly else UpdateChannel.Release
}

fun saveUpdateChannel(context: Context, channel: UpdateChannel) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KeyUpdateChannel, channel.name).apply()
}

// leaving nightly would mean installing an older release over it, which can break the library, so it waits for a newer release
fun canLeaveNightly(latestRelease: GithubRelease?): Boolean =
    !installedIsNightly || (latestRelease != null && isNewerThanInstalled(latestRelease.version))
