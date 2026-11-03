// customisation's launcher icons: the one in use, and behind a chevron every family's ready-made ones and a custom colour each

package com.mochyy.mochymusic.ui.screens.gesture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.findActivity
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.utils.AppIcon
import com.mochyy.mochymusic.utils.AppIconColor
import com.mochyy.mochymusic.utils.AppIconFamily
import com.mochyy.mochymusic.utils.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val TileSize = 66.dp
// close to a launcher's squircle, so the tile reads as the icon it will be
private val TileShape = RoundedCornerShape(TileSize * 0.27f)
private val TileRing = Color.White.copy(alpha = 0.12f)
private val TileRingPicked = Color.White.copy(alpha = 0.9f)

// the custom colours sit in two rows of six, which fit across any phone
private val SwatchSize = 30.dp
private const val SwatchesPerRow = 6

@Composable
internal fun IconsSection() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(AppIcons.current(context)) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    // the family whose colours are open under its customize button
    var customizing by remember { mutableStateOf<AppIconFamily?>(null) }
    // each family's custom tile keeps the colour last picked for it, starting from the one in use
    val customColors = remember { mutableStateMapOf<AppIconFamily, AppIconColor>().apply { picked.color?.let { put(picked.family, it) } } }
    val art = rememberIconArt()

    fun pick(icon: AppIcon) {
        icon.color?.let { customColors[icon.family] = it }
        if (icon == picked) return
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        picked = icon
        // the next launch's splash goes with it: the icon itself for an anime one, the bare mark for the rest
        context.findActivity()?.let { AppIcons.syncSplash(it, icon) }
        // the package manager is a call to the system, kept off the main thread
        scope.launch(Dispatchers.IO) { AppIcons.apply(context.applicationContext, icon) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(LabColors.Card)
            .padding(top = CardTextInset, bottom = 20.dp),
    ) {
        PickedIcon(icon = picked, art = art, expanded = expanded, onToggle = { expanded = !expanded })
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column {
                AppIconFamily.entries.forEach { family ->
                    FamilyIcons(
                        family = family,
                        picked = picked,
                        customColor = customColors[family] ?: AppIconColor.entries.first(),
                        art = art,
                        customizing = customizing == family,
                        onPick = ::pick,
                        onCustomize = { customizing = if (customizing == family) null else family },
                    )
                }
                Text(
                    text = stringResource(R.string.customise_icons_caption),
                    style = CaptionStyle,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = CardTextInset, end = CardTextInset, top = 16.dp),
                )
            }
        }
    }
}

// each piece of art loaded once and shared by every tile; anime mochy is a bitmap, and every tile used to decode its own
@Composable
private fun rememberIconArt(): Map<Int, Painter> {
    val ids = remember { AppIcon.entries.map { it.foreground }.distinct() }
    return ids.associateWith { painterResource(it) }
}

// the icon in use with its name and family, and the chevron that opens every other choice
@Composable
private fun PickedIcon(icon: AppIcon, art: Map<Int, Painter>, expanded: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(260), label = "iconsChevron")
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(horizontal = CardTextInset),
    ) {
        Text(
            text = stringResource(R.string.customise_icons),
            style = CardLabelStyle,
            color = Color.White.copy(alpha = 0.9f),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconArt(icon = icon, art = art, picked = true)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(icon.label),
                    style = CardLabelStyle,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(icon.family.title),
                    style = CaptionStyle,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .pressBounce(interaction, pressedScale = 0.9f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = stringResource(if (expanded) R.string.customise_icons_show_less else R.string.customise_icons_show_all),
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = turn },
                )
            }
        }
    }
}

// one family: its ready-made icons swiped sideways with its custom one last, and its colours under customize
@Composable
private fun FamilyIcons(
    family: AppIconFamily,
    picked: AppIcon,
    customColor: AppIconColor,
    art: Map<Int, Painter>,
    customizing: Boolean,
    onPick: (AppIcon) -> Unit,
    onCustomize: () -> Unit,
) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(family.title),
        style = CaptionStyle,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = CardTextInset),
    )
    Spacer(Modifier.height(10.dp))
    // swiped sideways: a family's tiles and its custom one don't fit across a phone with their names
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = CardTextInset)),
    ) {
        AppIcon.presets.filter { it.family == family }.forEach { icon ->
            IconTile(icon = icon, art = art, picked = icon == picked, onClick = { onPick(icon) }) {
                TileLabel(text = stringResource(icon.label), picked = icon == picked)
            }
        }
        val custom = AppIcon.custom(family, customColor)
        IconTile(icon = custom, art = art, picked = custom == picked, onClick = { onPick(custom) }) {
            CustomizeButton(open = customizing, onClick = onCustomize)
        }
    }
    AnimatedVisibility(
        visible = customizing,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        ColorSwatches(selected = customColor, onPick = { color -> onPick(AppIcon.custom(family, color)) })
    }
}

// a tile you tap to wear that icon, with its name or its customize button under it
@Composable
private fun IconTile(icon: AppIcon, art: Map<Int, Painter>, picked: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(TileSize + 10.dp),
    ) {
        IconArt(
            icon = icon,
            art = art,
            picked = picked,
            modifier = Modifier
                .pressBounce(interaction, pressedScale = 0.94f)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        )
        Spacer(Modifier.height(8.dp))
        label()
    }
}

/** the icon as the launcher will draw it: its background, and its art at the adaptive icon's 108 to 72 crop */
@Composable
private fun IconArt(icon: AppIcon, art: Map<Int, Painter>, picked: Boolean, modifier: Modifier = Modifier) {
    val ring by animateColorAsState(if (picked) TileRingPicked else TileRing, tween(220), label = "iconRing")
    // a custom tile fades between colours as they're picked; a see-through one has nothing behind its art
    val fill by animateColorAsState(icon.background?.let { colorResource(it) } ?: Color.Transparent, tween(220), label = "iconFill")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TileSize)
            .clip(TileShape)
            .drawBehind { drawRect(fill) }
            .border(if (picked) 2.dp else 1.dp, ring, TileShape),
    ) {
        Image(
            painter = art.getValue(icon.foreground),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.requiredSize(TileSize * 1.5f),
        )
    }
}

@Composable
private fun TileLabel(text: String, picked: Boolean) {
    Text(
        text = text,
        style = CaptionStyle,
        color = Color.White.copy(alpha = if (picked) 0.95f else 0.6f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// the pill under a custom tile that opens and closes its family's colours, filled while they're open
@Composable
private fun CustomizeButton(open: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(if (open) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.1f), tween(220), label = "customizeFill")
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = stringResource(R.string.app_icon_customize),
        style = CaptionStyle,
        color = if (open) Color.Black else Color.White.copy(alpha = 0.85f),
        maxLines = 1,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.92f)
            .clip(CircleShape)
            .drawBehind { drawRect(fill) }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

// the twelve custom colours; tapping one puts the family's art on it straight away
@Composable
private fun ColorSwatches(selected: AppIconColor, onPick: (AppIconColor) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CardTextInset, end = CardTextInset, top = 16.dp),
    ) {
        AppIconColor.entries.chunked(SwatchesPerRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                row.forEach { color -> Swatch(color = color, selected = color == selected, onClick = { onPick(color) }) }
            }
        }
    }
}

@Composable
private fun Swatch(color: AppIconColor, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) TileRingPicked else TileRing, tween(220), label = "swatchRing")
    val name = stringResource(color.label)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(SwatchSize)
            .pressBounce(interaction, pressedScale = 0.9f)
            .clip(CircleShape)
            .background(colorResource(color.background))
            .border(if (selected) 2.dp else 1.dp, ring, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            },
    )
}
