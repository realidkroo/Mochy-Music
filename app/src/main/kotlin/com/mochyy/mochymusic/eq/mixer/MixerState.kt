// MixerState.kt

package com.mochyy.mochymusic.eq.mixer

import kotlinx.serialization.Serializable

/** what part of its input a bus works on; the surround ones are the song's own channel groups and stay silent on a stereo song */
@Serializable
enum class BusFocus(val native: Int) {
    All(MixerNative.FocusAll),
    Mid(MixerNative.FocusMid),
    Side(MixerNative.FocusSide),
    Left(MixerNative.FocusLeft),
    Right(MixerNative.FocusRight),
    Centre(MixerNative.FocusLane + 1),
    Lfe(MixerNative.FocusLane + 2),
    Rear(MixerNative.FocusLane + 3),
    Surround(MixerNative.FocusLane + 4),
    TopFront(MixerNative.FocusLane + 5),
    TopRear(MixerNative.FocusLane + 6),
    ;

    /** only means something when the song has more than two channels */
    val surroundOnly: Boolean get() = native >= MixerNative.FocusLane
}

/** one effect on a bus: what kind it is, its knobs in the kind's order, and for a script the script itself */
@Serializable
data class MixSlot(
    val uid: Long,
    val kind: String,
    val values: List<Float>,
    val bypass: Boolean = false,
    /** a liveprog script someone opened or wrote, with the name it came with */
    val script: String? = null,
    val scriptName: String? = null,
    /** bumped when a script without @slider has to run @init again for its knobs */
    val revision: Int = 0,
) {
    val isScript: Boolean get() = kind == MixerKinds.Liveprog
}

@Serializable
data class MixBus(
    val id: Int,
    val name: String = "",
    /** which of the strip colours it wears */
    val color: Int = 0,
    val songIn: Boolean = false,
    val focus: BusFocus = BusFocus.All,
    val gainDb: Float = 0f,
    /** -1 left to 1 right; a balance on stereo buses */
    val pan: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    /** destination bus id (the master is [MixerNative.Master]) to its post-fader level, 0..1 */
    val sends: Map<Int, Float> = mapOf(MixerNative.Master to 1f),
    val slots: List<MixSlot> = emptyList(),
) {
    val isMaster: Boolean get() = id == MixerNative.Master

    /** the bit mask the engine reads: a bit per destination that gets any of this bus */
    val sendMask: Int get() = sends.entries.filter { it.value > 0f }.fold(0) { mask, (to, _) -> mask or (1 shl to) }
}

@Serializable
data class MixerState(
    val enabled: Boolean = false,
    val buses: List<MixBus> = listOf(MixBus(id = 0, songIn = true)),
    val master: MixBus = MixBus(id = MixerNative.Master, sends = emptyMap()),
    val nextUid: Long = 1,
    /** the saved mix this still matches; null once it's been changed */
    val presetId: String? = null,
) {
    fun bus(id: Int): MixBus? = if (id == MixerNative.Master) master else buses.firstOrNull { it.id == id }

    fun slot(uid: Long): MixSlot? = (buses + master).firstNotNullOfOrNull { b -> b.slots.firstOrNull { it.uid == uid } }

    fun withBus(id: Int, transform: (MixBus) -> MixBus): MixerState =
        if (id == MixerNative.Master) copy(master = transform(master)) else copy(buses = buses.map { if (it.id == id) transform(it) else it })

    fun withSlot(uid: Long, transform: (MixSlot) -> MixSlot): MixerState {
        fun on(bus: MixBus) = bus.copy(slots = bus.slots.map { if (it.uid == uid) transform(it) else it })
        return copy(buses = buses.map(::on), master = on(master))
    }

    /** whether [from] can send to [to] without closing a loop through other buses */
    fun canSend(from: Int, to: Int): Boolean {
        if (from == to) return false
        if (to == MixerNative.Master) return true
        val seen = mutableSetOf<Int>()
        val stack = ArrayDeque(listOf(to))
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (id == from) return false
            if (!seen.add(id)) continue
            bus(id)?.sends?.filter { it.value > 0f && it.key != MixerNative.Master }?.keys?.forEach(stack::addLast)
        }
        return true
    }
}

/** a whole mix saved under a name */
@Serializable
data class MixPreset(val id: String, val name: String, val state: MixerState)

object MixerKinds {
    const val Liveprog = "mochy.liveprog"
    const val Native = "mochy.native"
    /** a module's processor: "module:<module id>/<processor id>" */
    const val ModulePrefix = "module:"
}
