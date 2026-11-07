// settings → modules: a word on what modules are, mochy's bar, then the looks and the js and source modules, each list dragged into order and swiped to delete

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.DiscordUrl
import com.mochyy.mochymusic.core.updater.GithubRepoUrl
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.ui.component.ActionPromptDialog
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

/** how to make a module, the guide in mochy's repo */
const val ModuleDocsUrl = "$GithubRepoUrl/blob/main/modules/README.md"

private val CardShape = RoundedCornerShape(26.dp)

@Composable
fun ModulesScreen(navController: NavController, viewModel: ModulesViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val overlay = LocalZoomOutOverlayState.current
    val context = LocalContext.current
    var removing by remember { mutableStateOf<ModuleEntry?>(null) }
    val looks = entries.filter { it.isLookOnly }
    val sources = entries.filter { !it.isLookOnly }
    val open: (ModuleEntry) -> Unit = { navController.navigate(moduleRoute(it.id)) }
    val findMore = { overlay.showFindModulesSheet(viewModel) { id -> navController.navigate(moduleRoute(id)) } }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_modules_category),
        onBack = { navController.navigateUp() },
        titleBadge = { BetaBadge() },
    ) {
        ThirdPartyCard { context.openDevUrl(ModuleDocsUrl) }
        Spacer(Modifier.height(14.dp))
        // mochy's bar is where people share modules, so it sits right up top
        ModulesBarBanner { context.openDevUrl(DiscordUrl) }
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(1.dp).background(Color.White.copy(alpha = 0.28f)))
        Spacer(Modifier.height(28.dp))

        ModuleSectionTitle(stringResource(R.string.modules_looks))
        if (looks.isEmpty()) {
            DashedSlot(stringResource(R.string.modules_looks_empty), onClick = findMore)
        } else {
            ModuleList(looks, onOpen = open, onRemove = { removing = it }, onReorder = { viewModel.reorder(it, looks = true) })
            Spacer(Modifier.height(12.dp))
            DashedSlot(stringResource(R.string.modules_add_more), onClick = findMore, compact = true)
        }

        Spacer(Modifier.height(34.dp))
        ModuleSectionTitle(stringResource(R.string.modules_sources), stringResource(R.string.modules_swipe_to_delete))
        ModuleList(sources, onOpen = open, onRemove = { removing = it }, onReorder = { viewModel.reorder(it, looks = false) })
        Spacer(Modifier.height(12.dp))
        DashedSlot(stringResource(R.string.modules_add_more), onClick = findMore, compact = true)
        Spacer(Modifier.height(120.dp))
    }

    removing?.let { entry ->
        ActionPromptDialog(
            title = stringResource(R.string.module_remove_title, entry.module.info.name),
            onDismiss = { removing = null },
            onCancel = { removing = null },
            onConfirm = {
                viewModel.remove(entry.id)
                removing = null
            },
        ) {
            Text(stringResource(R.string.module_remove_body), fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/** the note that modules come from other people, with the way to the guide for making one */
@Composable
private fun ThirdPartyCard(onDocs: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(CardShape)
            .background(LabColors.Card)
            .clickable(onClick = onDocs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TwoPhones(Modifier.padding(start = 26.dp, top = 12.dp).width(84.dp).fillMaxHeight())
        Spacer(Modifier.width(18.dp))
        val docs = stringResource(R.string.modules_third_party_docs)
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.modules_third_party))
                append(' ')
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(docs) }
            },
            fontFamily = InterFontFamily, fontSize = 12.5.sp, lineHeight = 16.sp, letterSpacing = (-0.2).sp,
            color = LabColors.Secondary,
            modifier = Modifier.weight(1f).padding(end = 18.dp),
        )
    }
}

// the mock's two phones leaning on each other, cut off by the card's bottom edge
@Composable
private fun TwoPhones(modifier: Modifier) {
    Canvas(modifier) {
        val phone = Size(size.width * 0.58f, size.height * 1.25f)
        val corner = CornerRadius(phone.width * 0.16f)
        val outline = Stroke(1.dp.toPx())
        rotate(-7f, pivot = Offset(phone.width * 0.4f, size.height)) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF3A3A3D), Color(0xFF2A2A2C))),
                topLeft = Offset(0f, size.height * 0.16f),
                size = phone,
                cornerRadius = corner,
            )
        }
        rotate(5f, pivot = Offset(size.width * 0.7f, size.height)) {
            val left = size.width - phone.width
            drawRoundRect(color = Color(0xFF161618), topLeft = Offset(left, 0f), size = phone, cornerRadius = corner)
            drawRoundRect(color = Color.White.copy(alpha = 0.16f), topLeft = Offset(left, 0f), size = phone, cornerRadius = corner, style = outline)
        }
    }
}

/** mochy peeking up from the bottom of the card, leading to the discord server where people share modules */
@Composable
private fun ModulesBarBanner(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(CardShape)
            .background(LabColors.Card)
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the drawing ends at her jacket, so sitting on the card's bottom edge cuts her off there like the design
        Image(
            painterResource(R.drawable.modules_bar_mochy), null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.Bottom).padding(top = 10.dp).fillMaxHeight(),
        )
        Spacer(Modifier.width(22.dp))
        Text(
            stringResource(R.string.modules_bar),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp,
            letterSpacing = (-0.7).sp, color = Color.White, modifier = Modifier.weight(1f),
        )
    }
}
