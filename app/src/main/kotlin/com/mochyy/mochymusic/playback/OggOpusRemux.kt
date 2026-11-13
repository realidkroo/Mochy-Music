// OggOpusRemux.kt

package com.mochyy.mochymusic.playback

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.annotation.RequiresApi
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

// youtube keeps opus inside webm, which most players and file managers don't see as music; this moves the same audio into a plain .opus file, no re-encoding
@RequiresApi(29)
internal object OggOpusRemux {

    // false when the file isn't opus or the muxer refuses it, so the caller can write the original instead
    fun remux(spans: List<File>, out: File): Boolean {
        val source = SpanSource(spans)
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(source)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_OPUS
            } ?: return false
            extractor.selectTrack(track)
            copySamples(extractor, extractor.getTrackFormat(track), out)
            true
        } catch (_: Exception) {
            out.delete()
            false
        } finally {
            extractor.release()
            source.close()
        }
    }

    private fun copySamples(extractor: MediaExtractor, format: MediaFormat, out: File) {
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        try {
            val track = muxer.addTrack(format)
            muxer.start()
            val buffer = ByteBuffer.allocate(SampleBufferBytes)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, extractor.sampleTime, flags)
                muxer.writeSampleData(track, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            muxer.release()
        }
    }

    // opus packets are a few hundred bytes, this is plenty
    private const val SampleBufferBytes = 256 * 1024

    // the download cache's pieces read as one file, so nothing has to be glued together on disk first
    private class SpanSource(spans: List<File>) : MediaDataSource() {
        private val files = spans.map { RandomAccessFile(it, "r") }
        private val starts = LongArray(files.size)
        private val total: Long

        init {
            var position = 0L
            files.forEachIndexed { index, file ->
                starts[index] = position
                position += file.length()
            }
            total = position
        }

        override fun getSize(): Long = total

        // reads from one piece at a time, a short read is fine and the extractor just asks again
        @Synchronized
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= total) return -1
            if (size == 0) return 0
            val index = starts.indexOfLast { it <= position }
            val file = files[index]
            file.seek(position - starts[index])
            return file.read(buffer, offset, size)
        }

        override fun close() = files.forEach { runCatching { it.close() } }
    }
}
