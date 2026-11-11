// SongEditSheet.kt

package com.mochyy.mochymusic.ui.menu.songedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.di.AudioLabEntryPoint
import com.mochyy.mochymusic.eq.AudioLabStore
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.playback.SongTuning
import com.mochyy.mochymusic.songedit.SongEditSaver
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.viewmodels.LyricsMenuViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

// opens the song edit sheet for a song; one that isn't in the library yet first asks to be added, like the mock's second sheet
@Composable
fun SongEditSheet(metadata: MediaMetadata, onDismiss: () -> Unit) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val context = LocalContext.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val entry = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, AudioLabEntryPoint::class.java)
    }
    // outlives each sheet, so "add to library" can finish and open the editor after its own sheet has gone
    val scope = rememberCoroutineScope()
    val flow = remember { SongEditFlow(menuState, database, scope, entry.songTuning(), entry.audioLabStore()) { currentOnDismiss() } }

    LaunchedEffect(metadata.id) {
        val song = database.getSongById(metadata.id)
        if (song != null && (song.song.inLibrary != null || song.song.isLocal)) flow.openEditor(metadata.id) else flow.askFirst(metadata)
    }
    DisposableEffect(flow) { onDispose { flow.closeAll() } }
}

// the sheets one edit opens, one after another; the caller hears about it once the last of them is gone
private class SongEditFlow(
    private val menuState: PopupSheetState,
    private val database: MusicDatabase,
    private val scope: CoroutineScope,
    private val tuning: SongTuning,
    private val audioLab: AudioLabStore,
    private val onDone: () -> Unit,
) {
    private val open = mutableListOf<PopupSheetHandle>()
    // set between closing the library question and the editor opening, so that gap doesn't end the flow
    private var movingOn = false
    private var finished = false

    fun askFirst(metadata: MediaMetadata) {
        var handle: PopupSheetHandle? = null
        handle = show(
            buttonBar = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetPillButton(
                        text = stringResource(R.string.song_edit_add_to_library),
                        onClick = {
                            movingOn = true
                            handle?.dismiss()
                            scope.launch {
                                addToLibrary(metadata)
                                openEditor(metadata.id)
                            }
                        },
                    )
                    SheetPillButton(text = stringResource(R.string.song_edit_not_now), onClick = { handle?.dismiss() })
                }
            },
        ) {
            Text(
                text = stringResource(R.string.song_edit_need_library),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 96.dp),
            )
        }
    }

    suspend fun openEditor(songId: String) {
        val state = SongEditState.load(database, tuning, songId)
        movingOn = false
        if (state == null || finished) {
            endIfIdle()
            return
        }
        var handle: PopupSheetHandle? = null
        handle = show(
            buttonBar = {
                SaveButton(
                    state = state,
                    tuning = tuning,
                    onLock = { locked -> handle?.setLocked(locked) },
                    onSaved = { handle?.dismiss() },
                )
            },
        ) {
            SongEditContent(state, audioLab)
        }
    }

    fun closeAll() {
        finished = true
        open.toList().forEach { it.dismiss() }
    }

    private fun show(
        buttonBar: @Composable () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ): PopupSheetHandle {
        var handle: PopupSheetHandle? = null
        handle = menuState.showWithHandle(
            buttonBar = buttonBar,
            onClosed = {
                handle?.let(open::remove)
                endIfIdle()
            },
            content = content,
        )
        open.add(handle)
        return handle
    }

    private fun endIfIdle() {
        if (open.isEmpty() && !movingOn && !finished) {
            finished = true
            onDone()
        }
    }

    // the same add as the library button elsewhere: saved first if it was only ever streamed, then put in the library
    private suspend fun addToLibrary(metadata: MediaMetadata) {
        database.withTransaction {
            insert(metadata)
            val entity = getSongByIdBlocking(metadata.id)?.song
            if (entity != null && entity.inLibrary == null) update(entity.toggleLibrary())
        }
    }
}

// the pinned ok: saves everything, then closes; locked while it works so the sheet can't be pulled away mid-save
@Composable
private fun SaveButton(state: SongEditState, tuning: SongTuning, onLock: (Boolean) -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val lyricsViewModel: LyricsMenuViewModel = hiltViewModel()
    SheetPillButton(
        text = if (state.busy) stringResource(R.string.song_edit_saving) else stringResource(android.R.string.ok),
        enabled = !state.busy && state.title.isNotBlank(),
        onClick = {
            state.busy = true
            onLock(true)
            scope.launch {
                val saved = try {
                    SongEditSaver.save(context, database, tuning, state.songId, state.draft())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag("SongEditSheet").e(e, "Saving the song edit failed")
                    false
                }
                onLock(false)
                if (saved) {
                    if (state.pickedLyrics == null) state.lyricsSource?.let { lyricsViewModel.useSource(state.metadata, it) }
                    onSaved()
                } else {
                    TopToaster.show(text = context.getString(R.string.song_edit_failed), iconRes = R.drawable.edit)
                    state.busy = false
                }
            }
        },
    )
}
