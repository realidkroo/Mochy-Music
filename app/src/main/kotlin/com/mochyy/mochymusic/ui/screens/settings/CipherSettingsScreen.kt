package com.mochyy.mochymusic.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.utils.cipher.PlayerJsFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** each refresh re-downloads youtube's player script, so spamming the button just gets us throttled */
private object CipherRefreshRateLimiter {
    private const val MAX_REFRESHES = 3
    private val WINDOW_MS = TimeUnit.MINUTES.toMillis(10)

    private val timestamps = ArrayDeque<Long>()

    @Synchronized
    private fun prune(now: Long) {
        while (timestamps.isNotEmpty() && now - timestamps.first() > WINDOW_MS) {
            timestamps.removeFirst()
        }
    }

    @Synchronized
    fun remaining(): Int {
        prune(System.currentTimeMillis())
        return (MAX_REFRESHES - timestamps.size).coerceAtLeast(0)
    }

    /** milliseconds until another refresh is allowed, or 0 if one is available now */
    @Synchronized
    fun cooldownMs(): Long {
        val now = System.currentTimeMillis()
        prune(now)
        if (timestamps.size < MAX_REFRESHES) return 0L
        return (timestamps.first() + WINDOW_MS - now).coerceAtLeast(0L)
    }

    @Synchronized
    fun record() {
        timestamps.addLast(System.currentTimeMillis())
    }
}

private fun formatDuration(
    context: Context,
    millis: Long,
): String {
    if (millis <= 0L) return context.getString(R.string.cipher_duration_zero)
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> context.getString(R.string.cipher_duration_hms, hours, minutes, seconds)
        minutes > 0 -> context.getString(R.string.cipher_duration_ms, minutes, seconds)
        else -> context.getString(R.string.cipher_duration_s, seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CipherSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var status by remember { mutableStateOf(PlayerJsFetcher.readCacheStatus()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // drives the countdown, ticks once a second
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // reading now here keeps these recomputing as the clock ticks
    val remainingMs = remember(status, now) { status.remainingMs }
    val cooldownMs = remember(now) { CipherRefreshRateLimiter.cooldownMs() }
    val remainingRefreshes = remember(now) { CipherRefreshRateLimiter.remaining() }

    val progress =
        remember(status, now) {
            if (!status.hasCache) {
                0f
            } else {
                (1f - remainingMs.toFloat() / PlayerJsFetcher.CACHE_TTL_MS.toFloat()).coerceIn(0f, 1f)
            }
        }

    val statusLabel =
        when {
            !status.hasCache -> stringResource(R.string.cipher_status_not_loaded)
            status.isExpired -> stringResource(R.string.cipher_status_stale)
            else -> stringResource(R.string.cipher_status_ready)
        }

    SubSettingsScaffold(
        title = stringResource(R.string.cipher_settings_title),
        onBack = { navController.navigateUp() },
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (status.isExpired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.cipher_card_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                CipherInfoRow(stringResource(R.string.cipher_player_id), status.playerHash ?: "—")
                CipherInfoRow(
                    stringResource(R.string.cipher_last_updated),
                    status.lastUpdatedMs?.let {
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                    } ?: "—",
                )
                CipherInfoRow(
                    stringResource(R.string.cipher_script_size),
                    if (status.sizeBytes > 0) stringResource(R.string.cipher_script_size_kb, status.sizeBytes / 1024) else "—",
                )
                CipherInfoRow(
                    stringResource(R.string.cipher_next_refresh),
                    if (!status.hasCache) {
                        "—"
                    } else if (remainingMs <=
                        0L
                    ) {
                        stringResource(R.string.cipher_due_now)
                    } else {
                        stringResource(R.string.cipher_in_duration, formatDuration(context, remainingMs))
                    },
                )

                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    SettingsItem(
                        title = {
                            Text(
                                if (isRefreshing) {
                                    stringResource(
                                        R.string.cipher_refreshing,
                                    )
                                } else {
                                    stringResource(R.string.cipher_refresh_now)
                                },
                            )
                        },
                        descriptionText =
                            when {
                                isRefreshing -> {
                                    stringResource(R.string.cipher_downloading_script)
                                }

                                cooldownMs > 0L -> {
                                    stringResource(R.string.cipher_rate_limited, formatDuration(context, cooldownMs))
                                }

                                else -> {
                                    context.resources.getQuantityString(
                                        R.plurals.cipher_manual_refreshes_left,
                                        remainingRefreshes,
                                        remainingRefreshes,
                                    )
                                }
                            },
                        icon = painterResource(R.drawable.refresh),
                        iconShape = CircleShape,
                        onClick = onClick@{
                            if (isRefreshing || CipherRefreshRateLimiter.cooldownMs() > 0L) return@onClick
                            CipherRefreshRateLimiter.record()
                            isRefreshing = true
                            message = null
                            coroutineScope.launch {
                                val result =
                                    withContext(Dispatchers.IO) {
                                        PlayerJsFetcher.invalidateCache()
                                        PlayerJsFetcher.getPlayerJs(forceRefresh = true)
                                    }
                                status = PlayerJsFetcher.readCacheStatus()
                                message =
                                    if (result != null) {
                                        context.getString(R.string.cipher_updated_to_player, result.second)
                                    } else {
                                        context.getString(R.string.cipher_refresh_failed)
                                    }
                                isRefreshing = false
                            }
                        },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.cipher_clear_cached_title)) },
                        descriptionText = stringResource(R.string.cipher_clear_cached_desc),
                        icon = painterResource(R.drawable.restore),
                        iconShape = CircleShape,
                        onClick = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) { PlayerJsFetcher.invalidateCache() }
                                status = PlayerJsFetcher.readCacheStatus()
                                message = context.getString(R.string.cipher_cache_cleared_msg)
                            }
                        },
                    ),
                ),
        )
    }
}

@Composable
private fun CipherInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
