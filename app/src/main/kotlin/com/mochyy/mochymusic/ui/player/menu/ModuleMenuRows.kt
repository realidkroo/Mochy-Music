// the rows switched-on modules add to the player's menu: a web page, one of their own pages, or a search, with the song filled in

package com.mochyy.mochymusic.ui.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.modules.skin.ActionTarget
import com.mochyy.mochymusic.modules.skin.ModuleMenu
import com.mochyy.mochymusic.modules.skin.ModuleMenuAction
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetIcon
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.screens.settings.modules.modulePageRoute
import java.net.URLEncoder

@Composable
internal fun ModuleMenuRows(song: MediaMetadata?, onDismiss: () -> Unit, onOpenRoute: ((String) -> Unit)?) {
    val actions = ModuleMenu.actions
    if (actions.isEmpty()) return
    val context = LocalContext.current
    Spacer(Modifier.height(16.dp))
    actions.forEachIndexed { index, item ->
        if (index > 0) Spacer(Modifier.height(8.dp))
        // a page or a search needs somewhere to open, a web link only needs the phone's browser
        val enabled = song != null && (item.action.target is ActionTarget.Link || onOpenRoute != null)
        ModuleMenuRow(item, enabled) {
            val now = song ?: return@ModuleMenuRow
            onDismiss()
            when (val target = item.action.target) {
                is ActionTarget.Link -> context.openDevUrl(fill(target.template, now, encode = true))
                is ActionTarget.Page -> onOpenRoute?.invoke(modulePageRoute(item.moduleId, target.id))
                is ActionTarget.Search -> onOpenRoute?.invoke("search/" + URLEncoder.encode(fill(target.template, now, encode = false), "UTF-8"))
            }
        }
    }
}

// the same look as the menu's own rows, with the module's picture in place of mochy's icon when it brings one
@Composable
private fun ModuleMenuRow(item: ModuleMenuAction, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.35f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        val icon = item.skin.resolve(item.action.icon)
        if (icon != null) {
            AsyncImage(
                model = icon, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)),
                alpha = alpha,
            )
        } else {
            SheetIcon(R.drawable.puzzle, tint = Color.White.copy(alpha = alpha))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            item.action.title, color = Color.White.copy(alpha = alpha), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
    }
}

// {title}, {artist}, {album} and {id} swapped for the song's own, made safe for a link when it goes into one
private fun fill(template: String, song: MediaMetadata, encode: Boolean): String {
    fun part(value: String?) = value.orEmpty().let { if (encode) URLEncoder.encode(it, "UTF-8").replace("+", "%20") else it }
    return template
        .replace("{title}", part(song.title))
        .replace("{artist}", part(song.artists.joinToString(", ") { it.name }))
        .replace("{album}", part(song.album?.title))
        .replace("{id}", part(song.id))
}
