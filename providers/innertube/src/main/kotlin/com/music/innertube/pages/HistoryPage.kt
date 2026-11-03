package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.MusicShelfRenderer
import com.music.innertube.models.SongItem
import com.music.innertube.models.getItems
import com.music.innertube.models.looksLikeTimestamp
import com.music.innertube.models.oddElements
import com.music.innertube.models.splitBySeparator
import com.music.innertube.utils.parseTime

data class HistoryPage(
    val sections: List<HistorySection>?,
) {
    data class HistorySection(
        val title: String,
        val songs: List<SongItem>
    )

    companion object {
        // null for a shelf with no title; one shelf like that used to fail the whole history
        fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): HistorySection? {
            val title = renderer.title?.runs?.joinToString("") { it.text }?.takeIf { it.isNotBlank() } ?: return null
            return HistorySection(title = title, songs = songsOf(renderer.contents.orEmpty()))
        }

        fun songsOf(contents: List<MusicShelfRenderer.Content>): List<SongItem> =
            contents.getItems().mapNotNull { fromMusicResponsiveListItemRenderer(it) }

        private fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            // a video in the history can carry its id only on its play button
            val videoId = renderer.playlistItemData?.videoId
                ?: renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId
                ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
                ?: return null

            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

            val secondaryLineRuns = renderer.flexColumns
                .getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.splitBySeparator()

            return SongItem(
                id = videoId,
                title = renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.text ?: return null,
                artists = secondaryLineRuns?.firstOrNull()?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                }?.filter { !it.name.looksLikeTimestamp() } ?: emptyList(),
                album = renderer.flexColumns.getOrNull(3)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.let {
                    Album(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null
                    )
                },
                duration = renderer.fixedColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer
                    ?.text?.runs?.firstOrNull()?.text?.parseTime(),
                musicVideoType = renderer.musicVideoType,
                // a missing cover is no reason to lose the play
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: "",
                explicit = renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
                historyRemoveToken = renderer.menu?.menuRenderer?.items?.find {
                    it.menuServiceItemRenderer?.icon?.iconType == "REMOVE_FROM_HISTORY"
                }?.menuServiceItemRenderer?.serviceEndpoint?.feedbackEndpoint?.feedbackToken
            )
        }
    }
}
