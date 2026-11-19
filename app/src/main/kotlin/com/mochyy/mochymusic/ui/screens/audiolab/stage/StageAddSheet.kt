// the sheet behind the room's plus button: another copy of any part of the song, up to four of each

package com.mochyy.mochymusic.ui.screens.audiolab.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.stage.StageNative
import com.mochyy.mochymusic.eq.stage.StageSettings
import com.mochyy.mochymusic.eq.stage.StageSource
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetOptionRow
import com.mochyy.mochymusic.ui.utils.stableSystemBars

// picking a part adds it and closes the sheet, so the new dot is right there to drag
internal fun PopupSheetState.showStageAddSheet(stage: State<StageSettings>, onAdd: (StageSource) -> Unit) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        StageAddPicker(stage) { source ->
            onAdd(source)
            sheet?.dismiss()
        }
    }
}

@Composable
private fun StageAddPicker(stage: State<StageSettings>, onPick: (StageSource) -> Unit) {
    val counts by remember { derivedStateOf { StageSource.entries.associateWith { s -> stage.value.objects.count { it.source == s } } } }
    // the list reaches the screen's bottom edge, so its last row clears the gesture bar
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp + navBottom),
    ) {
        SheetLabel(stringResource(R.string.stage_add_title), Modifier.padding(start = 4.dp, bottom = 4.dp))
        for (source in StageSource.entries) {
            val count = counts[source] ?: 0
            val full = count >= StageNative.Copies
            SheetOptionRow(
                title = stringResource(sourceName(source)),
                subtitle = if (full) stringResource(R.string.stage_add_full, StageNative.Copies)
                else stringResource(R.string.stage_add_count, count, StageNative.Copies),
                enabled = !full,
                onClick = { if (!full) onPick(source) },
                leading = { SourceDot(source) },
            )
        }
    }
}

// the part's colour, the same one its dot and chip wear
@Composable
private fun SourceDot(source: StageSource) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(14.dp)
                .clip(if (source == StageSource.Sub) RoundedCornerShape(4.dp) else CircleShape)
                .background(sourceColor(source)),
        )
    }
}
