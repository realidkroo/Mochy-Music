// FontCustomizerSheet.kt

package com.mochyy.mochymusic.ui.screens.gesture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.ui.component.HomeCardCornerRadius
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetHeader
import com.mochyy.mochymusic.ui.component.SheetHeaderGap
import com.mochyy.mochymusic.ui.component.SheetIcon
import com.mochyy.mochymusic.ui.component.SheetOptionRow
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.bleedHorizontally
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.theme.AppFonts
import com.mochyy.mochymusic.ui.theme.FollowGlobalFontId
import com.mochyy.mochymusic.ui.theme.FontCapabilities
import com.mochyy.mochymusic.ui.theme.FontOption
import com.mochyy.mochymusic.ui.theme.FontRole
import com.mochyy.mochymusic.ui.theme.ResolvedFonts
import com.mochyy.mochymusic.ui.theme.ResolvedRoleFont
import com.mochyy.mochymusic.ui.theme.RoleFontConfig
import com.mochyy.mochymusic.ui.theme.RoleWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val RoleCardShape = RoundedCornerShape(20.dp)
private val RoleCardBorder = Color.White.copy(alpha = 0.05f)
private val DropdownShape = RoundedCornerShape(10.dp)
private val DropdownFill = Color(0xFF232327)
private val DropdownBorder = Color.White.copy(alpha = 0.07f)
private val StyleChipShape = RoundedCornerShape(8.dp)
private val StyleChipActiveFill = Color(0xFFE6E6E8)
private val StyleChipActiveInk = Color(0xFF0B0B0C)
private val StyleChipIdleFill = Color(0xFF232327)
private val StyleChipIdleInk = Color.White.copy(alpha = 0.72f)
private val ResetCircleActive = Color(0xFFD9D9D9)
private val ResetCircleIdle = Color.White.copy(alpha = 0.16f)
private val PlaceholderCoverFill = Color(0xFFD9D9D9)

private val PreviewHeadingBase = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.04).em,
    color = Color.White,
)

private val PreviewSubheadingBase = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 19.sp,
    lineHeight = 23.sp,
    letterSpacing = (-0.03).em,
    color = Color.White.copy(alpha = 0.78f),
)

private val PreviewTrackTitleBase = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = (-0.02).em,
    color = Color.White,
)

private val PreviewTrackArtistBase = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.01).em,
    color = Color.White.copy(alpha = 0.56f),
)

@Immutable
private data class PreviewTrack(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
)

internal fun PopupSheetState.showFontCustomizerSheet(state: FontSectionState) {
    var handle: PopupSheetHandle? = null
    handle = showWithHandle(
        buttonBar = {
            SheetPillButton(
                text = stringResource(R.string.done),
                onClick = { handle?.dismiss() },
            )
        },
    ) {
        FontCustomizerSheetBody(state = state)
    }
}

@Composable
private fun FontCustomizerSheetBody(state: FontSectionState) {
    val database = LocalDatabase.current
    val sheets = LocalMenuState.current
    val fontState = AppFonts.state
    val resolved = AppFonts.resolvedFonts()
    val fallbackTracks = listOf(
        PreviewTrack("sample-1", stringResource(R.string.customise_font_preview_track_1), stringResource(R.string.customise_font_preview_artist_1), null),
        PreviewTrack("sample-2", stringResource(R.string.customise_font_preview_track_2), stringResource(R.string.customise_font_preview_artist_2), null),
        PreviewTrack("sample-3", stringResource(R.string.customise_font_preview_track_3), stringResource(R.string.customise_font_preview_artist_3), null),
    )
    var previewTracks by remember { mutableStateOf(fallbackTracks) }

    // pick a handful of random covers from the user's library once off the main thread
    LaunchedEffect(database) {
        val loaded = loadRandomPreviewTracks(database)
        if (loaded.isNotEmpty()) previewTracks = loaded
    }

    val controlsEnabled = fontState.ready && !state.busy && !state.picking

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val pinPreview = maxHeight >= 520.dp
        if (pinPreview) {
            Column(modifier = Modifier.fillMaxWidth()) {
                FontLivePreview(resolved = resolved, tracks = previewTracks)
                Spacer(Modifier.height(16.dp))
                FontStatusBanner(state = state, ready = fontState.ready)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                ) {
                    FontRoleCards(
                        fontState = fontState,
                        resolved = resolved,
                        enabled = controlsEnabled,
                        state = state,
                        onOpenPicker = { role -> sheets.showRoleFontPickerSheet(role, state) },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
            ) {
                FontLivePreview(resolved = resolved, tracks = previewTracks)
                Spacer(Modifier.height(16.dp))
                FontStatusBanner(state = state, ready = fontState.ready)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    FontRoleCards(
                        fontState = fontState,
                        resolved = resolved,
                        enabled = controlsEnabled,
                        state = state,
                        onOpenPicker = { role -> sheets.showRoleFontPickerSheet(role, state) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FontLivePreview(resolved: ResolvedFonts, tracks: List<PreviewTrack>) {
    val coverCorner = HomeCardCornerRadius.coerceIn(10.dp, 20.dp)
    val coverShape = remember(coverCorner) { RoundedCornerShape(coverCorner) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.customise_font_preview_heading),
            style = resolved.heading.applyTo(PreviewHeadingBase),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.customise_font_preview_subheading),
            style = resolved.subheading.applyTo(PreviewSubheadingBase),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .bleedHorizontally(20.dp)
                .horizontalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 20.dp)),
        ) {
            tracks.forEach { track ->
                Column(modifier = Modifier.width(102.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(102.dp)
                            .clip(coverShape)
                            .background(if (track.thumbnailUrl != null) MenuRowSurface else PlaceholderCoverFill),
                    ) {
                        if (track.thumbnailUrl != null) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(102.dp),
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                tint = Color.Black.copy(alpha = 0.25f),
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = track.title,
                        style = resolved.global.applyTo(PreviewTrackTitleBase),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist,
                        style = resolved.global.applyTo(PreviewTrackArtistBase),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FontRoleCards(
    fontState: AppFonts.State,
    resolved: ResolvedFonts,
    enabled: Boolean,
    state: FontSectionState,
    onOpenPicker: (FontRole) -> Unit,
) {
    val roles = remember { listOf(FontRole.HEADING, FontRole.SUBHEADING, FontRole.GLOBAL) }
    roles.forEach { role ->
        val config = fontState.config.roleConfig(role)
        val roleResolved = resolved.forRole(role)
        val effectiveId = AppFonts.effectiveFontId(role, fontState.config)
        val optionName = fontState.options.firstOrNull { it.id == effectiveId }?.name ?: "Inter"
        val dropdownLabel = if (role != FontRole.GLOBAL && config.fontId == FollowGlobalFontId) {
            stringResource(R.string.customise_font_following_value, optionName)
        } else {
            optionName
        }

        RoleCustomizerCard(
            role = role,
            config = config,
            resolved = roleResolved,
            dropdownLabel = dropdownLabel,
            enabled = enabled,
            onOpenPicker = { onOpenPicker(role) },
            onToggleBold = {
                state.updateRole(role) { cur ->
                    val inheritedBold = role != FontRole.GLOBAL && cur.fontId == FollowGlobalFontId &&
                        fontState.config.global.weight == RoleWeight.BOLD
                    val nextWeight = when {
                        roleResolved.effectiveWeight == RoleWeight.BOLD && inheritedBold -> RoleWeight.NORMAL
                        roleResolved.effectiveWeight == RoleWeight.BOLD -> RoleWeight.AUTO
                        else -> RoleWeight.BOLD
                    }
                    cur.copy(weight = nextWeight)
                }
            },
            onToggleItalic = {
                state.updateRole(role) { cur ->
                    val inheritedItalic = role != FontRole.GLOBAL && cur.fontId == FollowGlobalFontId &&
                        fontState.config.global.italic == true
                    val nextItalic = when {
                        roleResolved.effectiveItalic && inheritedItalic -> false
                        roleResolved.effectiveItalic -> null
                        else -> true
                    }
                    cur.copy(italic = nextItalic)
                }
            },
            onToggleLight = {
                state.updateRole(role) { cur ->
                    val inheritedLight = role != FontRole.GLOBAL && cur.fontId == FollowGlobalFontId &&
                        fontState.config.global.weight == RoleWeight.LIGHT
                    val nextWeight = when {
                        roleResolved.effectiveWeight == RoleWeight.LIGHT && inheritedLight -> RoleWeight.NORMAL
                        roleResolved.effectiveWeight == RoleWeight.LIGHT -> RoleWeight.AUTO
                        else -> RoleWeight.LIGHT
                    }
                    cur.copy(weight = nextWeight)
                }
            },
            onReset = { state.resetRole(role) },
        )
    }
}

@Composable
private fun RoleCustomizerCard(
    role: FontRole,
    config: RoleFontConfig,
    resolved: ResolvedRoleFont,
    dropdownLabel: String,
    enabled: Boolean,
    onOpenPicker: () -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleLight: () -> Unit,
    onReset: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val roleLabel = stringResource(role.labelRes)
    val canReset = !config.isDefaultFor(role) && enabled
    val titleSize: TextUnit = when (role) {
        FontRole.HEADING -> 24.sp
        FontRole.SUBHEADING -> 17.sp
        FontRole.GLOBAL -> 13.sp
    }
    val titleBase = remember(titleSize) {
        TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = titleSize,
            letterSpacing = (-0.03).em,
            color = Color.White,
        )
    }
    val caps = resolved.capabilities
    val activeWeight = resolved.effectiveWeight
    val activeItalic = resolved.effectiveItalic

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoleCardShape)
            .background(MenuRowSurface)
            .border(1.dp, RoleCardBorder, RoleCardShape)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = roleLabel,
                style = resolved.applyTo(titleBase),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            val resetDesc = stringResource(R.string.customise_font_reset_role, roleLabel)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (canReset) ResetCircleActive else ResetCircleIdle)
                    .clickable(enabled = canReset, role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onReset()
                    }
                    .semantics { contentDescription = resetDesc },
            ) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = null,
                    tint = if (canReset) StyleChipActiveInk else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val dropdownInteraction = remember { MutableInteractionSource() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .pressBounce(dropdownInteraction, pressedScale = 0.99f)
                .clip(DropdownShape)
                .background(DropdownFill)
                .border(1.dp, DropdownBorder, DropdownShape)
                .clickable(
                    interactionSource = dropdownInteraction,
                    indication = RemoteFocus,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onOpenPicker,
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                text = dropdownLabel,
                style = TextStyle(
                    fontFamily = resolved.family,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color.White,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(
            visible = caps.hasAnyStyle,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                if (caps.supportsBold) {
                    StyleToggleChip(
                        glyph = "B",
                        label = stringResource(R.string.customise_font_style_bold),
                        selected = activeWeight == RoleWeight.BOLD,
                        enabled = enabled,
                        glyphStyle = TextStyle(
                            fontFamily = resolved.family,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onToggleBold()
                        },
                    )
                }
                if (caps.supportsItalic) {
                    StyleToggleChip(
                        glyph = "i",
                        label = stringResource(R.string.customise_font_style_italic),
                        selected = activeItalic,
                        enabled = enabled,
                        glyphStyle = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            fontStyle = FontStyle.Italic,
                            fontSize = 15.sp,
                        ),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onToggleItalic()
                        },
                    )
                }
                if (caps.supportsLight) {
                    StyleToggleChip(
                        glyph = "L",
                        label = stringResource(R.string.customise_font_style_light),
                        selected = activeWeight == RoleWeight.LIGHT,
                        enabled = enabled,
                        glyphStyle = TextStyle(
                            fontFamily = resolved.family,
                            fontWeight = FontWeight.Light,
                            fontSize = 14.sp,
                        ),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onToggleLight()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleToggleChip(
    glyph: String,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    glyphStyle: TextStyle,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .pressBounce(interaction, pressedScale = 0.92f)
            .clip(StyleChipShape)
            .background(if (selected) StyleChipActiveFill else StyleChipIdleFill)
            .border(1.dp, if (selected) Color.Transparent else DropdownBorder, StyleChipShape)
            .clickable(
                interactionSource = interaction,
                indication = RemoteFocus,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                this.selected = selected
            },
    ) {
        Text(
            text = glyph,
            style = glyphStyle,
            color = if (selected) StyleChipActiveInk else StyleChipIdleInk,
            maxLines = 1,
        )
    }
}

@Composable
private fun FontStatusBanner(state: FontSectionState, ready: Boolean) {
    if (state.busy || !ready) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
    }
    val message = when (state.error) {
        AppFonts.Error.InvalidFont -> R.string.customise_font_invalid
        AppFonts.Error.TooLarge -> R.string.customise_font_too_large
        AppFonts.Error.Storage -> R.string.customise_font_storage_error
        null -> return
    }
    Text(
        text = stringResource(message),
        style = CaptionStyle.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .padding(bottom = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private fun PopupSheetState.showRoleFontPickerSheet(role: FontRole, state: FontSectionState) {
    var pickerHandle: PopupSheetHandle? = null
    pickerHandle = showWithHandle {
        RoleFontPickerContent(
            role = role,
            state = state,
            onDismiss = { pickerHandle?.dismiss() },
        )
    }
}

@Composable
private fun RoleFontPickerContent(
    role: FontRole,
    state: FontSectionState,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val fontState = AppFonts.state
    val currentRoleConfig = fontState.config.roleConfig(role)
    val roleLabel = stringResource(role.labelRes)
    val globalOptionName = fontState.options.firstOrNull { it.id == fontState.config.global.fontId }?.name ?: "Inter"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        SheetHeader(
            icon = R.drawable.tune,
            title = stringResource(R.string.customise_font_picker_title, roleLabel),
            subtitle = stringResource(R.string.customise_font_file_help),
        )
        Spacer(Modifier.height(SheetHeaderGap))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetOptionRow(
                title = stringResource(R.string.customise_font_import),
                subtitle = stringResource(R.string.customise_font_file_help),
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismiss()
                    state.openPickerFor(role)
                },
            )

            if (role != FontRole.GLOBAL) {
                val isFollowing = currentRoleConfig.fontId == FollowGlobalFontId
                FontOptionPickerRow(
                    name = stringResource(R.string.customise_font_follow_global),
                    subtitle = globalOptionName,
                    family = AppFonts.familyForOption(fontState.config.global.fontId),
                    selected = isFollowing,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        state.updateRole(role) { it.copy(fontId = FollowGlobalFontId) }
                        onDismiss()
                    },
                    onDelete = null,
                )
            }

            fontState.options.forEach { option ->
                val isSelected = currentRoleConfig.fontId == option.id
                FontOptionPickerRow(
                    name = option.name,
                    subtitle = optionSubtitle(option),
                    family = AppFonts.familyForOption(option.id),
                    selected = isSelected,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        state.updateRole(role) { it.copy(fontId = option.id) }
                        onDismiss()
                    },
                    onDelete = if (option.isCustom) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            state.deleteCustomFont(option.id)
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun FontOptionPickerRow(
    name: String,
    subtitle: String,
    family: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DropdownFill),
        ) {
            Text(
                text = "Aa",
                style = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White),
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = CaptionStyle.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onDelete != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDelete),
            ) {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = stringResource(R.string.customise_font_remove),
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        if (selected) {
            SheetIcon(R.drawable.check, size = 18.dp)
        }
    }
}

@Composable
private fun optionSubtitle(option: FontOption): String {
    val kind = when {
        option.isVariable -> stringResource(R.string.customise_font_kind_variable)
        option.isCustom -> stringResource(R.string.customise_font_kind_custom)
        else -> stringResource(R.string.customise_font_kind_builtin)
    }
    val styles = capabilitiesSummary(option.capabilities)
    return stringResource(R.string.customise_font_meta_pair, kind, styles)
}

@Composable
private fun capabilitiesSummary(caps: FontCapabilities): String {
    val parts = buildList {
        if (caps.supportsBold) add(stringResource(R.string.customise_font_style_bold))
        if (caps.supportsItalic) add(stringResource(R.string.customise_font_style_italic))
        if (caps.supportsLight) add(stringResource(R.string.customise_font_style_light))
    }
    return if (parts.isEmpty()) stringResource(R.string.customise_font_single_style) else parts.joinToString(", ")
}

private val FontRole.labelRes: Int
    get() = when (this) {
        FontRole.HEADING -> R.string.customise_font_role_heading
        FontRole.SUBHEADING -> R.string.customise_font_role_subheading
        FontRole.GLOBAL -> R.string.customise_font_role_global
    }

private suspend fun loadRandomPreviewTracks(database: MusicDatabase): List<PreviewTrack> = withContext(Dispatchers.IO) {
    runCatching {
        val pool = LinkedHashMap<String, PreviewTrack>()
        fun collect(songs: List<com.mochyy.mochymusic.db.entities.Song>) {
            for (song in songs) {
                val art = song.song.thumbnailUrl?.takeIf { it.isNotBlank() } ?: continue
                if (pool.containsKey(art)) continue
                val artist = song.artists.joinToString(", ") { it.name }.ifBlank { song.song.albumName.orEmpty() }
                pool[art] = PreviewTrack(
                    id = song.id,
                    title = song.song.title,
                    artist = artist,
                    thumbnailUrl = art,
                )
                if (pool.size >= 24) break
            }
        }
        collect(database.recentlyPlayedSongs(limit = 30).first())
        if (pool.size < 4) collect(database.likedSongsByRowIdAsc().first().take(30))
        if (pool.size < 4) collect(database.localSongsByRowIdAsc().first().take(30))
        pool.values.shuffled().take(4)
    }.getOrElse { emptyList() }
}
