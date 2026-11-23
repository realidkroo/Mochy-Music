// pairs a signed code module with its manifest's look, player, settings and art so a code zip can ship a custom player and live surface

package com.mochyy.mochymusic.modules.code

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.LookModule
import com.mochyy.mochymusic.module.LyricsModule
import com.mochyy.mochymusic.module.MetadataModule
import com.mochyy.mochymusic.module.MochyModule
import com.mochyy.mochymusic.module.ModuleArt
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.module.ModuleFields
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SignInSpec
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.TrackMetadata
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.module.bridge.BridgedModule
import com.mochyy.mochymusic.modules.skin.ModuleSkin
import com.mochyy.mochymusic.modules.skin.SkinGlass
import com.mochyy.mochymusic.modules.skin.SkinParser
import com.mochyy.mochymusic.modules.skin.SkinSource
import com.mochyy.mochymusic.modules.skin.appliedLook
import com.mochyy.mochymusic.modules.web.FieldRange
import com.mochyy.mochymusic.modules.web.FieldRanges
import com.mochyy.mochymusic.modules.web.ManifestSettings
import com.mochyy.mochymusic.modules.web.WebModuleManifest
import com.mochyy.mochymusic.modules.web.resolveModuleArt
import com.mochyy.mochymusic.ui.player.styles.SurfaceSession
import java.io.File

internal class CodeSkinModule private constructor(
    private val inner: BridgedModule,
    private val manifest: WebModuleManifest,
    files: File?,
) : SourceModule, LyricsModule, LookModule, MetadataModule, ModuleArt, ModuleFields, SkinSource, FieldRanges, AutoCloseable {

    override val info: ModuleInfo get() = inner.info

    override val skin: ModuleSkin? = SkinParser.parse(
        inner.info.id, inner.info.name, manifest.player, manifest.font, manifest.pages,
        files, base = null, manifest.actions, manifest.glass, manifest.players, manifest.bar, manifest.mini,
        surface = inner.surface,
    )

    override val adds: Set<ModuleFeature> =
        if (skin != null || manifest.lookValues.isNotEmpty()) inner.adds + ModuleFeature.Look else inner.adds

    override val signIn: SignInSpec? get() = inner.signIn

    override val choices: List<ModuleChoice> get() = inner.choices

    override val icon: String? = resolveModuleArt(manifest.icon, files, base = null) ?: inner.icon

    override val banner: String? = resolveModuleArt(manifest.banner, files, base = null) ?: inner.banner

    private val settings = ManifestSettings(manifest)

    override val fields: List<ModuleField> get() = settings.fields.ifEmpty { inner.fields }

    override val ranges: Map<String, FieldRange> get() = settings.ranges

    override fun onFields(values: Map<String, String>) {
        settings.values = values
        inner.onFields(values)
    }

    override val look: Map<String, String>
        get() = appliedLook(inner.look + settings.look(manifest.lookValues), skin)

    override val glass: SkinGlass? get() = settings.glass(skin?.glass)

    override fun attach(host: ModuleHost) = inner.attach(host)

    override fun accountState(): AccountState = inner.accountState()

    override fun onSignInHeader(name: String, value: String, url: String): Boolean = inner.onSignInHeader(name, value, url)

    override suspend fun verifyAccount(): AccountState = inner.verifyAccount()

    override fun signOut() = inner.signOut()

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult = inner.resolve(track, picks)

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult =
        inner.diagnose(track, picks, report)

    override suspend fun lyrics(track: TrackQuery): String? = inner.lyrics(track)

    override suspend fun metadata(track: TrackQuery): TrackMetadata? = inner.metadata(track)

    override fun close() {
        SurfaceSession.release(inner.surface)
        inner.close()
    }

    companion object {
        /** returns [inner] untouched when the manifest has no skin, look or art of its own (so older code zips like js modules stay as-is) */
        fun wrap(inner: BridgedModule, manifest: WebModuleManifest?, files: File?): MochyModule {
            if (manifest == null) return inner
            val hasExtras = manifest.changesLook || manifest.icon != null || manifest.banner != null || manifest.settings.isNotEmpty()
            return if (hasExtras) CodeSkinModule(inner, manifest, files) else inner
        }
    }
}
