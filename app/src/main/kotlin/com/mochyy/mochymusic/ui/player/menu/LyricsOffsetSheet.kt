// LyricsOffsetSheet.kt

package com.mochyy.mochymusic.ui.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.lyrics.LyricsAutoSync
import com.mochyy.mochymusic.lyrics.LyricsUtils
import kotlin.math.abs
import kotlin.math.roundToInt
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** The slider and the typed value both stop here: 10 seconds either way, shown in ms. */
private const val MaxOffsetMs = 10_000

/** The slider snaps to this many ms, so a drag lands on clean numbers instead of 4137. */
private const val SliderStepMs = 10

/** What the - and + labels above the slider move the offset by. */
private const val NudgeMs = 50

/**
 * Shifts the current song's lyrics earlier (+) or later (-), in ms. Every change is saved to the
 * song right away, so the lyrics move while the sheet is still open and you can judge it by ear.
 * Auto offset works the number out by listening to the song (see LyricsAutoSync).
 */
@Composable
fun LyricsOffsetSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val focusManager = LocalFocusManager.current
    val songId = remember { playerConnection.mediaMetadata.value?.id }
    val startOffset = remember { playerConnection.lyricsOffsetMs.value }

    var offset by remember { mutableIntStateOf(startOffset) }
    var draft by remember { mutableStateOf(startOffset.toString()) }

    LaunchedEffect(offset) {
        if (songId == null || offset == playerConnection.lyricsOffsetMs.value) return@LaunchedEffect
        playerConnection.database.query { updateLyricsOffset(songId, offset) }
    }

    // Moves that don't come from the keyboard also close it, so the box never shows a stale draft.
    fun setOffset(value: Int) {
        focusManager.clearFocus()
        offset = value.coerceIn(-MaxOffsetMs, MaxOffsetMs)
        draft = offset.toString()
    }

    // Auto offset needs lyrics timed by line or word: plain text has no timing to move.
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val timedLines = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics?.takeIf { it.isNotBlank() && it != LyricsEntity.LYRICS_NOT_FOUND }
        raw?.let(LyricsUtils::parseLyrics)?.takeIf { it.isNotEmpty() && !LyricsUtils.isUnsynced(it) }
    }
    val autoSync = playerConnection.service.lyricsAutoSync
    val autoState by autoSync.state.collectAsState()
    val autoHere = autoState?.takeIf { it.songId == songId }
    val autoRunning = autoHere is LyricsAutoSync.State.Listening || autoHere is LyricsAutoSync.State.Matching
    // the sync saves the offset itself; the slider and the box catch up with it
    LaunchedEffect(autoHere) {
        (autoHere as? LyricsAutoSync.State.Synced)?.let { setOffset(it.offsetMs) }
    }

    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        val closeSheet = LocalMenuSheetClose.current
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                // the full video mode's card is only as tall as the phone is wide, so this scrolls there
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            Text(
                text = "Lyrics offset",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OffsetValueBox(
                text = draft,
                onTextChange = { raw ->
                    val cleaned = raw.filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '-') }.take(6)
                    draft = cleaned
                    // "" and "-" are halfway through typing a number, so they change nothing yet.
                    cleaned.toIntOrNull()?.let { parsed ->
                        offset = parsed.coerceIn(-MaxOffsetMs, MaxOffsetMs)
                        if (offset != parsed) draft = offset.toString()
                    }
                },
                onFocusChange = { focused -> if (!focused) draft = offset.toString() },
            )

            Spacer(modifier = Modifier.height(36.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                NudgeLabel("-") { setOffset(offset - NudgeMs) }
                NudgeLabel("+") { setOffset(offset + NudgeMs) }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OffsetTrack(offset = offset, onOffsetChange = ::setOffset)

            Spacer(modifier = Modifier.height(24.dp))

            AutoSyncStatus(
                state = autoHere,
                timed = timedLines != null,
                onUndo = { previous -> setOffset(previous) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            PillButton(
                label = when (autoHere) {
                    is LyricsAutoSync.State.Listening -> "listening… ${(autoHere.progress * 100).roundToInt()}%"
                    is LyricsAutoSync.State.Matching -> "matching…"
                    else -> "auto offset"
                },
                enabled = timedLines != null && !autoRunning,
                onClick = {
                    val lines = timedLines ?: return@PillButton
                    focusManager.clearFocus()
                    playerConnection.service.autoSyncLyrics(lines, offset)
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            PillButton(label = "done", onClick = closeSheet)
        }
    }
}

/**
 * One or two lines over the auto offset button: what it can do here, what it's doing, or what it
 * found. A finished sync offers Undo, back to the offset from before it.
 */
@Composable
private fun AutoSyncStatus(state: LyricsAutoSync.State?, timed: Boolean, onUndo: (Int) -> Unit) {
    val text = when {
        !timed -> "Auto offset needs lyrics timed by line or by word. Plain lyrics have no timing to move."
        state is LyricsAutoSync.State.Listening -> "Listening for where the singing starts and stops…"
        state is LyricsAutoSync.State.Matching -> "Lining each line up with its singing…"
        state is LyricsAutoSync.State.Synced -> syncedText(state)
        state is LyricsAutoSync.State.Failed -> state.message
        else -> "Listens to the song and lines the lyrics up with the singing."
    }
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    if (state is LyricsAutoSync.State.Synced && state.previousOffsetMs != state.offsetMs) {
        Text(
            text = "Undo",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = RemoteFocus,
                    onClick = { onUndo(state.previousOffsetMs) },
                ),
        )
    }
}

private fun syncedText(state: LyricsAutoSync.State.Synced): String {
    val moved = state.offsetMs - state.previousOffsetMs
    val change = when {
        abs(moved) < 20 -> "The lyrics were already in time with the singing."
        moved > 0 -> "Lyrics moved ${moved} ms earlier to meet the singing."
        else -> "Lyrics moved ${-moved} ms later to meet the singing."
    }
    val guess = if (state.sure) "" else " It's a best guess, so check it by ear."
    val drift = state.driftMs?.let {
        " The timing also drifts by ${"%.1f".format(abs(it) / 1_000f)} s over the song, so it's set to fit the middle best."
    }.orEmpty()
    return change + guess + drift
}

/** The grey "0 ms" box. Tap it to type an exact number. */
@Composable
private fun OffsetValueBox(
    text: String,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            // Sized to the digits, otherwise the box jumps around as numbers come and go.
            modifier = Modifier
                .width((text.length.coerceAtLeast(1) * 27 + 4).dp)
                .onFocusChanged { onFocusChange(it.isFocused) },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "ms",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun NudgeLabel(symbol: String, onClick: () -> Unit) {
    Text(
        text = symbol,
        color = Color.White,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
    )
}

/**
 * The thin bar from the mock: white up to the current value, and the middle of the bar is 0. A
 * Material slider can't be this flat and always draws a handle, so this one is drawn by hand.
 */
@Composable
private fun OffsetTrack(offset: Int, onOffsetChange: (Int) -> Unit) {
    var widthPx by remember { mutableIntStateOf(0) }
    // the edit sheet allows minutes either way, past this bar's ten seconds, so it stops at the ends
    val fraction = ((offset + MaxOffsetMs) / (2f * MaxOffsetMs)).coerceIn(0f, 1f)

    fun valueAt(x: Float): Int {
        if (widthPx <= 0) return offset
        val raw = (x / widthPx).coerceIn(0f, 1f) * 2f * MaxOffsetMs - MaxOffsetMs
        return (raw / SliderStepMs).roundToInt() * SliderStepMs
    }

    // The touch area is taller than the bar so it is easy to grab with a thumb.
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) { detectTapGestures { onOffsetChange(valueAt(it.x)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onOffsetChange(valueAt(change.position.x))
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
            )
        }
    }
}
