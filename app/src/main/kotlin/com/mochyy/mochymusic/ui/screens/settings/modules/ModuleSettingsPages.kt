// the pages switched-on modules put in settings, each a row under modules with the module's own icon

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.SubcomposeAsyncImage
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel

private val BubbleColor = Color(0xFF1C1C1E)
private val SelectedBubbleColor = Color(0xFF3A3A3E)

@Composable
fun ModuleSettingsPages(open: (String) -> Unit, selected: String? = null, viewModel: ModulesViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val rows = remember(entries) {
        entries.filter { it.active }.flatMap { entry ->
            val skin = viewModel.skinOf(entry.id)
            skin?.pages.orEmpty().filter { it.inSettings }.map { page ->
                // the page's own icon, else its module's
                val icon = page.icon?.let { path -> skin?.resolve(path)?.let { if (it is java.io.File) it.path else it.toString() } } ?: entry.icon
                SettingsPageRow(modulePageRoute(entry.id, page.id), page.title, page.subtitle ?: entry.module.info.name, icon, entry.id, entry.module.info.name)
            }
        }
    }
    rows.forEach { row ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (selected == row.route) SelectedBubbleColor else BubbleColor)
                .clickable { open(row.route) }
                .padding(horizontal = 16.dp),
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF333338)), contentAlignment = Alignment.Center) {
                if (row.icon != null) {
                    SubcomposeAsyncImage(
                        model = artModel(row.icon), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp), error = { ModuleLogo(row.moduleId, row.moduleName, size = 40, corner = 0.5f) },
                    )
                } else {
                    ModuleLogo(row.moduleId, row.moduleName, size = 40, corner = 0.5f)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(row.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.subtitle, fontFamily = InterFontFamily, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private class SettingsPageRow(val route: String, val title: String, val subtitle: String, val icon: String?, val moduleId: String, val moduleName: String)
