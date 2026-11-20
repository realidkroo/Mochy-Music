package com.mochyy.mochymusic.ui.screens.gesture

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.AppFontMode
import com.mochyy.mochymusic.ui.theme.AppFonts
import com.mochyy.mochymusic.ui.theme.fontFamilyForMode
import kotlinx.coroutines.launch

internal const val FontHighlightKey = "Font"

private val FontTileShape = RoundedCornerShape(18.dp)
private val FontTileBackground = Color.White.copy(alpha = 0.08f)
private val FontSecondary = Color.White.copy(alpha = 0.65f)

internal class FontSectionState(
    val expanded: Boolean,
    val busy: Boolean,
    val picking: Boolean,
    val error: AppFonts.Error?,
    val toggle: () -> Unit,
    val openPicker: () -> Unit,
    val select: (AppFontMode) -> Unit,
    val remove: () -> Unit,
)

@Composable
internal fun rememberFontSectionState(): FontSectionState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AppFonts.Error?>(null) }

    fun perform(action: suspend () -> AppFonts.Error?) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                error = action()
            } finally {
                busy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picking = false
        if (uri != null) perform { AppFonts.importFont(context, uri) }
    }
    return FontSectionState(
        expanded = expanded,
        busy = busy,
        picking = picking,
        error = error,
        toggle = { expanded = !expanded },
        openPicker = openPicker@{
            if (busy || picking) return@openPicker
            picking = true
            try {
                // Some providers expose valid TTF documents as application/octet-stream.
                picker.launch(arrayOf("*/*"))
            } catch (_: ActivityNotFoundException) {
                picking = false
                error = AppFonts.Error.Storage
            }
        },
        select = { mode -> perform { AppFonts.select(context, mode) } },
        remove = { perform { AppFonts.delete(context) } },
    )
}

@Composable
internal fun FontSection(state: FontSectionState) {
    val selection = AppFonts.selection
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(LabColors.Card)
            .padding(top = CardTextInset, bottom = 20.dp),
    ) {
        FontSummary(selection, state.expanded, state.toggle)
        FontOperationStatus(state, selection.ready)
        AnimatedVisibility(
            visible = state.expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            val enabled = state.expanded && selection.ready && !state.busy && !state.picking
            Column(
                modifier = Modifier.padding(start = CardTextInset, end = CardTextInset, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppFontMode.entries.forEach { mode ->
                        val hasFont = mode != AppFontMode.CUSTOM || selection.family != null
                        FontTile(
                            mode = mode,
                            family = if (hasFont) fontFamilyForMode(mode) else FontFamily.Default,
                            selected = selection.mode == mode,
                            enabled = enabled,
                            opensPicker = !hasFont,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!hasFont) state.openPicker()
                                else if (mode != selection.mode) state.select(mode)
                            },
                        )
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                FontFileActions(selection, state, enabled)
            }
        }
    }
}

@Composable
private fun FontSummary(selection: AppFonts.Selection, expanded: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(260), label = "fontChevron")
    val interaction = remember { MutableInteractionSource() }
    val toggleLabel = stringResource(if (expanded) R.string.show_less else R.string.show_more)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interaction, RemoteFocus, onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
            .padding(horizontal = CardTextInset),
    ) {
        Text(stringResource(R.string.customise_font), style = CardLabelStyle, color = Color.White, fontFamily = FontFamily.Default)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).clip(FontTileShape).background(FontTileBackground),
                contentAlignment = Alignment.Center,
            ) {
                FontSpecimen(fontFamilyForMode(selection.mode))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(selection.mode.titleRes), style = CardLabelStyle, color = Color.White, fontFamily = FontFamily.Default)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (selection.mode) {
                        AppFontMode.DEFAULT -> stringResource(R.string.app_icon_family_mark)
                        AppFontMode.SYSTEM -> "Android"
                        AppFontMode.CUSTOM -> selection.fileName.orEmpty()
                    },
                    style = CaptionStyle,
                    color = FontSecondary,
                    fontFamily = FontFamily.Default,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .pressBounce(interaction, pressedScale = 0.9f)
                    .clip(CircleShape)
                    .background(FontTileBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
                )
            }
        }
    }
}

@Composable
private fun FontTile(
    mode: AppFontMode,
    family: FontFamily,
    selected: Boolean,
    enabled: Boolean,
    opensPicker: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val target = if (opensPicker) {
        modifier.clickable(
            interactionSource = interaction,
            indication = RemoteFocus,
            enabled = enabled,
            onClickLabel = stringResource(R.string.customise_font_import),
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        modifier.selectable(
            selected = selected,
            interactionSource = interaction,
            indication = RemoteFocus,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        )
    }
    Column(
        modifier = target,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(FontTileShape)
                .background(FontTileBackground)
                .border(2.dp, if (selected) Color.White else Color.Transparent, FontTileShape),
            contentAlignment = Alignment.Center,
        ) {
            FontSpecimen(family)
        }
        Text(
            stringResource(mode.titleRes),
            style = CaptionStyle.copy(fontSize = 12.sp),
            color = if (enabled) Color.White else FontSecondary,
            fontFamily = FontFamily.Default,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FontSpecimen(family: FontFamily) {
    BasicText(
        text = "Aa",
        modifier = Modifier.padding(horizontal = 6.dp).clearAndSetSemantics {},
        style = CardValueStyle.copy(lineHeight = 1.15.em, fontFamily = family),
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 26.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FontFileActions(selection: AppFonts.Selection, state: FontSectionState, enabled: Boolean) {
    val fileName = selection.fileName
    if (fileName == null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = state.openPicker,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD9D9D9), contentColor = Color.Black),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.customise_font_import), style = CardLabelStyle, fontFamily = FontFamily.Default, textAlign = TextAlign.Center)
            }
            val help = if (selection.missing) R.string.customise_font_missing else R.string.customise_font_file_help
            Text(
                stringResource(help),
                modifier = Modifier.padding(top = 6.dp),
                style = CaptionStyle,
                color = FontSecondary,
                fontFamily = FontFamily.Default,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        if (selection.mode != AppFontMode.CUSTOM) {
            Text(fileName, style = CaptionStyle, color = FontSecondary, fontFamily = FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = state.openPicker,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = FontTileBackground, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.customise_font_replace), style = CardLabelStyle, fontFamily = FontFamily.Default, textAlign = TextAlign.Center)
            }
            TextButton(
                enabled = enabled,
                onClick = state.remove,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.customise_font_remove), style = CardLabelStyle, fontFamily = FontFamily.Default, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun FontOperationStatus(state: FontSectionState, ready: Boolean) {
    if (state.busy || !ready) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(start = CardTextInset, end = CardTextInset, top = 12.dp))
    }
    val message = when (state.error) {
        AppFonts.Error.InvalidFont -> R.string.customise_font_invalid
        AppFonts.Error.TooLarge -> R.string.customise_font_too_large
        AppFonts.Error.Storage -> R.string.customise_font_storage_error
        null -> return
    }
    Text(
        stringResource(message),
        modifier = Modifier
            .padding(start = CardTextInset, end = CardTextInset, top = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = CaptionStyle,
        color = MaterialTheme.colorScheme.error,
        fontFamily = FontFamily.Default,
    )
}

private val AppFontMode.titleRes: Int
    get() = when (this) {
        AppFontMode.DEFAULT -> R.string.customise_font_default
        AppFontMode.SYSTEM -> R.string.customise_font_system
        AppFontMode.CUSTOM -> R.string.customise_font_custom
    }
