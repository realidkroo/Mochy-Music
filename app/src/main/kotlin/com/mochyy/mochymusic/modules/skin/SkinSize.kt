// how big a module's player, bar, page or lyrics are drawn: fitted to the screen from the width it was made for, times its own scale or a setting's (docs/wiki, scaling)

package com.mochyy.mochymusic.modules.skin

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

/** [designWidth] the screen width in dp the part was laid out on, [scale] its own size, [scaleKey] a setting that changes it */
@Immutable
data class SkinSize(val designWidth: Float? = null, val scale: Float = 1f, val scaleKey: String? = null) {

    val plain: Boolean get() = designWidth == null && scale == 1f && scaleKey == null

    /** what every dp and sp inside the part is multiplied by on a screen [screenWidthDp] wide */
    fun factor(screenWidthDp: Float, values: Map<String, String>): Float {
        val fit = designWidth?.takeIf { screenWidthDp > 0f }?.let { (screenWidthDp / it).coerceIn(MinFit, MaxFit) } ?: 1f
        // a setting that isn't a number yet (a blank text field) leaves the part at its own scale
        val own = scaleKey?.let { values[it]?.trim()?.toFloatOrNull() }?.takeIf { it.isFinite() } ?: scale
        return fit * own.coerceIn(MinScale, MaxScale)
    }

    companion object {
        val Plain = SkinSize()
        const val MinScale = 0.5f
        const val MaxScale = 2f
        const val MinFit = 0.6f
        const val MaxFit = 1.8f

        // "designWidth": 390 and "scale": 1.1, "$size" or { "value": 1, "setting": "size" }; a block reads its scale under its own [scaleName], since "scale" already sizes every block's layer
        internal fun of(o: JsonObject, scaleName: String = "scale"): SkinSize {
            val design = (o.float("designWidth") ?: o.float("fit"))?.coerceIn(200f, 1600f)
            val (scale, key) = scaleOf(o[scaleName])
            return SkinSize(design, scale, key)
        }

        private fun scaleOf(element: JsonElement?): Pair<Float, String?> = when (element) {
            is JsonPrimitive -> {
                val text = element.contentOrNull?.trim().orEmpty()
                if (text.startsWith("$")) 1f to text.removePrefix("$").take(60).takeIf { it.isNotBlank() }
                else (element.floatOrNull ?: text.toFloatOrNull() ?: 1f).coerceIn(MinScale, MaxScale) to null
            }
            is JsonObject -> (element.float("value") ?: 1f).coerceIn(MinScale, MaxScale) to element.string("setting")?.take(60)
            else -> 1f to null
        }
    }
}
