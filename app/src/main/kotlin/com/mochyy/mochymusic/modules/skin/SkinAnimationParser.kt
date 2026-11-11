// reads a player's animations out of its manifest: keyframes, easings, clocks, states, transitions and events, every number kept in range

package com.mochyy.mochymusic.modules.skin

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

internal object SkinAnimationParser {

    /** the player's named keyframes: { "pop": [ { "at": 0, "scale": 0.6 }, ... ] } or css style { "pop": { "0%": {...}, "100%": {...} } } */
    fun library(element: JsonElement?): Map<String, SkinKeyframes> =
        (element as? JsonObject).orEmpty().entries
            .mapNotNull { (name, value) -> keyframes(value)?.let { name.trim().lowercase() to it } }
            .take(MaxLibrary)
            .toMap()

    /** one set of keyframes, as a list of frames with "at" or as an object keyed by "from", "to" and percentages */
    fun keyframes(element: JsonElement?): SkinKeyframes? {
        val frames = when (element) {
            is JsonArray -> element.mapNotNull { frame -> (frame as? JsonObject)?.let { o -> at(o["at"])?.let { it to o } } }
            is JsonObject -> element.entries.mapNotNull { (key, frame) -> (frame as? JsonObject)?.let { o -> at(JsonPrimitive(key))?.let { it to o } } }
            else -> return null
        }
        return build(frames.take(MaxFrames))
    }

    // each property gets its own track over the frames that set it, starting and ending on its neutral value when the first or last frame leaves it out, like css
    private fun build(frames: List<Pair<Float, JsonObject>>): SkinKeyframes? {
        val sorted = frames.sortedBy { it.first }
        val tracks = PoseProp.entries.mapNotNull { prop ->
            val points = sorted.mapNotNull { (at, o) -> value(o, prop)?.let { Point(at, it, easing(o.string("easing"))) } }
            if (points.isEmpty()) return@mapNotNull null
            val full = buildList {
                if (points.first().at > 0f) add(Point(0f, prop.neutral, null))
                addAll(points)
                if (points.last().at < 1f) add(Point(1f, prop.neutral, null))
            }
            SkinTrack(prop, FloatArray(full.size) { full[it].at }, FloatArray(full.size) { full[it].value }, full.map { it.easing })
        }
        return tracks.takeIf { it.isNotEmpty() }?.let(::SkinKeyframes)
    }

    private class Point(val at: Float, val value: Float, val easing: Easing?)

    // 0.5, 50 (read as a percentage), "50%", "from" or "to"
    private fun at(element: JsonElement?): Float? {
        val raw = (element as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase() ?: return null
        val number = when (raw) {
            "from" -> 0f
            "to" -> 1f
            else -> if (raw.endsWith("%")) raw.dropLast(1).toFloatOrNull()?.div(100f) else raw.toFloatOrNull()?.let { if (it > 1f) it / 100f else it }
        }
        return number?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    }

    /** the pose values an object sets: a state, a keyframe */
    fun pose(o: JsonObject): Map<PoseProp, Float> = PoseProp.entries.mapNotNull { prop -> value(o, prop)?.let { prop to it } }.toMap()

    private fun value(o: JsonObject, prop: PoseProp): Float? = when (prop) {
        PoseProp.X -> o.float("x")?.coerceIn(-800f, 800f)
        PoseProp.Y -> o.float("y")?.coerceIn(-800f, 800f)
        PoseProp.Scale -> o.float("scale")?.coerceIn(0f, 5f)
        PoseProp.ScaleX -> o.float("scaleX")?.coerceIn(0f, 5f)
        PoseProp.ScaleY -> o.float("scaleY")?.coerceIn(0f, 5f)
        PoseProp.Rotate -> (o.float("rotate") ?: o.float("rotation"))?.coerceIn(-3600f, 3600f)
        PoseProp.RotateX -> o.float("rotateX")?.coerceIn(-360f, 360f)
        PoseProp.RotateY -> o.float("rotateY")?.coerceIn(-360f, 360f)
        PoseProp.Opacity -> (o.float("opacity") ?: o.float("alpha"))?.coerceIn(0f, 1f)
        PoseProp.Blur -> o.float("blur")?.coerceIn(0f, MaxBlur)
    }

    /** "ease", "easeInOut", "back", "bounce", "elastic", "steps(4)" or "cubic-bezier(0.2, 0.8, 0.2, 1)" */
    fun easing(raw: String?): Easing? {
        val text = raw?.trim()?.lowercase() ?: return null
        if (text.startsWith("cubic-bezier(") || text.startsWith("cubicbezier(")) {
            val n = NumberPattern.findAll(text.substringAfter('(')).mapNotNull { it.value.toFloatOrNull() }.toList()
            if (n.size != 4) return null
            return CubicBezierEasing(n[0].coerceIn(0f, 1f), n[1].coerceIn(-2f, 3f), n[2].coerceIn(0f, 1f), n[3].coerceIn(-2f, 3f))
        }
        if (text.startsWith("steps(")) {
            val count = NumberPattern.find(text)?.value?.toFloatOrNull()?.toInt()?.coerceIn(1, 60) ?: return null
            return Easing { t -> if (t >= 1f) 1f else floor(t * count) / count }
        }
        return when (text.replace("-", "").replace("_", "").replace(" ", "")) {
            "linear" -> LinearEasing
            "ease" -> CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
            "easein" -> CubicBezierEasing(0.42f, 0f, 1f, 1f)
            "easeout" -> CubicBezierEasing(0f, 0f, 0.58f, 1f)
            "easeinout" -> CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
            "smooth", "standard" -> FastOutSlowInEasing
            "back", "overshoot", "easeoutback" -> CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
            "anticipate", "easeinback" -> CubicBezierEasing(0.36f, 0f, 0.66f, -0.56f)
            "bounce" -> Bounce
            "elastic" -> Elastic
            else -> null
        }
    }

    /** an animation using keyframes: { "keyframes": "wobble" or inline, "duration", "delay", "repeat", "easing", "clock", "run" } */
    fun timeline(o: JsonObject, library: Map<String, SkinKeyframes>, repeat: SkinRepeat, durationMs: Int): SkinTimeline? {
        val frames = when (val k = o["keyframes"]) {
            is JsonPrimitive -> k.contentOrNull?.trim()?.lowercase()?.let(library::get)
            else -> keyframes(k)
        } ?: return null
        val clock = when (o.string("clock")?.trim()?.lowercase()) {
            "progress", "song" -> SkinClock.Progress
            "level", "loudness", "volume" -> SkinClock.Level
            "bass", "low" -> SkinClock.Bass
            "mid", "middle" -> SkinClock.Mid
            "treble", "high" -> SkinClock.Treble
            else -> SkinClock.Time
        }
        return SkinTimeline(
            frames = frames,
            durationMs = (o.float("duration") ?: durationMs.toFloat()).coerceIn(50f, 120_000f).toInt(),
            delayMs = (o.float("delay") ?: 0f).coerceIn(0f, 20_000f).toInt(),
            repeat = repeat(o["repeat"]) ?: repeat,
            clock = clock,
            // music and the song's progress move it straight, the keyframes' own easings still apply
            easing = easing(o.string("easing")) ?: if (clock == SkinClock.Time) FastOutSlowInEasing else LinearEasing,
            always = o.string("run")?.trim()?.lowercase() == "always" || o.bool("always") == true,
        )
    }

    private fun repeat(element: JsonElement?): SkinRepeat? {
        val p = element as? JsonPrimitive ?: return null
        p.booleanOrNull?.let { return if (it) SkinRepeat.Loop else SkinRepeat.Once }
        return when (p.contentOrNull?.trim()?.lowercase()) {
            "once", "1", "none" -> SkinRepeat.Once
            "loop", "infinite", "forever" -> SkinRepeat.Loop
            "pingpong", "alternate", "yoyo", "bounce" -> SkinRepeat.PingPong
            else -> null
        }
    }

    /** "states": [ { "when": "@paused", "scale": 0.9 } ] or { "@paused": { "scale": 0.9 } } */
    fun states(element: JsonElement?): List<SkinState> {
        val pairs = when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonObject)?.let { o -> o.string("when")?.let { w -> w to o } } }
            is JsonObject -> element.entries.mapNotNull { (key, o) -> (o as? JsonObject)?.let { key to it } }
            else -> emptyList()
        }
        return pairs.mapNotNull { (raw, o) ->
            val condition = condition(raw) ?: return@mapNotNull null
            pose(o).takeIf { it.isNotEmpty() }?.let { SkinState(condition, it) }
        }.take(MaxStates)
    }

    /** 400, "spring", or { "duration": 400, "easing": "back" } */
    fun transition(element: JsonElement?): SkinTransition? = when (element) {
        is JsonPrimitive -> element.contentOrNull?.trim()?.lowercase()?.let { text ->
            if (text == "spring") SkinTransition(DefaultTransitionMs, FastOutSlowInEasing, spring = true)
            else text.toFloatOrNull()?.let { SkinTransition(it.coerceIn(0f, 10_000f).toInt(), FastOutSlowInEasing, spring = false) }
        }
        is JsonObject -> SkinTransition(
            durationMs = (element.float("duration") ?: DefaultTransitionMs.toFloat()).coerceIn(0f, 10_000f).toInt(),
            easing = easing(element.string("easing")) ?: FastOutSlowInEasing,
            spring = element.string("type")?.lowercase() == "spring" || element.bool("spring") == true,
        )
        else -> null
    }

    val DefaultTransition = SkinTransition(DefaultTransitionMs, FastOutSlowInEasing, spring = false)

    /** "on": { "song": "pop", "pause": { "keyframes": "sink", "duration": 500 } } */
    fun triggers(element: JsonElement?, library: Map<String, SkinKeyframes>): List<SkinTrigger> =
        (element as? JsonObject).orEmpty().entries.mapNotNull { (name, value) ->
            val event = event(name) ?: return@mapNotNull null
            val o = when (value) {
                is JsonPrimitive -> JsonObject(mapOf("keyframes" to value))
                is JsonObject -> value
                else -> return@mapNotNull null
            }
            // a one-shot animation is always timed, a song's progress can't set one off
            timeline(o, library, SkinRepeat.Once, DefaultTriggerMs)?.let { SkinTrigger(event, it.copy(clock = SkinClock.Time, always = true)) }
        }.take(MaxTriggers)

    private fun event(raw: String): SkinEvent? = when (raw.trim().lowercase()) {
        "song", "track", "change", "next" -> SkinEvent.Song
        "play", "resume" -> SkinEvent.Play
        "pause" -> SkinEvent.Pause
        "like" -> SkinEvent.Like
        "unlike" -> SkinEvent.Unlike
        "open", "show" -> SkinEvent.Open
        "beat", "kick" -> SkinEvent.Beat
        else -> null
    }

    /** "key", "!key", "key=value", "key!=value", or one of the built-in @playing, @paused, @liked… the same way */
    fun condition(raw: String): SkinCondition? {
        val text = raw.trim().take(120)
        return when {
            "!=" in text -> text.split("!=", limit = 2).let { SkinCondition(it[0].trim(), it[1].trim(), negate = true) }
            "=" in text -> text.split("=", limit = 2).let { SkinCondition(it[0].trim(), it[1].trim(), negate = false) }
            text.startsWith("!") -> SkinCondition(text.drop(1).trim(), null, negate = true)
            else -> SkinCondition(text, null, negate = false)
        }.takeIf { it.key.isNotEmpty() }
    }

    // a ball dropped on the floor, settling in three bounces
    private val Bounce = Easing { t ->
        val n = 7.5625f
        val d = 2.75f
        when {
            t < 1f / d -> n * t * t
            t < 2f / d -> (t - 1.5f / d).let { n * it * it + 0.75f }
            t < 2.5f / d -> (t - 2.25f / d).let { n * it * it + 0.9375f }
            else -> (t - 2.625f / d).let { n * it * it + 0.984375f }
        }
    }

    // a spring let go: past the end and back a few times
    private val Elastic = Easing { t ->
        if (t <= 0f || t >= 1f) t else (2.0.pow(-10.0 * t) * sin((t * 10.0 - 0.75) * (2.0 * PI / 3.0)) + 1.0).toFloat()
    }

    private val NumberPattern = Regex("""-?\d+(?:\.\d+)?""")
    const val MaxBlur = 60f
    private const val MaxLibrary = 24
    private const val MaxFrames = 24
    private const val MaxStates = 8
    private const val MaxTriggers = 7
    private const val DefaultTransitionMs = 350
    private const val DefaultTriggerMs = 600
}
