// a page a module brings, built from its blocks: words, pictures, web links, its own settings, songs and playlists to open, cards, shaders, a background of its own, and what it took over when it stands in for one of mochy's pages

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.modules.skin.ModuleSkin
import com.mochyy.mochymusic.modules.skin.PageBackground
import com.mochyy.mochymusic.modules.skin.PageBlock
import com.mochyy.mochymusic.modules.skin.SkinPage
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.component.onChrome
import com.mochyy.mochymusic.ui.player.styles.ScaledContent
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.search.searchRoute
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import com.music.innertube.models.WatchEndpoint

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
) {
    // a frosted card blurs the page's own background, captured only when a card asks
    val glass = remember(page) { if (page.frosted) GlassState() else null }
    val scale = page.size.factor(LocalConfiguration.current.screenWidthDp.toFloat(), entry.values)
    val blocks = PageContext(entry, skin, onField, navController, subject, onOriginal)
    Box(Modifier.fillMaxSize()) {
        if (page.background != null) PageBackdrop(page.background, skin, glass, entry.values)
        else if (glass != null) Box(Modifier.fillMaxSize().glassRoot(glass))
        if (page.header) {
            SubSettingsScaffold(title = subject.fill(page.title), backLabel = backLabel, onBack = { navController.navigateUp() }) {
                ScaledContent(scale) { Column { PageBlocks(page.blocks, blocks, glass) } }
                Spacer(Modifier.height(120.dp))
            }
        } else {
            // a page that draws its own header still keeps clear of the status bar, the nav bar and the mini player
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                    .padding(horizontal = 20.dp),
            ) {
                ScaledContent(scale) { Column { PageBlocks(page.blocks, blocks, glass) } }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** what every block on a page can reach: its module, the settings, where to go, and what the page took over */
internal class PageContext(
    val entry: ModuleEntry,
    val skin: ModuleSkin,
    val onField: (module: String, field: String, value: String) -> Unit,
    val navController: NavController,
    val subject: PageSubject,
    /** back to mochy's own page, on a takeover page */
    val onOriginal: (() -> Unit)?,
)

// the blocks one after another; a card draws its own the same way
@Composable
private fun PageBlocks(blocks: List<PageBlock>, page: PageContext, glass: GlassState?) {
    blocks.forEach { block ->
        when (block) {
            // the module's own setting, changed right here the same way as on its page
            is PageBlock.Setting -> {
                val one = page.entry.copy(fields = page.entry.fields.filter { it.id == block.key })
                if (one.fields.isNotEmpty()) {
                    ModuleOptions(entry = one, onPick = { _, _ -> }, onField = { field, value -> page.onField(page.entry.id, field, value) }, titled = false)
                }
            }
            is PageBlock.Card -> PageCardBlock(block, glass, page.entry.values) { PageBlocks(block.blocks, page, glass = null) }
            is PageBlock.Shader -> PageShaderBlock(block, page.skin, page.entry.values)
            is PageBlock.Cover, is PageBlock.Songs, is PageBlock.Control, is PageBlock.Player, PageBlock.Styles -> PageLiveBlock(block, page)
            else -> PageBlockView(block, page)
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** what the page draws behind everything: its colours, a picture, or its shader, captured for frosted cards when [glass] asks */
@Composable
private fun PageBackdrop(background: PageBackground, skin: ModuleSkin, glass: GlassState?, values: Map<String, String>) {
    val accent = MaterialTheme.colorScheme.primary
    val capture = glass?.let { Modifier.glassRoot(it) } ?: Modifier
    Box(Modifier.fillMaxSize().then(capture)) {
        when (background) {
            is PageBackground.Fill -> Box(Modifier.fillMaxSize().background(pageBrush(background.colors.map { it.onChrome(accent, values) })))
            is PageBackground.Picture -> {
                // a little bigger than the screen when it's blurred, so the blur's edges never show as a glow
                val blurred = background.blur > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                AsyncImage(
                    model = skin.resolve(background.image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(
                        if (blurred) Modifier.graphicsLayer { scaleX = 1.1f; scaleY = 1.1f }.blur(background.blur.dp) else Modifier,
                    ),
                )
                if (background.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
            }
            is PageBackground.Shader -> {
                val fallback = background.fallback.map { it.onChrome(accent, values) }
                val program = (rememberPageProgram(skin, background.program) as? PageProgram.Ready)?.program
                if (program != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PageShaderFill(program, background.program, Modifier.fillMaxSize(), touchable = false)
                } else {
                    Box(Modifier.fillMaxSize().background(pageBrush(fallback)))
                }
            }
        }
    }
}

/** blocks on a card: frosted glass over the page's background, or the card's own colours */
@Composable
private fun PageCardBlock(block: PageBlock.Card, glass: GlassState?, values: Map<String, String>, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(block.corner)
    val accent = MaterialTheme.colorScheme.primary
    val colors = block.colors.map { it.onChrome(accent, values) }
    Box(Modifier.fillMaxWidth().clip(shape)) {
        if (block.glass != null && glass != null) {
            val radius = with(LocalDensity.current) { block.glass.dp.toPx() }
            GlassPillBackground(
                state = glass,
                blurRadius = { radius },
                tint = colors.singleOrNull() ?: CardGlassTint,
                tileMode = android.graphics.Shader.TileMode.CLAMP,
                bleed = true,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(Modifier.matchParentSize().background(pageBrush(colors.ifEmpty { listOf(LabColors.Card) })))
        }
        Column(Modifier.fillMaxWidth().padding(block.padding)) { content() }
    }
}

/** the module's shader in a box that follows a finger, its fallback colour where it can't run, and why when it didn't compile */
@Composable
private fun PageShaderBlock(block: PageBlock.Shader, skin: ModuleSkin, values: Map<String, String>) {
    val shape = RoundedCornerShape(block.corner)
    val box = Modifier.fillMaxWidth().height(block.height).clip(shape)
    val loaded = rememberPageProgram(skin, block.program)
    val program = (loaded as? PageProgram.Ready)?.program
    if (program != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PageShaderFill(program, block.program, box, touchable = true)
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

private fun pageBrush(colors: List<Color>): Brush =
    if (colors.size == 1) SolidColor(colors[0]) else Brush.verticalGradient(colors.ifEmpty { listOf(Color.Black, Color.Black) })

private val CardGlassTint = Color.Black.copy(alpha = 0.28f)

@Composable
private fun PageBlockView(block: PageBlock, page: PageContext) {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val skin = page.skin
    val navController = page.navController
    when (block) {
        is PageBlock.Heading -> Text(
            page.subject.fill(block.text), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
        is PageBlock.Paragraph -> Text(
            page.subject.fill(block.text), fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, color = LabColors.Secondary,
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
                .clickable { context.openDevUrl(block.link) },
            contentAlignment = Alignment.Center,
        ) {
            Text(block.text, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.Black)
        }
        PageBlock.Divider -> Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        is PageBlock.Space -> Spacer(Modifier.height(block.size))
        is PageBlock.Song -> PageRow(R.drawable.play, block.title, block.artist) {
            connection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = block.id)))
        }
        is PageBlock.Playlist -> PageRow(R.drawable.queue_music, block.title, block.subtitle) {
            navController.navigate("online_playlist/${block.id}")
        }
        is PageBlock.Album -> PageRow(R.drawable.album, block.title, block.subtitle) {
            navController.navigate("album/${block.id}")
        }
        is PageBlock.Artist -> PageRow(R.drawable.person, block.title, null) {
            navController.navigate("artist/${block.id}")
        }
        is PageBlock.Search -> PageRow(R.drawable.search_filled, block.title, block.query.takeIf { it != block.title }) {
            navController.navigate(searchRoute(block.query))
        }
        // drawn by the page itself, which knows the module's values, its background and what it took over
        is PageBlock.Setting, is PageBlock.Card, is PageBlock.Shader, is PageBlock.Cover, is PageBlock.Songs, is PageBlock.Control, is PageBlock.Player, PageBlock.Styles -> Unit
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
