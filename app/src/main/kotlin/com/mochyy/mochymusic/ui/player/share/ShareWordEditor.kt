// ShareWordEditor.kt
//
// CoolfontEditor, the Cinematic share's word editor. Pick a look on a card (glow, caps or big), then
// tap words or swipe across them to give it to them; the card again (or Done) finishes.

package com.mochyy.mochymusic.ui.player.share

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import com.mochyy.mochymusic.ui.component.RemoteFocus

// the three looks a word can take, each with its card and its prompt
private enum class WordMode(@param:StringRes val label: Int, @param:StringRes val prompt: Int) {
    GLOW(R.string.ps_word_glow, R.string.ps_word_glow_prompt),
    CAPS(R.string.ps_word_caps, R.string.ps_word_caps_prompt),
    BIG(R.string.ps_word_big, R.string.ps_word_big_prompt);

    fun has(look: WordLook): Boolean = when (this) {
        GLOW -> look.glow
        CAPS -> look.caps
        BIG -> look.big
    }

    fun set(look: WordLook, on: Boolean): WordLook = when (this) {
        GLOW -> look.copy(glow = on)
        CAPS -> look.copy(caps = on)
        BIG -> look.copy(big = on)
    }
}

private val RingIdle = Color(0xFF5A5A5E)
private val CardLit = Color(0xFF6E6E73)
private val RingStroke = 3.dp
private val RingGap = 3.dp
private val RingCorner = 16.dp
private val PillCorner = 12.dp

// how long the "pick a look first" hint stays after tapping a word with no look chosen
private const val HintMs = 1_800L

// a fast swipe is checked every this many px along its path, so it can't jump over a word
private const val PaintStepPx = 8f

internal fun PopupSheetState.showWordEditor(session: LyricsShareSession) {
    val popup = this
    show(buttonBar = { SheetPillButton(text = stringResource(R.string.done), onClick = popup::dismiss) }) {
        WordEditorContent(session)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.WordEditorContent(session: LyricsShareSession) {
    var mode by remember { mutableStateOf<WordMode?>(null) }
    var hintTick by remember { mutableIntStateOf(0) }
    var hinting by remember { mutableStateOf(false) }
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // rings come in around every word while a look is being picked, and leave when it's done
    val selecting = animateFloatAsState(
        targetValue = if (mode != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "wordRings",
    )

    // tapping a word before picking a look nudges the cards and says what to do
    LaunchedEffect(hintTick) {
        if (hintTick == 0) return@LaunchedEffect
        hinting = true
        launch {
            nudge.animateTo(1f, tween(110))
            nudge.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 380f))
        }
        delay(HintMs)
        hinting = false
    }

    ShareTitle(
        "CoolfontEditor",
        beta = true,
        subtitleSlot = {
            val line = mode?.let { stringResource(it.prompt) } ?: if (hinting) stringResource(R.string.ps_word_pick_first) else null
            AnimatedContent(
                targetState = line,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 })
                        .togetherWith(fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 })
                        .using(SizeTransform(clip = false))
                },
                label = "wordEditorPrompt",
            ) { text ->
                if (text != null) {
                    Text(
                        text,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = (-0.2).sp,
                        color = Color.White,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    Spacer(Modifier.height(0.dp))
                }
            }
        },
    )

    // where each word's pill sits, so a touch can find the word under it without asking every pill
    val chips = remember { HashMap<WordKey, LayoutCoordinates>() }
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentMode by rememberUpdatedState(mode)

    fun wordAt(local: Offset): WordKey? {
        val area = holder[0]?.takeIf { it.isAttached } ?: return null
        val root = area.localToRoot(local)
        return chips.entries.firstOrNull { (_, c) -> c.isAttached && c.boundsInRoot().contains(root) }?.key
    }

    fun give(key: WordKey, look: WordMode, on: Boolean) {
        val now = session.look(key)
        if (look.has(now) == on) return
        session.setLook(key, look.set(now, on))
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.4f).dp)
            .verticalScroll(rememberScrollState())
            .onGloballyPositioned { holder[0] = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startWord = wordAt(down.position)
                    // sideways (or diagonal) movement paints across the words; straight up and down is left to the scroll
                    val moved = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        if (currentMode != null && abs(over.x) >= abs(over.y) * 0.6f) change.consume()
                    }
                    val look = currentMode
                    if (moved == null) {
                        // a tap: the finger came up before it moved
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up == null || !up.changedToUpIgnoreConsumed() || up.isConsumed || startWord == null) return@awaitEachGesture
                        if (look == null) {
                            hintTick++
                        } else {
                            give(startWord, look, !look.has(session.look(startWord)))
                        }
                        return@awaitEachGesture
                    }
                    if (look == null || !moved.isConsumed) return@awaitEachGesture

                    // the first word the finger crosses decides whether this swipe turns the look on or off
                    var paintOn: Boolean? = null
                    var last = down.position
                    fun paintAlong(to: Offset) {
                        val distance = hypot(to.x - last.x, to.y - last.y)
                        val steps = ceil(distance / PaintStepPx).toInt().coerceAtLeast(1)
                        for (i in 1..steps) {
                            val t = i / steps.toFloat()
                            val key = wordAt(Offset(last.x + (to.x - last.x) * t, last.y + (to.y - last.y) * t)) ?: continue
                            val on = paintOn ?: !look.has(session.look(key))
                            paintOn = on
                            give(key, look, on)
                        }
                        last = to
                    }
                    if (startWord != null) paintAlong(down.position)
                    paintAlong(moved.position)
                    drag(moved.id) { change ->
                        change.consume()
                        paintAlong(change.position)
                    }
                }
            },
    ) {
        val m = mode
        session.chosen.forEach { (line, text) ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                wordsOf(text).forEachIndexed { i, word ->
                    val key = WordKey(line, i)
                    val look = session.look(key)
                    WordChip(
                        word = word,
                        look = look,
                        mode = m,
                        selected = m?.has(look) == true,
                        selecting = selecting,
                        modifier = Modifier.onGloballyPositioned { chips[key] = it },
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        val pick: (WordMode) -> Unit = { picked ->
            mode = if (mode == picked) null else picked
            hinting = false
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MakeItCard(WordMode.GLOW, mode == WordMode.GLOW, nudge, Modifier.weight(1f)) { pick(WordMode.GLOW) }
            MakeItCard(WordMode.CAPS, mode == WordMode.CAPS, nudge, Modifier.weight(1f)) { pick(WordMode.CAPS) }
        }
        MakeItCard(WordMode.BIG, mode == WordMode.BIG, nudge, Modifier.fillMaxWidth()) { pick(WordMode.BIG) }
    }
    Spacer(Modifier.height(12.dp))
}

// a word in its pill, drawn the way it will look; while a look is being picked a ring shows it can be tapped, white once it has the look
@Composable
private fun WordChip(
    word: String,
    look: WordLook,
    mode: WordMode?,
    selected: Boolean,
    selecting: State<Float>,
    modifier: Modifier = Modifier,
) {
    val picked = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "wordPicked",
    )
    // a little pop when a word is picked or let go, but not when the whole editor switches look
    val pop = remember { Animatable(1f) }
    val lastMode = remember { arrayOf(mode) }
    LaunchedEffect(selected, mode) {
        val sameLook = lastMode[0] == mode
        lastMode[0] = mode
        if (!sameLook || mode == null) return@LaunchedEffect
        pop.animateTo(1.1f, tween(80))
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 520f))
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .drawBehind {
                val ring = selecting.value
                if (ring <= 0.01f) return@drawBehind
                val stroke = RingStroke.toPx()
                // the ring grows out from the pill as it fades in
                val inset = stroke / 2f + RingGap.toPx() * (1f - ring.coerceIn(0f, 1f))
                val colour = lerp(RingIdle, Color.White, picked.value.coerceIn(0f, 1f))
                drawRoundRect(
                    color = colour,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    cornerRadius = CornerRadius(RingCorner.toPx() - inset),
                    style = Stroke(stroke),
                    alpha = ring.coerceIn(0f, 1f),
                )
            }
            .padding(RingStroke + RingGap)
            .clip(RoundedCornerShape(PillCorner))
            .background(MenuRowSurface)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 500f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            buildAnnotatedString {
                pushStyle(wordSpan(look, Color.White, base = 14f))
                append(if (look.caps) word.uppercase() else word)
            },
            fontFamily = InterFontFamily,
            color = Color.White,
        )
    }
}

// one of the three "Make it" cards; picked, it lights up and becomes the way out of picking
@Composable
private fun MakeItCard(
    mode: WordMode,
    active: Boolean,
    nudge: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val lit = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "makeItLit",
    )
    Column(
        modifier
            .graphicsLayer {
                val s = 1f + 0.035f * nudge.value
                scaleX = s
                scaleY = s
            }
            .pressBounce(interaction)
            .clip(RoundedCornerShape(14.dp))
            .drawBehind { drawRect(lerp(MenuRowSurface, CardLit, lit.value.coerceIn(0f, 1f))) }
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        AnimatedContent(
            targetState = active,
            transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(120))).using(SizeTransform(clip = false)) },
            label = "makeItCaption",
        ) { on ->
            Text(
                stringResource(if (on) R.string.ps_word_done_making else R.string.ps_word_make_it),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = (-0.1).sp,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Text(
            stringResource(mode.label),
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = (-1.2).sp,
                color = Color.White,
                // the glow card shows its own look while it's picked
                shadow = if (mode == WordMode.GLOW && active) Shadow(Color.White.copy(alpha = 0.85f), Offset.Zero, 24f) else null,
            ),
        )
    }
}
