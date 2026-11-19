// the module player's editor over the player, the way the flagship's looks: mochy's top bar, a panel for the open part with its choices side by side and its settings, and the parts with no block of their own along the bottom (docs/wiki, your own editor)

package com.mochyy.mochymusic.ui.player.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.di.ModulesEntryPoint
import com.mochyy.mochymusic.module.FieldKind
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.SkinEditPart
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.player.styles.ModuleEditorState
import com.mochyy.mochymusic.ui.screens.settings.modules.ModuleOptions
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import dagger.hilt.android.EntryPointAccessors

@Composable
fun ModuleEditOverlay(studio: PlayerStudioState, editor: ModuleEditorState, glassState: GlassState) {
    val shown by remember(studio) { derivedStateOf { studio.edit.value > 0.0005f || studio.editing } }
    // the editor gone: nothing held over for the next time it opens
    LaunchedEffect(shown) { if (!shown) editor.reset() }
    if (!shown) return
    val style = ActiveSkin.style ?: return
    val parts = style.player.edit
    val moduleId = style.skin.moduleId
    val context = LocalContext.current
    // the registry straight from hilt: the modules screen's view model would start update checks the editor has no need for
    val registry = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, ModulesEntryPoint::class.java).moduleRegistry() }
    val entries by registry.entries.collectAsState()
    val entry = entries.firstOrNull { it.id == moduleId }
    // a picked or dragged value stays shown until the module has it, then the module's own takes over
    LaunchedEffect(editor) {
        snapshotFlow { ActiveSkin.style?.values }.collect { values ->
            if (values == null) return@collect
            editor.overrides.keys.filter { values[it] == editor.overrides[it] }.forEach { editor.overrides.remove(it) }
        }
    }
    val close = { if (editor.open != null) editor.open = null else studio.back() }
    BackHandler(enabled = studio.stage >= StudioStage.EDIT && studio.sheet == null) { close() }
    val open = parts.firstOrNull { it.id == editor.open }
    val hint = stringResource(if (parts.any { it.hold != null }) R.string.ps_module_edit_hint_hold else R.string.ps_module_edit_hint)

    // the boxes move with the layout, but this only needs to know which parts are on screen and where the open one sits
    val onScreen by remember(editor) { derivedStateOf { editor.shown } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = with(LocalDensity.current) { maxHeight.toPx() }
        // a part low on the screen gets its panel at the top, so the panel never covers what it changes
        val panelAtTop by remember(editor, open, height) {
            derivedStateOf { open?.let { editor.rectOf(it.id) }?.let { it.center.y > height * 0.55f } ?: false }
        }
        val onTiles = open == null && studio.stage == StudioStage.EDIT
        LooseParts(
            parts = parts.filter { it.id !in onScreen },
            visible = onTiles,
            alpha = { studio.edit.value },
            onOpen = { editor.open = it.id },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        PartPanel(open, entry, editor, registry::setField, panelAtTop, maxHeight * 0.46f)
        StudioTopBar(studio, glassState, titleOverride = open?.title, subtitleOverride = if (open == null) hint else "", onBack = close)
    }
}

/** the open part's panel: its choices side by side, then its other settings, every change showing on the player behind it */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PartPanel(
    open: SkinEditPart?,
    entry: ModuleEntry?,
    editor: ModuleEditorState,
    setField: (module: String, field: String, value: String) -> Unit,
    atTop: Boolean,
    maxHeight: androidx.compose.ui.unit.Dp,
) {
    // the last part stays in the panel while it slides away
    var shownPart by remember { mutableStateOf<SkinEditPart?>(null) }
    if (open != null) shownPart = open
    val statusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    AnimatedVisibility(
        visible = open != null && entry != null,
        enter = slideInVertically(tween(320)) { if (atTop) -it else it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(260)) { if (atTop) -it else it } + fadeOut(tween(180)),
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .padding(top = if (atTop) statusTop + 62.dp else 0.dp),
    ) {
        val part = shownPart ?: return@AnimatedVisibility
        val module = entry ?: return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (atTop) Modifier else Modifier.navigationBarsPadding())
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(PanelColor)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) {
            val choice = part.choose?.let { key -> module.fields.firstOrNull { it.id == key && it.kind == FieldKind.Select } }
            if (choice != null) {
                val picked = editor.overrides[choice.id] ?: module.values[choice.id] ?: choice.default
                Text(choice.label, color = Color.White, style = studioText(StudioType.Subtitle, FontWeight.Bold))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choice.options.forEach { option ->
                        val lit = option.id == picked
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (lit) Color.White else Color.White.copy(alpha = 0.12f))
                                .clickable {
                                    editor.overrides[choice.id] = option.id
                                    setField(module.id, choice.id, option.id)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(option.label, color = if (lit) Color.Black else Color.White, style = studioText(StudioType.Button), maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            val more = part.settings.mapNotNull { key -> module.fields.firstOrNull { it.id == key && it.id != choice?.id } }
            if (more.isNotEmpty()) {
                ModuleOptions(
                    entry = module.copy(fields = more),
                    onPick = { _, _ -> },
                    onField = { field, value -> setField(module.id, field, value) },
                    titled = false,
                )
            }
        }
    }
}

/** the parts with no block on the player (its background, its colours), as round buttons along the bottom */
@Composable
private fun LooseParts(parts: List<SkinEditPart>, visible: Boolean, alpha: () -> Float, onOpen: (SkinEditPart) -> Unit, modifier: Modifier) {
    if (parts.isEmpty()) return
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(160)), modifier = modifier) {
        Row(
            Modifier
                .padding(bottom = navBottom + 18.dp, start = 16.dp, end = 16.dp)
                .graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            parts.forEach { part ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onOpen(part) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                ) {
                    Text(part.title, color = Color.White, style = studioText(StudioType.Button), maxLines = 1)
                }
            }
        }
    }
}

private val PanelColor = Color(0xE6121212)
