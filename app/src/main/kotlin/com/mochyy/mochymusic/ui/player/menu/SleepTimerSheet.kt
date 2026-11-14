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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetIcon
import kotlinx.coroutines.delay

private val CardSurface = MenuRowSurface
private val SheetEasing = MenuEasing

@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val sleepTimer = playerConnection.service.sleepTimer

    var defaultDurationMinutes by remember { mutableIntStateOf(5) }
    var endAfterSong by remember { mutableStateOf(sleepTimer.pauseWhenSongEnd) }

    var customH by remember { mutableStateOf("") }
    var customM by remember { mutableStateOf("05") }
    var customS by remember { mutableStateOf("00") }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(200)
        }
    }

    val isRunning = sleepTimer.isRunning
    val isPaused = sleepTimer.isPaused
    val remainingMillis = when {
        isRunning -> (sleepTimer.triggerTime - nowMillis).coerceAtLeast(0L)
        isPaused -> sleepTimer.remainingMillisWhenPaused
        else -> defaultDurationMinutes * 60_000L
    }
    val totalMillis = when {
        sleepTimer.totalDurationMillis > 0L -> sleepTimer.totalDurationMillis
        else -> defaultDurationMinutes * 60_000L
    }
    val progress = if (totalMillis > 0L) {
        (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val done = sleepTimer.isActive && !isPaused && remainingMillis <= 0L && !sleepTimer.pauseWhenSongEnd

    val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val showHours = hours > 0L || (totalMillis >= 3600_000L)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val colonAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colon_alpha"
    )

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
                text = "SLEEP TIMER",
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Hero Live Timer Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (showHours) {
                            Text(
                                text = "%02d".format(hours),
                                color = Color.Black,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                text = ":",
                                color = Color.Black.copy(alpha = if (isRunning) colonAlpha else 0.4f),
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Light,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        Text(
                            text = "%02d".format(minutes),
                            color = Color.Black,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            text = ":",
                            color = Color.Black.copy(alpha = if (isRunning) colonAlpha else 0.4f),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Text(
                            text = "%02d".format(seconds),
                            color = Color.Black,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Light
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Progress track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFE5E5E5))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = when {
                            sleepTimer.pauseWhenSongEnd -> "Stops after current song ends"
                            done -> "Time is up"
                            isRunning -> "Running"
                            isPaused -> "Paused"
                            else -> "Ready"
                        },
                        color = Color(0xFF666666),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Controls Row: -1 min | Play/Pause/Resume | +1 min
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // -1 min
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Color(0xFF555555), RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = RemoteFocus,
                            onClick = {
                                if (sleepTimer.isActive) {
                                    sleepTimer.adjust(-60)
                                } else {
                                    defaultDurationMinutes = (defaultDurationMinutes - 1).coerceAtLeast(1)
                                }
                            }
                        )
                        .padding(horizontal = 16.dp)
                ) {
                    Text("−1 min", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                // Primary Start / Pause / Resume / Restart
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
                                    done -> sleepTimer.restart()
                                    else -> sleepTimer.start(defaultDurationMinutes)
                                }
                            }
                        )
                        .padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = when {
                            isRunning -> "Pause"
                            isPaused -> "Resume"
                            done -> "Restart"
                            else -> "Start"
                        },
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // +1 min
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Color(0xFF555555), RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = RemoteFocus,
                            onClick = {
                                if (sleepTimer.isActive) {
                                    sleepTimer.adjust(60)
                                } else {
                                    defaultDurationMinutes += 1
                                }
                            }
                        )
                        .padding(horizontal = 16.dp)
                ) {
                    Text("+1 min", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reset button
            Text(
                text = "Reset",
                color = Color(0xFFAAAAAA),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            sleepTimer.reset()
                        }
                    )
                    .padding(vertical = 4.dp, horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Set Presets
            Text(
                text = "QUICK SET",
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            val presets = listOf(1, 5, 10, 15, 25, 30, 45, 60)
            presets.chunked(4).forEach { rowPresets ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    rowPresets.forEach { presetMinutes ->
                        val isPresetActive = (sleepTimer.isActive && (totalMillis / 60_000L) == presetMinutes.toLong()) ||
                                (!sleepTimer.isActive && defaultDurationMinutes == presetMinutes)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (isPresetActive) {
                                        Modifier.background(Color.White)
                                    } else {
                                        Modifier
                                            .background(CardSurface)
                                            .border(1.dp, Color(0xFF38383E), RoundedCornerShape(10.dp))
                                    }
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = RemoteFocus,
                                    onClick = {
                                        defaultDurationMinutes = presetMinutes
                                        sleepTimer.start(presetMinutes)
                                    }
                                )
                        ) {
                            Text(
                                text = if (presetMinutes < 60) "${presetMinutes} min" else "1 hr",
                                color = if (isPresetActive) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Custom Time Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface)
                    .border(1.dp, Color(0xFF2C2C30), RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Custom time",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Type your own hours, minutes and seconds",
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.edit),
                            contentDescription = null,
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CustomTimeInput(
                            value = customH,
                            onValueChange = { customH = it },
                            label = "Hours",
                            max = 99,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ":",
                            color = Color(0xFF666666),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        CustomTimeInput(
                            value = customM,
                            onValueChange = { customM = it },
                            label = "Minutes",
                            max = 59,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ":",
                            color = Color(0xFF666666),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        CustomTimeInput(
                            value = customS,
                            onValueChange = { customS = it },
                            label = "Seconds",
                            max = 59,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val customTotalSec = (customH.toLongOrNull() ?: 0L) * 3600L +
                            (customM.toLongOrNull() ?: 0L) * 60L +
                            (customS.toLongOrNull() ?: 0L)
                    val customFmt = buildString {
                        val ch = customTotalSec / 3600L
                        val cm = (customTotalSec % 3600L) / 60L
                        val cs = customTotalSec % 60L
                        if (ch > 0) append("%02d:".format(ch))
                        append("%02d:%02d".format(cm, cs))
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (customTotalSec > 0L) Color.White else Color(0xFF26262B))
                            .clickable(
                                enabled = customTotalSec > 0L,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = RemoteFocus,
                                onClick = {
                                    sleepTimer.startSeconds(customTotalSec)
                                }
                            )
                    ) {
                        Text(
                            text = if (customTotalSec == 0L) "Enter a time" else "Set $customFmt",
                            color = if (customTotalSec > 0L) Color.Black else Color(0xFF777777),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // End after current song switch
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
                    text = "End after this music ends",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(
                    checked = endAfterSong,
                    onCheckedChange = { wanted ->
                        endAfterSong = wanted
                        if (wanted) sleepTimer.start(-1) else sleepTimer.clear()
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Cancel button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardSurface)
                    .clickable(
                        enabled = sleepTimer.isActive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = RemoteFocus,
                        onClick = {
                            sleepTimer.clear()
                            endAfterSong = false
                            closeSheet()
                        },
                    )
                    .padding(vertical = 14.dp)
            ) {
                Text(
                    text = "CANCEL TIMER",
                    color = Color.White.copy(alpha = if (sleepTimer.isActive) 1f else 0.35f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CustomTimeInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    max: Int,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
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
                    if (digits.isEmpty()) {
                        onValueChange("")
                    } else {
                        val num = digits.toIntOrNull() ?: 0
                        onValueChange(if (num > max) max.toString() else digits)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Color.White),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(
                    text = "00",
                    color = Color(0xFF555555),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Color(0xFFAAAAAA), fontSize = 11.sp)
    }
}
