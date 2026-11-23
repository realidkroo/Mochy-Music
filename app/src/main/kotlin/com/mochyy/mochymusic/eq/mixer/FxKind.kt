// FxKind.kt

package com.mochyy.mochymusic.eq.mixer

import org.json.JSONArray
import org.json.JSONObject

/** how a knob moves between its ends; the same order as the native Scale */
enum class KnobScale { Linear, Log, Int, Choice, Toggle }

data class FxKnob(
    val id: String,
    val name: String,
    val unit: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val scale: KnobScale,
    val choices: List<String> = emptyList(),
) {
    fun clamp(value: Float): Float {
        val v = if (value.isFinite()) value.coerceIn(min, max) else default
        return if (scale == KnobScale.Int || scale == KnobScale.Choice || scale == KnobScale.Toggle) kotlin.math.round(v) else v
    }

    /** 0..1 along the knob's travel, log knobs spread evenly by octave */
    fun toFraction(value: Float): Float = when {
        scale == KnobScale.Log && min > 0f -> (kotlin.math.ln(value / min) / kotlin.math.ln(max / min)).toFloat()
        else -> (value - min) / (max - min)
    }.coerceIn(0f, 1f)

    fun fromFraction(fraction: Float): Float {
        val f = fraction.coerceIn(0f, 1f)
        val raw = if (scale == KnobScale.Log && min > 0f) (min * kotlin.math.exp(f * kotlin.math.ln(max / min))) else min + f * (max - min)
        return clamp(raw)
    }
}

data class FxMeter(val id: String, val name: String, val unit: String, val min: Float, val max: Float)

/** a set of knob values that comes with a kind, in the knobs' order */
data class KindPreset(val name: String, val values: List<Float>)

/** where an effect kind comes from, for the add sheet's groups and for credits */
sealed interface KindOrigin {
    data object BuiltIn : KindOrigin
    data object Script : KindOrigin
    data class Module(val moduleId: String, val moduleName: String) : KindOrigin
}

/** everything the mixer needs to show an effect and run it */
data class FxKind(
    /** what a saved slot points at */
    val key: String,
    val name: String,
    val category: String,
    val description: String,
    val knobs: List<FxKnob>,
    val meters: List<FxMeter>,
    val presets: List<KindPreset>,
    val showsTail: Boolean,
    val stereoOnly: Boolean,
    val origin: KindOrigin,
    /** how the engine runs it: a built-in id, liveprog or a module's native processor */
    val engineType: String,
    /** the script for liveprog, the library and processor for native */
    val engineSource: String,
    val vars: List<String> = emptyList(),
    /** false for a script without @slider: moving a knob runs its @init again */
    val liveKnobs: Boolean = true,
) {
    val defaults: List<Float> get() = knobs.map { it.default }

    /** a saved slot's values fitted to these knobs: missing ones take their default, extra ones are dropped */
    fun fit(values: List<Float>): List<Float> = knobs.mapIndexed { i, k -> values.getOrNull(i)?.let(k::clamp) ?: k.default }

    companion object {
        /** the kinds the native side describes, as builtins() and loadLibrary() write them */
        fun parseAll(json: String, origin: KindOrigin, engineType: String?): List<FxKind> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i -> parse(array.getJSONObject(i), origin, engineType) }
        }.getOrDefault(emptyList())

        private fun parse(o: JSONObject, origin: KindOrigin, engineType: String?): FxKind {
            val key = o.getString("key")
            val params = o.optJSONArray("params") ?: JSONArray()
            val meters = o.optJSONArray("meters") ?: JSONArray()
            val presets = o.optJSONArray("presets") ?: JSONArray()
            return FxKind(
                key = if (engineType == MixerKinds.Native && origin is KindOrigin.Module) "${MixerKinds.ModulePrefix}${origin.moduleId}/${o.getString("id")}" else key,
                name = o.optString("name"),
                category = o.optString("category"),
                description = o.optString("description"),
                knobs = (0 until params.length()).map { i ->
                    val p = params.getJSONObject(i)
                    FxKnob(
                        id = p.optString("id"),
                        name = p.optString("name"),
                        unit = p.optString("unit"),
                        min = p.optDouble("min", 0.0).toFloat(),
                        max = p.optDouble("max", 1.0).toFloat(),
                        default = p.optDouble("def", 0.0).toFloat(),
                        scale = KnobScale.entries.getOrElse(p.optInt("scale")) { KnobScale.Linear },
                        choices = p.optString("choices").split('|').filter(String::isNotEmpty),
                    )
                },
                meters = (0 until meters.length()).map { i ->
                    val m = meters.getJSONObject(i)
                    FxMeter(m.optString("id"), m.optString("name"), m.optString("unit"), m.optDouble("min").toFloat(), m.optDouble("max").toFloat())
                },
                presets = (0 until presets.length()).map { i ->
                    val p = presets.getJSONObject(i)
                    val values = p.getJSONArray("values")
                    KindPreset(p.optString("name"), (0 until values.length()).map { values.getDouble(it).toFloat() })
                },
                showsTail = o.optBoolean("showsTail"),
                stereoOnly = o.optBoolean("stereoOnly"),
                origin = origin,
                engineType = engineType ?: key,
                engineSource = if (engineType == MixerKinds.Native) key else "",
            )
        }
    }
}
