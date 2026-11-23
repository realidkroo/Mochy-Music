package com.music.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class AddItemYouTubePlaylistResponse(
    val status: String? = null,
    val playlistEditResults: List<PlaylistEditResult>? = null,
) {
    @Serializable
    data class PlaylistEditResult(
        val playlistEditVideoAddedResultData: PlaylistEditVideoAddedResultData? = null,
    ) {
        @Serializable
        data class PlaylistEditVideoAddedResultData(
            val setVideoId: String? = null,
            val videoId: String? = null,
        )
    }
}
