// the sheet behind add effect: mochy's own effects by kind, liveprog scripts, then what each module brings

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.mixer.FxKind
import com.mochyy.mochymusic.eq.mixer.KindOrigin
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetOptionRow
import com.mochyy.mochymusic.ui.component.sheetHandleRoom
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.utils.stableSystemBars

/** what the sheet can do besides adding a listed kind */
class AddEffectActions(
    val onPick: (FxKind) -> Unit,
    val onNewScript: () -> Unit,
    val onOpenScript: () -> Unit,
)

// picking anything closes the sheet, so the new effect's card is right there
internal fun PopupSheetState.showAddEffectSheet(kinds: List<FxKind>, actions: AddEffectActions) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        AddEffectPicker(
            kinds,
            AddEffectActions(
                onPick = { actions.onPick(it); sheet?.dismiss() },
                onNewScript = { actions.onNewScript(); sheet?.dismiss() },
                onOpenScript = { actions.onOpenScript(); sheet?.dismiss() },
            ),
        )
    }
}

@Composable
private fun AddEffectPicker(kinds: List<FxKind>, actions: AddEffectActions) {
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()
    val builtIn = kinds.filter { it.origin == KindOrigin.BuiltIn }.groupBy { it.category }
    val fromModules = kinds.filter { it.origin is KindOrigin.Module }.groupBy { (it.origin as KindOrigin.Module).moduleName }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = sheetHandleRoom(), bottom = 12.dp + navBottom),
    ) {
        SheetLabel(stringResource(R.string.mixer_add_title), Modifier.padding(start = 4.dp, bottom = 4.dp))
        builtIn.entries.forEachIndexed { group, (category, list) ->
            SheetLabel(fxText(category), Modifier.padding(start = 4.dp, top = 8.dp))
            for (kind in list) KindRow(kind, LabColors.node(group), actions.onPick)
        }
        SheetLabel(stringResource(R.string.mixer_add_scripts), Modifier.padding(start = 4.dp, top = 8.dp))
        SheetOptionRow(
            title = stringResource(R.string.mixer_new_script),
            subtitle = stringResource(R.string.mixer_new_script_sub),
            onClick = actions.onNewScript,
            leading = { Dot(LabColors.node(7)) },
        )
        SheetOptionRow(
            title = stringResource(R.string.mixer_open_script),
            subtitle = stringResource(R.string.mixer_open_script_sub),
            onClick = actions.onOpenScript,
            leading = { Dot(LabColors.node(7)) },
        )
        for ((module, list) in fromModules) {
            SheetLabel(stringResource(R.string.mixer_from_module, module), Modifier.padding(start = 4.dp, top = 8.dp))
            for (kind in list) KindRow(kind, LabColors.node(8), actions.onPick)
        }
    }
}

@Composable
private fun KindRow(kind: FxKind, color: Color, onPick: (FxKind) -> Unit) {
    SheetOptionRow(
        title = fxText(kind.name),
        subtitle = kind.description.takeIf { it.isNotBlank() }?.let { fxText(it) },
        subtitleLines = 2,
        onClick = { onPick(kind) },
        leading = { Dot(color) },
    )
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
    }
}

/** the script a new liveprog slot starts with: one knob, every section, small enough to read at a glance */
const val NewScriptTemplate = """desc: My effect

// knobs are declared above the first section: name:default<min,max,step>Label
gain:0<-24,12,0.5>Gain (dB)

@init
g = 1;

@slider
// runs whenever a knob moves, without restarting the script
g = exp(gain * 0.1151292546);

@sample
spl0 = spl0 * g;
spl1 = spl1 * g;
"""
