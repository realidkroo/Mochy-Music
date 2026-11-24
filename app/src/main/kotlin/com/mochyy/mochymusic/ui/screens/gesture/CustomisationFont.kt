// CustomisationFont.kt

package com.mochyy.mochymusic.ui.screens.gesture

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.rememberOnScreen
import com.mochyy.mochymusic.ui.screens.audiolab.trackOnScreen
import com.mochyy.mochymusic.ui.theme.AppFonts
import com.mochyy.mochymusic.ui.theme.BundledBbhBartle
import com.mochyy.mochymusic.ui.theme.BundledInter
import com.mochyy.mochymusic.ui.theme.FontRole
import com.mochyy.mochymusic.ui.theme.RoleFontConfig
import com.mochyy.mochymusic.ui.utils.AppMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal const val FontHighlightKey = "Font"

private const val SpecimenStepMs = 1800L
private val ChevronFill = Color.White.copy(alpha = 0.08f)
private val SecondaryInk = Color.White.copy(alpha = 0.55f)

private data class SpecimenLook(
    val family: FontFamily,
    val weight: FontWeight = FontWeight.Bold,
    val style: FontStyle = FontStyle.Normal,
)

private val BaseSpecimens = listOf(
    SpecimenLook(BundledInter, FontWeight.Bold, FontStyle.Normal),
    SpecimenLook(FontFamily.Serif, FontWeight.Bold, FontStyle.Italic),
    SpecimenLook(FontFamily.Monospace, FontWeight.Normal, FontStyle.Normal),
    SpecimenLook(BundledInter, FontWeight.Light, FontStyle.Normal),
    SpecimenLook(BundledBbhBartle, FontWeight.Normal, FontStyle.Normal),
    SpecimenLook(FontFamily.Default, FontWeight.Bold, FontStyle.Normal),
)

// hoisted above the customize search filter so an in-flight font import survives search edits and recreation
@Stable
internal class FontSectionState(
    busyState: () -> Boolean,
    pickingState: () -> Boolean,
    errorState: () -> AppFonts.Error?,
    val openPickerFor: (FontRole) -> Unit,
    val updateRole: (FontRole, (RoleFontConfig) -> RoleFontConfig) -> Unit,
    val resetRole: (FontRole) -> Unit,
    val deleteCustomFont: (String) -> Unit,
) {
    val busy: Boolean get() = busyState()
    val picking: Boolean get() = pickingState()
    val error: AppFonts.Error? get() = errorState()

    private val busyState = busyState
    private val pickingState = pickingState
    private val errorState = errorState
}

@Composable
internal fun rememberFontSectionState(): FontSectionState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable { mutableStateOf(false) }
    var pendingRoleName by rememberSaveable { mutableStateOf(FontRole.GLOBAL.name) }
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
        val targetRole = FontRole.entries.firstOrNull { it.name == pendingRoleName } ?: FontRole.GLOBAL
        if (uri != null) perform { AppFonts.importFont(context, uri, assignToRole = targetRole) }
    }

    return remember(picker, context) {
        FontSectionState(
            busyState = { busy },
            pickingState = { picking },
            errorState = { error },
            openPickerFor = { role ->
                if (!busy && !picking) {
                    pendingRoleName = role.name
                    picking = true
                    try {
                        // some android file providers expose valid ttf and otf files as octet-stream
                        picker.launch(arrayOf("*/*"))
                    } catch (_: ActivityNotFoundException) {
                        picking = false
                        error = AppFonts.Error.Storage
                    }
                }
            },
            updateRole = { role, transform -> perform { AppFonts.updateRole(context, role, transform) } },
            resetRole = { role -> perform { AppFonts.resetRole(context, role) } },
            deleteCustomFont = { id -> perform { AppFonts.deleteCustomFont(context, id) } },
        )
    }
}

@Composable
internal fun FontSection(state: FontSectionState) {
    val sheets = LocalMenuState.current
    val fontState = AppFonts.state
    val interaction = remember { MutableInteractionSource() }
    val globalName = remember(fontState.config.global.fontId, fontState.options) {
        fontState.options.firstOrNull { it.id == fontState.config.global.fontId }?.name ?: "Inter"
    }
    val hasRoleOverrides = !fontState.config.heading.isDefaultFor(FontRole.HEADING) ||
        !fontState.config.subheading.isDefaultFor(FontRole.SUBHEADING)
    val summaryText = if (hasRoleOverrides) {
        stringResource(R.string.customise_font_summary_custom, globalName)
    } else {
        globalName
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(interaction, pressedScale = 0.98f)
            .clip(CardShape)
            .background(LabColors.Card)
            .clickable(
                interactionSource = interaction,
                indication = RemoteFocus,
                role = Role.Button,
                onClick = { sheets.showFontCustomizerSheet(state) },
            )
            .padding(horizontal = CardTextInset, vertical = 20.dp),
    ) {
        AnimatedFontSpecimen(customFamily = fontState.families.values.firstOrNull())
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.customise_font_open_customizer),
                style = CardLabelStyle.copy(fontSize = 16.sp, lineHeight = 20.sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = summaryText,
                style = CaptionStyle.copy(fontSize = 12.sp),
                color = SecondaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(ChevronFill),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// cycles Aa across bundled, system, and imported fonts inside its own damage boundary so nothing else repaints
@Composable
private fun AnimatedFontSpecimen(customFamily: FontFamily?) {
    val onScreen = rememberOnScreen()
    val obscured = LocalAppContentObscured.current
    val motionEnabled = AppMotion.enabled
    val specimens = remember(customFamily) {
        if (customFamily == null) BaseSpecimens else listOf(SpecimenLook(customFamily, FontWeight.Bold)) + BaseSpecimens
    }
    var step by remember { mutableIntStateOf(0) }

    // read visibility inside snapshotflow so opening the player pauses the loop without recomposing this card
    LaunchedEffect(motionEnabled, specimens.size) {
        if (!motionEnabled || specimens.size <= 1) return@LaunchedEffect
        snapshotFlow { onScreen.value && !obscured.value }.collectLatest { visible ->
            if (!visible) return@collectLatest
            while (true) {
                delay(SpecimenStepMs)
                step = (step + 1) % specimens.size
            }
        }
    }

    val current = specimens[step % specimens.size]
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .width(96.dp)
            .height(64.dp)
            .trackOnScreen(onScreen)
            .damageBoundary()
            .clearAndSetSemantics {},
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 4 }) togetherWith
                    (fadeOut(tween(240)) + slideOutVertically(tween(240)) { -it / 4 })
            },
            label = "animatedFontAa",
        ) { look ->
            Text(
                text = "Aa",
                style = TextStyle(
                    fontFamily = look.family,
                    fontWeight = look.weight,
                    fontStyle = look.style,
                    fontSize = 52.sp,
                    lineHeight = 56.sp,
                    letterSpacing = (-0.04).em,
                    color = Color.White,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
