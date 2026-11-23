// MixerStore.kt

package com.mochyy.mochymusic.eq.mixer

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** the mixer as the pages set it: every change reaches the players right away and is saved a moment later, so faders drag freely (docs/notes/mixer.md) */
@Singleton
class MixerStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modules: ModuleProcessors,
) {
    private val prefs = context.getSharedPreferences("mixer", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saveJob: Job? = null
    private val reinitJobs = HashMap<Long, Job>()

    private val _state = MutableStateFlow(load("state") ?: MixerState())
    val state: StateFlow<MixerState> = _state.asStateFlow()

    private val _presets = MutableStateFlow(load<List<MixPreset>>("presets") ?: emptyList())
    val presets: StateFlow<List<MixPreset>> = _presets.asStateFlow()

    private val _builtins = MutableStateFlow<List<FxKind>>(emptyList())
    /** the effects mochy ships, read once from the native library */
    val builtins: StateFlow<List<FxKind>> = _builtins.asStateFlow()

    /** read on the playback thread: true while the mixer is on and its library loaded */
    @Volatile
    var active: Boolean = false
        private set

    /** a module's native processor is in the mix, so the encoded stream is worth handing over */
    @Volatile
    var wantsBitstream: Boolean = false
        private set

    // what the engine was last told, so a fader drag sends one value instead of the whole mix
    private var pushedShape: List<Any?>? = null
    private val pushedValues = HashMap<Long, List<Float>>()
    private val pushedBypass = HashMap<Long, Boolean>()
    private val pushedSends = HashMap<Pair<Int, Int>, Float>()
    private val pushedBuses = HashMap<Int, List<Any>>()

    init {
        scope.launch {
            val loaded = withContext(Dispatchers.Default) { MixerNative.loaded }
            if (loaded) _builtins.value = withContext(Dispatchers.Default) { FxKind.parseAll(MixerNative.builtins(), KindOrigin.BuiltIn, null) }
            push()
        }
        // a module turned on or off changes what its slots run
        scope.launch { modules.kinds.collect { push() } }
    }

    // a script is read once per version of its text, not on every fader move
    private val parsedScripts = object : LinkedHashMap<String, LiveprogScript>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LiveprogScript>?) = size > 16
    }

    fun script(text: String): LiveprogScript = synchronized(parsedScripts) { parsedScripts.getOrPut(text) { LiveprogScript.parse(text) } }

    /** the kind a slot is: a built-in, its own script, or a module's processor; null when that module is gone */
    fun kindFor(slot: MixSlot): FxKind? = when {
        slot.isScript -> script(slot.script.orEmpty()).kind(slot.scriptName ?: "Liveprog")
        slot.kind.startsWith(MixerKinds.ModulePrefix) -> modules.find(slot.kind)
        else -> _builtins.value.firstOrNull { it.key == slot.kind }
    }

    /** every kind the add sheet offers, built-ins first, then each module's */
    fun addable(): List<FxKind> = _builtins.value + modules.all()

    fun setEnabled(on: Boolean) = edit { it.copy(enabled = on) }

    fun addBus() = edit { s ->
        val id = (0 until MixerNative.MaxBuses).firstOrNull { id -> s.buses.none { it.id == id } } ?: return@edit s
        s.copy(buses = s.buses + MixBus(id = id, color = id % BusColors))
    }

    /** the last bus can't go: something has to take the song */
    fun removeBus(id: Int) = edit { s ->
        if (s.buses.size <= 1) return@edit s
        val left = s.buses.filter { it.id != id }.map { it.copy(sends = it.sends - id) }
        s.copy(buses = left)
    }

    fun renameBus(id: Int, name: String) = edit { s -> s.withBus(id) { it.copy(name = name.trim().take(24)) } }

    fun setBusColor(id: Int, color: Int) = edit { s -> s.withBus(id) { it.copy(color = color) } }

    fun setGain(id: Int, db: Float) = edit { s -> s.withBus(id) { it.copy(gainDb = db.coerceIn(MinGainDb, MaxGainDb)) } }

    fun setPan(id: Int, pan: Float) = edit { s -> s.withBus(id) { it.copy(pan = pan.coerceIn(-1f, 1f)) } }

    fun setMute(id: Int, mute: Boolean) = edit { s -> s.withBus(id) { it.copy(mute = mute) } }

    fun setSolo(id: Int, solo: Boolean) = edit { s -> s.withBus(id) { it.copy(solo = solo) } }

    fun setSongIn(id: Int, on: Boolean) = edit { s -> s.withBus(id) { it.copy(songIn = on) } }

    fun setFocus(id: Int, focus: BusFocus) = edit { s -> s.withBus(id) { it.copy(focus = focus) } }

    /** 0 removes the route; a route that would loop back is refused */
    fun setSend(from: Int, to: Int, level: Float) = edit { s ->
        if (level > 0f && !s.canSend(from, to)) return@edit s
        s.withBus(from) { b -> b.copy(sends = if (level <= 0f) b.sends - to else b.sends + (to to level.coerceIn(0f, 1f))) }
    }

    fun addSlot(busId: Int, kind: FxKind, script: String? = null, scriptName: String? = null): Long? {
        var added: Long? = null
        edit { s ->
            val bus = s.bus(busId) ?: return@edit s
            if (bus.slots.size >= MaxSlots) return@edit s
            val uid = s.nextUid
            added = uid
            val slot = MixSlot(uid = uid, kind = kind.key, values = kind.defaults, script = script, scriptName = scriptName)
            s.withBus(busId) { it.copy(slots = it.slots + slot) }.copy(nextUid = uid + 1)
        }
        return added
    }

    fun removeSlot(uid: Long) = edit { s ->
        fun on(bus: MixBus) = bus.copy(slots = bus.slots.filter { it.uid != uid })
        s.copy(buses = s.buses.map(::on), master = on(s.master))
    }

    fun moveSlot(busId: Int, from: Int, to: Int) = edit { s ->
        s.withBus(busId) { b ->
            if (from !in b.slots.indices || to !in b.slots.indices) return@withBus b
            b.copy(slots = b.slots.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun setBypass(uid: Long, bypass: Boolean) = edit { s -> s.withSlot(uid) { it.copy(bypass = bypass) } }

    fun setValue(uid: Long, index: Int, value: Float) {
        val slot = _state.value.slot(uid) ?: return
        val kind = kindFor(slot) ?: return
        val knob = kind.knobs.getOrNull(index) ?: return
        val values = kind.fit(slot.values).toMutableList().also { it[index] = knob.clamp(value) }
        setValues(uid, values)
    }

    fun setValues(uid: Long, values: List<Float>) {
        val slot = _state.value.slot(uid) ?: return
        val kind = kindFor(slot)
        edit { s -> s.withSlot(uid) { it.copy(values = kind?.fit(values) ?: values) } }
        // a script that only reads its knobs in @init has to run it again; while a knob is dragged that waits for a pause
        if (kind != null && !kind.liveKnobs) {
            reinitJobs.remove(uid)?.cancel()
            reinitJobs[uid] = scope.launch {
                delay(ReinitPauseMs)
                edit { s -> s.withSlot(uid) { it.copy(revision = it.revision + 1) } }
            }
        }
    }

    /** a script edited in the editor: new knobs start at their defaults, ones that kept their variable keep their value */
    fun setScript(uid: Long, text: String, name: String?) = edit { s ->
        s.withSlot(uid) { slot ->
            val old = script(slot.script.orEmpty()).knobs.map { it.variable }.zip(slot.values).toMap()
            val parsed = script(text)
            slot.copy(
                script = text,
                scriptName = name ?: slot.scriptName,
                values = parsed.knobs.map { old[it.variable] ?: it.default },
                revision = slot.revision + 1,
            )
        }
    }

    fun saveMix(name: String) {
        val preset = MixPreset("mix_${System.currentTimeMillis()}", name.trim().ifBlank { "Mix" }, _state.value.copy(presetId = null))
        _presets.update { it + preset }
        _state.update { it.copy(presetId = preset.id) }
        scheduleSave()
    }

    fun loadMix(id: String) {
        val preset = _presets.value.firstOrNull { it.id == id } ?: return
        // the switch stays where it is: picking a mix shouldn't turn the mixer on or off
        edit({ s -> preset.state.copy(enabled = s.enabled, presetId = id, nextUid = maxOf(s.nextUid, preset.state.nextUid)) }, keepPreset = true)
    }

    fun deleteMix(id: String) {
        _presets.update { list -> list.filter { it.id != id } }
        if (_state.value.presetId == id) _state.update { it.copy(presetId = null) }
        scheduleSave()
    }

    fun renameMix(id: String, name: String) {
        _presets.update { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }
        scheduleSave()
    }

    /** back to one bus taking the song, no effects */
    fun resetMix() = edit { s -> MixerState(enabled = s.enabled, nextUid = s.nextUid) }

    fun resume(uid: Long) {
        if (MixerNative.loaded) MixerNative.resume(uid)
    }

    private fun edit(transform: (MixerState) -> MixerState) = edit(transform, keepPreset = false)

    private fun edit(transform: (MixerState) -> MixerState, keepPreset: Boolean) {
        val before = _state.value
        val after = transform(before)
        if (after == before) return
        _state.value = if (keepPreset || after.presetId == null) after else after.copy(presetId = null)
        push()
        scheduleSave()
    }

    /** tells the engine what changed: controls always, the whole structure only when its shape did */
    private fun push() {
        val s = _state.value
        active = s.enabled && MixerNative.loaded
        if (!MixerNative.loaded) return
        val all = s.buses + s.master
        for (bus in all) {
            val controls = listOf(bus.gainDb, bus.pan, bus.mute, bus.solo)
            if (pushedBuses[bus.id] != controls) {
                MixerNative.busControl(bus.id, bus.gainDb, bus.pan, bus.mute, bus.solo)
                pushedBuses[bus.id] = controls
            }
        }
        for (bus in s.buses) {
            for (to in 0..MixerNative.Master) {
                val level = bus.sends[to] ?: 0f
                if (pushedSends[bus.id to to] != level) {
                    MixerNative.sendLevel(bus.id, to, level)
                    pushedSends[bus.id to to] = level
                }
            }
        }
        val resolved = all.associate { bus -> bus.id to bus.slots.mapNotNull { slot -> kindFor(slot)?.let { slot to it } } }
        wantsBitstream = resolved.values.flatten().any { (_, kind) -> kind.engineType == MixerKinds.Native }
        val shape = listOf(s.enabled) + all.map { bus ->
            listOf(bus.id, bus.songIn, bus.focus, bus.sendMask, resolved[bus.id].orEmpty().map { (slot, kind) -> listOf(slot.uid, kind.engineType, engineSource(slot, kind), slot.revision) })
        }
        if (shape != pushedShape) {
            commit(s, resolved)
            pushedShape = shape
            return
        }
        for ((slot, kind) in resolved.values.flatten()) {
            val values = kind.fit(slot.values)
            if (pushedValues[slot.uid] != values) {
                MixerNative.params(slot.uid, values.toFloatArray())
                pushedValues[slot.uid] = values
            }
            if (pushedBypass[slot.uid] != slot.bypass) {
                MixerNative.bypass(slot.uid, slot.bypass)
                pushedBypass[slot.uid] = slot.bypass
            }
        }
    }

    private fun commit(s: MixerState, resolved: Map<Int, List<Pair<MixSlot, FxKind>>>) {
        MixerNative.begin()
        fun describe(bus: MixBus) {
            MixerNative.bus(bus.id, bus.songIn, bus.focus.native, bus.sendMask, bus.isMaster)
            for ((slot, kind) in resolved[bus.id].orEmpty()) {
                val values = kind.fit(slot.values)
                MixerNative.slot(bus.id, slot.uid, kind.engineType, engineSource(slot, kind), kind.vars.toTypedArray(), slot.revision, values.toFloatArray())
                pushedValues[slot.uid] = values
            }
        }
        s.buses.forEach(::describe)
        describe(s.master)
        MixerNative.commit(s.enabled)
        for ((slot, _) in resolved.values.flatten()) {
            MixerNative.bypass(slot.uid, slot.bypass)
            pushedBypass[slot.uid] = slot.bypass
        }
    }

    private fun engineSource(slot: MixSlot, kind: FxKind): String = if (slot.isScript) slot.script.orEmpty() else kind.engineSource

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SaveDelayMs)
            val state = json.encodeToString(MixerState.serializer(), _state.value)
            val presets = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MixPreset.serializer()), _presets.value)
            withContext(Dispatchers.IO) { prefs.edit { putString("state", state).putString("presets", presets) } }
        }
    }

    private inline fun <reified T> load(key: String): T? = prefs.getString(key, null)?.let { text ->
        runCatching { json.decodeFromString<T>(text) }.onFailure { Timber.w(it, "Couldn't read the saved mixer $key") }.getOrNull()
    }

    companion object {
        const val MaxSlots = 16
        const val MinGainDb = -60f
        const val MaxGainDb = 12f
        /** how many strip colours there are to cycle through */
        const val BusColors = 8
        private const val SaveDelayMs = 600L
        private const val ReinitPauseMs = 140L
    }
}
