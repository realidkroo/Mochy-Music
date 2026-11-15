// ListQueue.kt

package com.mochyy.mochymusic.playback.queues

import androidx.media3.common.MediaItem
import com.mochyy.mochymusic.models.MediaMetadata

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override val firstSongId: String? get() = items.getOrNull(startIndex)?.mediaId

    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex, position)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
}
