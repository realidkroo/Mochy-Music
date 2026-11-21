// AudioLabViewModel.kt

package com.mochyy.mochymusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.eq.AudioLabStore
import com.mochyy.mochymusic.eq.AutoEqCorrection
import com.mochyy.mochymusic.eq.EqPresets
import com.mochyy.mochymusic.eq.EqSettings
import com.mochyy.mochymusic.eq.audio.Biquad
import com.mochyy.mochymusic.eq.autoeq.AutoEqEntry
import com.mochyy.mochymusic.eq.autoeq.AutoEqFitter
import com.mochyy.mochymusic.eq.autoeq.AutoEqMeasurement
import com.mochyy.mochymusic.eq.autoeq.AutoEqModel
import com.mochyy.mochymusic.eq.autoeq.AutoEqRepository
import com.mochyy.mochymusic.eq.autoeq.AutoEqTuning
import com.mochyy.mochymusic.eq.data.FilterType
import com.mochyy.mochymusic.eq.data.ParametricEQBand
import com.mochyy.mochymusic.eq.stage.StageListener
import com.mochyy.mochymusic.eq.stage.StageNative
import com.mochyy.mochymusic.eq.stage.StageObject
import com.mochyy.mochymusic.eq.stage.StagePresets
import com.mochyy.mochymusic.eq.stage.StageSettings
import com.mochyy.mochymusic.eq.stage.StageSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

sealed interface AutoEqIndex {
    data object Loading : AutoEqIndex
    data class Failed(val message: String) : AutoEqIndex
    data class Ready(val models: List<AutoEqModel>) : AutoEqIndex
}

/** Every Audio Lab page: the equalizer's editing, its presets, the AutoEq database and Audio FX. */
@HiltViewModel
class AudioLabViewModel @Inject constructor(
    val store: AudioLabStore,
    private val autoEq: AutoEqRepository,
    /** what the effects are doing right now, for the pictures' meters */
    val fx: com.mochyy.mochymusic.eq.AudioFxService,
    /** the providers and their order, for the hub's quality tile */
    val sources: com.mochyy.mochymusic.modules.SourceModules,
) : ViewModel() {

    val eq = store.eq
    val presets = store.presets
    val fxPresets = store.fxPresets
    val fxPresetId = store.fxPresetId
    val reverb = store.reverb
    val loudness = store.loudness
    val spatial = store.spatial
    val eightD = store.eightD
    val playback = store.playback
    val volume = store.volume
    val normalize = store.normalize
    val stage = store.stage
    val stageOutput = store.stageOutput

    // ---- the user's curve -------------------------------------------------------------------

    fun setEnabled(enabled: Boolean) = store.updateEq { it.copy(enabled = enabled) }

    fun updateNode(index: Int, transform: (ParametricEQBand) -> ParametricEQBand) = store.editEqCurve { s ->
        if (index !in s.nodes.indices) s
        else s.copy(nodes = s.nodes.toMutableList().also { it[index] = transform(it[index]) })
    }

    /** adds a band at [frequency], returning where it went */
    fun addNode(frequency: Double, gain: Double = 0.0): Int {
        var index = -1
        store.editEqCurve { s ->
            if (s.nodes.size >= MaxNodes) return@editEqCurve s
            val node = ParametricEQBand(frequency = frequency.coerceIn(20.0, 20_000.0), gain = gain, q = 1.0, filterType = FilterType.PK)
            index = s.nodes.size
            s.copy(nodes = s.nodes + node)
        }
        return index
    }

    fun removeNode(index: Int) = store.editEqCurve { s ->
        if (s.nodes.size <= 1 || index !in s.nodes.indices) s
        else s.copy(nodes = s.nodes.filterIndexed { i, _ -> i != index })
    }

    fun applyPreset(id: String) {
        val builtIn = EqPresets.BuiltIn.firstOrNull { it.id == id }
        val user = presets.value.firstOrNull { it.id == id }
        store.updateEq { s ->
            when {
                builtIn != null -> s.copy(nodes = builtIn.nodes, presetId = id)
                user != null -> s.copy(nodes = user.nodes, preamp = user.preamp, presetId = id)
                else -> s
            }
        }
    }

    fun reset() = store.updateEq { it.copy(nodes = EqPresets.flatNodes(), preamp = 0.0, presetId = EqPresets.FlatId) }

    fun savePreset(name: String) {
        if (name.isNotBlank()) store.savePreset(name)
    }

    fun renamePreset(id: String, name: String) {
        if (name.isNotBlank()) store.renamePreset(id, name)
    }

    fun deletePreset(id: String) = store.deletePreset(id)

    fun setPreamp(db: Double) = store.setEqPreamp(db)

    fun setAutoPreamp(on: Boolean) = store.updateEq { it.copy(autoPreamp = on) }

    fun autoHeadroom(settings: EqSettings): Double = store.autoHeadroom(settings)

    // ---- AutoEq -----------------------------------------------------------------------------

    private val _index = MutableStateFlow<AutoEqIndex>(AutoEqIndex.Loading)
    val index: StateFlow<AutoEqIndex> = _index.asStateFlow()

    val query = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    val results: StateFlow<List<AutoEqModel>> = combine(_index, query.debounce(120)) { idx, q ->
        if (idx is AutoEqIndex.Ready) AutoEqRepository.search(idx.models, q) else emptyList()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** the picked headphone's measurement, for the graph */
    private val _measurement = MutableStateFlow<AutoEqMeasurement?>(null)
    val measurement: StateFlow<AutoEqMeasurement?> = _measurement.asStateFlow()

    /** an entry being fetched or a fit being worked out */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var indexJob: Job? = null
    private var fitJob: Job? = null

    /** the AutoEq tab opened: the list, and the current headphone's curve for the graph */
    fun openAutoEq() {
        if (_index.value !is AutoEqIndex.Ready && indexJob?.isActive != true) loadIndex(refresh = false)
        val current = eq.value.correction
        if (current != null && _measurement.value?.entry?.path != current.path) {
            viewModelScope.launch {
                runCatching { autoEq.measurement(current.toEntry()) }.onSuccess { _measurement.value = it }
            }
        }
    }

    fun loadIndex(refresh: Boolean) {
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            if (_index.value !is AutoEqIndex.Ready) _index.value = AutoEqIndex.Loading
            _index.value = runCatching { AutoEqIndex.Ready(autoEq.models(refresh)) }
                .getOrElse { AutoEqIndex.Failed(it.message ?: "Couldn't reach the AutoEq database") }
        }
    }

    fun select(entry: AutoEqEntry) {
        fitJob?.cancel()
        fitJob = viewModelScope.launch {
            _busy.value = entry.path
            _error.value = null
            runCatching {
                val m = autoEq.measurement(entry)
                val tuning = AutoEqTuning()
                val fit = withContext(Dispatchers.Default) { AutoEqFitter.correction(m, tuning) }
                _measurement.value = m
                store.updateEq {
                    it.copy(
                        enabled = true,
                        correctionEnabled = true,
                        correction = AutoEqCorrection(
                            model = entry.name,
                            source = entry.source,
                            rig = entry.rig,
                            path = entry.path,
                            form = entry.form,
                            tuning = tuning,
                            filters = fit.bands,
                            preamp = fit.preamp,
                        ),
                    )
                }
            }.onFailure { _error.value = "Couldn't load ${entry.name}. Check your connection." }
            _busy.value = null
        }
    }

    fun tune(transform: (AutoEqTuning) -> AutoEqTuning) {
        val current = eq.value.correction ?: return
        val m = _measurement.value ?: return
        val tuning = transform(current.tuning)
        // the sliders move first; the fit follows as soon as the finger rests
        store.updateEq { it.copy(correction = current.copy(tuning = tuning)) }
        fitJob?.cancel()
        fitJob = viewModelScope.launch {
            kotlinx.coroutines.delay(140)
            _busy.value = "fit"
            val fit = withContext(Dispatchers.Default) { AutoEqFitter.correction(m, tuning) }
            store.updateEq { s ->
                val c = s.correction ?: return@updateEq s
                if (c.path != current.path) s else s.copy(correction = c.copy(tuning = tuning, filters = fit.bands, preamp = fit.preamp))
            }
            _busy.value = null
        }
    }

    fun setCorrectionEnabled(on: Boolean) = store.updateEq { it.copy(correctionEnabled = on, enabled = it.enabled || on) }

    fun clearCorrection() {
        _measurement.value = null
        store.updateEq { it.copy(correction = null) }
    }

    // ---- Audio FX ---------------------------------------------------------------------------

    fun updateReverb(transform: (com.mochyy.mochymusic.eq.ReverbSettings) -> com.mochyy.mochymusic.eq.ReverbSettings) =
        store.updateReverb(transform)

    fun updateLoudness(transform: (com.mochyy.mochymusic.eq.LoudnessSettings) -> com.mochyy.mochymusic.eq.LoudnessSettings) =
        store.updateLoudness(transform)

    fun updateSpatial(transform: (com.mochyy.mochymusic.eq.SpatialSettings) -> com.mochyy.mochymusic.eq.SpatialSettings) =
        store.updateSpatial(transform)

    fun updateEightD(transform: (com.mochyy.mochymusic.eq.EightDSettings) -> com.mochyy.mochymusic.eq.EightDSettings) =
        store.updateEightD(transform)

    fun updatePlayback(transform: (com.mochyy.mochymusic.eq.PlaybackSettings) -> com.mochyy.mochymusic.eq.PlaybackSettings) =
        store.updatePlayback(transform)

    fun updateVolume(transform: (com.mochyy.mochymusic.eq.VolumeSettings) -> com.mochyy.mochymusic.eq.VolumeSettings) =
        store.updateVolume(transform)

    fun updateNormalize(transform: (com.mochyy.mochymusic.eq.NormalizeSettings) -> com.mochyy.mochymusic.eq.NormalizeSettings) =
        store.updateNormalize(transform)

    fun saveFxPreset(name: String) {
        if (name.isNotBlank()) store.saveFxPreset(name)
    }

    fun applyFxPreset(id: String) = store.applyFxPreset(id)

    fun releaseFxPreset() = store.releaseFxPreset()

    fun renameFxPreset(id: String, name: String) {
        if (name.isNotBlank()) store.renameFxPreset(id, name)
    }

    fun deleteFxPreset(id: String) = store.deleteFxPreset(id)

    // ---- 3D Sims ----------------------------------------------------------------------------

    fun updateStage(transform: (StageSettings) -> StageSettings) = store.updateStage(transform)

    /** moves or changes one thing in the room; the preset it came from no longer matches */
    fun updateStageObject(id: Int, transform: (StageObject) -> StageObject) = store.updateStage { s ->
        s.copy(objects = s.objects.map { if (it.id == id) transform(it) else it }, presetId = null)
    }

    fun updateListener(transform: (StageListener) -> StageListener) = store.updateStage { s ->
        s.copy(listener = transform(s.listener), presetId = null)
    }

    /** another copy of a part, a little to the side of the last one; null when all four of that kind are in the room */
    fun addStageObject(source: StageSource): Int? {
        var added: Int? = null
        store.updateStage { s ->
            val same = s.objects.filter { it.source == source }
            if (same.size >= StageNative.Copies) return@updateStage s
            val last = same.lastOrNull()
            val id = (s.objects.maxOfOrNull { it.id } ?: -1) + 1
            val x = last?.let { if (it.x > 0.5f) it.x - 0.4f else it.x + 0.4f } ?: 0f
            added = id
            val copy = last?.copy(id = id, x = x, solo = false) ?: StageObject(id, source, x, if (source == StageSource.Sub) 0.05f else 0.4f, 0.5f)
            s.copy(objects = s.objects + copy, presetId = null)
        }
        return added
    }

    /** a copy can go; the last one of each part always keeps its place */
    fun removeStageObject(id: Int) = store.updateStage { s ->
        val target = s.objects.firstOrNull { it.id == id } ?: return@updateStage s
        if (s.objects.count { it.source == target.source } <= 1) s
        else s.copy(objects = s.objects - target, presetId = null)
    }

    fun applyStagePreset(id: String) {
        val preset = StagePresets.All.firstOrNull { it.id == id } ?: return
        store.updateStage { StagePresets.apply(it, preset) }
    }

    /** null goes back to guessing */
    fun setOutputKind(key: String, speaker: Boolean?) = store.updateStage { s ->
        s.copy(outputKinds = if (speaker == null) s.outputKinds - key else s.outputKinds + (key to speaker))
    }

    companion object {
        const val MaxNodes = 16
    }
}

private fun AutoEqCorrection.toEntry() = AutoEqEntry(model, source, rig, path, form)

/** how a node's frequency reads on a chip: 31Hz, 120Hz, 1.0k, 16k */
fun frequencyLabel(hz: Double): String = when {
    hz >= 10_000 -> "${(hz / 1000).toInt()}k"
    hz >= 1000 -> String.format("%.1fk", hz / 1000)
    else -> "${hz.toInt()}Hz"
}

fun gainLabel(db: Double): String = (if (db > 0.04) "+" else if (db < -0.04) "−" else "") + String.format("%.1f dB", abs(db))

fun filterTypeLabel(type: FilterType): String = when (type) {
    FilterType.PK -> "Peak"
    FilterType.LSC -> "Low shelf"
    FilterType.HSC -> "High shelf"
    FilterType.LPQ -> "Low pass"
    FilterType.HPQ -> "High pass"
}

/** whether a band is doing anything, for the "On · Custom" line */
fun EqSettings.isFlat(): Boolean = nodes.none(Biquad::isAudible) && preamp == 0.0 &&
    (!correctionEnabled || correction == null)
