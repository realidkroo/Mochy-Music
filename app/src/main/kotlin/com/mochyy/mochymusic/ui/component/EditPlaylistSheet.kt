// EditPlaylistSheet.kt

package com.mochyy.mochymusic.ui.component

import android.content.Context
import androidx.compose.runtime.rememberCoroutineScope
import com.music.innertube.YouTube
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.playlistcover.CoverChoice
import com.mochyy.mochymusic.playlistcover.PlaylistCoverStore
import com.mochyy.mochymusic.playlistcover.PlaylistCoverStyle
import com.mochyy.mochymusic.playlistcover.PlaylistCovers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Pushes the "Edit playlist" sheet (the mock under "Edit playlist"): the same carousel, title and
 * description as creating one, filled in from [playlist], with Done to save. Called from inside
 * the playlist menu, so it opens over it and the menu comes back forward behind it.
 *
 * The cover is only touched if the user touched the carousel - except that a generated one is
 * redrawn when the title changed, because the title is part of the picture.
 *
 * @param topSongId the playlist's top song, which a newly generated cover takes its colours from.
 */
fun PopupSheetState.showEditPlaylistSheet(
    playlist: PlaylistEntity,
    database: MusicDatabase,
    context: Context,
    topSongId: String?,
    topSongArtworkUrl: String?,
) {
    val appContext = context.applicationContext
    var sheet: PopupSheetHandle? = null
    val form = PlaylistFormState(playlist.name, playlist.description.orEmpty())

    // Which card the carousel opens on: the style this cover was drawn in, or the picture card for
    // one of the user's own pictures; anything else (song collage, YouTube's image) has no card.
    val styles = PlaylistCoverStyle.entries
    val generated = PlaylistCoverStore.parse(playlist.thumbnailUrl)
    val ownImage = playlist.thumbnailUrl.takeIf { generated == null && PlaylistCoverStore.isManaged(it) }
    val startPage = when {
        generated != null -> styles.indexOf(generated.style)
        ownImage != null -> styles.size
        else -> 0
    }

    sheet = showWithHandle(
        buttonBar = {
            val scope = rememberCoroutineScope()
            SheetPillButton(
                text = if (form.busy) "Saving…" else "Done",
                enabled = form.name.isNotBlank() && !form.busy,
                onClick = {
                    form.busy = true
                    // Nothing may close the sheet under the work below.
                    sheet?.setLocked(true)
                    scope.launch(Dispatchers.IO) {
                        try {
                            // the playlist as it is now: a cover redrawn while the sheet was open would otherwise be pointed back at a file that's gone
                            val current = database.playlistEntity(playlist.id) ?: playlist
                            val currentGenerated = PlaylistCoverStore.parse(current.thumbnailUrl)
                            val name = form.name.trim()
                            val titleChanged = name != current.name
                            var thumbnail = current.thumbnailUrl

                            val choice = form.choice
                            val redraw = when {
                                choice != null -> choice
                                currentGenerated != null && titleChanged -> CoverChoice.Generated(currentGenerated.style)
                                else -> null
                            }
                            if (redraw != null) {
                                PlaylistCovers.create(
                                    context = appContext,
                                    playlistId = playlist.id,
                                    title = name,
                                    choice = redraw,
                                    topSongId = topSongId,
                                    artworkUrl = topSongArtworkUrl,
                                )?.let { thumbnail = it }
                            }

                            val saved = current.copy(
                                name = name,
                                description = form.description.trim().ifBlank { null },
                                thumbnailUrl = thumbnail,
                                lastUpdateTime = LocalDateTime.now(),
                            )
                            database.update(saved)
                            if (thumbnail != current.thumbnailUrl) {
                                PlaylistCoverStore.deleteStale(appContext, playlist.id, thumbnail)
                            }
                            if (titleChanged) {
                                current.browseId?.let { runCatching { YouTube.renamePlaylist(it, name) } }
                            }

                            withContext(Dispatchers.Main) { sheet?.dismiss() }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.tag("EditPlaylistSheet").e(e, "Saving the playlist failed")
                            form.busy = false
                            sheet?.setLocked(false)
                        }
                    }
                },
            )
        },
    ) {
        PlaylistFormContent(
            form = form,
            seed = playlist.id,
            artworkUrl = topSongArtworkUrl,
            startPage = startPage,
            existingImage = ownImage,
            keepAllowed = true,
        )
    }
}
