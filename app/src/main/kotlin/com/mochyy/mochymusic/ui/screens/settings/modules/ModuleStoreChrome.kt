// the module store's floating layer: back button and account pill, the search circle that rides beside "categories" and slides up to the bar, the chips that stick under it, and the progressive blur behind them once they do

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.browser.FeedPerson
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.scrollEdgeFade
import kotlin.math.roundToInt

internal val StorePadding = 20.dp
internal val StoreButton = 44.dp
internal val StoreTitleHeight = 48.dp
internal val StoreChipsHeight = 32.dp
private val StoreBarTop = 12.dp
// between the bar and the chips once they've stuck under it
private val StoreBarGap = 12.dp
// between the account pill and the search circle when the circle sits in the bar
private val StoreButtonGap = 10.dp
// on a tablet the open search stops here instead of running across the whole store
private val StoreSearchMaxWidth = 460.dp
// how far the blur runs past the stuck chips before it fades out
private val StoreBlurTail = 30.dp

internal const val CategoriesKey = "categories"
internal const val ChipsKey = "chips"

/** where the list's first row starts, under the floating bar */
internal fun storeContentTop(statusTop: Dp): Dp = statusTop + StoreBarTop + StoreButton + 22.dp

private fun stuckChipsTop(statusTop: Dp): Dp = statusTop + StoreBarTop + StoreButton + StoreBarGap

// where a row of the list sits from the page's top right now, or null when it isn't on screen
private fun LazyListState.topOf(key: Any): Int? {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return null
    return item.offset - info.viewportStartOffset
}

// true once the row at this index has gone up past the top of the screen
private fun LazyListState.scrolledPast(index: Int): Boolean = layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.index > index } ?: false

/** 0 to 1 once the list has moved off the top, for the blur and dark fade behind the bar like home's */
@Composable
internal fun rememberVeil(list: LazyListState): State<Float> {
    val scrolled by remember(list) { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    return animateFloatAsState(if (scrolled) 1f else 0f, tween(300, easing = FastOutSlowInEasing), label = "veil")
}

/** 0 to 1 as the chips reach the bar and stick there; read only in layout and draw, so scrolling never recomposes the page */
@Composable
internal fun rememberStick(list: LazyListState, chipsIndex: Int, statusTop: Dp): State<Float> {
    val stuckPx = with(LocalDensity.current) { stuckChipsTop(statusTop).roundToPx() }
    val stuck by remember(list, chipsIndex, stuckPx) {
        derivedStateOf { list.scrolledPast(chipsIndex) || (list.topOf(ChipsKey)?.let { it <= stuckPx } ?: false) }
    }
    return animateFloatAsState(if (stuck) 1f else 0f, tween(340, easing = FastOutSlowInEasing), label = "stick")
}

/** everything that floats over the store's list; [showStore] is false while there's only a message on screen */
@Composable
internal fun StoreChrome(
    statusTop: Dp,
    person: FeedPerson?,
    glass: GlassState,
    list: LazyListState,
    chipsIndex: Int,
    veil: State<Float>,
    stick: State<Float>,
    searchShown: State<Float>,
    showStore: Boolean,
    categories: List<String>,
    category: String?,
    onCategory: (String?) -> Unit,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onBack: () -> Unit,
) {
    val density = LocalDensity.current
    val barTop = statusTop + StoreBarTop
    Box(Modifier.fillMaxSize()) {
        if (showStore) {
            StoreBlur(glass, veil, height = stuckChipsTop(statusTop) + StoreChipsHeight + StoreBlurTail)
            val stuckPx = with(density) { stuckChipsTop(statusTop).roundToPx() }
            StoreChips(
                categories = categories,
                selected = category,
                onSelect = onCategory,
                // sits on its slot in the list until that reaches the bar, then stays put under it
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        val slot = list.topOf(ChipsKey)
                        val y = when {
                            list.scrolledPast(chipsIndex) -> stuckPx
                            slot != null -> maxOf(slot, stuckPx)
                            else -> constraints.maxHeight
                        }
                        placeable.place(0, y)
                    }
                },
            )
        }
        BarButton(R.drawable.arrow_back_ios, stringResource(R.string.back_content_desc), onBack, Modifier.padding(top = barTop, start = StorePadding))
        if (person != null && person.name.isNotBlank()) {
            AccountChip(
                person,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = barTop, end = StorePadding)
                    .graphicsLayer {
                        // steps aside for the search circle when it comes up to the bar, and hides under the open search field
                        val p = if (showStore) stick.value else 0f
                        translationX = -(StoreButton + StoreButtonGap).toPx() * p
                        alpha = 1f - searchShown.value * p
                    },
            )
        }
        if (showStore) {
            StoreSearch(
                open = searching,
                query = query,
                onQuery = onQuery,
                onOpen = onSearch,
                onClose = onCloseSearch,
                shown = searchShown,
                modifier = Modifier.searchPlacement(list, stick, searchShown, barTop),
            )
        }
    }
}

// the circle rides beside the categories title, slides up into the bar as the chips stick, and widens into a field when open
private fun Modifier.searchPlacement(list: LazyListState, stick: State<Float>, shown: State<Float>, barTop: Dp): Modifier = layout { measurable, constraints ->
    val p = stick.value
    val pad = StorePadding.toPx()
    val button = StoreButton.toPx()
    val screen = constraints.maxWidth.toFloat()
    // open, it covers the title row, or the bar from the back button to the edge once it's up there
    val wide = lerp(screen - pad * 2, screen - pad * 2 - button - StoreButtonGap.toPx(), p).coerceAtMost(StoreSearchMaxWidth.toPx())
    val width = lerp(button, wide, shown.value).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(width, button.roundToInt()))
    val barY = barTop.toPx()
    val title = list.topOf(CategoriesKey)?.let { it + (StoreTitleHeight.toPx() - button) / 2f } ?: barY
    val y = lerp(title.coerceAtLeast(barY), barY, p)
    layout(placeable.width, placeable.height) {
        placeable.place(IntOffset((screen - pad - width).roundToInt(), y.roundToInt()))
    }
}

// the blur and dark fade behind the bar and the stuck chips, there once the list moves; the list is only captured while it shows
@Composable
private fun StoreBlur(glass: GlassState, veil: State<Float>, height: Dp) {
    Box(Modifier.fillMaxWidth().height(height).graphicsLayer { alpha = veil.value }) {
        ProgressiveGlassBackground(
            state = glass,
            maxBlurRadius = { 40f * veil.value },
            foundationColor = Color.Black.copy(alpha = 0.55f),
            direction = BlurDirection.BottomToTop,
            steps = 3,
            modifier = Modifier.fillMaxSize(),
        )
        // the top darkens so the status bar and buttons stay readable over a bright banner
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val scrim = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.85f), 0.45f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent)
                onDrawBehind { drawRect(scrim) }
            },
        )
    }
}

@Composable
private fun BarButton(icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(StoreButton).clip(CircleShape).background(BrowserCard).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** all, then every tag of the forum, the way discord's flair names them */
@Composable
private fun StoreChips(categories: List<String>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier) {
    val scroll = rememberScrollState()
    Row(
        modifier.fillMaxWidth().height(StoreChipsHeight).scrollEdgeFade(scroll, 28.dp).horizontalScroll(scroll).padding(horizontal = StorePadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StoreChip(stringResource(R.string.module_browser_all), selected == null) { onSelect(null) }
        categories.forEach { name -> StoreChip(name, selected == name) { onSelect(name) } }
    }
}

@Composable
private fun StoreChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(StoreChipsHeight)
            .clip(RoundedCornerShape(StoreChipsHeight / 2))
            .background(if (on) BrowserChipOn else BrowserCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) Color.White else BrowserSoft, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
    }
}

// one shape for both: a circle with the search icon, or the field it widens into
@Composable
private fun StoreSearch(open: Boolean, query: String, onQuery: (String) -> Unit, onOpen: () -> Unit, onClose: () -> Unit, shown: State<Float>, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(open) {
        if (open) focus.requestFocus() else focusManager.clearFocus()
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(StoreButton / 2))
            .background(BrowserCard)
            .clickable(enabled = !open, onClick = onOpen)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.search_outline), stringResource(R.string.module_browser_search), tint = Color.White, modifier = Modifier.size(24.dp))
        if (open) {
            Box(Modifier.weight(1f).padding(start = 10.dp).graphicsLayer { alpha = shown.value }, contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.module_browser_search), color = BrowserSoft, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    textStyle = TextStyle(color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                painterResource(R.drawable.close), stringResource(R.string.module_store_close_search), tint = Color.White,
                modifier = Modifier.size(22.dp).graphicsLayer { alpha = shown.value }.clip(CircleShape).clickable(onClick = onClose),
            )
        }
    }
}
