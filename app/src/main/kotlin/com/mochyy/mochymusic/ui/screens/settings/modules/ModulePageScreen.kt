// a page a module brings, built from its blocks: words, pictures, media lists, artist banners, settings, action callers, cards, blur, shaders, layered backgrounds, and modal sheet presentation

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalDownloadUtil
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.SpeedDialItem
import com.mochyy.mochymusic.di.ModulesEntryPoint
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.modules.security.ModuleActionGuard
import com.mochyy.mochymusic.modules.security.SafeModuleRoutes
import com.mochyy.mochymusic.modules.skin.ActionTarget
import com.mochyy.mochymusic.modules.skin.ButtonKind
import com.mochyy.mochymusic.modules.skin.ModuleSkin
import com.mochyy.mochymusic.modules.skin.PageBlock
import com.mochyy.mochymusic.modules.skin.SheetKind
import com.mochyy.mochymusic.modules.skin.SkinBlurDirection
import com.mochyy.mochymusic.modules.skin.SkinPage
import com.mochyy.mochymusic.modules.skin.StackAlign
import com.mochyy.mochymusic.playback.ExoDownloadService
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.CreatePlaylistDialog
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.component.onChrome
import com.mochyy.mochymusic.ui.menu.AddToPlaylistDialog
import com.mochyy.mochymusic.ui.menu.ArtistMenu
import com.mochyy.mochymusic.ui.menu.PlaylistMenu
import com.mochyy.mochymusic.ui.menu.songedit.SongEditSheet
import com.mochyy.mochymusic.ui.menu.songedit.VideoPickerSheet
import com.mochyy.mochymusic.ui.player.info.StreamInfoSheet
import com.mochyy.mochymusic.ui.player.menu.CanvasQualitySheet
import com.mochyy.mochymusic.ui.player.menu.DeviceOutputSheet
import com.mochyy.mochymusic.ui.player.menu.LyricsEditorSheet
import com.mochyy.mochymusic.ui.player.menu.LyricsOffsetSheet
import com.mochyy.mochymusic.ui.player.menu.LyricsProviderSheet
import com.mochyy.mochymusic.ui.player.menu.LyricsTranslationSheet
import com.mochyy.mochymusic.ui.player.menu.PlaybackSpeedSheet
import com.mochyy.mochymusic.ui.player.menu.PlayerActionMenu
import com.mochyy.mochymusic.ui.player.menu.SleepTimerSheet
import com.mochyy.mochymusic.ui.player.menu.SongDetailsSheet
import com.mochyy.mochymusic.ui.player.share.song.SongShare
import com.mochyy.mochymusic.ui.player.styles.ScaledContent
import com.mochyy.mochymusic.ui.player.styles.adjustVolume
import com.mochyy.mochymusic.ui.player.styles.applySafeAppSetting
import com.mochyy.mochymusic.ui.player.styles.iconForButton
import com.mochyy.mochymusic.ui.player.styles.iconForSheet
import com.mochyy.mochymusic.ui.player.video.VideoQualitySheet
import com.mochyy.mochymusic.ui.player.video.YouTubeVideoSessions
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.devices.rememberDevicesLink
import com.mochyy.mochymusic.ui.screens.devices.showDeviceIntegration
import com.mochyy.mochymusic.ui.screens.search.searchRoute
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import com.music.innertube.models.WatchEndpoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ModulePageRoutePattern = "module_page/{id}/{page}"
fun modulePageRoute(id: String, page: String) = "module_page/${Uri.encode(id)}/${Uri.encode(page)}"

@Composable
fun ModulePageScreen(navController: NavController, id: String, pageId: String, viewModel: ModulesViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val entry = entries.firstOrNull { it.id == id }
    val skin = remember(entry) { viewModel.skinOf(id) }
    // a page only shows while its module is on, the same as everything else a module adds
    val page = skin?.pages?.firstOrNull { it.id == pageId }?.takeIf { entry?.enabled == true }
    if (skin == null || page == null || entry == null) {
        SubSettingsScaffold(title = entry?.module?.info?.name.orEmpty(), backLabel = entry?.module?.info?.name, onBack = { navController.navigateUp() }) {
            Text(
                stringResource(R.string.module_page_missing),
                fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, color = LabColors.Secondary,
            )
        }
        return
    }
    ModulePage(page, entry, skin, viewModel::setField, navController, PageSubject.None, onOriginal = null, backLabel = entry.module.info.name)
}

/** renders any module page inside a native PopupSheet, strictly bounded to [ModuleActionGuard.MaxModuleSheetDepth] */
@Composable
internal fun ModulePageSheetContent(
    moduleId: String,
    pageId: String,
    onNavigateRoute: (String) -> Unit,
    onDismiss: () -> Unit,
    depth: Int = 1,
) {
    if (depth > ModuleActionGuard.MaxModuleSheetDepth) {
        onDismiss()
        return
    }
    val context = LocalContext.current
    val navController = LocalModuleNavController.current
    val registry = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, ModulesEntryPoint::class.java).moduleRegistry()
    }
    val entries by registry.entries.collectAsState()
    val entry = entries.firstOrNull { it.id == moduleId && it.enabled }
    val skin = remember(entry) { (entry?.module as? com.mochyy.mochymusic.modules.skin.SkinSource)?.skin }
    val page = skin?.pages?.firstOrNull { it.id == pageId }
    if (entry == null || skin == null || page == null || navController == null) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.module_page_missing), fontFamily = InterFontFamily, fontSize = 14.sp, color = LabColors.Secondary)
        }
        return
    }
    var activeSheet by remember { mutableStateOf<SheetKind?>(null) }
    val pageCtx = PageContext(
        entry = entry,
        skin = skin,
        onField = registry::setField,
        navController = navController,
        subject = PageSubject.None,
        onOriginal = null,
        sheetDepth = depth,
        onDismissSheet = onDismiss,
        onOpenRouteOverride = { route ->
            onDismiss()
            onNavigateRoute(route)
        },
        onRequestSheet = { kind -> activeSheet = kind },
    )
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Text(
                page.title.ifBlank { entry.module.info.name },
                fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onDismiss),
            ) {
                Icon(painterResource(R.drawable.close), null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            PageBlocks(page.blocks, pageCtx, glass = null)
            Spacer(Modifier.height(16.dp))
        }
    }
    activeSheet?.let { kind ->
        PageNativeSheetHost(kind = kind, page = pageCtx, onDismiss = { activeSheet = null })
    }
}

/** a module page's background and blocks, under mochy's title bar or on its own; a page that took one of mochy's over gets what it took over as [subject] */
@Composable
internal fun ModulePage(
    page: SkinPage,
    entry: ModuleEntry,
    skin: ModuleSkin,
    onField: (module: String, field: String, value: String) -> Unit,
    navController: NavController,
    subject: PageSubject,
    onOriginal: (() -> Unit)?,
    backLabel: String?,
    insetTop: Boolean = true,
) {
    // a frosted card or blur block blurs the page's own background, captured only when requested
    val glass = remember(page) { if (page.frosted) GlassState() else null }
    val scale = page.size.factor(LocalConfiguration.current.screenWidthDp.toFloat(), entry.values)
    var activeSheet by remember { mutableStateOf<SheetKind?>(null) }
    val blocks = PageContext(
        entry = entry,
        skin = skin,
        onField = onField,
        navController = navController,
        subject = subject,
        onOriginal = onOriginal,
        onRequestSheet = { kind -> activeSheet = kind },
    )
    val connection = LocalPlayerConnection.current
    val needsTitleMeta = page.header && '{' in page.title
    val playing by remember(connection, needsTitleMeta) {
        if (needsTitleMeta) connection?.mediaMetadata ?: PageNullMeta else PageNullMeta
    }.collectAsState()
    Box(Modifier.fillMaxSize()) {
        if (page.background != null || page.layers.isNotEmpty()) {
            PageBackdrop(page, skin, glass, entry.values, subject.cover)
        } else if (glass != null) {
            Box(Modifier.fillMaxSize().glassRoot(glass))
        }
        if (page.header) {
            SubSettingsScaffold(title = subject.fill(page.title, entry.values, playing), backLabel = backLabel, onBack = { navController.navigateUp() }) {
                ScaledContent(scale) { Column { PageBlocks(page.blocks, blocks, glass) } }
                Spacer(Modifier.height(120.dp))
            }
        } else {
            val insets = LocalPlayerAwareWindowInsets.current
            val appliedInsets = if (insetTop) insets else insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(appliedInsets)
                    .padding(horizontal = 20.dp),
            ) {
                ScaledContent(scale) { Column { PageBlocks(page.blocks, blocks, glass) } }
                Spacer(Modifier.height(24.dp))
            }
        }
        activeSheet?.let { kind ->
            PageNativeSheetHost(kind = kind, page = blocks, onDismiss = { activeSheet = null })
        }
    }
}

/** what every block on a page can reach: its module, the settings, where to go, what the page took over, and sheet callers */
internal class PageContext(
    val entry: ModuleEntry,
    val skin: ModuleSkin,
    val onField: (module: String, field: String, value: String) -> Unit,
    val navController: NavController,
    val subject: PageSubject,
    /** back to mochy's own page, on a takeover page */
    val onOriginal: (() -> Unit)?,
    val sheetDepth: Int = 0,
    val onDismissSheet: (() -> Unit)? = null,
    val onOpenRouteOverride: ((String) -> Unit)? = null,
    val onRequestSheet: (SheetKind) -> Unit = {},
)

// the blocks one after another; containers and cards recurse through the same renderer
@Composable
internal fun PageBlocks(blocks: List<PageBlock>, page: PageContext, glass: GlassState?) {
    blocks.forEach { block ->
        PageSingleBlock(block, page, glass)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PageSingleBlock(block: PageBlock, page: PageContext, glass: GlassState?) {
    when (block) {
        is PageBlock.Conditional -> {
            if (block.condition.holdsFor(page.entry.values)) {
                PageSingleBlock(block.inner, page, glass)
            }
        }
        // the module's own setting, changed right here the same way as on its page
        is PageBlock.Setting -> {
            val one = page.entry.copy(fields = page.entry.fields.filter { it.id == block.key })
            if (one.fields.isNotEmpty()) {
                ModuleOptions(entry = one, onPick = { _, _ -> }, onField = { field, value -> page.onField(page.entry.id, field, value) }, titled = false)
            }
        }
        is PageBlock.Card -> PageCardBlock(block, glass, page) { PageBlocks(block.blocks, page, glass = null) }
        is PageBlock.Shader -> PageShaderBlock(block, page.skin, page.entry.values)
        is PageBlock.Blur -> PageBlurBlock(block, page, glass)
        is PageBlock.Row -> Row(horizontalArrangement = Arrangement.spacedBy(block.gap), modifier = Modifier.fillMaxWidth()) {
            block.blocks.forEach { child ->
                Box(Modifier.weight(1f)) { PageSingleBlock(child, page, glass) }
            }
        }
        is PageBlock.Column -> Column(verticalArrangement = Arrangement.spacedBy(block.gap), modifier = Modifier.fillMaxWidth()) {
            block.blocks.forEach { child -> PageSingleBlock(child, page, glass) }
        }
        is PageBlock.Stack -> {
            val alignment = when (block.align) {
                StackAlign.Center -> Alignment.Center
                StackAlign.Top -> Alignment.TopCenter
                StackAlign.Bottom -> Alignment.BottomCenter
                StackAlign.Start -> Alignment.CenterStart
                StackAlign.End -> Alignment.CenterEnd
            }
            Box(
                contentAlignment = alignment,
                modifier = Modifier.fillMaxWidth().then(block.height?.let { Modifier.height(it) } ?: Modifier),
            ) {
                block.blocks.forEach { child -> PageSingleBlock(child, page, glass) }
            }
        }
        is PageBlock.ArtistBanner -> PageArtistBannerBlock(block, page)
        is PageBlock.Bio -> PageBioBlock(block, page)
        is PageBlock.Playlists -> PagePlaylistsBlock(block, page)
        is PageBlock.Albums -> PageAlbumsBlock(block, page)
        is PageBlock.Artists -> PageArtistsBlock(block, page)
        is PageBlock.Songs -> PageSongsBlock(block, page, glass)
        is PageBlock.NowPlaying -> PageNowPlayingBlock(block, glass)
        is PageBlock.Lyrics -> PageLyricsBlock(block, page)
        is PageBlock.ActionButton -> PageActionButtonBlock(block, page, glass)
        is PageBlock.SettingsSearch -> PageSettingsSearchBlock(block, page, glass)
        is PageBlock.SettingsCategories -> PageSettingsCategoriesBlock(block, page, glass)
        is PageBlock.AppSetting -> PageAppSettingBlock(block, glass)
        is PageBlock.SettingsLink -> PageSettingsLinkBlock(block, page, glass)
        is PageBlock.ModuleSettingsPages -> PageModuleSettingsPagesBlock(block, page)
        is PageBlock.SearchBar -> PageSearchBarBlock(block, page, glass)
        is PageBlock.SpeedDial -> PageSpeedDialBlock(block, page)
        is PageBlock.Stats -> PageStatsBlock(block, glass)
        is PageBlock.Tabs -> PageTabsBlock(block, page, glass)
        is PageBlock.Cover, is PageBlock.Control, is PageBlock.Player, PageBlock.Styles -> PageLiveBlock(block, page)
        else -> PageBlockView(block, page)
    }
}

/** what the page draws behind everything, plus optional stacked layers, captured for frosted cards when [glass] asks */
@Composable
private fun PageBackdrop(page: SkinPage, skin: ModuleSkin, glass: GlassState?, values: Map<String, String>, subjectCover: String?) {
    val capture = glass?.let { Modifier.glassRoot(it) } ?: Modifier
    Box(Modifier.fillMaxSize().then(capture)) {
        page.background?.let { bg -> PageBackgroundLayer(bg, skin, values, subjectCover) }
        page.layers.forEach { layer ->
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = layer.opacity }) {
                PageBackgroundLayer(layer.background, skin, values, subjectCover)
            }
        }
    }
}

/** blocks on a card: frosted/progressive glass over the page's background or fill colours, optional border and click target */
@Composable
private fun PageCardBlock(block: PageBlock.Card, glass: GlassState?, page: PageContext, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(block.corner)
    val accent = MaterialTheme.colorScheme.primary
    val values = page.entry.values
    val colors = block.colors.map { it.onChrome(accent, values) }
    val runAction = if (block.target != null) rememberPageActionRunner(page) else null
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (block.target != null && runAction != null) Modifier.clickable { runAction(block.target) } else Modifier),
    ) {
        if (block.glass != null && glass != null) {
            val radius = with(LocalDensity.current) { block.glass.dp.toPx() }
            val tint = colors.singleOrNull() ?: CardGlassTint
            when (block.progressive) {
                SkinBlurDirection.TopToBottom -> ProgressiveGlassBackground(
                    state = glass, maxBlurRadius = { radius }, tint = tint,
                    direction = BlurDirection.TopToBottom, fallbackBlur = true, modifier = Modifier.matchParentSize(),
                )
                SkinBlurDirection.BottomToTop -> ProgressiveGlassBackground(
                    state = glass, maxBlurRadius = { radius }, tint = tint,
                    direction = BlurDirection.BottomToTop, fallbackBlur = true, modifier = Modifier.matchParentSize(),
                )
                SkinBlurDirection.Radial -> {
                    val radial = remember { Brush.radialGradient(0f to Color.Black, 0.72f to Color.Black, 1f to Color.Transparent) }
                    GlassPillBackground(
                        state = glass, blurRadius = { radius }, tint = tint, mask = radial,
                        tileMode = android.graphics.Shader.TileMode.CLAMP, bleed = true,
                        fallbackBlur = true, fallbackMaskMode = 3, fallbackMaskParam = 0.72f,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                null -> GlassPillBackground(
                    state = glass, blurRadius = { radius }, tint = tint,
                    tileMode = android.graphics.Shader.TileMode.CLAMP, bleed = true,
                    fallbackBlur = true, modifier = Modifier.matchParentSize(),
                )
            }
            if (colors.size > 1) Box(Modifier.matchParentSize().background(pageBackgroundBrush(colors)))
        } else {
            Box(Modifier.matchParentSize().background(pageBackgroundBrush(colors.ifEmpty { listOf(LabColors.Card) })))
        }
        block.stroke?.let { width ->
            val strokeColor = block.strokeColor?.onChrome(accent, values) ?: Color.White.copy(alpha = 0.18f)
            Box(Modifier.matchParentSize().border(width, strokeColor, shape))
        }
        Column(Modifier.fillMaxWidth().padding(block.padding)) { content() }
    }
}

/** rich action button invoking any sandboxed ActionTarget (sheet, module page in sheet, route, control, setting, search, link) */
@Composable
private fun PageActionButtonBlock(block: PageBlock.ActionButton, page: PageContext, glass: GlassState?) {
    val connection = LocalPlayerConnection.current
    val playing by remember(connection, block.text) {
        if ('{' in block.text) connection?.mediaMetadata ?: PageNullMeta else PageNullMeta
    }.collectAsState()
    val runAction = rememberPageActionRunner(page)
    val accent = MaterialTheme.colorScheme.primary
    val resolvedColors = block.colors.map { it.onChrome(accent, page.entry.values) }
    val bgColors = when {
        resolvedColors.isNotEmpty() -> resolvedColors
        block.filled -> listOf(Color.White)
        else -> listOf(LabColors.Card)
    }
    val textColor = when {
        block.glass != null -> Color.White
        resolvedColors.isEmpty() && block.filled -> Color.Black
        else -> Color.White
    }
    val fallbackIcon = when (val t = block.target) {
        is ActionTarget.Control -> iconForButton(t.kind)
        is ActionTarget.Sheet -> iconForSheet(t.kind)
        is ActionTarget.Search -> R.drawable.search_filled
        is ActionTarget.Route -> R.drawable.navigate_next
        is ActionTarget.SetField, is ActionTarget.SetAppSetting -> R.drawable.tune
        is ActionTarget.ModuleSheet -> R.drawable.puzzle
        is ActionTarget.Page, is ActionTarget.Link -> null
    }
    SettingsBlockSurface(
        corner = block.corner,
        glass = glass,
        glassRadius = block.glass,
        colors = if (block.glass != null) block.colors else emptyList(),
        values = page.entry.values,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (block.glass == null) {
                    Modifier.clip(RoundedCornerShape(block.corner)).background(pageBackgroundBrush(bgColors))
                } else {
                    Modifier
                },
            ),
        onClick = { runAction(block.target) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
        ) {
            if (block.icon != null) {
                AsyncImage(
                    model = page.skin.resolve(block.icon), contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            } else if (fallbackIcon != null) {
                Icon(painterResource(fallbackIcon), null, tint = textColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                page.subject.fill(block.text, page.entry.values, playing),
                fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** sandboxed action runner for custom pages: rate-limited, depth-capped, allowlisted routes and settings */
@Composable
internal fun rememberPageActionRunner(page: PageContext): (ActionTarget) -> Unit {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val menuState = LocalMenuState.current
    val playerSheet = LocalPlayerBottomSheetState.current
    val zoomSheets = LocalZoomOutOverlayState.current
    val devices = rememberDevicesLink()
    val scope = rememberCoroutineScope()

    val openRoute: (String) -> Unit = { route ->
        page.onOpenRouteOverride?.invoke(route) ?: page.navController.navigate(route)
    }

    return { target ->
        if (ModuleActionGuard.allowTrigger(page.entry.id)) {
            val playing = connection?.mediaMetadata?.value
            when (target) {
                is ActionTarget.Link -> {
                    val url = page.subject.fill(target.template, page.entry.values, playing)
                    if (ModuleActionGuard.isSafeFinalUrl(url)) context.openDevUrl(url)
                }
                is ActionTarget.Page -> openRoute(modulePageRoute(page.entry.id, target.id))
                is ActionTarget.Search -> {
                    val query = page.subject.fill(target.template, page.entry.values, playing).trim()
                    if (query.isNotEmpty()) openRoute(searchRoute(query))
                }
                is ActionTarget.Route -> SafeModuleRoutes.sanitize(target.route)?.let(openRoute)
                is ActionTarget.SetField -> {
                    val next = target.value ?: if (page.entry.values[target.key].equals("true", ignoreCase = true)) "false" else "true"
                    page.onField(page.entry.id, target.key, next)
                }
                is ActionTarget.SetAppSetting -> scope.launch { applySafeAppSetting(context, target.key, target.value) }
                // a module sheet holds player blocks, so it only opens over the player, never from a page
                is ActionTarget.ModuleSheet -> Unit
                is ActionTarget.Sheet -> when (target.kind) {
                    SheetKind.ModulePage -> {
                        val pid = target.pageId
                        if (pid != null && page.sheetDepth < ModuleActionGuard.MaxModuleSheetDepth) {
                            menuState.show {
                                ModulePageSheetContent(
                                    moduleId = page.entry.id,
                                    pageId = pid,
                                    onNavigateRoute = openRoute,
                                    onDismiss = { menuState.dismiss() },
                                    depth = page.sheetDepth + 1,
                                )
                            }
                        }
                    }
                    SheetKind.Devices -> zoomSheets.showDeviceIntegration(devices)
                    SheetKind.Share -> playing?.let { SongShare.open(it) }
                    SheetKind.SongMenu -> playing?.let { openSongMenuSheet(it, database, page.navController, menuState, scope) }
                    SheetKind.PlaylistMenu -> page.subject.playlistEntity?.let { pl ->
                        menuState.show { PlaylistMenu(playlist = pl, coroutineScope = scope, onDismiss = { menuState.dismiss() }, navController = page.navController) }
                    }
                    SheetKind.ArtistMenu -> page.subject.artist?.id?.let { artistId ->
                        scope.launch {
                            val entity = withContext(Dispatchers.IO) { database.artist(artistId).firstOrNull() }
                            if (entity != null) {
                                menuState.show { ArtistMenu(originalArtist = entity, coroutineScope = scope, onDismiss = { menuState.dismiss() }) }
                            }
                        }
                    }
                    else -> page.onRequestSheet(target.kind)
                }
                is ActionTarget.Control -> when (target.kind) {
                    ButtonKind.PlayPause -> connection?.player?.let { if (it.isPlaying) it.pause() else it.play() }
                    ButtonKind.Play -> connection?.player?.play()
                    ButtonKind.Pause -> connection?.player?.pause()
                    ButtonKind.Previous -> connection?.seekToPrevious()
                    ButtonKind.Next -> connection?.seekToNext()
                    ButtonKind.Like -> connection?.toggleLike()
                    ButtonKind.Shuffle -> connection?.player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                    ButtonKind.Repeat -> connection?.player?.toggleRepeatMode()
                    ButtonKind.Forward -> connection?.let { it.seekTo((it.player.currentPosition + 10_000L).coerceAtLeast(0L)) }
                    ButtonKind.Rewind -> connection?.let { it.seekTo((it.player.currentPosition - 10_000L).coerceAtLeast(0L)) }
                    ButtonKind.Forward30 -> connection?.let { it.seekTo((it.player.currentPosition + 30_000L).coerceAtLeast(0L)) }
                    ButtonKind.Rewind30 -> connection?.let { it.seekTo((it.player.currentPosition - 30_000L).coerceAtLeast(0L)) }
                    ButtonKind.Restart -> connection?.seekTo(0L)
                    ButtonKind.OpenPlayer -> if (playing != null) playerSheet?.expandSoft()
                    ButtonKind.Close, ButtonKind.Back -> page.onDismissSheet?.invoke() ?: page.navController.navigateUp()
                    ButtonKind.Settings -> openRoute("mochy_settings")
                    ButtonKind.Search -> openRoute("search_input")
                    ButtonKind.Home -> openRoute("home")
                    ButtonKind.Equalizer -> openRoute("equalizer")
                    ButtonKind.AudioLab -> openRoute("audio_lab")
                    ButtonKind.Artist -> (page.subject.artist?.id ?: playing?.artists?.firstOrNull()?.id)?.let { openRoute("artist/${Uri.encode(it)}") }
                    ButtonKind.Album -> playing?.album?.id?.let { openRoute("album/${Uri.encode(it)}") }
                    ButtonKind.FloatingLyrics -> scope.launch { applySafeAppSetting(context, "floatingLyrics", null) }
                    ButtonKind.Library -> connection?.toggleLibrary()
                    ButtonKind.Download -> {
                        val song = playing
                        if (song != null && song.localUri == null) {
                            val existing = downloadUtil.downloads.value[song.id]?.state
                            if (existing == Download.STATE_COMPLETED || existing == Download.STATE_DOWNLOADING || existing == Download.STATE_QUEUED) {
                                DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.id, false)
                            } else {
                                val req = DownloadRequest.Builder(song.id, song.id.toUri())
                                    .setCustomCacheKey(song.id)
                                    .setData(song.title.toByteArray())
                                    .build()
                                DownloadService.sendAddDownload(context, ExoDownloadService::class.java, req, false)
                            }
                        }
                    }
                    ButtonKind.Pin -> playing?.let { song ->
                        scope.launch(Dispatchers.IO) {
                            if (database.speedDialDao.isPinned(song.id).first()) {
                                database.speedDialDao.delete(song.id)
                            } else {
                                database.pinToSpeedDial(
                                    SpeedDialItem(
                                        id = song.id, title = song.title, subtitle = song.artists.joinToString(", ") { it.name },
                                        thumbnailUrl = song.thumbnailUrl, type = "SONG", explicit = song.explicit,
                                    ),
                                )
                            }
                        }
                    }
                    ButtonKind.Radio -> playing?.let { connection.playQueue(YouTubeQueue.radio(it)) }
                    ButtonKind.Share -> playing?.let { SongShare.open(it) }
                    ButtonKind.VolumeUp -> adjustVolume(context, AudioManager.ADJUST_RAISE)
                    ButtonKind.VolumeDown -> adjustVolume(context, AudioManager.ADJUST_LOWER)
                    ButtonKind.Mute -> adjustVolume(context, AudioManager.ADJUST_TOGGLE_MUTE)
                    ButtonKind.SleepTimer -> page.onRequestSheet(SheetKind.SleepTimer)
                    ButtonKind.Output -> page.onRequestSheet(SheetKind.Output)
                    ButtonKind.Speed -> page.onRequestSheet(SheetKind.Speed)
                    ButtonKind.SongDetails -> page.onRequestSheet(SheetKind.SongDetails)
                    ButtonKind.StreamInfo -> page.onRequestSheet(SheetKind.StreamInfo)
                    ButtonKind.AddToPlaylist -> page.onRequestSheet(SheetKind.AddToPlaylist)
                    ButtonKind.CreatePlaylist -> page.onRequestSheet(SheetKind.CreatePlaylist)
                    ButtonKind.LyricsProvider -> page.onRequestSheet(SheetKind.LyricsProvider)
                    ButtonKind.LyricsOffset -> page.onRequestSheet(SheetKind.LyricsOffset)
                    ButtonKind.LyricsTranslation -> page.onRequestSheet(SheetKind.LyricsTranslation)
                    ButtonKind.LyricsEditor -> page.onRequestSheet(SheetKind.LyricsEditor)
                    ButtonKind.SongEdit -> page.onRequestSheet(SheetKind.SongEdit)
                    ButtonKind.VideoQuality -> page.onRequestSheet(SheetKind.VideoQuality)
                    ButtonKind.CanvasQuality -> page.onRequestSheet(SheetKind.CanvasQuality)
                    ButtonKind.VideoPicker -> page.onRequestSheet(SheetKind.VideoPicker)
                    ButtonKind.Devices -> zoomSheets.showDeviceIntegration(devices)
                    ButtonKind.OtherMenu, ButtonKind.Menu -> page.onRequestSheet(SheetKind.PlayerMenu)
                    ButtonKind.Lyrics, ButtonKind.Queue, ButtonKind.EditPlayer, ButtonKind.Customise, ButtonKind.Editor, ButtonKind.FullVideo, ButtonKind.Rotate -> {
                        if (playing != null) playerSheet?.expandSoft()
                    }
                }
            }
        }
    }
}

/** hosts any native Mochy sheet requested directly from a custom module page */
@Composable
private fun PageNativeSheetHost(kind: SheetKind, page: PageContext, onDismiss: () -> Unit) {
    val connection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val metadata = connection?.mediaMetadata?.value
    when (kind) {
        SheetKind.SleepTimer -> SleepTimerSheet(onDismiss = onDismiss)
        SheetKind.Output -> DeviceOutputSheet(onDismiss = onDismiss)
        SheetKind.Speed -> PlaybackSpeedSheet(onDismiss = onDismiss)
        SheetKind.SongDetails -> SongDetailsSheet(
            onDismiss = onDismiss,
            onOpenArtist = { id -> onDismiss(); page.navController.navigate("artist/$id") },
            onOpenAlbum = { id -> onDismiss(); page.navController.navigate("album/$id") },
        )
        SheetKind.StreamInfo -> StreamInfoSheet(onDismiss = onDismiss)
        SheetKind.AddToPlaylist -> if (metadata != null) {
            AddToPlaylistDialog(
                isVisible = true,
                onGetSong = {
                    database.transaction { insert(metadata) }
                    listOf(metadata.id)
                },
                onDismiss = onDismiss,
                topSongId = metadata.id,
                topSongArtworkUrl = metadata.thumbnailUrl,
            )
        } else {
            onDismiss()
        }
        SheetKind.CreatePlaylist -> CreatePlaylistDialog(onDismiss = onDismiss)
        SheetKind.LyricsProvider -> LyricsProviderSheet(onDismiss = onDismiss)
        SheetKind.LyricsOffset -> LyricsOffsetSheet(onDismiss = onDismiss)
        SheetKind.LyricsTranslation -> LyricsTranslationSheet(onDismiss = onDismiss)
        SheetKind.LyricsEditor -> LyricsEditorSheet(onDismiss = onDismiss)
        SheetKind.SongEdit -> if (metadata != null) SongEditSheet(metadata = metadata, onDismiss = onDismiss) else onDismiss()
        SheetKind.VideoQuality -> VideoQualitySheet(session = YouTubeVideoSessions.showing, onDismiss = onDismiss)
        SheetKind.CanvasQuality -> CanvasQualitySheet(onDismiss = onDismiss)
        SheetKind.VideoPicker -> if (metadata != null) {
            VideoPickerSheet(songId = metadata.id, title = metadata.title, artist = metadata.artists.joinToString { it.name }, onDismiss = onDismiss)
        } else {
            onDismiss()
        }
        SheetKind.PlayerMenu -> PlayerActionMenu(
            fromLyrics = false,
            onFullVideo = onDismiss,
            onEqualizer = { onDismiss(); page.navController.navigate("equalizer") },
            onOpenRoute = { route -> onDismiss(); page.navController.navigate(route) },
            onDismiss = onDismiss,
            onEditPlayer = onDismiss,
            modifier = Modifier.fillMaxSize(),
        )
        else -> onDismiss()
    }
}

/** the module's shader in a box that follows a finger, its fallback colour where it can't run, and why when it didn't compile */
@Composable
private fun PageShaderBlock(block: PageBlock.Shader, skin: ModuleSkin, values: Map<String, String>) {
    val shape = RoundedCornerShape(block.corner)
    val box = Modifier.fillMaxWidth().height(block.height).clip(shape)
    val loaded = rememberPageProgram(skin, block.program)
    val program = (loaded as? PageProgram.Ready)?.program
    var failed by remember(program) { mutableStateOf(false) }
    if (program != null && !failed) {
        PageShaderFill(program, block.program, box, touchable = true, onFailed = { failed = true })
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(box.background(block.fallback?.onChrome(accent, values) ?: LabColors.Card))
    (loaded as? PageProgram.Broken)?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.module_page_shader_problem, it.why), fontFamily = InterFontFamily, fontSize = 12.sp,
            lineHeight = 17.sp, color = LabColors.Secondary, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private val CardGlassTint = Color.Black.copy(alpha = 0.28f)
private val PageNullMeta = MutableStateFlow<MediaMetadata?>(null)

@Composable
private fun PageBlockView(block: PageBlock, page: PageContext) {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val needsMeta = when (block) {
        is PageBlock.Heading -> '{' in block.text
        is PageBlock.Paragraph -> '{' in block.text
        is PageBlock.Button -> '{' in block.text || '{' in block.link
        else -> false
    }
    val playing by remember(connection, needsMeta) {
        if (needsMeta) connection?.mediaMetadata ?: PageNullMeta else PageNullMeta
    }.collectAsState()
    val skin = page.skin
    val navController = page.navController
    when (block) {
        is PageBlock.Heading -> Text(
            page.subject.fill(block.text, page.entry.values, playing), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
        is PageBlock.Paragraph -> Text(
            page.subject.fill(block.text, page.entry.values, playing), fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, color = LabColors.Secondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        is PageBlock.Picture -> AsyncImage(
            model = skin.resolve(block.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(block.height).clip(RoundedCornerShape(block.corner)),
        )
        is PageBlock.Button -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .clickable {
                    val url = page.subject.fill(block.link, page.entry.values, playing)
                    if (ModuleActionGuard.isSafeFinalUrl(url)) context.openDevUrl(url)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(page.subject.fill(block.text, page.entry.values, playing), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.Black)
        }
        PageBlock.Divider -> Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        is PageBlock.Space -> Spacer(Modifier.height(block.size))
        is PageBlock.Song -> PageRow(R.drawable.play, block.title, block.artist) {
            connection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = block.id)))
        }
        is PageBlock.Playlist -> PageRow(R.drawable.queue_music, block.title, block.subtitle) {
            navController.navigate("online_playlist/${Uri.encode(block.id)}")
        }
        is PageBlock.Album -> PageRow(R.drawable.album, block.title, block.subtitle) {
            navController.navigate("album/${Uri.encode(block.id)}")
        }
        is PageBlock.Artist -> PageRow(R.drawable.person, block.title, null) {
            navController.navigate("artist/${Uri.encode(block.id)}")
        }
        is PageBlock.Search -> PageRow(R.drawable.search_filled, block.title, block.query.takeIf { it != block.title }) {
            navController.navigate(searchRoute(block.query))
        }
        else -> Unit
    }
}

/** a row to tap: something to play or open, with what it is on the left */
@Composable
private fun PageRow(@DrawableRes icon: Int, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LabColors.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(LabColors.Row),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(painterResource(R.drawable.navigate_next), null, tint = LabColors.Secondary, modifier = Modifier.size(18.dp))
    }
}
