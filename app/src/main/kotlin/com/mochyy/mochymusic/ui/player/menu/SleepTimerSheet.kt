// SleepTimerSheet.kt

package com.mochyy.mochymusic.ui.player.menu

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.SleepTimer
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetIcon
import kotlinx.coroutines.delay

private val CardSurface = MenuRowSurface
private val SheetEasing = MenuEasing
private val Muted = Color(0xFFAAAAAA)
private val PresetMinutes = listOf(1, 5, 10, 15, 25, 30, 45, 60)

@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val sleepTimer = playerConnection.service.sleepTimer

    // what start uses while nothing is set; the -1 / +1 buttons nudge it until then
    var idleMinutes by remember { mutableIntStateOf(30) }

    MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.72f, fullDetent = 0.94f) { _ ->
        val closeSheet = LocalMenuSheetClose.current
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 10.dp, bottom = 28.dp)
                .animateContentSize(animationSpec = tween(420, easing = SheetEasing))
        ) {
            Text(
                text = stringResource(R.string.sleep_timer),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            CountdownCard(sleepTimer = sleepTimer, idleMillis = idleMinutes * 60_000L)
            Spacer(modifier = Modifier.height(18.dp))

            TimerControls(
                sleepTimer = sleepTimer,
                onNudgeIdle = { delta -> idleMinutes = (idleMinutes + delta).coerceAtLeast(1) },
                onStart = { sleepTimer.start(idleMinutes) },
            )
            Spacer(modifier = Modifier.height(22.dp))

            QuickSet(
                sleepTimer = sleepTimer,
                idleMinutes = idleMinutes,
                onPick = { minutes ->
                    idleMinutes = minutes
                    sleepTimer.start(minutes)
                },
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomTimeCard(onSet = sleepTimer::startSeconds)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                SheetIcon(R.drawable.music_note)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.sleep_timer_end_after_song),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // read from the timer itself, so picking a preset turns this off without the sheet tracking it
                AppSwitch(
                    checked = sleepTimer.pauseWhenSongEnd,
                    onCheckedChange = { wanted -> if (wanted) sleepTimer.start(-1) else sleepTimer.clear() },
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            SheetButton(
                label = stringResource(R.string.sleep_timer_cancel),
                enabled = sleepTimer.isActive,
                onClick = {
                    sleepTimer.clear()
                    closeSheet()
                },
            )
        }
    }
}

// the big clock ticks on its own, so only this card redraws while the timer counts down
@Composable
private fun CountdownCard(sleepTimer: SleepTimer, idleMillis: Long) {
    val isRunning = sleepTimer.isRunning
    val isPaused = sleepTimer.isPaused
    val now by rememberSecondTick(if (isRunning) sleepTimer.triggerTime else null)

    val remainingMillis = when {
        isRunning -> (sleepTimer.triggerTime - now).coerceAtLeast(0L)
        isPaused -> sleepTimer.remainingMillisWhenPaused
        else -> idleMillis
    }
    val totalMillis = sleepTimer.totalDurationMillis.takeIf { it > 0L } ?: idleMillis
    val progress = if (totalMillis > 0L) (remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f) else 0f

    // rounded up, so a fresh 5 minute timer reads 05:00 and not 04:59
    val totalSeconds = (remainingMillis + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val showHours = hours > 0L || totalMillis >= 3_600_000L

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardSurface)
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showHours) {
                ClockDigits(hours)
                ClockColon(pulsing = isRunning)
            }
            ClockDigits(minutes)
            ClockColon(pulsing = isRunning)
            ClockDigits(seconds)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = radius)
                    drawRoundRect(Color.White, size = Size(size.width * progress, size.height), cornerRadius = radius)
                }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(
                when {
                    sleepTimer.pauseWhenSongEnd -> R.string.sleep_timer_until_song_ends
                    isRunning -> R.string.sleep_timer_running
                    isPaused -> R.string.sleep_timer_paused
                    else -> R.string.sleep_timer_ready
                }
            ),
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// wakes right as the next whole second ticks over, so the clock never skips or repeats a digit
@Composable
private fun rememberSecondTick(triggerTime: Long?): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(triggerTime) {
        if (triggerTime == null) return@LaunchedEffect
        while (true) {
            val time = System.currentTimeMillis()
            now.longValue = time
            val left = triggerTime - time
            if (left <= 0L) break
            delay(left % 1000L + 1L)
        }
    }
    return now
}

@Composable
private fun ClockDigits(value: Long) {
    Text(
        text = "%02d".format(value),
        color = Color.White,
        fontSize = 54.sp,
        fontWeight = FontWeight.Light,
        style = TextStyle(fontFeatureSettings = "tnum"),
    )
}

// the pulse is read while drawing, so blinking never recomposes the clock
@Composable
private fun ClockColon(pulsing: Boolean) {
    if (pulsing) {
        val pulse = rememberInfiniteTransition(label = "sleepColon").animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
            label = "sleepColonAlpha",
        )
        ColonText(Modifier.graphicsLayer { alpha = pulse.value })
    } else {
        ColonText(Modifier.graphicsLayer { alpha = 0.4f })
    }
}

@Composable
private fun ColonText(modifier: Modifier) {
    Text(
        text = ":",
        color = Color.White,
        fontSize = 54.sp,
        fontWeight = FontWeight.Light,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun TimerControls(sleepTimer: SleepTimer, onNudgeIdle: (Int) -> Unit, onStart: () -> Unit) {
    val isRunning = sleepTimer.isRunning
    val isPaused = sleepTimer.isPaused
    val counting = isRunning || isPaused
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinePill(stringResource(R.string.sleep_timer_minus_minute)) {
            if (counting) sleepTimer.adjust(-60) else onNudgeIdle(-1)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = RemoteFocus,
                    onClick = {
                        when {
                            isRunning -> sleepTimer.pause()
                            isPaused -> sleepTimer.resume()
                            else -> onStart()
                        }
                    },
                )
                .padding(horizontal = 32.dp)
        ) {
            Text(
                text = stringResource(
                    when {
                        isRunning -> R.string.sleep_timer_pause
                        isPaused -> R.string.sleep_timer_resume
                        else -> R.string.sleep_timer_start
                    }
                ),
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinePill(stringResource(R.string.sleep_timer_plus_minute)) {
            if (counting) sleepTimer.adjust(60) else onNudgeIdle(1)
        }
    }
}

@Composable
private fun OutlinePill(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color(0xFF555555), RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp)
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuickSet(sleepTimer: SleepTimer, idleMinutes: Int, onPick: (Int) -> Unit) {
    val counting = sleepTimer.isRunning || sleepTimer.isPaused
    val setMinutes = if (counting) sleepTimer.totalDurationMillis / 60_000L else idleMinutes.toLong()

    Text(
        text = stringResource(R.string.sleep_timer_quick_set),
        color = Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(10.dp))
    PresetMinutes.chunked(4).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            row.forEach { minutes ->
                val picked = !sleepTimer.pauseWhenSongEnd && setMinutes == minutes.toLong()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (picked) Modifier.background(Color.White)
                            else Modifier
                                .background(CardSurface)
                                .border(1.dp, Color(0xFF38383E), RoundedCornerShape(10.dp))
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = RemoteFocus,
                            onClick = { onPick(minutes) },
                        )
                ) {
                    Text(
                        text = if (minutes < 60) {
                            pluralStringResource(R.plurals.sleep_timer_preset_minutes, minutes, minutes)
                        } else {
                            stringResource(R.string.sleep_timer_preset_hour)
                        },
                        color = if (picked) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomTimeCard(onSet: (Long) -> Unit) {
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("30") }
    var seconds by remember { mutableStateOf("00") }

    val totalSeconds = (hours.toLongOrNull() ?: 0L) * 3600L +
        (minutes.toLongOrNull() ?: 0L) * 60L +
        (seconds.toLongOrNull() ?: 0L)
    val canSet = totalSeconds > 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardSurface)
            .border(1.dp, Color(0xFF2C2C30), RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sleep_timer_custom),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = stringResource(R.string.sleep_timer_custom_desc), color = Muted, fontSize = 11.sp)
            }
            Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TimeField(hours, { hours = it }, stringResource(R.string.sleep_timer_hours), max = 99, modifier = Modifier.weight(1f))
            FieldColon()
            TimeField(minutes, { minutes = it }, stringResource(R.string.sleep_timer_minutes), max = 59, modifier = Modifier.weight(1f))
            FieldColon()
            TimeField(seconds, { seconds = it }, stringResource(R.string.sleep_timer_seconds), max = 59, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(14.dp))

        val label = if (canSet) {
            val h = totalSeconds / 3600L
            val clock = if (h > 0) "%02d:%02d:%02d".format(h, (totalSeconds % 3600L) / 60L, totalSeconds % 60L)
            else "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
            stringResource(R.string.sleep_timer_set_custom, clock)
        } else {
            stringResource(R.string.sleep_timer_enter_time)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .background(if (canSet) Color.White else Color(0xFF26262B))
                .clickable(
                    enabled = canSet,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = RemoteFocus,
                    onClick = { onSet(totalSeconds) },
                )
        ) {
            Text(
                text = label,
                color = if (canSet) Color.Black else Color(0xFF777777),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FieldColon() {
    Text(
        text = ":",
        color = Color(0xFF666666),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

// two digits at most, and never past the unit's max, so 75 minutes turns into 59
@Composable
private fun TimeField(value: String, onValueChange: (String) -> Unit, label: String, max: Int, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F0F12))
                .border(1.dp, Color(0xFF2C2C30), RoundedCornerShape(14.dp))
        ) {
            BasicTextField(
                value = value,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(2)
                    val number = digits.toIntOrNull()
                    onValueChange(if (number != null && number > max) max.toString() else digits)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = Color.White, fontSize = 24.sp, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Color.White),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Text(text = "00", color = Color(0xFF555555), fontSize = 24.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SheetButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(vertical = 14.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
