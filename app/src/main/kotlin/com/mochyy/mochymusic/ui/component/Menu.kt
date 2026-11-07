// Menu.kt
//
// The row style shared by every item menu — song, album, artist, playlist, and their YouTube
// variants.
//
// Despite the `Material3` names (kept so the ~10 menus that call this don't all need editing),
// nothing here is Material 3 any more: these rows now render in the same language as the player's
// own action sheet — flat dark pills, a plain white icon, one line of text. The player sheet
// and the item menus used to be two visibly different designs reachable from the same screen, and
// the fastest way to make every menu match is to change the one component they all go through
// rather than to restyle each menu by hand.
//
// Two deliberate omissions relative to the old M3 version:
//
//   * `description` is accepted but never rendered. Menus are a list of verbs; a second
//     explanatory line under each one doubles the height of the sheet and slows down the scan.
//   * `cardColors` is accepted but ignored, so one menu can't quietly diverge from the rest.
//
// Both fields stay on the data class purely so existing call sites keep compiling; they are inert.

package com.mochyy.mochymusic.ui.component

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface

@Composable
fun Material3MenuGroup(
    items: List<Material3MenuItemData>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            MenuItemRow(
                icon = item.icon,
                enabled = item.onClick != null,
                onClick = { item.onClick?.invoke() },
                trailing = item.trailingContent,
                title = item.title,
            )
        }
    }
}

/**
 * One row of a menu: a flat dark pill with the icon, the title, and anything [trailing]. Also used
 * by NewActionGrid for the actions that don't make it onto its two cards.
 */
@Composable
internal fun MenuItemRow(
    icon: (@Composable () -> Unit)?,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.35f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            // Menus pass a bare Icon with no tint, which picks up LocalContentColor, so white here
            // makes every menu's icons white without each one asking. A menu that sets its own tint
            // (the red heart) still wins, which is intended.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(SheetIconSize),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides Color.White.copy(alpha = contentAlpha),
                ) {
                    icon()
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            ProvideTextStyle(
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = contentAlpha),
                )
            ) {
                title()
            }
        }

        trailing?.let {
            Spacer(modifier = Modifier.width(8.dp))
            it()
        }
    }
}

data class Material3MenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    /** Accepted for source compatibility, never rendered. See the file header. */
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    /** Accepted for source compatibility, never applied. See the file header. */
    val cardColors: CardColors? = null,
    val trailingContent: (@Composable () -> Unit)? = null
)
