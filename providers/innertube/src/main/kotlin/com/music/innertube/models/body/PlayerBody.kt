package com.music.innertube.models.body

import com.music.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String?,
    val playbackContext: PlaybackContext? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val signatureTimestamp: Int,
            // the web and tv players ask for html5 streams, and the older tv app's answer can come without links otherwise
            val html5Preference: String = "HTML5_PREF_WANTS",
        )
    }

    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String
    )
}
