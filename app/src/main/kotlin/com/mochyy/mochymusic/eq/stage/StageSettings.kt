// StageSettings.kt

package com.mochyy.mochymusic.eq.stage

import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
enum class StageSource { Vocal, Center, Left, Right, Ambience, Sub }

/** one thing placed in the room: x and z run -1..1 from wall to wall (z ahead), y 0..1 from the floor to the ceiling */
@Serializable
data class StageObject(
    val id: Int,
    val source: StageSource,
    val x: Float,
    val y: Float,
    val z: Float,
    val levelDb: Float = 0f,
    val muted: Boolean = false,
    val solo: Boolean = false,
    /** how far apart the ambience pair spreads, 0..1 */
    val width: Float = 0.6f,
)

/** where you sit in the room, in the same terms as an object, and which way you face (degrees, right is positive) */
@Serializable
data class StageListener(val x: Float = 0f, val y: Float = 0.4f, val z: Float = 0f, val yaw: Float = 0f)

@Serializable
enum class StageWalls(val absorption: Float, val damping: Float) {
    Soft(0.45f, 0.7f),
    Wood(0.22f, 0.45f),
    Concrete(0.08f, 0.15f),
}

/** what the room does on a speaker, where 3d sound made for two ears doesn't work */
@Serializable
enum class StageSpeakers { Off, Stereo, Keep3D }

/** the room in metres */
data class StageBox(val width: Float, val depth: Float, val height: Float)

@Serializable
data class StageSettings(
    val enabled: Boolean = false,
    val presetId: String? = StagePresets.DefaultId,
    val objects: List<StageObject> = StagePresets.Default.objects,
    val listener: StageListener = StagePresets.Default.listener,
    /** 0 a small studio .. 1 a concert hall */
    val roomSize: Float = StagePresets.Default.roomSize,
    val walls: StageWalls = StagePresets.Default.walls,
    val roomAmount: Float = StagePresets.Default.roomAmount,
    val levelDb: Float = 0f,
    val onSpeakers: StageSpeakers = StageSpeakers.Stereo,
    /** outputs the user said are speakers (true) or headphones (false), by StageOutput.key; the rest are guessed */
    val outputKinds: Map<String, Boolean> = emptyMap(),
) {
    val box: StageBox
        get() {
            val width = 3.5f * (22f / 3.5f).pow(roomSize)
            return StageBox(width, width * 1.3f, 2.6f + 6.4f * roomSize.pow(1.5f))
        }

    /** whether the room plays at all on this output */
    fun playsOn(speaker: Boolean): Boolean = enabled && !(speaker && onSpeakers == StageSpeakers.Off)

    /** the float array the native room reads */
    fun toParams(speaker: Boolean): StageParams {
        val values = FloatArray(StageNative.ParamCount)
        val box = box
        val wet = playsOn(speaker)
        values[StageNative.Enabled] = if (wet) 1f else 0f
        values[StageNative.Mode] = if (speaker && onSpeakers == StageSpeakers.Stereo) StageNative.ModeSpeakers else StageNative.ModeHeadphones
        values[StageNative.RoomWidth] = box.width
        values[StageNative.RoomDepth] = box.depth
        values[StageNative.RoomHeight] = box.height
        values[StageNative.Absorption] = walls.absorption
        values[StageNative.Damping] = walls.damping
        values[StageNative.RoomAmount] = roomAmount
        values[StageNative.ListenerX] = listener.x * box.width / 2
        values[StageNative.ListenerY] = listener.y * box.height
        values[StageNative.ListenerZ] = listener.z * box.depth / 2
        values[StageNative.ListenerYaw] = listener.yaw
        values[StageNative.Master] = dbToGain(levelDb)

        val soloing = objects.any { it.solo && !it.muted }
        fun gainOf(o: StageObject) = if (o.muted || (soloing && !o.solo)) 0f else dbToGain(o.levelDb)
        fun put(slot: Int, o: StageObject, gain: Float) {
            val at = StageNative.Objects + slot * StageNative.ObjectStride
            values[at] = o.x * box.width / 2
            values[at + 1] = o.y * box.height
            values[at + 2] = o.z * box.depth / 2
            values[at + 3] = gain
            values[at + 4] = o.width
        }
        // every copy of a part plays the same sound, so together they stay as loud as one
        StageSource.entries.forEach { source ->
            val copies = objects.filter { it.source == source }.take(StageNative.Copies)
            copies.forEachIndexed { i, o -> put(source.ordinal * StageNative.Copies + i, o, gainOf(o) / copies.size) }
        }
        return StageParams(wet, values)
    }
}

/** what the playback thread reads; replaced, never mutated, so the processor notices a change by identity */
class StageParams(val enabled: Boolean, val values: FloatArray) {
    companion object {
        val Off = StageParams(false, FloatArray(StageNative.ParamCount))
    }
}

internal fun dbToGain(db: Float): Float = 10f.pow(db / 20f)
