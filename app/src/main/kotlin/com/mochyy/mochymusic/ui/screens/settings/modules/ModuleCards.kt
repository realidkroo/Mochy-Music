// the cards near the top of a module's page: its switch, where it's from, its account and server checks, and what data saver does to it

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.modules.ModuleOrigin
import com.mochyy.mochymusic.modules.SourceModules
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.SkinSource
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberDataSaverActive
import java.text.DateFormat
import java.util.Date

/** a card on a module's page: a lighter wash over whatever colour the page took, so it reads on any banner */
internal val PageCardColor = Color.White.copy(alpha = 0.10f)
internal val PageCardShape = RoundedCornerShape(22.dp)
internal val PageSoftText = Color.White.copy(alpha = 0.62f)

@Composable
internal fun PageCard(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PageCardShape)
            .background(PageCardColor)
            .padding(padding.dp),
        content = content,
    )
}

/** the one switch: on, everything the module adds shows up; off, it's all gone again */
@Composable
internal fun EnableCard(entry: ModuleEntry, onChange: (Boolean) -> Unit) {
    val name = entry.module.info.name
    val needsAccount = entry.module.signIn != null
    val signedIn = entry.account is AccountState.SignedIn
    val on = entry.enabled && signedIn
    val what = buildList {
        if (entry.adds(ModuleFeature.AudioSource)) add(stringResource(R.string.module_use_songs, name))
        if (entry.adds(ModuleFeature.Lyrics)) add(stringResource(R.string.module_use_lyrics))
        if (entry.adds(ModuleFeature.Metadata)) add(stringResource(R.string.module_use_metadata))
        if (entry.adds(ModuleFeature.Look)) add(stringResource(R.string.module_use_look))
    }
    PageCard(padding = 14) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = signedIn) { onChange(!on) },
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = if (on) 0.9f else 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(painterResource(R.drawable.check), null, tint = Color.Black, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.module_enable), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f),
            )
            AppSwitch(checked = on, onCheckedChange = onChange, enabled = signedIn)
        }
        val detail = if (needsAccount && !signedIn) stringResource(R.string.module_use_sign_in_first) else what.joinToString(" ")
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(detail, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = PageSoftText)
        }
    }
}

/** when it came in, from where, which version and who made it */
@Composable
internal fun InfoCard(entry: ModuleEntry) {
    val info = entry.module.info
    val added = entry.addedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
    val source = when (val origin = entry.origin) {
        ModuleOrigin.BuiltIn -> stringResource(R.string.module_info_built_in)
        is ModuleOrigin.App -> origin.packageName
        is ModuleOrigin.Web -> origin.base.substringAfter("://")
        is ModuleOrigin.File -> origin.fileName
        is ModuleOrigin.Code -> origin.fileName
    }
    PageCard(padding = 18) {
        Text(stringResource(R.string.module_info), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = PageSoftText)
        Spacer(Modifier.height(6.dp))
        added?.let { InfoLine(stringResource(R.string.module_info_added), it) }
        InfoLine(stringResource(R.string.module_info_source), source)
        InfoLine(stringResource(R.string.module_info_version), info.version)
        info.author?.takeIf { it.isNotBlank() }?.let { InfoLine(stringResource(R.string.module_info_author), it) }
        InfoLine(stringResource(R.string.module_info_status), moduleStatus(entry))
        GlassNote(entry)
    }
}

/** a look that brings its own glass or player shaders says when this phone can't run them, or when one didn't compile and why */
@Composable
private fun GlassNote(entry: ModuleEntry) {
    val skin = (entry.module as? SkinSource)?.skin ?: return
    if (skin.glass == null && skin.playerShaders.isEmpty()) return
    val glassProblem = ActiveSkin.glassProblem?.takeIf { it.first == entry.id }?.second
    val shaderProblem = ActiveSkin.shaderProblem?.takeIf { it.first == entry.id }?.second
    val text = when {
        glassProblem != null -> stringResource(R.string.module_glass_problem, glassProblem)
        shaderProblem != null -> stringResource(R.string.module_shader_problem, shaderProblem)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && skin.glass != null -> stringResource(R.string.module_glass_needs_13)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> stringResource(R.string.module_shader_needs_13)
        else -> return
    }
    Spacer(Modifier.height(8.dp))
    Text(text, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = PageSoftText)
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label ", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        Text(value, fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

/** signing in, checking the account or the server, signing out; a look from a zip has nothing to check */
@Composable
internal fun AccountActions(entry: ModuleEntry, checking: Boolean, onSignIn: () -> Unit, onCheck: () -> Unit, onSignOut: () -> Unit) {
    if (entry.origin is ModuleOrigin.File) return
    val module = entry.module
    val needsAccount = module.signIn != null
    Spacer(Modifier.height(10.dp))
    if (needsAccount && entry.account !is AccountState.SignedIn) {
        PillButton(
            text = if (entry.account is AccountState.Expired) stringResource(R.string.module_sign_in_again)
            else stringResource(R.string.module_sign_in_to, module.info.name),
            filled = true,
            onClick = onSignIn,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(
            text = when {
                checking -> stringResource(R.string.module_checking)
                needsAccount -> stringResource(R.string.module_check_account)
                module is ItemListModule -> stringResource(R.string.module_check_items, module.itemsLabel.lowercase())
                else -> stringResource(R.string.module_check_server)
            },
            icon = R.drawable.refresh,
            busy = checking,
            modifier = Modifier.weight(1f),
            onClick = onCheck,
        )
        if (needsAccount) PillButton(stringResource(R.string.module_sign_out), icon = R.drawable.close, modifier = Modifier.weight(1f), onClick = onSignOut)
    }
}

/** what data saver does to this module's songs while it's on: the lighter stream it asks for, or that it has none */
@Composable
internal fun DataSaverCard(entry: ModuleEntry) {
    if (!rememberDataSaverActive()) return
    val quality = entry.module.choices.firstOrNull { it.id == SourceModules.QualityChoice }?.options
    val cap = quality?.let { options -> options.firstOrNull { it.id == "high" } ?: options.getOrNull(1) }
    Spacer(Modifier.height(10.dp))
    PageCard(padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.speed), null, tint = Color.Black, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.module_data_saver_on), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (cap != null) stringResource(R.string.module_data_saver_capped, cap.label) else stringResource(R.string.module_data_saver_no_cap),
            fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = PageSoftText,
        )
    }
}

/** a round-ended button on a module's page; filled is the one thing to do next */
@Composable
internal fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    filled: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (filled && enabled) Color.White else PageCardColor)
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        val ink = when {
            !enabled -> Color.White.copy(alpha = 0.35f)
            filled -> Color.Black
            else -> Color.White
        }
        if (busy) {
            CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(painterResource(icon), null, tint = ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ink, maxLines = 1)
    }
}

/** a section title on a module's page, the mock's "Options" */
@Composable
internal fun PageSectionTitle(text: String, subtitle: String? = null) {
    Spacer(Modifier.height(26.dp))
    Text(text, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.4).sp, color = Color.White)
    subtitle?.let {
        Spacer(Modifier.height(2.dp))
        Text(it, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = PageSoftText)
    }
    Spacer(Modifier.height(12.dp))
}

/** where the module updates from, and the button that brings the newer copy in once one is out */
@Composable
internal fun UpdateCard(entry: ModuleEntry, updating: Boolean, checking: Boolean, onUpdate: () -> Unit, onCheck: () -> Unit) {
    val source = entry.source ?: return
    val update = entry.update
    val checked = entry.checkedAt?.let { android.text.format.DateUtils.getRelativeTimeSpanString(it).toString() }
    val headline = when {
        update?.version != null -> stringResource(R.string.module_update_ready, update.version)
        update != null -> stringResource(R.string.module_update_ready_unnamed)
        checked != null -> stringResource(R.string.module_update_none, checked)
        else -> stringResource(R.string.module_update_never)
    }
    Spacer(Modifier.height(10.dp))
    PageCard(padding = 16) {
        Text(stringResource(R.string.module_update_title), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = PageSoftText)
        Spacer(Modifier.height(6.dp))
        Text(headline, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.module_update_from, source.label), fontFamily = InterFontFamily, fontSize = 12.sp, color = PageSoftText)
        Spacer(Modifier.height(12.dp))
        if (update != null) {
            PillButton(
                text = when {
                    updating -> stringResource(R.string.module_updating)
                    update.version != null -> stringResource(R.string.module_update_to, update.version)
                    else -> stringResource(R.string.module_update_now)
                },
                icon = R.drawable.download,
                filled = true,
                busy = updating,
                onClick = onUpdate,
            )
        } else {
            PillButton(
                text = stringResource(if (checking) R.string.module_checking else R.string.module_update_check),
                icon = R.drawable.refresh,
                busy = checking,
                onClick = onCheck,
            )
        }
    }
}
