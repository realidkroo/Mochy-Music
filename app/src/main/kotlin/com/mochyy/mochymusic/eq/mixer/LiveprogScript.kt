// LiveprogScript.kt

package com.mochyy.mochymusic.eq.mixer

/** one knob a script declares, in jamesdsp's "dB:-8<-30,15,1>Volume" or jsfx's "slider1:gain=0<-24,24,0.1{A,B}>Gain" form */
data class ScriptKnob(
    val variable: String,
    val name: String,
    val default: Float,
    val min: Float,
    val max: Float,
    val step: Float?,
    val choices: List<String>,
)

/** what mochy reads out of a liveprog script before it runs: its name, its knobs and which sections it has */
data class LiveprogScript(
    val description: String?,
    val tags: List<String>,
    val knobs: List<ScriptKnob>,
    /** a script with @slider works its knobs out live; one without has its @init run again, with a short crossfade */
    val hasSlider: Boolean,
    val hasSample: Boolean,
) {
    val defaults: List<Float> get() = knobs.map { it.default }

    /** the kind the mixer shows for it: the knobs become the slot's params, in this order */
    fun kind(title: String): FxKind = FxKind(
        key = MixerKinds.Liveprog,
        name = title,
        category = CategoryScripts,
        description = description.orEmpty(),
        knobs = knobs.map { k ->
            val scale = when {
                k.choices.isNotEmpty() -> KnobScale.Choice
                k.step != null && k.step >= 1f && k.step == kotlin.math.floor(k.step) -> KnobScale.Int
                else -> KnobScale.Linear
            }
            FxKnob(k.variable, k.name, "", k.min, k.max, k.default, scale, k.choices)
        },
        meters = emptyList(),
        presets = emptyList(),
        showsTail = tags.any { it.equals("reverb", true) || it.equals("delay", true) },
        stereoOnly = false,
        origin = KindOrigin.Script,
        engineType = MixerKinds.Liveprog,
        engineSource = "",
        vars = knobs.map { it.variable },
        liveKnobs = hasSlider,
    )

    companion object {
        const val CategoryScripts = "Scripts"

        private val Knob = Regex(
            """^\s*([A-Za-z_][\w.]*)\s*:\s*(?:([A-Za-z_][\w.]*)\s*=\s*)?(-?\d*\.?\d+)\s*<\s*(-?\d*\.?\d+)\s*,\s*(-?\d*\.?\d+)\s*(?:,\s*(-?\d*\.?\d+))?\s*(?:\{([^}]*)\})?\s*>\s*(.*)$""",
        )
        private val Description = Regex("""^\s*desc\s*:\s*(.+)$""")
        private val Tags = Regex("""^[\W/]*tags\s*:\s*(.+)$""")

        fun parse(text: String): LiveprogScript {
            var description: String? = null
            var tags = emptyList<String>()
            val knobs = mutableListOf<ScriptKnob>()
            var inSection = false
            var hasSlider = false
            var hasSample = false
            for (raw in text.lineSequence()) {
                val line = raw.trimEnd()
                val trimmed = line.trimStart()
                if (trimmed.startsWith("@")) {
                    inSection = true
                    val section = trimmed.substringBefore(' ').substringBefore('\t')
                    if (section == "@slider") hasSlider = true
                    if (section == "@sample") hasSample = true
                    continue
                }
                // knobs and the description are declared above the first section
                if (inSection) continue
                Description.find(line)?.let { if (description == null) description = it.groupValues[1].trim() }
                Tags.find(line)?.let { tags = it.groupValues[1].trim().split(Regex("\\s+")).filter(String::isNotBlank) }
                val m = Knob.find(line) ?: continue
                val key = m.groupValues[1]
                val variable = m.groupValues[2].ifBlank { key }
                val min = m.groupValues[4].toFloatOrNull() ?: continue
                val max = m.groupValues[5].toFloatOrNull() ?: continue
                if (max <= min) continue
                val choices = m.groupValues[7].split(',').map(String::trim).filter(String::isNotEmpty)
                // jsfx hides a slider whose name starts with '-'; it still sets its variable
                val name = m.groupValues[8].trim().removePrefix("-").ifBlank { variable }
                val default = (m.groupValues[3].toFloatOrNull() ?: min).coerceIn(min, max)
                knobs += ScriptKnob(variable, name, default, min, max, m.groupValues[6].toFloatOrNull(), choices)
            }
            return LiveprogScript(description, tags, knobs.distinctBy { it.variable }, hasSlider, hasSample)
        }
    }
}
