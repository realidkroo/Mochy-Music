package com.mochyy.mochymusic.utils

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.music.innertube.models.response.PlayerResponse

// one youtube video's pictures, each track the same picture at another size, with what the requests have to carry
data class VideoStreams(
    val videoId: String,
    // smallest first
    val tracks: List<VideoTrack>,
    val headers: Map<String, String>,
    val expiresAtMs: Long,
    val durationMs: Long,
    // the same picture past what tracks reach (1440p, 4k), which youtube only serves as vp9 or av1; empty when this phone can't decode it in hardware
    val sharpTracks: List<VideoTrack> = emptyList(),
) {
    // a minute early, so a video that is just starting doesn't run into a dead link halfway
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAtMs - 60_000L

    // every size there is by youtube's own names (1080 for 1080p), biggest first
    val sizes: List<Int> get() = (tracks + sharpTracks).map { it.lines }.distinct().sortedDescending()

    // h264 for anything it has, the sharper family only once a size past it is picked
    fun tracksFor(lines: Int): List<VideoTrack> =
        if (sharpTracks.isNotEmpty() && lines > (tracks.lastOrNull()?.lines ?: 0)) sharpTracks else tracks

    // the largest track named at or under lines, or the smallest when none is
    fun trackFor(lines: Int): VideoTrack? {
        val family = tracksFor(lines)
        return family.lastOrNull { it.lines <= lines } ?: family.firstOrNull()
    }
}

// every size can be fetched in small pieces, so the player can switch between them as the connection changes
val List<VideoTrack>.chunked: Boolean get() = isNotEmpty() && all { it.chunked }

data class VideoTrack(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val codecs: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
    val initRange: LongRange?,
    val indexRange: LongRange?,
    val contentLength: Long?,
    // the size youtube names this by (1080 for 1080p), so a wide 1920x800 video still lists as 1080p
    val lines: Int = videoLines(width, height),
) {
    val shortSide: Int get() = minOf(width, height)

    val chunked: Boolean get() = initRange != null && indexRange != null
}

private val CodecsPattern = Regex("codecs=\"([^\"]+)\"")

private val LinesPattern = Regex("(\\d+)p")

// the sizes youtube names its videos by
private val LineSteps = intArrayOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)

// a wide picture is named by its width (1920 wide is 1080p) and a narrow or upright one by its short side, snapped to youtube's steps
internal fun videoLines(width: Int, height: Int): Int {
    val shortSide = minOf(width, height)
    if (shortSide <= 0) return 0
    val fromLongSide = maxOf(width, height) * 9 / 16
    val raw = maxOf(shortSide, fromLongSide)
    return LineSteps.minBy { kotlin.math.abs(it - raw) }
}

// youtube's own label ("1080p60", "2160p HDR") when it sent one, worked out from the size otherwise
internal fun PlayerResponse.StreamingData.Format.lines(): Int =
    qualityLabel?.let { LinesPattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        ?: videoLines(width ?: 0, height ?: 0)

internal fun PlayerResponse.StreamingData.Format.containerType(): String = mimeType.substringBefore(';').trim()

internal fun PlayerResponse.StreamingData.Format.codecs(): String =
    CodecsPattern.find(mimeType)?.groupValues?.get(1).orEmpty()

internal fun PlayerResponse.StreamingData.Format.ByteRange.toLongRange(): LongRange? {
    val first = start.toLongOrNull() ?: return null
    val last = end.toLongOrNull() ?: return null
    return if (last >= first) first..last else null
}

// h264 has a hardware decoder on every phone, so it wins; vp9 and av1 only stand in for a video that has no h264
private val CodecFamilies = listOf("avc1", "vp9", "vp09", "av01")

// what youtube serves past 1080p, with the decoder type each one needs; vp9 first, far more phones decode it in hardware than av1
private val SharpFamilies = listOf(
    listOf("vp9", "vp09") to "video/x-vnd.on2.vp9",
    listOf("av01") to "video/av01",
)

// the picture-only formats of one codec, one per size, smallest first; 30fps beats 60 at the same size, it's half the decoding behind lyrics
internal fun pickVideoFamily(formats: List<PlayerResponse.StreamingData.Format>): List<PlayerResponse.StreamingData.Format> {
    val pictures = formats.pictures()
    for (family in CodecFamilies) {
        val inFamily = pictures.filter { it.codecs().startsWith(family) }
        if (inFamily.isEmpty()) continue
        return inFamily.onePerSize()
    }
    return emptyList()
}

// the same picture bigger than [aboveLines], in the first family this phone's hardware can decode at that size; a software decoder can't keep up with 4k
internal fun pickSharpFamily(
    formats: List<PlayerResponse.StreamingData.Format>,
    aboveLines: Int,
): List<PlayerResponse.StreamingData.Format> {
    val pictures = formats.pictures()
    for ((prefixes, decoderType) in SharpFamilies) {
        val inFamily = pictures.filter { format -> prefixes.any { format.codecs().startsWith(it) } }
            .onePerSize()
            .filter { HardwareDecoders.canPlay(decoderType, it.width!!, it.height!!, it.fps ?: 30) }
        if (inFamily.any { it.lines() > aboveLines }) return inFamily
    }
    return emptyList()
}

private fun List<PlayerResponse.StreamingData.Format>.pictures() =
    filter { !it.isAudio && (it.width ?: 0) > 0 && (it.height ?: 0) > 0 && it.bitrate > 0 }

private fun List<PlayerResponse.StreamingData.Format>.onePerSize() =
    groupBy { it.lines() }
        .map { (_, sameSize) -> sameSize.minWith(compareBy({ it.fps ?: 30 }, { it.bitrate })) }
        .sortedBy { it.lines() }

// which picture sizes this phone decodes in hardware, read from the codec list once per type
private object HardwareDecoders {
    private val byType = java.util.concurrent.ConcurrentHashMap<String, List<MediaCodecInfo.VideoCapabilities>>()

    fun canPlay(type: String, width: Int, height: Int, fps: Int): Boolean =
        byType.getOrPut(type) { find(type) }.any { caps ->
            // some decoders only list landscape sizes, so a portrait video is checked turned on its side too
            runCatching {
                caps.areSizeAndRateSupported(width, height, fps.toDouble()) ||
                    caps.areSizeAndRateSupported(height, width, fps.toDouble())
            }.getOrDefault(false)
        }

    private fun find(type: String): List<MediaCodecInfo.VideoCapabilities> =
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { info -> !info.isEncoder && info.supportedTypes.any { it.equals(type, ignoreCase = true) } && info.isHardware() }
                .mapNotNull { runCatching { it.getCapabilitiesForType(type).videoCapabilities }.getOrNull() }
        }.getOrDefault(emptyList())

    // android 10 says so outright; before that the software decoders are the ones google ships under its own names
    private fun MediaCodecInfo.isHardware(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return isHardwareAccelerated
        val lower = name.lowercase()
        return !lower.startsWith("omx.google.") && !lower.startsWith("c2.android.") && !lower.contains(".sw.")
    }
}
