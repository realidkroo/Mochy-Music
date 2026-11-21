// a module's store page: its banner melting into a page tinted from it, its icon, name and get button, its flairs, size, kind and date, what its maker wrote, its previews and its post

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.rememberLazyListState
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.ui.component.DownscaledBlur
import com.mochyy.mochymusic.ui.component.LocalBlurQuality
import com.mochyy.mochymusic.ui.component.ScrollAwayCover
import com.mochyy.mochymusic.ui.component.rememberElementBlurAvailable
import com.mochyy.mochymusic.ui.utils.scrollEdgeFade
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.browser.FeedInstall
import com.mochyy.mochymusic.modules.browser.FeedModule
import com.mochyy.mochymusic.modules.browser.FeedResult
import com.mochyy.mochymusic.modules.browser.PackItem
import com.mochyy.mochymusic.ui.component.ActionPromptDialog
import com.mochyy.mochymusic.ui.component.DiscordFormattedText
import com.mochyy.mochymusic.ui.component.containerTransformTarget
import com.mochyy.mochymusic.ui.component.coverTintBackground
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.isWideLayout
import com.mochyy.mochymusic.viewmodels.ModuleBrowserViewModel
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import com.mochyy.mochymusic.viewmodels.packKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ModuleStoreRoutePattern = "module_store/{post}"
fun moduleStoreRoute(post: String) = "module_store/$post"

private val StoreBannerHeight = 330.dp
// how far the icon row climbs into the banner's faded bottom
private val HeaderOverlap = 118.dp
private val StorePageMaxWidth = 760.dp
// the page's own cards: a light glass over whatever colour the banner gave the page
private val PageCard = Color.White.copy(alpha = 0.16f)
private val PageInk = Color.White.copy(alpha = 0.88f)
private val PageSoft = Color.White.copy(alpha = 0.62f)
private val CardShape = RoundedCornerShape(26.dp)
private val PreviewShape = RoundedCornerShape(26.dp)
private const val StoreFootBlur = 28f
// the same red as removing a module on its own page
private val RemoveFill = Color(0x33FF453A)
private val RemoveInk = Color(0xFFFF8A80)
private val StoreFootMask = Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Black.copy(alpha = 0.85f), 1f to Color.Black)

/** [viewModel] is the browser's own, so the page shows the list already on screen instead of asking the feed again */
@Composable
fun ModuleStoreScreen(navController: NavController, post: String, viewModel: ModuleBrowserViewModel, modules: ModulesViewModel = hiltViewModel()) {
    val page by viewModel.page.collectAsState()
    val fetching by viewModel.fetching.collectAsState()
    val entries by modules.entries.collectAsState()
    val module = (page as? FeedResult.Modules)?.modules?.firstOrNull { it.id == post }
    val added = remember(entries) { addedModules(entries) }
    val act = rememberStoreAdd(viewModel, modules, navController)
    val addItem = rememberPackAdd(viewModel, modules, navController)
    val context = LocalContext.current
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val wide = isWideLayout()
    // the page takes its colour from the banner or icon, like an added module's own page
    val art = remember(module?.banner, module?.icon) { listOfNotNull(module?.banner ?: module?.icon) }
    val scroll = rememberScrollState()
    val bannerPx = with(LocalDensity.current) { StoreBannerHeight.toPx() }
    var removing by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            // grows out of the card that was tapped, like a playlist out of its cover
            .containerTransformTarget(moduleTransitionKey(post), sourceCornerRadius = 36.dp)
            .background(Color.Black)
            .coverTintBackground(art, seed = post),
    ) {
        // on a tablet or a phone on its side the words stay in a readable column under the full-width banner
        val column = Modifier.widthIn(max = StorePageMaxWidth).fillMaxWidth().padding(horizontal = 24.dp)
        Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
            // like home's hero: as the page scrolls the banner zooms, blurs and dissolves, read at draw time so it never recomposes
            ScrollAwayCover(
                progress = { (scroll.value / bannerPx).coerceIn(0f, 1f) },
                follow = 0.2f,
                modifier = Modifier.fillMaxWidth().height(StoreBannerHeight),
            ) {
                StoreBannerArt(module?.banner ?: module?.icon)
            }
            if (page is FeedResult.Unofficial) {
                ForkNote(column)
                return@Column
            }
            if (module == null) {
                Text(stringResource(R.string.module_store_gone), color = PageSoft, fontFamily = InterFontFamily, fontSize = 15.sp, modifier = column)
                return@Column
            }
            val action = actionFor(module, added)
            val entry = added.entryFor(module)
            Column(column.pullUp(HeaderOverlap)) {
                StoreHeader(module, action, fetching == module.id, onAction = { act(module) }, onRemove = { removing = true })
                Spacer(Modifier.height(22.dp))
                StoreFacts(module)
                if (action == BrowserAction.Update) {
                    Spacer(Modifier.height(22.dp))
                    UpdateNotice(module, installed = entry?.let { added.versions[it] }, fetching = fetching == module.id) { act(module) }
                }
                if (module.install == FeedInstall.Pack && module.pack.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    PackList(module, added, fetching) { item -> addItem(module, item) }
                }
                Spacer(Modifier.height(if (action == BrowserAction.Update) 14.dp else 26.dp))
                AboutCard(module)
            }
            if (removing && entry != null) {
                ActionPromptDialog(
                    title = stringResource(R.string.module_remove_title, module.name),
                    onDismiss = { removing = false },
                    onCancel = { removing = false },
                    onConfirm = {
                        modules.remove(entry)
                        removing = false
                    },
                ) {
                    Text(stringResource(R.string.module_remove_body), fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            if (module.previews.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                StorePreviews(module, column, wide)
            }
            Spacer(Modifier.height(22.dp))
            Box(column) {
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(PageCard).clickable { context.openDevUrl(module.postUrl) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.module_store_open_post), color = PageInk, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(bottom + 40.dp))
        }
        BackButton(onClick = { navController.navigateUp() })
    }
}

/** the banner with a progressive blur along its bottom like home's hero, melting into the page below */
@Composable
private fun StoreBannerArt(picture: String?) {
    val recorded = rememberGraphicsLayer()
    val blur = rememberElementBlurAvailable()
    // high quality on android 13+ grows the blur toward the bottom in one pass; a faded fixed blur bloomed around bright parts
    val smooth = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && LocalBlurQuality.current == BlurQuality.HIGH
    Box(
        Modifier
            .fillMaxSize()
            // one offscreen pass for a still picture, so its bottom melts into whatever colour the page took
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val fade = Brush.verticalGradient(0f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent)
                onDrawWithContent {
                    drawContent()
                    drawRect(fade, blendMode = BlendMode.DstIn)
                }
            },
    ) {
        AsyncImage(
            model = picture?.let(::artModel), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().drawWithContent {
                // recorded once, so the blurred foot draws the same picture without loading it twice
                recorded.record { this@drawWithContent.drawContent() }
                drawLayer(recorded)
            },
        )
        if (blur) {
            DownscaledBlur(
                radius = { BlurEffectCache.effectiveRadius(StoreFootBlur) },
                tileMode = Shader.TileMode.CLAMP,
                mask = StoreFootMask,
                growDown = if (smooth) true else null,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.45f),
            ) { fullSize ->
                translate(top = fullSize.height - recorded.size.height.toFloat()) { drawLayer(recorded) }
            }
        }
    }
}

/** icon, name and maker, with the round button that gets it, or takes it out again, at the end */
@Composable
private fun StoreHeader(module: FeedModule, action: BrowserAction, fetching: Boolean, onAction: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ModuleLogo(module.moduleId ?: module.id, module.name, size = 96, art = module.icon, corner = 0.26f)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                module.name, color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.8).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            module.author?.let {
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.module_browser_by, it), color = PageSoft, fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // a pack has no one thing to get, its modules each have their own button below
        if (action != BrowserAction.View) {
            Spacer(Modifier.width(12.dp))
            GetButton(action, fetching, onClick = if (action == BrowserAction.Added) onRemove else onAction)
        }
    }
}

/** a newer copy than the one that's in: from which version to which when both are known, with the button that brings it in */
@Composable
private fun UpdateNotice(module: FeedModule, installed: String?, fetching: Boolean, onUpdate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(PageCard).padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.update), null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.module_store_update_title), color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val known = installed?.takeIf { it.isNotBlank() }
            val detail = if (known != null && module.version != null && known != module.version) {
                stringResource(R.string.module_store_update_versions, known, module.version)
            } else {
                stringResource(R.string.module_store_update_newer)
            }
            Text(detail, color = PageSoft, fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        ActionPill(BrowserAction.Update, fetching, onClick = onUpdate)
    }
}

// the store's add pill as a round button: a download, an update, a red bin once it's in, a lock, or a globe for a web page
@Composable
private fun GetButton(action: BrowserAction, fetching: Boolean, onClick: () -> Unit) {
    val (icon, label) = when (action) {
        BrowserAction.Add -> R.drawable.download to R.string.module_browser_add
        BrowserAction.Update -> R.drawable.update to R.string.module_browser_update
        BrowserAction.Added -> R.drawable.delete to R.string.module_remove
        BrowserAction.Locked -> R.drawable.lock to R.string.module_browser_locked
        BrowserAction.Open -> R.drawable.language to R.string.module_browser_open
        BrowserAction.View -> R.drawable.download to R.string.module_browser_view
    }
    val (fill, ink) = when (action) {
        BrowserAction.Added -> RemoveFill to RemoveInk
        BrowserAction.Locked -> PageCard to Color.White
        else -> Color.White to Color.Black
    }
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = !fetching, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (fetching) {
            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(painterResource(icon), stringResource(label), tint = ink, modifier = Modifier.size(18.dp))
        }
    }
}

// flairs | size | how it's added | date updated, split by thin lines, each part only when there's something to say
@Composable
private fun StoreFacts(module: FeedModule) {
    val context = LocalContext.current
    val size = remember(module.size) { module.size?.let { Formatter.formatShortFileSize(context, it) } }
    val date = remember(module.updated) { module.updated?.let { DayMonthYear.format(Date(it)) } }
    val install = stringResource(
        when (module.install) {
            FeedInstall.Zip -> R.string.module_store_install_zip
            FeedInstall.Link -> R.string.module_store_install_link
            FeedInstall.Web -> R.string.module_store_install_web
            FeedInstall.Pack -> R.string.module_store_install_pack
        },
    )
    // how it's added always shows, so each part before it brings its own line and the date brings one after
    val factsScroll = rememberScrollState()
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).scrollEdgeFade(factsScroll, 28.dp).horizontalScroll(factsScroll),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (module.tags.isNotEmpty()) {
            Flairs(module.tags)
            FactLine()
        }
        size?.let {
            BigFact(it)
            FactLine()
        }
        BigFact(install)
        date?.let {
            FactLine()
            DateFact(it)
        }
    }
}

@Composable
private fun FactLine() {
    Box(Modifier.padding(horizontal = 14.dp).width(1.5.dp).fillMaxHeight().background(PageSoft))
}

// the first two flairs stacked in small pills, as the forum names them
@Composable
private fun Flairs(tags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        tags.take(2).forEach { tag ->
            Text(
                tag, color = PageInk, fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(PageCard).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun BigFact(text: String) {
    Text(text, color = PageInk, fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 26.sp, letterSpacing = (-0.8).sp, maxLines = 1)
}

@Composable
private fun DateFact(date: String) {
    Column {
        Text(stringResource(R.string.module_store_updated), color = PageSoft, fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 10.sp)
        Text(date, color = PageInk, fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, maxLines = 1)
    }
}

/** a pack's modules, each with its own button, added one by one */
@Composable
private fun PackList(module: FeedModule, added: AddedModules, fetching: String?, onAdd: (PackItem) -> Unit) {
    Text(stringResource(R.string.module_store_pack_title), color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.5).sp)
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        module.pack.forEach { item ->
            PackRow(item, actionFor(module, item, added), fetching == packKey(module, item)) { onAdd(item) }
        }
    }
}

@Composable
private fun PackRow(item: PackItem, action: BrowserAction, fetching: Boolean, onAction: () -> Unit) {
    val context = LocalContext.current
    val detail = if (item.isLink) {
        stringResource(R.string.module_store_pack_link)
    } else {
        remember(item) { listOfNotNull(item.version, item.size?.let { Formatter.formatShortFileSize(context, it) }).joinToString(" · ") }
    }
    Row(Modifier.fillMaxWidth().clip(CardShape).background(PageCard).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ModuleLogo(item.moduleId ?: item.id, item.name, size = 52, art = item.icon, corner = 0.28f)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, color = PageSoft, fontFamily = InterFontFamily, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        ActionPill(action, fetching, onClick = onAction)
    }
}

// what the maker wrote, with discord's bold, links, emoji and the rest kept
@Composable
private fun AboutCard(module: FeedModule) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(PageCard).padding(horizontal = 22.dp, vertical = 18.dp)) {
        Text(stringResource(R.string.module_store_about), color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(6.dp))
        if (module.description.isNotBlank()) {
            DiscordFormattedText(module.description, TextStyle(color = PageInk, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp))
        } else {
            Text(stringResource(R.string.module_store_no_about), color = PageSoft, fontFamily = InterFontFamily, fontSize = 14.sp)
        }
    }
}

/** the maker's screenshots, tall and side by side; bigger on a tablet */
@Composable
private fun StorePreviews(module: FeedModule, column: Modifier, wide: Boolean) {
    val width: Dp = if (wide) 220.dp else 170.dp
    Text(stringResource(R.string.module_store_previews), color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.5).sp, modifier = column)
    Spacer(Modifier.height(12.dp))
    val description = stringResource(R.string.module_store_preview, module.name)
    val row = rememberLazyListState()
    LazyRow(
        Modifier.widthIn(max = StorePageMaxWidth).fillMaxWidth().scrollEdgeFade(row, 28.dp),
        state = row,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(module.previews, key = { it }, contentType = { "preview" }) { picture ->
            AsyncImage(
                model = picture, contentDescription = description, contentScale = ContentScale.Crop,
                modifier = Modifier.width(width).height(width * 1.9f).clip(PreviewShape).background(PageCard),
            )
        }
    }
}

// day - month - year the way the mock writes it, the same on every phone
private val DayMonthYear = SimpleDateFormat("dd - MM - yy", Locale.ROOT)
