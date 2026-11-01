// settings → modules: every module mochy has, what each adds, and ways to find more

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.DiscordUrl
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.ui.component.BetaBadge
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel

const val ModulesRoute = "modules"
const val ModuleRoutePattern = "module/{id}"
fun moduleRoute(id: String) = "module/$id"

private val RowShape = RoundedCornerShape(24.dp)

@Composable
fun ModulesScreen(navController: NavController, viewModel: ModulesViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val overlay = LocalZoomOutOverlayState.current
    val context = LocalContext.current
    SubSettingsScaffold(
        title = stringResource(R.string.settings_modules_category),
        onBack = { navController.navigateUp() },
        titleBadge = { BetaBadge() },
    ) {
        // mochy's bar is where people share modules, so it leads the page
        ModulesBarBanner { context.openDevUrl(DiscordUrl) }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.modules_intro),
            fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 19.sp, color = LabColors.Secondary,
        )
        Spacer(Modifier.height(16.dp))
        entries.forEach { entry ->
            ModuleRow(entry) { navController.navigate(moduleRoute(entry.id)) }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(6.dp))
        FindMoreButton {
            overlay.showFindModulesSheet(viewModel) { id -> navController.navigate(moduleRoute(id)) }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun ModuleRow(entry: ModuleEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(LabColors.Card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModuleLogo(entry.id, entry.module.info.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.module.info.name, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
            Text(moduleStatus(entry), fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FeatureChips(entry.module.adds)
                Spacer(Modifier.width(8.dp))
                // the chips come first, the origin gives way when the row runs out of room
                Text(moduleOrigin(entry), fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(painterResource(R.drawable.navigate_next), null, tint = LabColors.Secondary, modifier = Modifier.size(18.dp))
    }
}

/** an empty slot drawn in dashes with a puzzle piece in it: there's room for one more module */
@Composable
private fun FindMoreButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(LabColors.Row),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.puzzle), null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.modules_find_more), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
            Text(stringResource(R.string.modules_find_more_desc), fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary)
        }
        Icon(painterResource(R.drawable.add), null, tint = LabColors.Secondary, modifier = Modifier.size(20.dp))
    }
}

/** mochy peeking up from the bottom of the card, leading to the discord server where people share modules */
@Composable
private fun ModulesBarBanner(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RowShape)
            .background(LabColors.Card)
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the drawing ends at her jacket, so sitting on the card's bottom edge cuts her off there like the design
        Image(
            painterResource(R.drawable.modules_bar_mochy), null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.Bottom).padding(top = 8.dp).fillMaxHeight(),
        )
        Spacer(Modifier.width(22.dp))
        Text(
            stringResource(R.string.modules_bar),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 22.sp,
            letterSpacing = (-0.6).sp, color = Color.White, modifier = Modifier.weight(1f),
        )
    }
}
