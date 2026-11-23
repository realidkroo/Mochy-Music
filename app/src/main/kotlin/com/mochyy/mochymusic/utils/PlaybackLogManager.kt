// PlaybackLogManager.kt

package com.mochyy.mochymusic.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class PlaybackLogLevel {
    INFO,
    WARNING,
    ERROR,
    DEBUG,
    BOT
}

data class PlaybackLogEntry(
    val timestamp: String,
    val level: PlaybackLogLevel,
    val message: String,
    val details: String? = null
)

object PlaybackLogManager {
    private const val MAX_LOG_ENTRIES = 500
    private const val TRIM_LOG_ENTRIES = 80
    // reuse one formatter and ring buffer so playback logging doesn't allocate a formatter and copy 500 items per call
    private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val lock = Any()
    private val ring = ArrayDeque<PlaybackLogEntry>(MAX_LOG_ENTRIES)

    private val _logs = MutableStateFlow<List<PlaybackLogEntry>>(emptyList())
    val logs: StateFlow<List<PlaybackLogEntry>> = _logs.asStateFlow()

    fun log(level: PlaybackLogLevel, message: String, details: String? = null) {
        val timestamp = LocalDateTime.now().format(TimeFormatter)
        val entry = PlaybackLogEntry(timestamp, level, message, details)
        val snapshot = synchronized(lock) {
            if (ring.size >= MAX_LOG_ENTRIES) ring.removeFirst()
            ring.addLast(entry)
            ring.toList()
        }
        _logs.value = snapshot
    }

    // keeps recent diagnostics while shedding older log entries when the ui hides
    fun trimMemory() {
        val snapshot = synchronized(lock) {
            if (ring.size <= TRIM_LOG_ENTRIES) return
            while (ring.size > TRIM_LOG_ENTRIES) ring.removeFirst()
            ring.toList()
        }
        _logs.value = snapshot
    }

    fun clearLogs() {
        synchronized(lock) { ring.clear() }
        _logs.value = emptyList()
    }
}
