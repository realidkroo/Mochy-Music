// StreamInspector.kt

package com.mochyy.mochymusic.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** what one player's song really is on its way to the speaker, for stream info's stats for nerds */
data class StreamDetails(
    val mediaId: String? = null,
    val containerMime: String? = null,
    val sampleMime: String? = null,
    val codecs: String? = null,
    /** bits per second, as the stream says */
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    /** what a lossless file was mastered at (16, 24); lossy streams have none */
    val sourceBitDepth: Int? = null,
    val decoderName: String? = null,
    val decoderInitMs: Long? = null,
    /** the pcm the decoder hands over, a C.ENCODING_PCM_* */
    val decodedEncoding: Int? = null,
    val decodedSampleRate: Int? = null,
    val decodedChannels: Int? = null,
    /** what the effects and the audio track were given: float when hi-res is passing through untouched */
    val outputEncoding: Int? = null,
    val outputSampleRate: Int? = null,
    val outputChannelConfig: Int? = null,
    val outputBufferBytes: Int? = null,
    val offload: Boolean = false,
    val tunneling: Boolean = false,
    val underruns: Int = 0,
    val fileSizeBytes: Long? = null,
    /** mochy's own resampler, when it's converting this song */
    val resampledFrom: Int? = null,
    val resampledTo: Int? = null,
    val resamplerTaps: Int? = null,
    val resamplerPassbandHz: Double? = null,
    /** brought down to 16-bit with tpdf dither, rather than cut */
    val dithered: Boolean = false,
)

/** one player's stats for nerds, each event filed under its own song since gapless reads the next one early */
@OptIn(UnstableApi::class)
class StreamInspector : AnalyticsListener {
    private val _details = MutableStateFlow(StreamDetails())
    val details: StateFlow<StreamDetails> = _details.asStateFlow()

    private val songs = LinkedHashMap<String, StreamDetails>()
    private var playingId: String? = null
    // the song the decoder is working on, which the sink's half gets filed under
    private var readingId: String? = null
    private val window = Timeline.Window()
    private val main = Handler(Looper.getMainLooper())

    override fun onMediaItemTransition(eventTime: AnalyticsListener.EventTime, mediaItem: MediaItem?, reason: Int) {
        playingId = mediaItem?.mediaId
        _details.value = playingId?.let(::entryFor) ?: StreamDetails()
    }

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        val id = mediaIdOf(eventTime) ?: return
        readingId = id
        edit(id) { old ->
            old.copy(
                containerMime = format.containerMimeType ?: old.containerMime,
                sampleMime = format.sampleMimeType,
                codecs = format.codecs,
                bitrate = format.averageBitrate.takeIf { it > 0 } ?: format.bitrate.takeIf { it > 0 } ?: old.bitrate,
                sampleRate = format.sampleRate.takeIf { it > 0 },
                channels = format.channelCount.takeIf { it > 0 },
                sourceBitDepth = com.mochyy.mochymusic.playback.audio.AudioDecoderPolicy.sourceBitDepth(format),
            )
        }
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        edit(mediaIdOf(eventTime)) { it.copy(decoderName = decoderName, decoderInitMs = initializationDurationMs) }
    }

    override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        edit(mediaIdOf(eventTime)) {
            it.copy(
                outputEncoding = audioTrackConfig.encoding,
                outputSampleRate = audioTrackConfig.sampleRate,
                outputChannelConfig = audioTrackConfig.channelConfig,
                outputBufferBytes = audioTrackConfig.bufferSize,
                offload = audioTrackConfig.offload,
                tunneling = audioTrackConfig.tunneling,
            )
        }
    }

    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        edit(playingId) { it.copy(underruns = it.underruns + 1) }
    }

    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        if (mediaLoadData.dataType != C.DATA_TYPE_MEDIA) return
        val size = totalSizeOf(loadEventInfo.responseHeaders) ?: return
        edit(mediaIdOf(eventTime)) { if (it.fileSizeBytes == size) it else it.copy(fileSizeBytes = size) }
    }

    // the decoder's pcm, as the sink is configured with it; arrives on the playback thread
    fun onSinkConfigured(format: Format) {
        val encoding = format.pcmEncoding.takeIf { it != Format.NO_VALUE }
        val rate = format.sampleRate.takeIf { it > 0 }
        val channels = format.channelCount.takeIf { it > 0 }
        main.post {
            edit(readingId) { it.copy(decodedEncoding = encoding, decodedSampleRate = rate, decodedChannels = channels) }
        }
    }

    // what the precision sink is doing to the song's samples; arrives on the playback thread
    fun onProcessing(resampler: com.mochyy.mochymusic.playback.audio.SincResampler?, dithered: Boolean) {
        val from = resampler?.inRate
        val to = resampler?.outRate
        val taps = resampler?.taps
        val passband = resampler?.passbandHz
        main.post {
            edit(readingId) {
                it.copy(resampledFrom = from, resampledTo = to, resamplerTaps = taps, resamplerPassbandHz = passband, dithered = dithered)
            }
        }
    }

    private fun mediaIdOf(eventTime: AnalyticsListener.EventTime): String? {
        val timeline = eventTime.timeline
        if (timeline.isEmpty || eventTime.windowIndex >= timeline.windowCount) return playingId
        return timeline.getWindow(eventTime.windowIndex, window).mediaItem.mediaId
    }

    private fun edit(mediaId: String?, change: (StreamDetails) -> StreamDetails) {
        val id = mediaId ?: return
        val next = change(entryFor(id))
        songs[id] = next
        if (id == playingId) _details.value = next
    }

    // a song met for the first time starts from the one before: a reused decoder or audio track says nothing new
    private fun entryFor(id: String): StreamDetails = songs[id] ?: run {
        val previous = songs.values.lastOrNull()
        val fresh = StreamDetails(
            mediaId = id,
            decoderName = previous?.decoderName,
            decoderInitMs = previous?.decoderInitMs,
            decodedEncoding = previous?.decodedEncoding,
            decodedSampleRate = previous?.decodedSampleRate,
            decodedChannels = previous?.decodedChannels,
            outputEncoding = previous?.outputEncoding,
            outputSampleRate = previous?.outputSampleRate,
            outputChannelConfig = previous?.outputChannelConfig,
            outputBufferBytes = previous?.outputBufferBytes,
            offload = previous?.offload ?: false,
            tunneling = previous?.tunneling ?: false,
            resampledFrom = previous?.resampledFrom,
            resampledTo = previous?.resampledTo,
            resamplerTaps = previous?.resamplerTaps,
            resamplerPassbandHz = previous?.resamplerPassbandHz,
            dithered = previous?.dithered ?: false,
        )
        songs[id] = fresh
        while (songs.size > KeptSongs) songs.remove(songs.keys.first())
        fresh
    }

    companion object {
        private const val KeptSongs = 4

        fun bitDepthOf(encoding: Int): Int? = when (encoding) {
            C.ENCODING_PCM_8BIT -> 8
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
            else -> null
        }

        // "bytes 0-1023/31415926" names the whole file; a plain length could be one chunk or one hls segment, so it's not trusted
        private fun totalSizeOf(headers: Map<String, List<String>>): Long? =
            headers.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }?.value?.firstOrNull()
                ?.substringAfterLast('/')?.trim()?.toLongOrNull()?.takeIf { it > 0 }
    }
}
