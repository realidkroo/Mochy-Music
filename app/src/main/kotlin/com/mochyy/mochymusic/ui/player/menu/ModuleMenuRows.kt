// the rows switched-on modules add to the player's menu: a web page, one of their own pages, or a search, with the song filled in, and the look's customise sheet

package com.mochyy.mochymusic.ui.player.menu

import android.content.Context
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.modules.skin.ActionTarget
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.ModuleMenu
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetIcon
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.screens.settings.modules.modulePageRoute
import java.net.URLEncoder

@Composable
internal fun ModuleMenuRows(song: MediaMetadata?, onDismiss: () -> Unit, onOpenRoute: ((String) -> Unit)?, onCustomise: (() -> Unit)? = null) {
    val actions = ModuleMenu.actions
    // the look's player offers its customise sheet here too, next to the module's own rows
    val customise = ActiveSkin.current?.takeIf { it.player?.customise != null && onCustomise != null }
    if (actions.isEmpty() && customise == null) return
    val context = LocalContext.current
    Spacer(Modifier.height(16.dp))
    if (customise != null && onCustomise != null) {
        val name = customise.player?.name ?: customise.moduleName
        ModuleMenuRow(stringResource(R.string.skin_customise_row, name), picture = null, enabled = true, onClick = onCustomise)
    }
    actions.forEachIndexed { index, item ->
        if (index > 0 || customise != null) Spacer(Modifier.height(8.dp))
        // a page or a search needs somewhere to open, a web link only needs the phone's browser
        val enabled = song != null && (item.action.target is ActionTarget.Link || onOpenRoute != null)
        ModuleMenuRow(item.action.title, item.skin.resolve(item.action.icon), enabled) {
            val now = song ?: return@ModuleMenuRow
            onDismiss()
            openModuleTarget(context, item.action.target, now, item.moduleId) { route -> onOpenRoute?.invoke(route) }
        }
    }
}

/** runs a module's link, page or search with the song filled in; pages and searches go through [openRoute] */
internal fun openModuleTarget(context: Context, target: ActionTarget, song: MediaMetadata?, moduleId: String, openRoute: (String) -> Unit) {
    when (target) {
        is ActionTarget.Link -> context.openDevUrl(fill(target.template, song, encode = true))
        is ActionTarget.Page -> openRoute(modulePageRoute(moduleId, target.id))
        is ActionTarget.Search -> openRoute("search/" + URLEncoder.encode(fill(target.template, song, encode = false), "UTF-8"))
    }
}

// the same look as the menu's own rows, with the module's picture in place of mochy's icon when it brings one
@Composable
private fun ModuleMenuRow(title: String, picture: Any?, enabled: Boolean, onClick: () -> Unit) {
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
        if (picture != null) {
            AsyncImage(
                model = picture, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)),
                alpha = alpha,
            )
        } else {
            SheetIcon(R.drawable.puzzle, tint = Color.White.copy(alpha = alpha))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            title, color = Color.White.copy(alpha = alpha), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
    }
}

// {title}, {artist}, {album} and {id} swapped for the song's own, made safe for a link when it goes into one; empty with no song
private fun fill(template: String, song: MediaMetadata?, encode: Boolean): String {
    fun part(value: String?) = value.orEmpty().let { if (encode) URLEncoder.encode(it, "UTF-8").replace("+", "%20") else it }
    return template
        .replace("{title}", part(song?.title))
        .replace("{artist}", part(song?.artists?.joinToString(", ") { it.name }))
        .replace("{album}", part(song?.album?.title))
        .replace("{id}", part(song?.id))
}
