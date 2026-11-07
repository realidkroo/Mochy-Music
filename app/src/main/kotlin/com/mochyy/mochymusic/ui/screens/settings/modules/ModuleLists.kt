// the lists on a module's page: the servers it plays from, the scripts and links people added to it, and the pages it brings

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.ModuleItem
import com.mochyy.mochymusic.module.ModuleServer
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.SwipeAction
import com.mochyy.mochymusic.ui.component.SwipeActionsBox
import com.mochyy.mochymusic.ui.component.SwipeRemoveColor
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel

/** the servers the module plays from, the ones people added first, and adding another */
@Composable
internal fun ModuleServers(id: String, viewModel: ModulesViewModel) {
    val servers by viewModel.servers.collectAsState()
    val overlay = LocalZoomOutOverlayState.current
    LaunchedEffect(id) { viewModel.loadServers(id) }

    PageSectionTitle(stringResource(R.string.module_servers), stringResource(R.string.module_servers_body))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        servers.forEach { server -> ServerRow(server) { viewModel.removeServer(id, server.url) } }
        PillButton(stringResource(R.string.module_server_add), icon = R.drawable.add) { overlay.showAddServerSheet(id, viewModel) }
    }
}

@Composable
private fun ServerRow(server: ModuleServer, onRemove: () -> Unit) {
    val address = server.url.substringAfter("://")
    // a server found through a site goes by the site's name, with its own address under it
    val detail = when {
        server.builtIn -> stringResource(R.string.module_server_built_in)
        server.name != address -> address
        else -> null
    }
    ListCard(
        name = server.name.ifBlank { address },
        detail = detail,
        icon = null,
        working = null,
        onRemove = onRemove.takeIf { !server.builtIn },
    )
}

/** what people added to the module (scripts, links), each with whether it worked last time, swiped away to remove */
@Composable
internal fun ModuleItems(id: String, module: ItemListModule, viewModel: ModulesViewModel) {
    val items by viewModel.items.collectAsState()
    val overlay = LocalZoomOutOverlayState.current
    LaunchedEffect(id) { viewModel.loadItems(id) }

    val working = items.count { it.working == true }
    val tried = items.count { it.working != null }
    PageSectionTitle(
        module.itemsLabel,
        if (tried > 0) stringResource(R.string.module_items_working, working, items.size) else module.itemsAbout.ifBlank { null },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> ItemRow(item) { viewModel.removeItem(id, item.id) } }
        PillButton(stringResource(R.string.module_items_add), icon = R.drawable.add, filled = items.isEmpty()) {
            overlay.showAddItemsSheet(id, module.info.name, viewModel)
        }
    }
    if (tried > 0 && module.itemsAbout.isNotBlank()) {
        Text(module.itemsAbout, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = PageSoftText, modifier = Modifier.padding(top = 10.dp, start = 6.dp, end = 6.dp))
    }
}

@Composable
private fun ItemRow(item: ModuleItem, onRemove: () -> Unit) {
    ListCard(item.name, item.detail.ifBlank { null }, icon = item.icon, working = item.working, onRemove = onRemove, seed = item.id)
}

/** one row of a module's list: an icon or a letter, a name, a line under it, a dot for whether it works, and a swipe to remove it */
@Composable
private fun ListCard(name: String, detail: String?, icon: String?, working: Boolean?, onRemove: (() -> Unit)?, seed: String = name) {
    val label = stringResource(R.string.module_remove_short)
    SwipeActionsBox(
        end = if (onRemove != null) {
            { SwipeAction(R.drawable.delete, SwipeRemoveColor, label, onTrigger = onRemove) }
        } else null,
        slab = false,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PageCardColor)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            ModuleLogo(seed, name, size = 40, art = icon, corner = 0.28f)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (working != null) {
                        Spacer(Modifier.width(7.dp))
                        StatusDot(if (working) StatusOn else StatusTrouble, size = 7.dp)
                    }
                }
                detail?.let { Text(it, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = PageSoftText, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** the pages a look module brings, there to open while it's on */
@Composable
internal fun ModulePages(entry: ModuleEntry, viewModel: ModulesViewModel, onOpen: (String) -> Unit) {
    val pages = remember(entry.id) { viewModel.skinOf(entry.id)?.pages.orEmpty() }
    if (pages.isEmpty()) return
    PageSectionTitle(stringResource(R.string.module_pages), stringResource(R.string.module_pages_off).takeIf { !entry.enabled })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pages.forEach { page ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(PageCardColor)
                    .clickable(enabled = entry.enabled) { onOpen(page.id) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(page.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White.copy(alpha = if (entry.enabled) 1f else 0.45f))
                    if (page.inSettings) Text(stringResource(R.string.module_page_in_settings), fontFamily = InterFontFamily, fontSize = 12.sp, color = PageSoftText)
                }
                Icon(painterResource(R.drawable.navigate_next), null, tint = PageSoftText, modifier = Modifier.size(18.dp))
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
