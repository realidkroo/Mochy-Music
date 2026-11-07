// settings → storage: how much room mochy takes on each drive, the cache limit, clearing things, where things are saved and exporting songs

package com.mochyy.mochymusic.ui.screens.settings.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.MaxSongCacheSizeKey
import com.mochyy.mochymusic.playback.ExportStep
import com.mochyy.mochymusic.playback.MoveStep
import com.mochyy.mochymusic.playback.StoredKind
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.library.showConfirm
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.DriveUsage
import com.mochyy.mochymusic.viewmodels.StorageUsage
import com.mochyy.mochymusic.viewmodels.StorageViewModel

const val StorageRoute = "storage_settings"

// song cache limits on offer, in mb, -1 is no limit, the player's default is 1 gb
private val CacheLimitSteps = listOf(0, 256, 512, 1024, 2048, 4096, 8192, -1)

@Composable
fun StorageScreen(navController: NavController, viewModel: StorageViewModel = hiltViewModel()) {
    val usage by viewModel.usage.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val (cacheLimit, setCacheLimit) = rememberPreference(MaxSongCacheSizeKey, 1024)
    // the player picked its limit when the app started; a new one only applies from the next start
    val startingLimit = remember { cacheLimit }

    // songs removed from the Manage downloads sheet change the numbers here too
    LaunchedEffect(downloads?.size) { viewModel.measure() }

    SubSettingsScaffold(title = stringResource(R.string.storage), onBack = { navController.navigateUp() }) {
        // one overall card per drive mochy keeps things on, the phone first
        val drives = usage?.drives
        if (drives == null) {
            OverallCard(null)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { drives.forEach { OverallCard(it) } }
        }

        Spacer(Modifier.height(22.dp))
        SectionText(usage?.let { headline(it) } ?: stringResource(R.string.storage_measuring))
        Spacer(Modifier.height(14.dp))
        BreakdownCard(usage)

        Spacer(Modifier.height(26.dp))
        SectionText(stringResource(R.string.storage_junk_title))
        usage?.let { SmartHint(it, downloadCount = downloads?.size ?: 0) }
        Spacer(Modifier.height(14.dp))
        CacheLimitCard(
            limit = cacheLimit,
            onLimitChange = setCacheLimit,
            appliesLater = cacheLimit != startingLimit,
        )
        Spacer(Modifier.height(10.dp))
        ClearButtons(viewModel, downloadCount = downloads?.size ?: 0)

        Spacer(Modifier.height(26.dp))
        SectionText(stringResource(R.string.storage_or))
        Spacer(Modifier.height(14.dp))
        PlaceButtons(viewModel, downloadCount = downloads?.size ?: 0)

        Spacer(Modifier.height(26.dp))
        TransferSection()
    }
}

@Composable
private fun SectionText(text: String) {
    Text(text, style = storageText(17f, FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 2.dp))
}

@Composable
private fun headline(usage: StorageUsage): String =
    if (usage.drives.size == 1) {
        stringResource(R.string.storage_uses, percentOf(usage.mochy, usage.phone.total), formatSize(usage.mochy))
    } else {
        stringResource(R.string.storage_uses_drives, formatSize(usage.mochy), usage.drives.size)
    }

// like 1.4%, or <0.1% for a sliver, so a small app never reads as using nothing
private fun percentOf(part: Long, total: Long): String {
    if (total <= 0L) return "0%"
    val p = part * 100.0 / total
    return when {
        p < 0.1 -> "<0.1%"
        p < 10 -> String.format("%.1f%%", p)
        else -> "${p.toInt()}%"
    }
}

@Composable
private fun OverallCard(drive: DriveUsage?) {
    StorageCardColumn {
        Text(
            if (drive == null || drive.isPhone) stringResource(R.string.storage_overall) else drive.label,
            style = storageText(17f),
        )
        Spacer(Modifier.height(14.dp))
        UsageBar(
            parts = listOf(
                BarPart(MochyGreen, drive?.mochy ?: 0),
                BarPart(OthersGrey, drive?.othersUsed ?: 0),
            ),
            total = drive?.total ?: 0,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LegendSwatch(MochyGreen)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.storage_legend_mochy), style = storageText(16f, FontWeight.SemiBold))
            Spacer(Modifier.width(16.dp))
            LegendSwatch(OthersGrey)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.storage_legend_yours), style = storageText(16f, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
            if (drive != null) {
                Text(stringResource(R.string.storage_free, formatSize(drive.free)), style = storageText(12f, FontWeight.Medium, StorageMuted, 0f))
            }
        }
    }
}

@Composable
private fun BreakdownCard(usage: StorageUsage?) {
    StorageCardColumn {
        Text(stringResource(R.string.storage_breakdown_title), style = storageText(17f))
        Spacer(Modifier.height(14.dp))
        UsageBar(
            parts = listOf(
                BarPart(CacheOrange, usage?.cache ?: 0),
                BarPart(MochyGreen, usage?.downloads ?: 0),
                BarPart(DatabaseRed, usage?.database ?: 0),
                BarPart(AppDataBlue, usage?.appData ?: 0),
            ),
            total = usage?.mochy ?: 0,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendRow(CacheOrange, stringResource(R.string.storage_legend_cache), usage?.let { formatSize(it.cache) })
            LegendRow(MochyGreen, stringResource(R.string.storage_legend_downloads), usage?.let { formatSize(it.downloads) })
            LegendRow(DatabaseRed, stringResource(R.string.storage_legend_database), usage?.let { formatSize(it.database) })
            LegendRow(AppDataBlue, stringResource(R.string.storage_legend_app_data), usage?.let { formatSize(it.appData) })
        }
    }
}

// one line pointing at the biggest easy win, the cache or the downloads sheet, and a nudge when the phone is nearly full
@Composable
private fun SmartHint(usage: StorageUsage, downloadCount: Int) {
    val phone = usage.phone
    val nearlyFull = phone.total > 0 && phone.free * 10 < phone.total
    val hint = when {
        usage.cache >= usage.downloads && usage.cache > 50_000_000L -> stringResource(R.string.storage_hint_cache, formatSize(usage.cache))
        downloadCount > 0 && usage.downloads > 50_000_000L -> stringResource(R.string.storage_hint_downloads)
        else -> null
    }
    val full = if (nearlyFull) stringResource(R.string.storage_hint_full) else null
    val text = listOfNotNull(full, hint).joinToString(" ")
    if (text.isEmpty()) return
    Text(
        text,
        style = storageText(13f, FontWeight.Medium, StorageMuted, -0.2f),
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp),
    )
}

@Composable
private fun CacheLimitCard(limit: Int, onLimitChange: (Int) -> Unit, appliesLater: Boolean) {
    StorageCardColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDADADA)),
            ) {
                Icon(painterResource(R.drawable.cached), contentDescription = null, tint = StorageCard, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.storage_cache_limit), style = storageText(15f, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text(cacheLimitLabel(limit), style = storageText(15f, FontWeight.Bold))
        }
        Spacer(Modifier.height(12.dp))
        StepSlider(steps = CacheLimitSteps, value = limit, onValueChange = onLimitChange)
        Row(Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)) {
            Text(stringResource(R.string.storage_cache_none), style = storageText(10f, FontWeight.Medium, StorageMuted, 0f), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.storage_cache_unlimited), style = storageText(10f, FontWeight.Medium, StorageMuted, 0f))
        }
        AnimatedVisibility(
            visible = appliesLater,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                stringResource(R.string.storage_cache_applies_later),
                style = storageText(12f, FontWeight.Medium, StorageMuted, -0.1f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun cacheLimitLabel(megabytes: Int): String = when {
    megabytes < 0 -> stringResource(R.string.storage_cache_unlimited)
    megabytes == 0 -> stringResource(R.string.storage_cache_off)
    megabytes < 1024 -> stringResource(R.string.storage_cache_mb, megabytes)
    else -> stringResource(R.string.storage_cache_gb, megabytes / 1024)
}

@Composable
private fun ClearButtons(viewModel: StorageViewModel, downloadCount: Int) {
    val busy by viewModel.busy.collectAsState()
    val menuState = LocalMenuState.current
    val removeTitle = stringResource(R.string.storage_remove_downloads_title)
    val removeMessage = stringResource(R.string.storage_remove_downloads_message)
    val removeAction = stringResource(R.string.storage_remove_downloads)
    val clearTitle = stringResource(R.string.storage_clear_cache_title)
    val clearMessage = stringResource(R.string.storage_clear_cache_message)
    val clearAction = stringResource(R.string.storage_clear_cache_action)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StorageActionButton(
            label = removeAction,
            danger = true,
            enabled = !busy && downloadCount > 0,
            onClick = { menuState.showConfirm(removeTitle, removeMessage, removeAction, viewModel::removeAllDownloads) },
        )
        StorageActionButton(
            label = stringResource(R.string.storage_clear_cache),
            danger = true,
            enabled = !busy,
            onClick = { menuState.showConfirm(clearTitle, clearMessage, clearAction, viewModel::clearCaches) },
        )
        StorageActionButton(
            label = stringResource(R.string.storage_manage_downloads),
            enabled = downloadCount > 0,
            onClick = { menuState.showManageDownloadsSheet(viewModel) },
        )
    }
}

// where downloads and the cache are saved, and copying the songs out; each button only speaks up while its job runs
@Composable
private fun PlaceButtons(viewModel: StorageViewModel, downloadCount: Int) {
    val move by viewModel.moveStep.collectAsState()
    val export by viewModel.exportStep.collectAsState()
    val menuState = LocalMenuState.current
    val moving = move as? MoveStep.Working

    @Composable
    fun moveDetail(kind: StoredKind): String? = when {
        moving?.kind == kind -> stringResource(R.string.storage_moving, (moving.progress * 100).toInt())
        (move as? MoveStep.Failed)?.kind == kind -> stringResource(R.string.storage_move_failed)
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StorageActionButton(
            label = stringResource(R.string.storage_downloads_place),
            detail = moveDetail(StoredKind.Downloads),
            enabled = moving == null,
            onClick = { menuState.showStoragePlaceSheet(viewModel, StoredKind.Downloads) },
        )
        StorageActionButton(
            label = stringResource(R.string.storage_cache_place),
            detail = moveDetail(StoredKind.SongCache),
            enabled = moving == null,
            onClick = { menuState.showStoragePlaceSheet(viewModel, StoredKind.SongCache) },
        )
        StorageActionButton(
            label = stringResource(R.string.storage_export_downloads),
            detail = when (val step = export) {
                is ExportStep.Working -> if (step.total == 0) stringResource(R.string.storage_export_starting)
                else stringResource(R.string.storage_export_progress, step.done + 1, step.total)
                is ExportStep.Done -> pluralStringResource(R.plurals.storage_export_done, step.songs, step.songs)
                is ExportStep.Failed -> step.message
                ExportStep.Idle -> null
            },
            enabled = downloadCount > 0,
            onClick = { menuState.showExportDownloadsSheet(viewModel) },
        )
    }
}
