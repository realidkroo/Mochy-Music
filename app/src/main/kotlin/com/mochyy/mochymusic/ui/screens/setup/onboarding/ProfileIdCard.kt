// the profile id card from the mocks, dark on the profile page and silver on hello and done

package com.mochyy.mochymusic.ui.screens.setup.onboarding

import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import kotlinx.coroutines.flow.first

/** width to height of the card, from the mock (332 x 176) */
internal const val IdCardAspect = 1.89f

/** the name the card shows, shared with the profile page so the two always say the same thing */
internal fun idCardName(username: String): String = username.trim().ifEmpty { "{username}" }

/** where the card's photo, name and number are, read only while drawing so the profile page can fly copies without recomposing */
internal class IdCardAnchors {
    var root: LayoutCoordinates? = null
    var photo: LayoutCoordinates? = null
    var name: LayoutCoordinates? = null
    var number: LayoutCoordinates? = null
    var nameLayout: TextLayoutResult? = null
    var numberLayout: TextLayoutResult? = null

    val isReady: Boolean
        get() = listOf(root, photo, name, number).all { it?.isAttached == true } && nameLayout != null && numberLayout != null
}

@Composable
internal fun ProfileIdCard(
    username: String,
    photo: Any?,
    cardNumber: String,
    joinedText: String,
    modifier: Modifier = Modifier,
    silver: Boolean = false,
    showIdCardOf: Boolean = silver,
    /** 0..1 across the card, where the silver highlight sits */
    sheen: () -> Float = { 0.5f },
    onPhotoClick: (() -> Unit)? = null,
    /** filled in with where the photo, name and number sit, for the profile page's morph */
    anchors: IdCardAnchors? = null,
    /** alpha for just the photo, name and number, the morph hides them while their copies fly */
    partsAlpha: () -> Float = { 1f },
    /** false holds the side strip's text still while something covers the card */
    marqueeRunning: () -> Boolean = { true },
    /** shows a tiny pencil after the number and the name so people see the card can be edited */
    showEditHint: Boolean = false,
    /** true while a finger is down on the card, the whole card lights up a little */
    pressed: () -> Boolean = { false },
) {
    val name = idCardName(username)
    val shape = RoundedCornerShape(22.dp)
    val bodyText = if (silver) Color(0xFF111114) else Color.White
    val mutedText = if (silver) Color(0xFF3A3A40) else Color(0xFFB5B5B5)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(IdCardAspect)
            .then(if (anchors != null) Modifier.onPlaced { anchors.root = it } else Modifier)
            .clip(shape)
            .then(if (silver) Modifier.silverSurface(sheen) else Modifier.background(Color(0xFF2F2F2F)))
            .drawWithContent {
                drawContent()
                if (pressed()) drawRect(Color.White.copy(alpha = if (silver) 0.28f else 0.14f))
            },
    ) {
        // everything scales with the card, so it reads the same on a narrow phone and a tablet
        val unit = maxWidth / 332f
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = unit * 22f, top = unit * 20f, end = unit * 12f, bottom = unit * 18f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.onb_card_profile_usr, cardNumber),
                        color = mutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (unit.value * 11f).sp,
                        maxLines = 1,
                        onTextLayout = { anchors?.numberLayout = it },
                        modifier = Modifier.flyingPart(anchors, partsAlpha) { number = it },
                    )
                    if (showEditHint) EditHint(mutedText, unit * 10f, partsAlpha)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(unit * 96f)
                            .flyingPart(anchors, partsAlpha) { this.photo = it }
                            .clip(CircleShape)
                            .background(if (silver) Color(0xFF5A5A60) else Color(0xFF4F4F4F))
                            .then(if (onPhotoClick != null) Modifier.clickable(onClick = onPhotoClick) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photo != null) {
                            AsyncImage(
                                model = photo,
                                contentDescription = stringResource(R.string.onb_profile_picture),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (onPhotoClick != null) {
                            Icon(
                                painterResource(R.drawable.add),
                                contentDescription = stringResource(R.string.onb_add_profile_picture),
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(unit * 34f),
                            )
                        }
                    }
                    Spacer(Modifier.width(unit * 18f))
                    Column(modifier = Modifier.weight(1f)) {
                        if (showIdCardOf) {
                            Text(
                                stringResource(R.string.onb_card_id_of),
                                color = mutedText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = (unit.value * 10f).sp,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // the pencil is dropped when the name is cut off, so it never squeezes a long name
                            var nameCut by remember { mutableStateOf(false) }
                            Text(
                                text = name,
                                color = bodyText,
                                fontSize = (unit.value * 21f).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = {
                                    anchors?.nameLayout = it
                                    nameCut = it.hasVisualOverflow
                                },
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .flyingPart(anchors, partsAlpha) { this.name = it },
                            )
                            if (showEditHint && !nameCut) EditHint(bodyText.copy(alpha = 0.7f), unit * 12f, partsAlpha)
                        }
                        Text(
                            text = joinedText,
                            color = bodyText.copy(alpha = 0.85f),
                            fontSize = (unit.value * 10.5f).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            MarqueeStrip(
                text = stringResource(R.string.onb_card_of_name, name),
                color = if (silver) Color(0xFF2B2B30).copy(alpha = 0.75f) else Color(0xFF8A8A8A),
                background = if (silver) Color.Black.copy(alpha = 0.18f) else Color(0xFF1B1B1B),
                modifier = Modifier
                    .width(unit * 40f)
                    .fillMaxHeight(),
                fontSizeSp = unit.value * 17f,
                running = marqueeRunning,
            )
        }
    }
}

/** a tiny bare pencil, hidden with the other parts while the profile page's copies are flying */
@Composable
private fun EditHint(tint: Color, size: Dp, alpha: () -> Float) {
    Icon(
        painter = painterResource(R.drawable.edit),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .padding(start = size / 3f)
            .size(size)
            .graphicsLayer { this.alpha = alpha() },
    )
}

/** tracks where a flying part of the card is and lets the morph hide it while its copy is out */
private fun Modifier.flyingPart(
    anchors: IdCardAnchors?,
    alpha: () -> Float,
    track: IdCardAnchors.(LayoutCoordinates) -> Unit,
): Modifier {
    if (anchors == null) return this
    return onPlaced { anchors.track(it) }.graphicsLayer { this.alpha = alpha() }
}

/** one full pass of the side strip's text */
private const val MarqueePeriodNanos = 5_200_000_000f

/** vertical text rotated a quarter turn and sliding downward forever, drawn not recomposed, holds still while running is false */
@Composable
internal fun MarqueeStrip(
    text: String,
    color: Color,
    background: Color,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    running: () -> Boolean = { true },
) {
    val measurer = rememberTextMeasurer()
    val style = remember(color, fontSizeSp) {
        TextStyle(color = color, fontFamily = InterFontFamily, fontSize = fontSizeSp.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
    val layout = remember(text, style) { measurer.measure("$text   ·   ", style) }
    var progress by remember { mutableFloatStateOf(0f) }
    val isRunning by rememberUpdatedState(running)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            if (!isRunning()) {
                snapshotFlow { isRunning() }.first { it }
                last = 0L
            }
            withInfiniteAnimationFrameNanos { now ->
                if (last != 0L) progress = (progress + (now - last) / MarqueePeriodNanos) % 1f
                last = now
            }
        }
    }
    Canvas(modifier = modifier.background(background)) {
        val step = layout.size.width.toFloat().coerceAtLeast(1f)
        // rotated 90 degrees so the text's x-axis runs down the strip, moving it +x scrolls it downward
        rotate(90f, pivot = Offset.Zero) {
            translate(left = 0f, top = -size.width / 2f - layout.size.height / 2f) {
                var x = -step + progress * step
                while (x < size.height) {
                    drawText(layout, topLeft = Offset(x, 0f))
                    x += step
                }
            }
        }
    }
}

/** brushed silver with a soft diagonal highlight at sheen, 0..1 across the card */
private fun Modifier.silverSurface(sheen: () -> Float): Modifier = drawWithCache {
    val base = Brush.linearGradient(
        0f to Color(0xFFBFC1C6),
        0.35f to Color(0xFFE9EAEE),
        0.55f to Color(0xFFA9ABB2),
        0.8f to Color(0xFFD7D9DE),
        1f to Color(0xFF9C9EA5),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    // fine horizontal grain, what makes it read as metal rather than grey plastic
    val lines = (size.height / 3f).toInt()
    onDrawBehind {
        drawRect(base)
        for (i in 0 until lines) {
            val y = i * 3f
            drawLine(Color.White.copy(alpha = if (i % 2 == 0) 0.05f else 0.025f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val center = sheen().coerceIn(-0.2f, 1.2f) * size.width
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = 0.55f),
                1f to Color.Transparent,
                start = Offset(center - size.width * 0.35f, 0f),
                end = Offset(center + size.width * 0.15f, size.height),
            ),
            blendMode = BlendMode.Screen,
        )
    }
}
