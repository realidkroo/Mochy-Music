// PresenceSettings.kt

package com.mochyy.mochymusic.discord

import androidx.annotation.DrawableRes
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.DiscordActivityNameKey
import com.mochyy.mochymusic.constants.DiscordActivityTypeKey
import com.mochyy.mochymusic.constants.DiscordButton1TargetKey
import com.mochyy.mochymusic.constants.DiscordButton1TextKey
import com.mochyy.mochymusic.constants.DiscordButton2TargetKey
import com.mochyy.mochymusic.constants.DiscordButton2TextKey
import com.mochyy.mochymusic.constants.DiscordClearOnPauseKey
import com.mochyy.mochymusic.constants.DiscordDetailsKey
import com.mochyy.mochymusic.constants.DiscordLargeImageKey
import com.mochyy.mochymusic.constants.DiscordLargeTextKey
import com.mochyy.mochymusic.constants.DiscordRefreshSecondsKey
import com.mochyy.mochymusic.constants.DiscordSmallImageKey
import com.mochyy.mochymusic.constants.DiscordSmallTextKey
import com.mochyy.mochymusic.constants.DiscordSpinFpsKey
import com.mochyy.mochymusic.constants.DiscordSpinSecondsKey
import com.mochyy.mochymusic.constants.DiscordStateKey
import com.mochyy.mochymusic.constants.EnableDiscordRPCKey

/** The line Discord writes above the status: "Listening to ..." or "Playing ...". */
enum class PresenceKind(val prefValue: String, val label: String, val discordType: Int) {
    LISTENING("listening", "Listening", 2),
    PLAYING("playing", "Playing", 0),
}

/** What a status picture shows. Discord shows the big one as a square and the small one as a circle. */
enum class PresenceImage(val label: String, val forLargeImage: Boolean) {
    OFF("Turn it off", false),
    ARTWORK("Music artwork", true),
    ARTIST("Artist artwork", true),
    PLAYBACK_STATUS("Playback status", false),
    APP_LOGO("Mochy logo", true),
    SPINNING_ARTIST("Rotating artist artwork", false),
    SPINNING_ARTWORK("Rotating music artwork", false),
    ;

    /** a looping GIF of the artwork turning, made and uploaded by SpinningArtwork */
    val isRotating: Boolean get() = this == SPINNING_ARTIST || this == SPINNING_ARTWORK

    companion object {
        val ForLarge = entries.filter { it.forLargeImage }
        val ForSmall = entries
    }
}

/** Where a status button takes whoever presses it. */
enum class PresenceButtonTarget(val label: String, @DrawableRes val icon: Int) {
    YOUTUBE_MUSIC("YouTube Music", R.drawable.provider_youtube_music),
    SPOTIFY("Spotify", R.drawable.provider_spotify),
    APPLE_MUSIC("Apple Music", R.drawable.provider_apple_music),
    TIDAL("Tidal", R.drawable.provider_tidal),
    MOCHY("Mochy on GitHub", R.drawable.github),
}

data class PresenceButton(val text: String, val target: PresenceButtonTarget)

/**
 * Everything the user set on the Discord page. Text can use the placeholders in [PresenceText];
 * an empty one leaves that line off.
 */
data class PresenceSettings(
    val enabled: Boolean = true,
    val kind: PresenceKind = PresenceKind.LISTENING,
    val refreshSeconds: Int = DefaultRefreshSeconds,
    val name: String = "{artist} on {app}",
    val details: String = "{name}",
    val state: String = "{artist}",
    val largeText: String = "",
    val smallText: String = "{quality}",
    val largeImage: PresenceImage = PresenceImage.ARTWORK,
    val smallImage: PresenceImage = PresenceImage.PLAYBACK_STATUS,
    val button1: PresenceButton = PresenceButton("See on YouTube Music", PresenceButtonTarget.YOUTUBE_MUSIC),
    val button2: PresenceButton = PresenceButton("Open Mochy", PresenceButtonTarget.MOCHY),
    /** how long one full turn of the rotating artwork takes */
    val spinSeconds: Int = DefaultSpinSeconds,
    /** frames per second of the rotating artwork's GIF, one of [SpinFpsSteps] */
    val spinFps: Int = DefaultSpinFps,
    /** off the profile the moment the music pauses, rather than staying up frozen for a while */
    val clearOnPause: Boolean = false,
) {
    val spin: SpinStyle get() = SpinStyle(spinSeconds, spinFps)

    fun writeTo(prefs: MutablePreferences) {
        prefs[EnableDiscordRPCKey] = enabled
        prefs[DiscordActivityTypeKey] = kind.prefValue
        prefs[DiscordRefreshSecondsKey] = refreshSeconds
        prefs[DiscordActivityNameKey] = name
        prefs[DiscordDetailsKey] = details
        prefs[DiscordStateKey] = state
        prefs[DiscordLargeTextKey] = largeText
        prefs[DiscordSmallTextKey] = smallText
        prefs[DiscordLargeImageKey] = largeImage.name
        prefs[DiscordSmallImageKey] = smallImage.name
        prefs[DiscordButton1TextKey] = button1.text
        prefs[DiscordButton1TargetKey] = button1.target.name
        prefs[DiscordButton2TextKey] = button2.text
        prefs[DiscordButton2TargetKey] = button2.target.name
        prefs[DiscordSpinSecondsKey] = spinSeconds
        prefs[DiscordSpinFpsKey] = spinFps
        prefs[DiscordClearOnPauseKey] = clearOnPause
    }

    companion object {
        const val MinRefreshSeconds = 1
        const val MaxRefreshSeconds = 10
        const val DefaultRefreshSeconds = 3

        const val MinSpinSeconds = 2
        const val MaxSpinSeconds = 20
        const val DefaultSpinSeconds = 8

        /**
         * The frame rates a GIF can really play. Its frame times are whole hundredths of a second
         * (10, 8, 7 ... 2), and viewers slow anything under 2 right down, so 50 is the top.
         */
        val SpinFpsSteps = listOf(10, 12, 14, 17, 20, 25, 33, 50)
        const val DefaultSpinFps = 25

        private val Defaults = PresenceSettings()

        fun from(prefs: Preferences) = PresenceSettings(
            enabled = prefs[EnableDiscordRPCKey] ?: Defaults.enabled,
            kind = PresenceKind.entries.find { it.prefValue == prefs[DiscordActivityTypeKey] } ?: Defaults.kind,
            refreshSeconds = (prefs[DiscordRefreshSecondsKey] ?: Defaults.refreshSeconds)
                .coerceIn(MinRefreshSeconds, MaxRefreshSeconds),
            name = prefs[DiscordActivityNameKey] ?: Defaults.name,
            details = prefs[DiscordDetailsKey] ?: Defaults.details,
            state = prefs[DiscordStateKey] ?: Defaults.state,
            largeText = prefs[DiscordLargeTextKey] ?: Defaults.largeText,
            smallText = prefs[DiscordSmallTextKey] ?: Defaults.smallText,
            largeImage = enumOf(prefs[DiscordLargeImageKey], Defaults.largeImage).takeIf { it.forLargeImage }
                ?: Defaults.largeImage,
            smallImage = enumOf(prefs[DiscordSmallImageKey], Defaults.smallImage),
            button1 = PresenceButton(
                text = prefs[DiscordButton1TextKey] ?: Defaults.button1.text,
                target = enumOf(prefs[DiscordButton1TargetKey], Defaults.button1.target),
            ),
            button2 = PresenceButton(
                text = prefs[DiscordButton2TextKey] ?: Defaults.button2.text,
                target = enumOf(prefs[DiscordButton2TargetKey], Defaults.button2.target),
            ),
            spinSeconds = (prefs[DiscordSpinSecondsKey] ?: Defaults.spinSeconds).coerceIn(MinSpinSeconds, MaxSpinSeconds),
            spinFps = prefs[DiscordSpinFpsKey]?.takeIf { it in SpinFpsSteps } ?: Defaults.spinFps,
            clearOnPause = prefs[DiscordClearOnPauseKey] ?: Defaults.clearOnPause,
        )

        private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
            name?.let { stored -> enumValues<T>().find { it.name == stored } } ?: fallback
    }
}
