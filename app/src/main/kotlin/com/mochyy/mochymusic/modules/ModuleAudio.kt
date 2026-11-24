// the mixer effects a module brings: liveprog scripts from its own files, and for a module signed with mochy's key a native library (modules/api/native/mochy_fx.h)

package com.mochyy.mochymusic.modules

import com.mochyy.mochymusic.eq.mixer.FxKind
import com.mochyy.mochymusic.eq.mixer.ModuleProcessors
import com.mochyy.mochymusic.modules.security.ModuleSanitizer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import java.io.File

/** a module whose manifest lists effects for the mixer */
internal interface AudioModule {
    val audio: ModuleAudio?
}

/**
 * the manifest's "audio": a list of processors (or an object holding one under "processors"), each
 * {"id", "name", "category", "description", "script": "path/in/zip.eel", "presets": [{"name", "values": {knob: value}}]}
 * or, for a module with its own code, {"id", "native": "libsomething.so"} listing every processor that library exports
 */
internal class ModuleAudio(
    private val moduleId: String,
    private val moduleName: String,
    private val spec: JsonElement,
    private val files: File?,
    private val nativeDir: File?,
) {
    /** the effects as mixer kinds; reads scripts and opens libraries, so the first read happens off the main thread */
    val kinds: List<FxKind> by lazy { read() }

    private fun read(): List<FxKind> {
        val list = when (spec) {
            is JsonArray -> spec
            is JsonObject -> spec["processors"] as? JsonArray
            else -> null
        } ?: return emptyList()
        return list.take(MaxProcessors).flatMap { element -> (element as? JsonObject)?.let(::processor).orEmpty() }.distinctBy { it.key }
    }

    private fun processor(o: JsonObject): List<FxKind> {
        val id = o.text("id")?.takeIf { it.matches(IdPattern) } ?: return emptyList()
        o.text("native")?.let { return nativeKinds(it) }
        val script = o.text("script")?.let(::readScript) ?: return emptyList()
        val presets = (o["presets"] as? JsonArray).orEmpty().mapNotNull { p ->
            val preset = p as? JsonObject ?: return@mapNotNull null
            val name = preset.text("name") ?: return@mapNotNull null
            val values = (preset["values"] as? JsonObject).orEmpty().mapNotNull { (knob, v) -> (v as? JsonPrimitive)?.floatOrNull?.let { knob to it } }.toMap()
            name to values
        }
        return listOf(ModuleProcessors.scriptKind(moduleId, moduleName, id, o.text("name") ?: id, o.text("category"), o.text("description"), script, presets))
    }

    // only scripts the zip brought, inside the module's own folder
    private fun readScript(path: String): String? {
        val folder = files ?: return null
        val parts = path.trim('/').split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        val file = ModuleSanitizer.safeChild(folder, *parts.toTypedArray()) ?: return null
        if (!file.isFile || file.length() > MaxScriptBytes) return null
        return runCatching { file.readText() }.getOrNull()
    }

    // only a module with its own code has a native folder, and that code is signed with mochy's key
    private fun nativeKinds(library: String): List<FxKind> {
        val dir = nativeDir ?: return emptyList()
        if ('/' in library || '\\' in library || library.startsWith(".") || !library.endsWith(".so")) return emptyList()
        val file = File(dir, library).takeIf { it.isFile } ?: return emptyList()
        return ModuleProcessors.nativeKinds(moduleId, moduleName, file.path)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        private const val MaxProcessors = 32
        private const val MaxScriptBytes = 512L * 1024
        private val IdPattern = Regex("[A-Za-z0-9._-]{1,64}")
    }
}
