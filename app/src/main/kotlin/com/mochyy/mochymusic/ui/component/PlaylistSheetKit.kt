// PlaylistSheetKit.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.ui.unit.em
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface

/*
 * The small pieces the sheets are built from, taken from the reference mocks: a labelled field, a
 * pill button, a search field, a section label, the two big action cards, and a row with a plain
 * icon. No lines and no discs behind icons, on purpose: the user asked for both to go. All on
 * the sheet's own greys (see docs/popup-sheet.skill.md) and sized in dp with sp text that is free
 * to grow: nothing here has a fixed height, only a minimum, so a large font scale pushes the
 * layout out instead of clipping it.
 */

/** Smallest height of a field or button: comfortable to hit, and the compact 46dp the sheets use. */
private val ControlMinHeight = 46.dp

@Composable
internal fun SheetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier,
    )
}

/** A rounded field on [MenuRowSurface]: placeholder when empty, [leading] before the text. */
@Composable
internal fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = TextStyle(
            color = Color.White,
            fontFamily = InterFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.03).em,
        ),
        cursorBrush = SolidColor(Color.White),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ControlMinHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MenuRowSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(12.dp))
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

/**
 * The search field at the top of a sheet. Searching happens as you type, so the keyboard's Search
 * key just puts the keyboard away to show the results.
 */
@Composable
internal fun SheetSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    SheetTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}

/** The sheet's one main action: a pill a step lighter than the rows, dimmed while [enabled] is false. */
@Composable
internal fun SheetPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ControlMinHeight)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(percent = 50))
            .background(lerp(MenuRowSurface, Color.White, 0.22f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The space between a sheet's header and its first actions. The sheets used to draw a line here;
 * the redesign drops every line and lets this gap do the separating.
 */
internal val SheetHeaderGap = 20.dp

/**
 * A row on [MenuRowSurface]: [leading] (a [SheetIcon], usually), the [title] and an optional
 * second line, then [trailing]. The same look as the player menu's rows, taller only when the text is.
 */
@Composable
internal fun SheetOptionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleLines: Int = 1,
    enabled: Boolean = true,
    leading: @Composable () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** How big a row's icon is drawn. There's no disc behind it any more, so the glyph itself is the marker. */
internal val SheetIconSize = 20.dp

/** The plain white icon every row of the sheets starts with. */
@Composable
internal fun SheetIcon(
    icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = SheetIconSize,
    tint: Color = Color.White,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * Lets this overflow the sheet's side padding by [amount] on both sides, back out to the screen
 * edges, so something scrolling sideways (the cover carousel) runs off the screen instead of
 * stopping short of it. The sheet clips at its own edge, not at its padding.
 */
internal fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = extra * 2))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extra, 0)
    }
}

/**
 * The top of an item's sheet, as in the song-menu mock: a rounded square cover, the title and a
 * second line under it, and a round button at the end ([trailing]). Wraps to a taller row, never
 * clips, when the text is large.
 */
@Composable
internal fun SheetHeader(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        AsyncImage(
            model = thumbnailUrl?.resize(240, 240),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MenuRowSurface),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/**
 * The button at the end of a [SheetHeader] (the like heart, usually): just the [icon], no disc.
 * The touch area stays 42dp so it's as easy to hit as before.
 */
@Composable
internal fun SheetHeaderButton(
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(42.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Shortest a [SheetActionCard] gets, about two and a half rows tall as in the mock. */
private val ActionCardMinHeight = 112.dp

/**
 * The two big cards at the top of every menu sheet, for the actions people reach for each time
 * (Download and Edit playlist in the mock). Both cards end up as tall as the taller one, so a label
 * that wraps at a large font size doesn't leave them uneven.
 */
@Composable
internal fun SheetActionCardRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A big card for a headline action: the icon at the top, the label at the bottom. */
@Composable
internal fun SheetActionCard(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SheetActionCard(
        icon = { SheetIcon(icon, size = 24.dp) },
        title = title,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

/** The same card, for callers that bring their own icon (a spinner while something downloads). */
@Composable
internal fun SheetActionCard(
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = ActionCardMinHeight)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(16.dp),
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The Download card, following the download as it goes: "Download" to start it, a spinner while
 * it runs (tap to cancel), and "Remove download" once the song is saved. [downloadState] is a
 * media3 `Download.STATE_*`, or null when nothing was ever downloaded.
 */
@Composable
internal fun SheetDownloadCard(
    downloadState: Int?,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (downloadState) {
        Download.STATE_COMPLETED -> SheetActionCard(
            icon = R.drawable.offline,
            title = stringResource(R.string.remove_download),
            onClick = onRemove,
            modifier = modifier,
        )
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> SheetActionCard(
            icon = {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            },
            title = stringResource(R.string.downloading),
            onClick = onRemove,
            modifier = modifier,
        )
        else -> SheetActionCard(
            icon = R.drawable.download,
            title = stringResource(R.string.action_download),
            onClick = onDownload,
            modifier = modifier,
        )
    }
}

/**
 * Looks like [SheetSearchField] but is a button: for a sheet whose search happens on the screen
 * behind it (the playlist's own list), where typing here would have nothing to filter.
 */
@Composable
internal fun SheetSearchLauncher(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ControlMinHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = placeholder,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
