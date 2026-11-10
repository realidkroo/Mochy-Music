// the page every audio lab screen sits on: a dotted backdrop with the title and picture fixed, and a black sheet that slides up over it

package com.mochyy.mochymusic.ui.screens.audiolab

import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlin.math.roundToInt
import com.mochyy.mochymusic.ui.utils.stableStatusBars

/** the backdrop behind the sheet, and the sheet itself */
private val PageBackdrop = Color(0xFF141416)
private val SheetColor = Color.Black
private val SheetCorner = 40.dp

/** past this the sheet's content stops widening, so a tablet reads like a wide phone */
private val ContentMaxWidth = 680.dp
private val SideMargin = 24.dp
private val BackSize = 32.dp

/** how the page's two-line title is set, audio over a big fx say */
class LabTitle(
    val first: String,
    val second: String,
    val firstSize: TextUnit = 46.sp,
    val secondSize: TextUnit = 46.sp,
    /** the first line greyed, as on Audio FX */
    val firstDim: Boolean = false,
    /** the second line greyed, as under dac's stuff */
    val secondDim: Boolean = false,
    /** a small outlined tag after the second line, like dac's stuff's beta */
    val secondBadge: String? = null,
)

/** art is drawn at the header's right behind the sheet and is told when it's hidden so it can stop animating, subtitle is a small line under the title */
@Composable
fun AudioLabPage(
    title: LabTitle,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    art: (@Composable BoxScope.(hidden: State<Boolean>) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val statusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // what shows round the backdrop's edges as it shrinks back
            .background(Color.Black),
    ) {
        val wide = maxWidth > ContentMaxWidth + SideMargin * 2
        // on a tablet the title lines up with the sheet's content, not the screen's edge
        val inset = if (wide) (maxWidth - ContentMaxWidth) / 2 + SideMargin else SideMargin
        val titleScale = (maxWidth / 400.dp).coerceIn(1f, 1.6f)
        val viewport = maxHeight

        SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
            // measured on its own first, to know where the sheet starts; the one drawn is in the backdrop
            val headerHeight = subcompose("measure") {
                LabHeader(title, subtitle, inset, statusTop, titleScale)
            }.maxOfOrNull { it.measure(loose).height } ?: 0
            val headerDp = headerHeight.toDp()

            val backdrop = subcompose("backdrop") {
                LabBackdrop(scroll, headerHeight) {
                    if (art != null) {
                        // mostly covered and blurred: it stops animating there
                        val hidden = remember(headerHeight) { derivedStateOf { scroll.value >= headerHeight * 0.6f } }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .fillMaxWidth(if (wide) 0.4f else 0.5f)
                                // past the header's bottom, so what stands in it dips under the sheet
                                .height(headerDp + LabArtOverhang)
                                .padding(top = statusTop + 24.dp),
                        ) { art(hidden) }
                    }
                    LabHeader(title, subtitle, inset, statusTop, titleScale)
                }
            }.map { it.measure(constraints) }

            val body = subcompose("body") {
                // its own layer: scrolling repaints the sheet, not the backdrop and title behind it
                Box(Modifier.fillMaxSize().graphicsLayer()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        Spacer(Modifier.height(headerDp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                // black to the bottom even when there's little on the page
                                .heightIn(min = viewport - headerDp + SheetCorner)
                                .clip(RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner))
                                .background(SheetColor),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = ContentMaxWidth)
                                    .fillMaxWidth()
                                    .padding(horizontal = SideMargin)
                                    .padding(top = 30.dp, bottom = bottomInset + 32.dp),
                                content = content,
                            )
                        }
                    }
                    TopScrim(scroll, headerHeight, statusTop)
                    LabBackButton(onBack, Modifier.padding(start = inset, top = statusTop + 14.dp))
                }
            }.map { it.measure(constraints) }

            layout(constraints.maxWidth, constraints.maxHeight) {
                backdrop.forEach { it.place(0, 0) }
                body.forEach { it.place(0, 0) }
            }
        }
    }
}

/** how far the header's picture reaches below the header, under the sheet */
internal val LabArtOverhang = 64.dp

/** the dotted backdrop with title and picture as one layer, it shrinks, rounds, dims and blurs as the sheet slides over it, blur only on android 12+ */
@Composable
private fun LabBackdrop(scroll: ScrollState, headerHeight: Int, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val covered = (scroll.value / headerHeight.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                val scale = 1f - 0.07f * covered
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
                alpha = 1f - 0.4f * covered
                shape = RoundedCornerShape((30f * covered).dp)
                clip = covered > 0f
                // DECAL is android 12's, named outside this check it fails to load on older phones
                renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BlurEffectCache.element(18.dp.toPx() * covered, android.graphics.Shader.TileMode.DECAL)
                } else {
                    null
                }
            }
            .background(PageBackdrop)
            .dotGrid(),
        content = content,
    )
}

/** a section's name over its group, grey and bold, as in the mocks */
@Composable
fun LabSheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.02).em,
        color = Color(0xFF8E8E93),
        modifier = modifier.padding(start = 4.dp, top = 6.dp, bottom = 12.dp),
    )
}

/** the rounded group a section's rows sit in */
@Composable
fun LabGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF1A1A1C))
            .padding(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun LabHeader(
    title: LabTitle,
    subtitle: String?,
    inset: Dp,
    statusTop: Dp,
    scale: Float,
) {
    // headline type grows a little with a big system font, then holds: at twice the size these words wouldn't fit a phone's width
    val fontScale = LocalDensity.current.fontScale
    val keep = fontScale.coerceAtMost(1.15f) / fontScale * scale
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = statusTop + 14.dp + BackSize + 18.dp, bottom = SheetCorner + 26.dp),
    ) {
        Text(
            title.first,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = title.firstSize * keep,
            lineHeight = title.firstSize * keep * 0.95f,
            letterSpacing = (-0.035).em,
            color = if (title.firstDim) Color(0xFF8A8A8E) else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
        // a one-word title like gesture has no second line to leave a gap for
        if (title.second.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.second,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = title.secondSize * keep,
                    lineHeight = title.secondSize * keep * 0.92f,
                    letterSpacing = (-0.04).em,
                    color = if (title.secondDim) Color(0xFF8A8A8E) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    modifier = Modifier.offset(y = (-2).dp),
                )
                title.secondBadge?.let { badge ->
                    Spacer(Modifier.width(12.dp))
                    LabBadge(badge)
                }
            }
        }
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 0.06.em,
                color = Color(0xFF8A8A8E),
                maxLines = 2,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
    }
}

/** the yellow outlined pill that marks a page as still being worked on */
@Composable
private fun LabBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF2C2C2E))
            .border(1.5.dp, Color(0xFFFFF59D), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 0.08.em,
            color = Color(0xFFFFD866),
            maxLines = 1,
        )
    }
}

/** keeps what scrolls up under the status bar and the back button readable */
@Composable
private fun TopScrim(scroll: ScrollState, headerHeight: Int, statusTop: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusTop + 64.dp)
            .graphicsLayer {
                // only once the sheet has reached the top
                val reach = headerHeight - (statusTop + 64.dp).toPx()
                alpha = ((scroll.value - reach) / 48.dp.toPx()).coerceIn(0f, 1f)
            }
            .background(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.85f), Color.Transparent))),
    )
}

// the round grey back button, shared with the customize page
@Composable
internal fun LabBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BackSize)
            .clip(CircleShape)
            .background(Color(0xFF5E5E62))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back_ios),
            contentDescription = stringResource(R.string.lab_back),
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** the faint dot grid of the backdrop: one dot drawn into a tile once and repeated by the gpu, drawing every dot was thousands of circles per paint */
private fun Modifier.dotGrid(): Modifier = drawWithCache {
    val step = 12.dp.toPx().roundToInt().coerceAtLeast(2)
    val tile = ImageBitmap(step, step)
    val paint = Paint().apply {
        color = Color.White.copy(alpha = 0.06f)
        isAntiAlias = true
    }
    androidx.compose.ui.graphics.Canvas(tile).drawCircle(Offset(step / 2f, step / 2f), 0.9.dp.toPx(), paint)
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind { drawRect(brush) }
}
