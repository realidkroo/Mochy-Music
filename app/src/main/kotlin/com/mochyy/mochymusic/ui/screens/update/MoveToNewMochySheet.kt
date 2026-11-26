// MoveToNewMochySheet.kt

package com.mochyy.mochymusic.ui.screens.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.player.menu.MenuSheetSurface
import com.mochyy.mochymusic.ui.screens.Screens
import com.mochyy.mochymusic.viewmodels.MoveToNewMochyViewModel
import com.mochyy.mochymusic.viewmodels.TransferStep

private val Soft = Color(0xFFDCDCDC)
private val Muted = Color(0xFFBDBDBD)
private val Faint = Color(0xFF8C8C8C)
private val ButtonFace = Color(0xFFD9D9D9)
private val ButtonInk = Color(0xFF1C1C1C)
private val NoteRed = Color(0xFFFF8A80)

// once per launch is plenty, closing it means not now, and the next start asks again
private var promptShownThisLaunch = false

/** pops the move page over home once per launch, so nobody misses that mochy is moving to a new app */
@Composable
fun HomeMoveToNewMochyPrompt(currentRoute: String?) {
    if (currentRoute != Screens.Home.route) return
    var open by remember { mutableStateOf(!promptShownThisLaunch) }
    if (!open) return
    MenuSheetSurface(
        onDismiss = {
            promptShownThisLaunch = true
            open = false
        },
        wrapHeight = true,
        fullDetent = 0.94f,
    ) { _ -> MoveToNewMochyContent() }
}

/** the musicfy move page again: old mark to new mark, the three steps that carry everything over, and why */
@Composable
private fun MoveToNewMochyContent(vm: MoveToNewMochyViewModel = hiltViewModel()) {
    val export by vm.export.collectAsState()
    val app by vm.app.collectAsState()
    val installed by vm.newMochyInstalled.collectAsState()
    var exportFirstHint by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    // the installer runs outside the app, so look for the new mochy again whenever we're back
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshInstalled() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 26.dp)
            .padding(top = 28.dp, bottom = 28.dp)
            .animateContentSize(),
    ) {
        MoveHeader()
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepCard(
                number = 1,
                text = stringResource(R.string.move_step_export),
                note = exportNote(export),
                noteIsError = export is TransferStep.Failed,
            ) {
                StepButton(icon = R.drawable.backup, step = export) { picker.launch(vm.exportFileName()) }
            }
            val needsExportFirst = exportFirstHint && export !is TransferStep.Done
            StepCard(
                number = 2,
                text = stringResource(R.string.move_step_app),
                note = if (needsExportFirst) stringResource(R.string.move_export_first) else appNote(app, installed),
                noteIsError = app is TransferStep.Failed || needsExportFirst,
            ) {
                StepButton(
                    icon = R.drawable.download,
                    step = if (installed) TransferStep.Done else app,
                    dimmed = export !is TransferStep.Done && !installed,
                ) {
                    when {
                        installed -> vm.openNewMochy()
                        export !is TransferStep.Done -> exportFirstHint = true
                        else -> vm.getNewMochy()
                    }
                }
            }
            StepCard(
                number = 3,
                text = stringResource(R.string.move_step_import),
                note = if (installed) stringResource(R.string.move_app_ready) else null,
            ) {
                if (installed) StepButton(icon = R.drawable.arrow_forward, step = TransferStep.Idle) { vm.openNewMochy() }
            }
        }
        Spacer(Modifier.height(28.dp))
        WhySection()
    }
}

// the marks, the apology and what's going on, the top of the page
@Composable
private fun MoveHeader() {
    MarkSwap()
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(R.string.move_title),
        color = Color.White,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp,
    )
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.move_subtitle), color = Soft, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.move_intro),
        color = Soft,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    )
}

// the same mark twice, dim for this app and bright for the new one, with the arrow between them
@Composable
private fun MarkSwap() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_mochy_mark), contentDescription = stringResource(R.string.move_mark_old), tint = Faint, modifier = Modifier.size(46.dp))
        Spacer(Modifier.width(16.dp))
        Icon(painterResource(R.drawable.arrow_forward), contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Icon(painterResource(R.drawable.ic_mochy_mark), contentDescription = stringResource(R.string.move_mark_new), tint = Color.White, modifier = Modifier.size(46.dp))
    }
}

@Composable
private fun WhySection() {
    Text(stringResource(R.string.move_why_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WhyText(stringResource(R.string.move_why_key))
        WhyText(stringResource(R.string.move_why_new_app))
        WhyText(stringResource(R.string.move_why_sorry))
        WhyText(stringResource(R.string.move_why_thanks))
    }
    Spacer(Modifier.height(22.dp))
    Text(stringResource(R.string.move_signoff), color = Faint, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StepCard(
    number: Int,
    text: String,
    note: String?,
    noteIsError: Boolean = false,
    button: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MenuRowSurface)
            .padding(start = 22.dp, end = 18.dp, top = 22.dp, bottom = 22.dp),
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Text("$number.", color = Muted, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(18.dp))
            Column {
                Text(text, color = Muted, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
                if (note != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(note, color = if (noteIsError) NoteRed else Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        button()
    }
}

// the light square from the musicfy page, it spins while working and ticks when done
@Composable
private fun StepButton(@DrawableRes icon: Int, step: TransferStep, dimmed: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .pressBounce(interaction, pressedScale = 0.94f)
            .graphicsLayer {
                alpha = if (dimmed) 0.45f else 1f
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .clip(RoundedCornerShape(16.dp))
            .background(ButtonFace)
            .clickable(interactionSource = interaction, indication = null, enabled = step !is TransferStep.Working, onClick = onClick),
    ) {
        when (step) {
            is TransferStep.Working -> StepSpinner(step.progress)
            TransferStep.Done -> Icon(painterResource(R.drawable.check), contentDescription = null, tint = ButtonInk, modifier = Modifier.size(26.dp))
            else -> Icon(painterResource(icon), contentDescription = null, tint = ButtonInk, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun StepSpinner(progress: Float?) {
    if (progress == null) {
        CircularProgressIndicator(color = ButtonInk, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
    } else {
        CircularProgressIndicator(progress = { progress }, color = ButtonInk, trackColor = ButtonInk.copy(alpha = 0.15f), strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun WhyText(text: String) {
    Text(text, color = Soft, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
}

@Composable
private fun exportNote(step: TransferStep): String? = when (step) {
    is TransferStep.Working -> step.progress?.let { stringResource(R.string.move_export_working_percent, (it * 100).toInt()) }
        ?: stringResource(R.string.move_export_working)
    TransferStep.Done -> stringResource(R.string.move_export_done)
    is TransferStep.Failed -> step.message
    TransferStep.Idle -> null
}

@Composable
private fun appNote(step: TransferStep, installed: Boolean): String? = when {
    installed -> stringResource(R.string.move_app_installed)
    step is TransferStep.Working -> step.progress?.let { stringResource(R.string.move_app_downloading_percent, (it * 100).toInt()) }
        ?: stringResource(R.string.move_app_looking)
    step is TransferStep.Failed -> step.message
    else -> null
}
