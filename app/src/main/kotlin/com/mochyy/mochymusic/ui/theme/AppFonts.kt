package com.mochyy.mochymusic.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.constants.AppFontModeKey
import com.mochyy.mochymusic.constants.CustomFontIdKey
import com.mochyy.mochymusic.constants.CustomFontNameKey
import com.mochyy.mochymusic.constants.FontCustomizerConfigKey
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

internal object AppFonts {
    @Immutable
    data class CustomFaceRecord(
        val fileId: String,
        val ext: String,
        val weight: Int,
        val isItalic: Boolean,
        val varWeightMin: Float?,
        val varWeightMax: Float?,
        val varItalic: Boolean,
        val varSlant: Float?,
    )

    @Immutable
    data class CustomFontRecord(
        val id: String,
        val displayName: String,
        val familyKey: String,
        val faces: List<CustomFaceRecord>,
    ) {
        fun capabilities(): FontCapabilities {
            var light = false
            var bold = false
            var extraBold = false
            var italic = false
            val weights = HashSet<Int>(faces.size)
            var hasUpright = false
            var hasItalicFace = false

            for (face in faces) {
                val minW = face.varWeightMin
                val maxW = face.varWeightMax
                if (minW != null && maxW != null) {
                    if (minW <= 320f) light = true
                    if (maxW >= 650f && minW < 650f) bold = true
                    if (maxW >= 780f && minW < 780f) extraBold = true
                }
                if (face.varItalic || face.varSlant != null) italic = true
                weights.add(face.weight)
                if (face.isItalic) hasItalicFace = true else hasUpright = true
            }
            if (weights.size > 1) {
                if (weights.any { it <= 350 }) light = true
                if (weights.any { it >= 650 }) bold = true
                if (weights.any { it >= 780 }) extraBold = true
            }
            if (hasUpright && hasItalicFace) italic = true
            return FontCapabilities(
                supportsBold = bold,
                supportsExtraBold = extraBold,
                supportsItalic = italic,
                supportsLight = light,
            )
        }

        val isVariable: Boolean
            get() = faces.any { it.varWeightMin != null || it.varItalic || it.varSlant != null }
    }

    @Immutable
    data class Config(
        val global: RoleFontConfig = RoleFontConfig(fontId = BuiltinInterId),
        val heading: RoleFontConfig = RoleFontConfig(fontId = FollowGlobalFontId),
        val subheading: RoleFontConfig = RoleFontConfig(fontId = FollowGlobalFontId),
        val customFonts: List<CustomFontRecord> = emptyList(),
    ) {
        fun roleConfig(role: FontRole): RoleFontConfig = when (role) {
            FontRole.HEADING -> heading
            FontRole.SUBHEADING -> subheading
            FontRole.GLOBAL -> global
        }

        fun withRole(role: FontRole, next: RoleFontConfig): Config = when (role) {
            FontRole.HEADING -> copy(heading = next)
            FontRole.SUBHEADING -> copy(subheading = next)
            FontRole.GLOBAL -> copy(global = next)
        }
    }

    @Immutable
    data class State(
        val config: Config = Config(),
        val options: List<FontOption> = defaultBuiltinOptions(DefaultSystemCaps),
        val families: Map<String, FontFamily> = emptyMap(),
        val ready: Boolean = false,
    )

    enum class Error { InvalidFont, TooLarge, Storage }

    private data class BuiltinSystemCaps(
        val system: FontCapabilities,
        val serif: FontCapabilities,
        val mono: FontCapabilities,
    )

    private val InterCaps = FontCapabilities(
        supportsBold = true,
        supportsExtraBold = true,
        supportsItalic = false,
        supportsLight = true,
    )

    private val BbhBartleCaps = FontCapabilities(
        supportsBold = false,
        supportsExtraBold = false,
        supportsItalic = false,
        supportsLight = false,
    )

    private val DefaultSystemCaps = BuiltinSystemCaps(
        system = FontCapabilities(supportsBold = true, supportsExtraBold = true, supportsItalic = true, supportsLight = true),
        serif = FontCapabilities(supportsBold = true, supportsExtraBold = false, supportsItalic = true, supportsLight = false),
        mono = FontCapabilities(supportsBold = false, supportsExtraBold = false, supportsItalic = false, supportsLight = false),
    )

    var state by mutableStateOf(State())
        private set

    private val lock = Mutex()
    private val loadedFamilies = HashMap<String, Pair<String, FontFamily>>()
    private var probedSystemCaps: BuiltinSystemCaps? = null

    // cached resolved bundle keyed on state + look font so composition reads do zero allocations
    @Volatile private var lastResolvedState: State? = null
    @Volatile private var lastResolvedLook: FontFamily? = null
    @Volatile private var cachedResolved: ResolvedFonts = buildResolved(State(), null)

    fun resolvedFonts(): ResolvedFonts {
        val currentState = state
        val currentLook = LookFont.family
        if (currentState === lastResolvedState && currentLook === lastResolvedLook) {
            return cachedResolved
        }
        val next = buildResolved(currentState, currentLook)
        lastResolvedState = currentState
        lastResolvedLook = currentLook
        cachedResolved = next
        return next
    }

    fun resolvedFor(role: FontRole): ResolvedRoleFont = resolvedFonts().forRole(role)

    // returns the font family for a specific option id, used by previews and the picker list
    fun familyForOption(fontId: String): FontFamily =
        familyForId(fontId, state.families, LookFont.family)

    fun capabilitiesForOption(fontId: String): FontCapabilities =
        state.options.firstOrNull { it.id == fontId }?.capabilities ?: InterCaps

    fun effectiveFontId(role: FontRole, config: Config = state.config): String {
        val raw = config.roleConfig(role).fontId
        return if (role != FontRole.GLOBAL && raw == FollowGlobalFontId) config.global.fontId else raw
    }

    fun start(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val caps = probeSystemCapabilities()
            probedSystemCaps = caps
            val store = context.dataStore
            store.data.map { it.storedSnapshot() }.distinctUntilChanged()
                .onEach {
                    lock.withLock {
                        val snapshot = store.data.first().storedSnapshot()
                        val config = resolveConfig(context, snapshot)
                        publish(context, config, caps)
                        cleanFiles(context, config)
                    }
                }
                .retryWhen { error, _ ->
                    if (error !is IOException) return@retryWhen false
                    delay(1_000)
                    true
                }
                .collect {}
        }
    }

    suspend fun updateRole(context: Context, role: FontRole, transform: (RoleFontConfig) -> RoleFontConfig): Error? = operate {
        val store = context.dataStore
        val current = resolveConfig(context, store.data.first().storedSnapshot())
        val updatedRole = transform(current.roleConfig(role))
        val sanitizedRole = sanitizeRole(role, updatedRole, current)
        val next = current.withRole(role, sanitizedRole)
        currentCoroutineContext().ensureActive()
        persistAndPublish(context, next)
    }

    suspend fun resetRole(context: Context, role: FontRole): Error? = updateRole(context, role) {
        RoleFontConfig(fontId = if (role == FontRole.GLOBAL) BuiltinInterId else FollowGlobalFontId)
    }

    suspend fun importFont(context: Context, uri: Uri, assignToRole: FontRole = FontRole.GLOBAL): Error? = operate {
        val resolver = context.contentResolver
        val rawName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: throw InvalidFontException()
        if (!isSupportedFontName(rawName)) throw InvalidFontException()

        val ext = if (rawName.endsWith(".otf", ignoreCase = true)) "otf" else "ttf"
        val directory = directory(context)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create font directory")

        val fileId = UUID.randomUUID().toString()
        val destination = File(directory, "$fileId.$ext")
        val temporary = File.createTempFile("import-", ".tmp", directory)
        var committed = false
        try {
            val jobContext = currentCoroutineContext()
            resolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    input.copyFontTo(output, checkCancellation = { jobContext.ensureActive() })
                    output.fd.sync()
                }
            } ?: throw IOException("Could not open selected font")

            validateTypeface(temporary)
            val meta = FontMetadataParser.parse(temporary)
            if (!temporary.renameTo(destination)) throw IOException("Could not finalize font")

            val fallbackName = rawName.substringBeforeLast('.').filterNot { it.isISOControl() }.trim().take(120).ifBlank { "Custom Font" }
            val displayName = meta.familyName?.takeIf { it.isNotBlank() } ?: fallbackName
            val familyKey = displayName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim().ifBlank { fileId }
            val newFace = CustomFaceRecord(
                fileId = fileId,
                ext = ext,
                weight = meta.weight,
                isItalic = meta.isItalic,
                varWeightMin = meta.variableWeightMin,
                varWeightMax = meta.variableWeightMax,
                varItalic = meta.variableItalic,
                varSlant = meta.variableSlant,
            )

            val store = context.dataStore
            val current = resolveConfig(context, store.data.first().storedSnapshot())
            // group static faces sharing a family name so importing regular + bold + italic unlocks B and i together
            val existingIndex = if (!meta.isVariable) {
                current.customFonts.indexOfFirst { it.familyKey == familyKey && !it.isVariable }
            } else {
                -1
            }

            val updatedFonts = current.customFonts.toMutableList()
            val targetFontId: String
            if (existingIndex >= 0) {
                val existing = updatedFonts[existingIndex]
                val keptFaces = existing.faces.filterNot { it.weight == newFace.weight && it.isItalic == newFace.isItalic }
                updatedFonts[existingIndex] = existing.copy(faces = keptFaces + newFace)
                targetFontId = existing.id
            } else {
                val newId = "custom:$fileId"
                updatedFonts.add(
                    CustomFontRecord(
                        id = newId,
                        displayName = displayName,
                        familyKey = familyKey,
                        faces = listOf(newFace),
                    ),
                )
                targetFontId = newId
            }

            val next = current.copy(customFonts = updatedFonts).withRole(
                assignToRole,
                current.roleConfig(assignToRole).copy(fontId = targetFontId),
            )
            val sanitized = sanitizeAllRoles(next)
            jobContext.ensureActive()
            withContext(NonCancellable) {
                committed = true
                persistAndPublish(context, sanitized)
                cleanFiles(context, sanitized)
            }
        } finally {
            temporary.delete()
            if (!committed) destination.delete()
        }
    }

    suspend fun deleteCustomFont(context: Context, fontId: String): Error? = operate {
        val store = context.dataStore
        val current = resolveConfig(context, store.data.first().storedSnapshot())
        val remaining = current.customFonts.filterNot { it.id == fontId }
        val next = current.copy(
            global = if (current.global.fontId == fontId) RoleFontConfig(BuiltinInterId) else current.global,
            heading = if (current.heading.fontId == fontId) RoleFontConfig(FollowGlobalFontId) else current.heading,
            subheading = if (current.subheading.fontId == fontId) RoleFontConfig(FollowGlobalFontId) else current.subheading,
            customFonts = remaining,
        )
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            persistAndPublish(context, next)
            cleanFiles(context, next)
        }
    }

    private suspend fun persistAndPublish(context: Context, config: Config) {
        val encoded = encodeConfig(config)
        val caps = probedSystemCaps ?: probeSystemCapabilities().also { probedSystemCaps = it }
        withContext(NonCancellable) {
            context.dataStore.edit { prefs ->
                prefs[FontCustomizerConfigKey] = encoded
                // clear legacy keys once new config is persisted so old state never shadows it
                prefs.remove(AppFontModeKey)
                prefs.remove(CustomFontIdKey)
                prefs.remove(CustomFontNameKey)
            }
            publish(context, config, caps)
        }
    }

    private fun publish(context: Context, config: Config, caps: BuiltinSystemCaps) {
        val validCustom = ArrayList<CustomFontRecord>(config.customFonts.size)
        val families = HashMap<String, FontFamily>(config.customFonts.size)
        val activeIds = HashSet<String>(config.customFonts.size)

        for (record in config.customFonts) {
            val existingFaces = record.faces.filter { face ->
                FontIdRegex.matches(face.fileId) && File(directory(context), "${face.fileId}.${face.ext}").isFile
            }
            if (existingFaces.isEmpty()) continue
            val cleanRecord = record.copy(faces = existingFaces)
            val family = loadFamily(context, cleanRecord) ?: continue
            validCustom.add(cleanRecord)
            families[cleanRecord.id] = family
            activeIds.add(cleanRecord.id)
        }
        loadedFamilies.keys.retainAll(activeIds)

        val cleanedConfig = sanitizeAllRoles(config.copy(customFonts = validCustom), caps)
        val options = buildList {
            addAll(defaultBuiltinOptions(caps))
            validCustom.forEach { rec ->
                add(
                    FontOption(
                        id = rec.id,
                        name = rec.displayName,
                        capabilities = rec.capabilities(),
                        isCustom = true,
                        isVariable = rec.isVariable,
                        faceCount = rec.faces.size,
                    ),
                )
            }
        }
        state = State(
            config = cleanedConfig,
            options = options,
            families = families,
            ready = true,
        )
    }

    private fun buildResolved(current: State, lookFamily: FontFamily?): ResolvedFonts {
        val cfg = current.config
        val globalResolved = resolveSingleRole(cfg.global, cfg.global, current, lookFamily)
        val headingResolved = resolveSingleRole(cfg.heading, cfg.global, current, lookFamily)
        val subheadingResolved = resolveSingleRole(cfg.subheading, cfg.global, current, lookFamily)
        return ResolvedFonts(
            heading = headingResolved,
            subheading = subheadingResolved,
            global = globalResolved,
        )
    }

    private fun resolveSingleRole(
        roleConfig: RoleFontConfig,
        globalConfig: RoleFontConfig,
        current: State,
        lookFamily: FontFamily?,
    ): ResolvedRoleFont {
        val followingGlobal = roleConfig.fontId == FollowGlobalFontId
        val effectiveId = if (followingGlobal) globalConfig.fontId else roleConfig.fontId
        val caps = current.options.firstOrNull { it.id == effectiveId }?.capabilities ?: InterCaps
        val family = familyForId(effectiveId, current.families, lookFamily)
        val weight = if (followingGlobal && roleConfig.weight == RoleWeight.AUTO) globalConfig.weight else roleConfig.weight
        val italic = if (followingGlobal && roleConfig.italic == null) (globalConfig.italic == true) else (roleConfig.italic == true)
        val isInter = effectiveId == BuiltinInterId
        return ResolvedRoleFont(
            fontId = effectiveId,
            family = family,
            boldFamily = if (isInter && lookFamily == null) InterBoldOverride else null,
            weight = weight,
            italic = italic,
            capabilities = caps,
            isDefaultInter = isInter,
        )
    }

    private fun familyForId(fontId: String, customFamilies: Map<String, FontFamily>, lookFamily: FontFamily?): FontFamily =
        when (fontId) {
            BuiltinInterId -> lookFamily ?: BundledInter
            BuiltinSystemId -> FontFamily.Default
            BuiltinSerifId -> FontFamily.Serif
            BuiltinMonoId -> FontFamily.Monospace
            BuiltinBbhBartleId -> BundledBbhBartle
            else -> customFamilies[fontId] ?: lookFamily ?: BundledInter
        }

    private fun loadFamily(context: Context, record: CustomFontRecord): FontFamily? {
        val signature = record.faces.joinToString(",") { "${it.fileId}.${it.ext}" }
        val cached = loadedFamilies[record.id]
        if (cached != null && cached.first == signature) return cached.second

        val dir = directory(context)
        val fonts = ArrayList<Font>()
        for (face in record.faces) {
            val file = File(dir, "${face.fileId}.${face.ext}")
            if (!file.isFile) continue
            val built = buildAndroidFonts(file, face)
            fonts.addAll(built)
        }
        if (fonts.isEmpty()) return null
        val family = FontFamily(fonts)
        loadedFamilies[record.id] = signature to family
        return family
    }

    private fun buildAndroidFonts(file: File, face: CustomFaceRecord): List<Font> {
        val isVar = face.varWeightMin != null || face.varItalic || face.varSlant != null
        if (isVar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val result = ArrayList<Font>(8)
            val minW = face.varWeightMin ?: face.weight.toFloat()
            val maxW = face.varWeightMax ?: face.weight.toFloat()
            val targetWeights = buildList {
                if (minW <= 320f) add(300)
                add(400)
                if (maxW >= 500f && minW <= 500f) add(500)
                if (maxW >= 600f && minW <= 600f) add(600)
                if (maxW >= 650f) add(700)
                if (maxW >= 780f) add(800)
            }.distinct()

            fun buildVariation(w: Int, italic: Boolean): Font? {
                val clamped = w.toFloat().coerceIn(minW, maxW)
                val settings = buildList {
                    if (face.varWeightMin != null) add("'wght' ${clamped.toInt()}")
                    if (italic) {
                        if (face.varItalic) add("'ital' 1")
                        else if (face.varSlant != null) add("'slnt' ${face.varSlant}")
                    }
                }.joinToString(", ")
                val tf = runCatching {
                    Api26Typeface.build(file, settings.ifEmpty { null })
                }.getOrNull() ?: return null
                return ImportedFont(
                    typeface = tf,
                    weight = FontWeight(w.coerceIn(100, 900)),
                    style = if (italic) FontStyle.Italic else FontStyle.Normal,
                )
            }

            for (w in targetWeights) {
                buildVariation(w, italic = false)?.let(result::add)
                if (face.varItalic || face.varSlant != null) {
                    buildVariation(w, italic = true)?.let(result::add)
                }
            }
            if (result.isNotEmpty()) return result
        }

        val tf = runCatching { readTypeface(file) }.getOrNull() ?: return emptyList()
        return listOf(
            ImportedFont(
                typeface = tf,
                weight = FontWeight(face.weight.coerceIn(100, 900)),
                style = if (face.isItalic) FontStyle.Italic else FontStyle.Normal,
            ),
        )
    }

    private fun validateTypeface(file: File): Typeface = readTypeface(file)

    private fun readTypeface(file: File): Typeface {
        if (!file.isFile || file.length() > MaxCustomFontBytes ||
            !file.inputStream().buffered().use(::hasSupportedFontHeader)
        ) throw InvalidFontException()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Api26Typeface.build(file, null) ?: throw InvalidFontException()
            } else {
                Typeface.createFromFile(file)
            }
        } catch (_: RuntimeException) {
            throw InvalidFontException()
        }
    }

    // probe device fonts once off the main thread so mono/serif/system toggles match what android actually ships
    private fun probeSystemCapabilities(): BuiltinSystemCaps {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return DefaultSystemCaps
        return runCatching { Api29SystemFonts.probe(DefaultSystemCaps) }.getOrElse { DefaultSystemCaps }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private object Api26Typeface {
        fun build(file: File, variationSettings: String?): Typeface? =
            Typeface.Builder(file)
                .apply { if (!variationSettings.isNullOrEmpty()) setFontVariationSettings(variationSettings) }
                .build()
    }

    // kept in its own class so android 8 and 9 never try to verify api 29 systemfonts symbols
    @RequiresApi(Build.VERSION_CODES.Q)
    private object Api29SystemFonts {
        fun probe(defaults: BuiltinSystemCaps): BuiltinSystemCaps {
            val fonts = android.graphics.fonts.SystemFonts.getAvailableFonts()
            var monoBold = false
            var monoItalic = false
            var monoLight = false
            var serifBold = false
            var serifItalic = false
            var serifLight = false
            var sansBold = false
            var sansExtraBold = false
            var sansItalic = false
            var sansLight = false

            for (font in fonts) {
                val name = font.file?.name?.lowercase(Locale.ROOT) ?: continue
                val style = font.style
                val weight = style.weight
                val italic = style.slant == android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC
                when {
                    "mono" in name -> {
                        if (weight >= 650) monoBold = true
                        if (weight <= 320) monoLight = true
                        if (italic) monoItalic = true
                    }
                    "serif" in name && "sans" !in name -> {
                        if (weight >= 650) serifBold = true
                        if (weight <= 320) serifLight = true
                        if (italic) serifItalic = true
                    }
                    "roboto" in name && "mono" !in name -> {
                        if (weight >= 650) sansBold = true
                        if (weight >= 780) sansExtraBold = true
                        if (weight <= 320) sansLight = true
                        if (italic) sansItalic = true
                    }
                }
            }
            return BuiltinSystemCaps(
                system = FontCapabilities(
                    supportsBold = sansBold || defaults.system.supportsBold,
                    supportsExtraBold = sansExtraBold,
                    supportsItalic = sansItalic || defaults.system.supportsItalic,
                    supportsLight = sansLight || defaults.system.supportsLight,
                ),
                serif = FontCapabilities(
                    supportsBold = serifBold || defaults.serif.supportsBold,
                    supportsExtraBold = false,
                    supportsItalic = serifItalic || defaults.serif.supportsItalic,
                    supportsLight = serifLight,
                ),
                mono = FontCapabilities(
                    supportsBold = monoBold,
                    supportsExtraBold = false,
                    supportsItalic = monoItalic,
                    supportsLight = monoLight,
                ),
            )
        }
    }

    private fun defaultBuiltinOptions(caps: BuiltinSystemCaps): List<FontOption> = listOf(
        FontOption(BuiltinInterId, "Inter", InterCaps),
        FontOption(BuiltinSystemId, "System", caps.system),
        FontOption(BuiltinSerifId, "Serif", caps.serif),
        FontOption(BuiltinMonoId, "Monospace", caps.mono),
        FontOption(BuiltinBbhBartleId, "BBH Bartle", BbhBartleCaps),
    )

    private fun sanitizeRole(
        role: FontRole,
        roleConfig: RoleFontConfig,
        config: Config,
        caps: BuiltinSystemCaps = probedSystemCaps ?: DefaultSystemCaps,
    ): RoleFontConfig {
        val validCustomIds = config.customFonts.mapTo(HashSet()) { it.id }
        val allowed = BuiltinIds + validCustomIds
        val fontId = when {
            role != FontRole.GLOBAL && roleConfig.fontId == FollowGlobalFontId -> FollowGlobalFontId
            roleConfig.fontId in allowed -> roleConfig.fontId
            else -> if (role == FontRole.GLOBAL) BuiltinInterId else FollowGlobalFontId
        }
        val effectiveId = if (fontId == FollowGlobalFontId) config.global.fontId else fontId
        val fontCaps = when (effectiveId) {
            BuiltinInterId -> InterCaps
            BuiltinSystemId -> caps.system
            BuiltinSerifId -> caps.serif
            BuiltinMonoId -> caps.mono
            BuiltinBbhBartleId -> BbhBartleCaps
            else -> config.customFonts.firstOrNull { it.id == effectiveId }?.capabilities() ?: InterCaps
        }
        val weight = when (roleConfig.weight) {
            RoleWeight.BOLD -> if (fontCaps.supportsBold) RoleWeight.BOLD else RoleWeight.AUTO
            RoleWeight.LIGHT -> if (fontCaps.supportsLight) RoleWeight.LIGHT else RoleWeight.AUTO
            RoleWeight.NORMAL -> if (role != FontRole.GLOBAL && fontId == FollowGlobalFontId) RoleWeight.NORMAL else RoleWeight.AUTO
            RoleWeight.AUTO -> RoleWeight.AUTO
        }
        val italic = when {
            !fontCaps.supportsItalic -> null
            roleConfig.italic == true -> true
            roleConfig.italic == false && role != FontRole.GLOBAL && fontId == FollowGlobalFontId -> false
            else -> null
        }
        return RoleFontConfig(fontId = fontId, weight = weight, italic = italic)
    }

    private fun sanitizeAllRoles(config: Config, caps: BuiltinSystemCaps = probedSystemCaps ?: DefaultSystemCaps): Config {
        val global = sanitizeRole(FontRole.GLOBAL, config.global, config, caps)
        val withGlobal = config.copy(global = global)
        return withGlobal.copy(
            heading = sanitizeRole(FontRole.HEADING, withGlobal.heading, withGlobal, caps),
            subheading = sanitizeRole(FontRole.SUBHEADING, withGlobal.subheading, withGlobal, caps),
        )
    }

    private suspend fun operate(action: suspend () -> Unit): Error? = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                action()
                null
            } catch (error: CancellationException) {
                throw error
            } catch (_: FontTooLargeException) {
                Error.TooLarge
            } catch (_: InvalidFontException) {
                Error.InvalidFont
            } catch (_: IOException) {
                Error.Storage
            } catch (_: SecurityException) {
                Error.Storage
            } catch (_: IllegalArgumentException) {
                Error.Storage
            }
        }
    }

    private data class StoredSnapshot(
        val configJson: String?,
        val legacyMode: AppFontMode,
        val legacyId: String?,
        val legacyName: String?,
    )

    private fun Preferences.storedSnapshot() = StoredSnapshot(
        configJson = this[FontCustomizerConfigKey],
        legacyMode = AppFontMode.fromStoredValue(this[AppFontModeKey]),
        legacyId = this[CustomFontIdKey],
        legacyName = this[CustomFontNameKey],
    )

    private fun resolveConfig(context: Context, snapshot: StoredSnapshot): Config {
        snapshot.configJson?.let { raw ->
            decodeConfig(raw)?.let { return it }
        }
        // migrate single-font settings from earlier builds if present
        val customFonts = ArrayList<CustomFontRecord>()
        var legacyCustomFontId: String? = null
        val legacyId = snapshot.legacyId
        if (legacyId != null && FontIdRegex.matches(legacyId)) {
            val file = File(directory(context), "$legacyId.ttf")
            if (file.isFile) {
                val meta = FontMetadataParser.parse(file)
                val name = meta.familyName?.takeIf { it.isNotBlank() }
                    ?: snapshot.legacyName?.substringBeforeLast('.')?.ifBlank { "Custom Font" }
                    ?: "Custom Font"
                val id = "custom:$legacyId"
                customFonts.add(
                    CustomFontRecord(
                        id = id,
                        displayName = name,
                        familyKey = name.lowercase(Locale.ROOT),
                        faces = listOf(
                            CustomFaceRecord(
                                fileId = legacyId,
                                ext = "ttf",
                                weight = meta.weight,
                                isItalic = meta.isItalic,
                                varWeightMin = meta.variableWeightMin,
                                varWeightMax = meta.variableWeightMax,
                                varItalic = meta.variableItalic,
                                varSlant = meta.variableSlant,
                            ),
                        ),
                    ),
                )
                legacyCustomFontId = id
            }
        }
        val globalId = when (snapshot.legacyMode) {
            AppFontMode.DEFAULT -> BuiltinInterId
            AppFontMode.SYSTEM -> BuiltinSystemId
            AppFontMode.CUSTOM -> legacyCustomFontId ?: BuiltinInterId
        }
        return Config(global = RoleFontConfig(globalId), customFonts = customFonts)
    }

    internal fun encodeConfig(config: Config): String {
        val root = JSONObject()
        root.put("global", encodeRole(config.global))
        root.put("heading", encodeRole(config.heading))
        root.put("subheading", encodeRole(config.subheading))
        val customArr = JSONArray()
        for (font in config.customFonts) {
            val obj = JSONObject()
            obj.put("id", font.id)
            obj.put("name", font.displayName)
            obj.put("familyKey", font.familyKey)
            val facesArr = JSONArray()
            for (face in font.faces) {
                val f = JSONObject()
                f.put("fileId", face.fileId)
                f.put("ext", face.ext)
                f.put("weight", face.weight)
                f.put("italic", face.isItalic)
                face.varWeightMin?.let { f.put("varWeightMin", it.toDouble()) }
                face.varWeightMax?.let { f.put("varWeightMax", it.toDouble()) }
                if (face.varItalic) f.put("varItalic", true)
                face.varSlant?.let { f.put("varSlant", it.toDouble()) }
                facesArr.put(f)
            }
            obj.put("faces", facesArr)
            customArr.put(obj)
        }
        root.put("customFonts", customArr)
        return root.toString()
    }

    internal fun decodeConfig(raw: String): Config? = runCatching {
        val root = JSONObject(raw)
        val global = decodeRole(root.optJSONObject("global"), defaultId = BuiltinInterId)
        val heading = decodeRole(root.optJSONObject("heading"), defaultId = FollowGlobalFontId)
        val subheading = decodeRole(root.optJSONObject("subheading"), defaultId = FollowGlobalFontId)
        val customArr = root.optJSONArray("customFonts")
        val customFonts = ArrayList<CustomFontRecord>()
        if (customArr != null) {
            for (i in 0 until customArr.length()) {
                val obj = customArr.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                val key = obj.optString("familyKey").ifBlank { name.lowercase(Locale.ROOT) }
                val facesArr = obj.optJSONArray("faces") ?: continue
                val faces = ArrayList<CustomFaceRecord>()
                for (j in 0 until facesArr.length()) {
                    val f = facesArr.optJSONObject(j) ?: continue
                    val fileId = f.optString("fileId").takeIf { FontIdRegex.matches(it) } ?: continue
                    val ext = f.optString("ext").takeIf { it == "ttf" || it == "otf" } ?: "ttf"
                    faces.add(
                        CustomFaceRecord(
                            fileId = fileId,
                            ext = ext,
                            weight = f.optInt("weight", 400).coerceIn(100, 900),
                            isItalic = f.optBoolean("italic", false),
                            varWeightMin = if (f.has("varWeightMin")) f.optDouble("varWeightMin").toFloat() else null,
                            varWeightMax = if (f.has("varWeightMax")) f.optDouble("varWeightMax").toFloat() else null,
                            varItalic = f.optBoolean("varItalic", false),
                            varSlant = if (f.has("varSlant")) f.optDouble("varSlant").toFloat() else null,
                        ),
                    )
                }
                if (faces.isNotEmpty()) customFonts.add(CustomFontRecord(id, name, key, faces))
            }
        }
        Config(global = global, heading = heading, subheading = subheading, customFonts = customFonts)
    }.getOrNull()

    private fun encodeRole(role: RoleFontConfig): JSONObject = JSONObject().apply {
        put("fontId", role.fontId)
        put("weight", role.weight.name)
        role.italic?.let { put("italic", it) }
    }

    private fun decodeRole(obj: JSONObject?, defaultId: String): RoleFontConfig {
        if (obj == null) return RoleFontConfig(fontId = defaultId)
        val italic = if (obj.has("italic") && !obj.isNull("italic")) obj.optBoolean("italic", false) else null
        return RoleFontConfig(
            fontId = obj.optString("fontId", defaultId),
            weight = RoleWeight.fromStored(obj.optString("weight", "")),
            italic = italic,
        )
    }

    private fun cleanFiles(context: Context, config: Config) {
        val keep = HashSet<String>()
        for (font in config.customFonts) {
            for (face in font.faces) {
                keep.add("${face.fileId}.${face.ext}")
            }
        }
        directory(context).listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep) file.delete()
        }
    }

    private fun directory(context: Context) = File(context.noBackupFilesDir, "custom-font")

    // serve pre-validated typefaces straight from memory so font swaps stay instant and never hit disk again
    private class ImportedFont(
        private val typeface: Typeface,
        override val weight: FontWeight,
        override val style: FontStyle,
    ) : AndroidFont(FontLoadingStrategy.Blocking, Loader, FontVariation.Settings()) {
        private object Loader : TypefaceLoader {
            override fun loadBlocking(context: Context, font: AndroidFont) = (font as ImportedFont).typeface
            override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
                error("Imported fonts are already loaded")
        }
    }

    private class InvalidFontException : IOException()

    private val BuiltinIds = setOf(BuiltinInterId, BuiltinSystemId, BuiltinSerifId, BuiltinMonoId, BuiltinBbhBartleId)
    private val FontIdRegex = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
}
