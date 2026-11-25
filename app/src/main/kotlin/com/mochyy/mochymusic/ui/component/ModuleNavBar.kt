// a look module's own nav bar: its tabs and pictures, its marker for the lit tab, its colours and shader over mochy's glass, and its tabs' animations, sized to the screen it was made for (docs/wiki, your own nav bar)

package com.mochyy.mochymusic.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalGlassState
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.skin.ActiveBar
import com.mochyy.mochymusic.modules.skin.BarLabels
import com.mochyy.mochymusic.modules.skin.BarTarget
import com.mochyy.mochymusic.modules.skin.GradientKind
import com.mochyy.mochymusic.modules.skin.IndicatorKind
import com.mochyy.mochymusic.modules.skin.SkinBar
import com.mochyy.mochymusic.modules.skin.SkinBarTab
import com.mochyy.mochymusic.modules.skin.SkinColor
import com.mochyy.mochymusic.modules.skin.skinColor
import com.mochyy.mochymusic.ui.player.styles.PoseSum
import com.mochyy.mochymusic.ui.player.styles.ScaledContent
import com.mochyy.mochymusic.ui.screens.Screens
import com.mochyy.mochymusic.ui.screens.settings.modules.modulePageRoute

/** one tab as the bar draws it: what it opens, and the mochy tab behind it when it's one of those */
private class BarSlot(val tab: SkinBarTab, val screen: Screens?)

@Composable
internal fun ModuleNavBar(
    active: ActiveBar,
    navigationItems: List<Screens>,
    selectedTabRoute: String,
    currentPage: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    onOpenRoute: (String) -> Unit,
    onSearchDoubleTap: (() -> Unit)?,
    pureBlack: Boolean,
    obscured: () -> Boolean,
    modifier: Modifier,
) {
    val bar = active.bar
    val moduleId = active.skin.moduleId
    val slots = remember(bar, navigationItems) {
        val tabs = bar.tabs.ifEmpty { navigationItems.map { SkinBarTab(BarTarget.Tab(it.route), null, null, null) } }
        tabs.mapNotNull { tab ->
            when (val target = tab.target) {
                is BarTarget.Tab -> navigationItems.firstOrNull { it.route == target.route }?.let { BarSlot(tab, it) }
                is BarTarget.Page, is BarTarget.Route -> BarSlot(tab, null)
            }
        }
    }
    // a module page that's one of the tabs lights it; anywhere else the mochy tab or route it belongs to does
    val selectedIndex = slots.indexOfFirst { (it.tab.target as? BarTarget.Page)?.let { p -> "$moduleId/${p.id}" == currentPage } == true }
        .takeIf { it >= 0 }
        ?: slots.indexOfFirst { (it.tab.target as? BarTarget.Route)?.route == selectedTabRoute && currentPage == null }
            .takeIf { it >= 0 }
        ?: slots.indexOfFirst { it.screen?.route == selectedTabRoute && currentPage == null }
    val factor = bar.size.factor(LocalConfiguration.current.screenWidthDp.toFloat(), active.values)
    val height = min(bar.height * factor, MaxBarHeight)
    val shape = bar.corner?.let { RoundedCornerShape(it * factor) } ?: NavBarShape
    val glassState = LocalGlassState.current ?: remember { GlassState() }
    val accentWash = rememberChromeAccentWash()
    val accent = MaterialTheme.colorScheme.primary
    val pill = rememberNavPill(selectedIndex)
    val density = LocalDensity.current
    val glassPill = remember(pill, slots.size, density) {
        { size: Size -> pill.forGlass(size, with(density) { NavRowPadding.toPx() }, slots.size, density) }
    }
    val onTab: (Int) -> Unit = { index ->
        val slot = slots.getOrNull(index)
        when {
            slot == null -> Unit
            slot.screen != null -> onItemClick(slot.screen, index == selectedIndex)
            else -> when (val target = slot.tab.target) {
                is BarTarget.Page -> onOpenRoute(modulePageRoute(moduleId, target.id))
                is BarTarget.Route -> onOpenRoute(target.route)
                is BarTarget.Tab -> Unit
            }
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = bar.side, vertical = 8.dp)
                .align(Alignment.TopCenter)
                .height(height)
                .press3D(maxTilt = 4f, pressedScale = 0.985f)
                .then(if (bar.shadow) Modifier.dropShadow(shape, ChromeShadow) else Modifier)
                .clip(shape)
                .drawWithContent {
                    drawContent()
                    if (bar.outline) drawChromeOutline(Offset.Zero, size, cornerRadius = bar.corner?.toPx()?.times(factor) ?: (size.height / 2f))
                },
        ) {
            if (bar.glass) FrostedChromeGlass(state = glassState, pureBlack = pureBlack, accentWash = accentWash, modifier = Modifier.fillMaxSize(), pill = glassPill, fallbackBlur = true)
            if (bar.fill.isNotEmpty()) Box(Modifier.fillMaxSize().background(barBrush(bar, accent, active.values)))
            if (active.program != null) {
                BarShader(active, pill, slots.size, accent, pureBlack, obscured)
            }
            ScaledContent(factor) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = NavRowPadding)
                        .barIndicator(bar, pill, slots.size, accent, active.values)
                        .then(if (bar.drag) Modifier.navPillDrag(pill, slots.size, selectedIndex) { onTab(it) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    slots.forEachIndexed { index, slot ->
                        BarTabView(
                            slot = slot,
                            bar = bar,
                            active = active,
                            lit = index == selectedIndex,
                            accent = accent,
                            onTap = { onTab(index) },
                            onDoubleTap = onSearchDoubleTap.takeIf { slot.screen == Screens.Search },
                        )
                    }
                }
            }
        }
    }
}

/** one tab: its picture (the module's, or mochy's own), its words, and the keyframes it plays when pressed, lit and while lit */
@Composable
private fun RowScope.BarTabView(
    slot: BarSlot,
    bar: SkinBar,
    active: ActiveBar,
    lit: Boolean,
    accent: Color,
    onTap: () -> Unit,
    onDoubleTap: (() -> Unit)?,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val interaction = remember { MutableInteractionSource() }
    val lastTap = remember { longArrayOf(0L) }
    val litness = animateFloatAsState(if (lit) 1f else 0f, tween(200), label = "module_tab_lit")
    val press = rememberOneShot(bar.press, trigger = null)
    val select = rememberOneShot(bar.select, trigger = lit.takeIf { it })
    val idle = rememberLoop(bar.idle, running = lit)
    val pose = remember { PoseSum() }
    val unlit = bar.color?.onChrome(accent, active.values) ?: Color.White.copy(alpha = UnlitAlpha)
    val shown = bar.active?.onChrome(accent, active.values) ?: Color.White
    val label = slot.tab.label ?: slot.screen?.let { stringResource(it.titleId) }
    val showLabel = label != null && (bar.labels == BarLabels.Always || (bar.labels == BarLabels.Selected && lit))
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressBounce(interaction, pressedScale = 0.85f)
            .clickable(interactionSource = interaction, indication = RemoteFocus) {
                press.play()
                tabTapped(lastTap, viewConfiguration.doubleTapTimeoutMillis, onDoubleTap, onTap)
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                pose.reset()
                press.addTo(pose)
                select.addTo(pose)
                idle.addTo(pose)
                translationX = pose.x.dp.toPx()
                translationY = pose.y.dp.toPx()
                scaleX = pose.scaleX
                scaleY = pose.scaleY
                rotationZ = pose.rotate
                alpha = pose.alpha.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin.Center
            },
        ) {
            TabPicture(slot, bar, active, { litness.value }, unlit, shown, label)
            if (showLabel) {
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    color = if (lit) shown else unlit,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

// the unlit picture with the lit one fading in over it; mochy's own icons are always tinted, a module's only when the bar names colours
@Composable
private fun TabPicture(slot: BarSlot, bar: SkinBar, active: ActiveBar, lit: () -> Float, unlit: Color, shown: Color, label: String?) {
    val size = bar.iconSize
    val off = slot.tab.icon
    val on = slot.tab.iconOn ?: off
    Box(contentAlignment = Alignment.Center) {
        if (off == null) {
            val screen = slot.screen
            if (screen != null) {
                val unlitIcon = coil3.compose.rememberAsyncImagePainter(model = screen.iconIdInactive)
                val litIcon = coil3.compose.rememberAsyncImagePainter(model = screen.iconIdActive)
                val icons: @Composable () -> Unit = {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(unlitIcon, contentDescription = label, tint = unlit, modifier = Modifier.size(size).fading { 1f - lit() })
                        Icon(litIcon, contentDescription = null, tint = shown, modifier = Modifier.size(size).fading(lit))
                    }
                }
                if (screen == Screens.Library) LibraryDownloadIcon(icons) else icons()
            } else {
                val fallbackRes = routeFallbackIcon(slot.tab.target)
                val painter = androidx.compose.ui.res.painterResource(fallbackRes)
                Icon(painter, contentDescription = label, tint = unlit, modifier = Modifier.size(size).fading { 1f - lit() })
                Icon(painter, contentDescription = null, tint = shown, modifier = Modifier.size(size).fading(lit))
            }
            return@Box
        }
        val tinted = bar.color != null || bar.active != null
        AsyncImage(
            model = active.skin.resolve(off), contentDescription = label, contentScale = ContentScale.Fit,
            colorFilter = if (tinted) ColorFilter.tint(unlit) else null,
            modifier = Modifier.size(size).fading { 1f - lit() },
        )
        AsyncImage(
            model = active.skin.resolve(on), contentDescription = null, contentScale = ContentScale.Fit,
            colorFilter = if (tinted) ColorFilter.tint(shown) else null,
            modifier = Modifier.size(size).fading(lit),
        )
    }
}

// see-through set in the layer, one draw each, so the fade never needs an offscreen pass
private fun Modifier.fading(alpha: () -> Float): Modifier = graphicsLayer {
    this.alpha = alpha().coerceIn(0f, 1f)
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/** the module's mark under the lit tab, drawn behind the tabs from the pill's springs so it never recomposes them */
private fun Modifier.barIndicator(bar: SkinBar, pill: NavPillState, tabCount: Int, accent: Color, values: Map<String, String>): Modifier = drawBehind {
    val indicator = bar.indicator
    if (indicator.kind == IndicatorKind.None) return@drawBehind
    val rect = pill.rect(size, tabCount, this) ?: return@drawBehind
    val color = indicator.color?.onChrome(accent, values) ?: if (indicator.kind == IndicatorKind.Pill) ChromeHighlightColor else Color.White
    val shown = pill.shown.value
    val lift = pill.lift.value
    when (indicator.kind) {
        IndicatorKind.Pill -> {
            val grow = lift * 3.dp.toPx()
            val corner = indicator.corner?.toPx() ?: (rect.height / 2f + grow)
            drawRoundRect(
                color = color.copy(alpha = (color.alpha * (1f + 0.7f * lift)).coerceAtMost(1f)),
                topLeft = Offset(rect.left - grow, rect.top - grow),
                size = Size(rect.width + grow * 2f, rect.height + grow * 2f),
                cornerRadius = CornerRadius(corner),
                alpha = shown,
            )
        }
        IndicatorKind.Dot -> drawCircle(color, radius = 2.5.dp.toPx() * (1f + 0.4f * lift), center = Offset(rect.center.x, rect.bottom - 2.dp.toPx()), alpha = shown)
        IndicatorKind.Line -> {
            val width = rect.width * (0.38f + 0.12f * lift)
            val thick = 3.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(rect.center.x - width / 2f, rect.bottom - thick),
                size = Size(width, thick),
                cornerRadius = CornerRadius(indicator.corner?.toPx() ?: (thick / 2f)),
                alpha = shown,
            )
        }
        IndicatorKind.Glow -> drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = rect.center, radius = rect.height * (0.9f + 0.2f * lift)),
            radius = rect.height * (0.9f + 0.2f * lift),
            center = rect.center,
            alpha = shown,
        )
        IndicatorKind.None -> Unit
    }
}

// the bar's own colours behind the tabs, running the way it asks
private fun barBrush(bar: SkinBar, accent: Color, values: Map<String, String>): Brush {
    val colors = bar.fill.map { it.onChrome(accent, values) }
    if (colors.size == 1) return SolidColor(colors[0])
    return when (bar.gradient) {
        GradientKind.Vertical -> Brush.verticalGradient(colors)
        GradientKind.Horizontal -> Brush.horizontalGradient(colors)
        GradientKind.Diagonal -> Brush.linearGradient(colors)
        GradientKind.Radial -> Brush.radialGradient(colors)
    }
}

/** a skin colour away from the player, where there's no song to take tones from: the player's tones read as white shades and the cover as the accent */
internal fun SkinColor.onChrome(accent: Color, values: Map<String, String>): Color = when (this) {
    is SkinColor.Fixed -> color
    SkinColor.Accent, SkinColor.Cover, SkinColor.Played -> accent
    SkinColor.Remaining -> accent.copy(alpha = 0.6f)
    SkinColor.InkSoft -> Color.White.copy(alpha = 0.6f)
    SkinColor.Chip -> Color.White.copy(alpha = 0.12f)
    SkinColor.Ink -> Color.White
    // a setting can't point at another setting, so this never loops
    is SkinColor.Setting -> skinColor(values[key])?.takeIf { it !is SkinColor.Setting }?.onChrome(accent, values) ?: fallback.onChrome(accent, values)
}

private fun routeFallbackIcon(target: BarTarget): Int = when (target) {
    is BarTarget.Page -> R.drawable.puzzle
    is BarTarget.Route -> when {
        target.route.startsWith("mochy_settings") || target.route.endsWith("_settings") -> R.drawable.settings
        target.route == "audio_lab" || target.route == "equalizer" -> R.drawable.equalizer
        target.route == "history" -> R.drawable.music_history
        target.route == "stats" -> R.drawable.trending_up
        target.route == "explore" || target.route == "mood_and_genres" -> R.drawable.explore_outlined
        target.route.startsWith("auto_playlist/liked") -> R.drawable.ic_untitled_heart
        target.route.startsWith("auto_playlist/downloaded") -> R.drawable.offline
        target.route.contains("playlist") -> R.drawable.queue_music
        target.route == "modules" || target.route == "module_browser" -> R.drawable.library_add
        else -> R.drawable.library_music
    }
    is BarTarget.Tab -> R.drawable.home_filled
}

// the tallest a bar can be and still fit the room mochy keeps for it
private val MaxBarHeight: Dp = 64.dp
private const val UnlitAlpha = 0.7f
