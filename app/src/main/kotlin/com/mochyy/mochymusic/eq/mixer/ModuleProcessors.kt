// ModuleProcessors.kt

package com.mochyy.mochymusic.eq.mixer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** the audio processors switched-on modules bring, as mixer kinds; the module registry fills it in */
@Singleton
class ModuleProcessors @Inject constructor() {

    private val byModule = MutableStateFlow<Map<String, List<FxKind>>>(emptyMap())

    /** every module's processors, in module order */
    val kinds: StateFlow<Map<String, List<FxKind>>> = byModule.asStateFlow()

    fun all(): List<FxKind> = byModule.value.values.flatten()

    fun find(key: String): FxKind? = byModule.value.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.key == key } }

    /** drops every module not in [ids]: one removed takes its effects with it */
    fun keepOnly(ids: Set<String>) {
        byModule.update { map -> map.filterKeys { it in ids } }
    }

    /** replaces everything [moduleId] offers, an empty list takes them all away */
    fun publish(moduleId: String, kinds: List<FxKind>) {
        byModule.update { if (kinds.isEmpty()) it - moduleId else it + (moduleId to kinds) }
    }

    companion object {
        /** a script a module brings: its knobs come from the script itself, its presets from the manifest by knob name */
        fun scriptKind(
            moduleId: String,
            moduleName: String,
            processorId: String,
            name: String,
            category: String?,
            description: String?,
            script: String,
            presets: List<Pair<String, Map<String, Float>>>,
        ): FxKind {
            val parsed = LiveprogScript.parse(script)
            val base = parsed.kind(name)
            return base.copy(
                key = "${MixerKinds.ModulePrefix}$moduleId/$processorId",
                category = category?.takeIf { it.isNotBlank() } ?: moduleName,
                description = description?.takeIf { it.isNotBlank() } ?: base.description,
                presets = presets.map { (presetName, values) -> KindPreset(presetName, base.knobs.map { values[it.id] ?: it.default }) },
                origin = KindOrigin.Module(moduleId, moduleName),
                engineSource = script,
            )
        }

        /** a module's native library: opened once, every processor it lists becomes a kind; empty when it isn't one */
        fun nativeKinds(moduleId: String, moduleName: String, libraryPath: String): List<FxKind> {
            if (!MixerNative.loaded) return emptyList()
            val json = runCatching { MixerNative.loadLibrary(libraryPath) }.getOrNull() ?: return emptyList()
            return FxKind.parseAll(json, KindOrigin.Module(moduleId, moduleName), MixerKinds.Native).map { kind ->
                kind.copy(category = kind.category.ifBlank { moduleName })
            }
        }
    }
}
