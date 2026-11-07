// the look module's customise sheet: the settings its player names, changed right over the player

package com.mochyy.mochymusic.ui.player.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.ui.screens.settings.modules.ModuleOptions
import com.mochyy.mochymusic.viewmodels.ModulesViewModel

@Composable
fun SkinCustomiseSheet(onDismiss: () -> Unit) {
    val skin = ActiveSkin.current
    val customise = skin?.player?.customise
    val viewModel: ModulesViewModel = hiltViewModel()
    val entries by viewModel.entries.collectAsState()
    val entry = skin?.let { s -> entries.firstOrNull { it.id == s.moduleId } }
    // the settings in the order the player listed them, leaving out any key the module doesn't have
    val fields = customise?.keys.orEmpty().mapNotNull { key -> entry?.fields?.firstOrNull { it.id == key } }
    val name = skin?.player?.name ?: skin?.moduleName.orEmpty()

    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
        ) {
            Text(
                text = customise?.title ?: stringResource(R.string.skin_customise_title),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (entry == null || fields.isEmpty()) stringResource(R.string.skin_customise_empty) else stringResource(R.string.skin_customise_body, name),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            if (entry != null && fields.isNotEmpty()) {
                ModuleOptions(
                    entry = entry.copy(fields = fields),
                    onPick = { _, _ -> },
                    onField = { field, value -> viewModel.setField(entry.id, field, value) },
                    titled = false,
                )
            }
        }
    }
}
