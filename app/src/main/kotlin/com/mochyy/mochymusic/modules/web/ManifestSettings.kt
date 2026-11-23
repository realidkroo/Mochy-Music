// a link or zip module's settings while it runs: the fields its page shows, what people picked, and the look and glass those picks bring

package com.mochyy.mochymusic.modules.web

import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.modules.security.ModuleUrlGuard
import com.mochyy.mochymusic.modules.skin.SkinGlass
import java.io.File

internal class ManifestSettings(manifest: WebModuleManifest, skip: Set<String> = emptySet()) {

    private val settings = manifest.settings.filter { it.key.isNotBlank() && it.key.lowercase() !in skip }

    val fields: List<ModuleField> = settings.map { it.toField() }

    /** the settings drawn as sliders, by key */
    val ranges: Map<String, FieldRange> = settings.mapNotNull { setting -> setting.range?.let { setting.key to it } }.toMap()

    @Volatile
    var values: Map<String, String> = emptyMap()

    /** what each setting is right now, its default until someone changes it */
    fun current(): Map<String, String> = fields.associate { field -> field.id to (values[field.id] ?: field.default) }

    /** the looks the picked options and switched-on toggles bring, laid over [base] in the manifest's order */
    fun look(base: Map<String, String>): Map<String, String> {
        val now = current()
        return settings.fold(base) { look, setting -> look + setting.lookAt(now[setting.key]) }
    }

    /** the module's glass with whatever the picked options and switched-on toggles change about it, in the manifest's order */
    fun glass(base: SkinGlass?): SkinGlass? {
        base ?: return null
        val now = current()
        return settings.fold(base) { glass, setting -> setting.glassAt(now[setting.key])?.let(glass::with) ?: glass }
    }
}

/** a slider's ends and its notch, [step] null for a smooth one */
data class FieldRange(val start: Float, val end: Float, val step: Float?)

/** a module whose settings include sliders, which mochy draws from its manifest; the module api itself only knows text, lists and switches */
internal interface FieldRanges {
    val ranges: Map<String, FieldRange>
}

internal fun resolveModulePath(path: String?, files: File?, base: String?): Any? {
    val name = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (name.startsWith("https://") || name.startsWith("http://")) {
        return name.takeIf { ModuleUrlGuard.isSafeRemoteUrl(it) }
    }
    if ('\\' in name || '\u0000' in name) return null
    val inside = name.trimStart('/').takeIf { p -> p.isNotEmpty() && p.split('/').none { it == ".." || it == "." || it.isEmpty() } } ?: return null
    if (files != null) {
        val candidate = File(files, inside)
        val rootPath = runCatching { files.canonicalPath }.getOrNull() ?: return null
        val targetPath = runCatching { candidate.canonicalPath }.getOrNull() ?: return null
        return candidate.takeIf { targetPath.startsWith(rootPath.trimEnd(File.separatorChar) + File.separatorChar) }
    }
    return base?.let { "$it/$inside" }?.takeIf { ModuleUrlGuard.isSafeRemoteUrl(it) }
}

/** the same as text, the way the module api carries a picture: a link, or a file's own path */
internal fun resolveModuleArt(path: String?, files: File?, base: String?): String? = when (val found = resolveModulePath(path, files, base)) {
    is File -> found.path
    is String -> found
    else -> null
}
