// TvPlayerRemote.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// what a tv remote can ask of the open player that only the player itself can do
enum class TvPlayerCommand { Lyrics, SongInfo, Source, ClosePage }

// the tv's control strip and now playing pill talk to the player sheet through here; a phone never sends anything
object TvPlayerRemote {
    // true while the tv's control bar is up, so the player's own title and seek bar step aside for it
    var controlsShown by mutableStateOf(false)

    private val _commands = MutableSharedFlow<TvPlayerCommand>(extraBufferCapacity = 4)
    val commands: SharedFlow<TvPlayerCommand> = _commands.asSharedFlow()

    fun send(command: TvPlayerCommand) {
        _commands.tryEmit(command)
    }
}
