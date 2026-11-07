// TvPlayerRemote.kt

package com.mochyy.mochymusic.ui.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// what a tv remote can ask of the open player that only the player itself can do
enum class TvPlayerCommand { Lyrics, Queue, ClosePage }

// the tv's control strip and now playing pill talk to the player sheet through here; a phone never sends anything
object TvPlayerRemote {
    private val _commands = MutableSharedFlow<TvPlayerCommand>(extraBufferCapacity = 4)
    val commands: SharedFlow<TvPlayerCommand> = _commands.asSharedFlow()

    fun send(command: TvPlayerCommand) {
        _commands.tryEmit(command)
    }
}
