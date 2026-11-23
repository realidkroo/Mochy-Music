package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.security.ScriptFinding
import com.mochyy.mochymusic.modules.security.ScriptScanResult
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.sheetHandleRoom
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily

private val WarnAccent = Color(0xFFFFB340)
private val WarnFill = Color(0x26FFB340)
private val BlockAccent = Color(0xFFFF6B63)
private val BlockFill = Color(0x26FF453A)
private val SafeAccent = Color(0xFF4CD964)
private val SafeFill = Color(0x1F30D158)
private val WarningCardShape = RoundedCornerShape(20.dp)

// pops up before running or adding a script / code module so nobody installs sketchy stuff blind
internal fun PopupSheetState.showScriptWarningSheet(
    targetName: String,
    isCodeModule: Boolean,
    scan: ScriptScanResult? = null,
    onContinue: () -> Unit,
    onCancel: () -> Unit = {},
) {
    var sheet: PopupSheetHandle? = null
    var continued = false
    sheet = showWithHandle(onClosed = { if (!continued) onCancel() }) {
        ScriptWarningContent(
            targetName = targetName,
            isCodeModule = isCodeModule,
            scan = scan,
            onContinue = {
                continued = true
                sheet?.dismiss()
                onContinue()
            },
            onCancel = { sheet?.dismiss() },
        )
    }
}

// shown when the scanner catches a script poking creds, localhost, or mochy internals
internal fun PopupSheetState.showScriptBlockedSheet(
    targetName: String,
    scan: ScriptScanResult,
) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        ScriptBlockedContent(
            targetName = targetName,
            findings = scan.findings.filter { it.blocked }.ifEmpty { scan.findings },
            onClose = { sheet?.dismiss() },
        )
    }
}

@Composable
private fun ScriptWarningContent(
    targetName: String,
    isCodeModule: Boolean,
    scan: ScriptScanResult?,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    val warnings = scan?.findings?.filter { !it.blocked }.orEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = sheetHandleRoom(), bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(WarnFill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.warning), null, tint = WarnAccent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (isCodeModule) R.string.module_script_warn_code_title else R.string.module_script_warn_title),
                    color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                )
                Text(
                    targetName, color = SearchColors.Secondary, fontFamily = InterFontFamily, fontSize = 13.sp, maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(if (isCodeModule) R.string.module_script_warn_code_body else R.string.module_script_warn_body),
            color = SearchColors.Secondary, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
        )

        Spacer(Modifier.height(16.dp))
        SecurityInfoCard(
            title = stringResource(R.string.module_script_sandbox_title),
            body = stringResource(R.string.module_script_sandbox_body),
            fill = SafeFill,
            titleColor = SafeAccent,
        )

        Spacer(Modifier.height(10.dp))
        SecurityInfoCard(
            title = stringResource(R.string.module_script_risks_title),
            body = stringResource(R.string.module_script_risks_body),
            fill = LabColors.Card,
            titleColor = Color.White,
        )

        if (scan != null && scan.scriptNames.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SecurityInfoCard(
                title = stringResource(R.string.module_script_files_title),
                body = scan.scriptNames.joinToString("\n") { "• $it" },
                fill = LabColors.Card,
                titleColor = Color.White,
            )
        }

        if (warnings.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SecurityInfoCard(
                title = stringResource(R.string.module_script_flags_title),
                body = warnings.joinToString("\n") { "• ${it.file}: ${it.detail}" },
                fill = WarnFill,
                titleColor = WarnAccent,
            )
        }

        Spacer(Modifier.height(20.dp))
        SheetPillButton(text = stringResource(R.string.module_script_warn_continue), onClick = onContinue)
        Text(
            stringResource(R.string.modules_cancel),
            color = SearchColors.Secondary,
            fontFamily = InterFontFamily,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onCancel)
                .padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScriptBlockedContent(
    targetName: String,
    findings: List<ScriptFinding>,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = sheetHandleRoom(), bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(BlockFill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.warning), null, tint = BlockAccent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.module_script_blocked_title),
                    color = BlockAccent, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                )
                Text(targetName, color = SearchColors.Secondary, fontFamily = InterFontFamily, fontSize = 13.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.module_script_blocked_body),
            color = SearchColors.Secondary, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
        )
        if (findings.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SecurityInfoCard(
                title = stringResource(R.string.module_script_blocked_reasons),
                body = findings.joinToString("\n") { "• ${it.file}: ${it.detail}" },
                fill = BlockFill,
                titleColor = BlockAccent,
            )
        }
        Spacer(Modifier.height(20.dp))
        SheetPillButton(text = stringResource(R.string.done), onClick = onClose)
    }
}

@Composable
private fun SecurityInfoCard(title: String, body: String, fill: Color, titleColor: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(WarningCardShape)
            .background(fill)
            .padding(16.dp),
    ) {
        Text(title, color = titleColor, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(body, color = Color.White.copy(alpha = 0.78f), fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
