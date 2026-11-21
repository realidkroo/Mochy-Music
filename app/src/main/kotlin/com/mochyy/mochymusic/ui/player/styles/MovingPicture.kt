// a module's own gif or webp that keeps moving, decoded by android itself, so it never animates anywhere else in the app

package com.mochyy.mochymusic.ui.player.styles

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import java.io.File
import java.nio.ByteBuffer

// a big loop is held in memory frame by frame, so anything longer than this on a side is decoded smaller
private const val MaxSide = 1080

// the files android can play as a loop
private val MovingName = Regex("""\.(gif|webp)$""", RegexOption.IGNORE_CASE)

/** whether a module picture could be a loop, from its name alone so nothing touches the disk while drawing */
internal fun isMovingPicture(model: Any?): Boolean = when (model) {
    is File -> MovingName.containsMatchIn(model.name)
    is String -> MovingName.containsMatchIn(model.substringBefore('?'))
    else -> false
}

/** a request that plays a module's gif or webp as a loop on android 9+, or the picture as it is everywhere else */
internal fun movingPictureRequest(context: Context, model: Any?): Any? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !isMovingPicture(model)) return model
    return ImageRequest.Builder(context)
        .data(model)
        .decoderFactory(movingDecoder())
        // a still copy of the same file kept in memory would otherwise come back frozen
        .memoryCacheKey("moving:$model")
        .build()
}

@RequiresApi(Build.VERSION_CODES.P)
private fun movingDecoder(): Decoder.Factory = MovingPictureDecoder.Factory

@RequiresApi(Build.VERSION_CODES.P)
private class MovingPictureDecoder(private val result: SourceFetchResult) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = result.source.source().readByteArray()
        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val scale = MaxSide.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }
        // a loop is meant to keep going, whatever count the file was saved with
        if (drawable is AnimatedImageDrawable) drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        return DecodeResult(image = drawable.asImage(), isSampled = false)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    object Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder = MovingPictureDecoder(result)
    }
}
