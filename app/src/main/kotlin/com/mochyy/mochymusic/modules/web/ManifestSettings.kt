// a link or zip module's settings while it runs: the fields its page shows, what people picked, and the look and glass those picks bring

package com.mochyy.mochymusic.modules.web

import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.modules.skin.SkinGlass
import java.io.File

internal class ManifestSettings(manifest: WebModuleManifest, skip: Set<String> = emptySet()) {

    private val settings = manifest.settings.filter { it.key.isNotBlank() && it.key.lowercase() !in skip }

    val fields: List<ModuleField> = settings.map { it.toField() }

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

/** a picture or font a module names: a link as it is, a path from the module's zip, or a path next to a link module's manifest */
internal fun resolveModulePath(path: String?, files: File?, base: String?): Any? {
    val name = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (name.startsWith("https://") || name.startsWith("http://")) return name
    // plain string work, no disk: this runs while things draw, and a path can't climb out of the module's folder
    val inside = name.trimStart('/').takeIf { ".." !in it.split('/') } ?: return null
    if (files != null) return File(files, inside)
    return base?.let { "$it/$inside" }
}

/** the same as text, the way the module api carries a picture: a link, or a file's own path */
internal fun resolveModuleArt(path: String?, files: File?, base: String?): String? = when (val found = resolveModulePath(path, files, base)) {
    is File -> found.path
    is String -> found
    else -> null
}
