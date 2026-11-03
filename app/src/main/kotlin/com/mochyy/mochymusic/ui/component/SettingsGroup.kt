// SettingsGroup.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.mochyy.mochymusic.R
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onPlaced
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class SettingsGroupStyle {

    Classic,

    Grouped,
}

@Composable
fun SettingsGroup(
    title: String? = null,
    items: List<SettingsItem>,
    style: SettingsGroupStyle = SettingsGroupStyle.Classic,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {

        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )
        }

        when (style) {
            SettingsGroupStyle.Classic -> ClassicItems(items)
            SettingsGroupStyle.Grouped -> GroupedItems(items)
        }
    }
}

@Composable
private fun ClassicItems(items: List<SettingsItem>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        val visibleItems = items.filter { it.isVisible }
        items.forEach { item ->
            AnimatedVisibility(
                visible = item.isVisible,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {

                val shape = RoundedCornerShape(20.dp)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = com.mochyy.mochymusic.ui.screens.search.SearchColors.Field
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    SettingsItemRow(item = item, style = SettingsGroupStyle.Classic)
                }
            }
        }
    }
}

private class ItemCluster(
    val parent: SettingsItem,
    val subs: MutableList<SettingsItem> = mutableListOf(),
)

private fun clusterItems(items: List<SettingsItem>): List<ItemCluster> {
    val clusters = mutableListOf<ItemCluster>()
    items.forEach { item ->
        if (item.isSubOption && clusters.isNotEmpty()) {
            clusters.last().subs += item
        } else {
            clusters += ItemCluster(item)
        }
    }
    return clusters
}

@Composable
private fun GroupedItems(items: List<SettingsItem>) {
    val clusters = clusterItems(items)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = com.mochyy.mochymusic.ui.screens.search.SearchColors.Field
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            clusters.forEach { cluster ->
                AnimatedVisibility(
                    visible = cluster.parent.isVisible,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                ) {
                    ClusterRows(cluster)
                }
            }
        }
    }
}

@Composable
private fun ClusterRows(cluster: ItemCluster) {
    val hasVisibleSubs = cluster.subs.any { it.isVisible }

    val pillAlpha by animateFloatAsState(
        targetValue = if (hasVisibleSubs) 1f else 0f,
        animationSpec = tween(300),
        label = "pillAlpha"
    )
    val pillInset by animateDpAsState(
        targetValue = if (hasVisibleSubs) 6.dp else 0.dp,
        animationSpec = tween(300),
        label = "pillInset"
    )

    // Expanded sub-menu background slightly brighter than accent surface
    val pillColor = Color(0xFF282830).copy(alpha = pillAlpha)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = pillInset, vertical = pillInset / 2)
            .clip(RoundedCornerShape(22.dp))
            .background(pillColor)
    ) {
        Column {
            SettingsItemRow(item = cluster.parent, style = SettingsGroupStyle.Grouped)
            cluster.subs.forEach { sub ->
                AnimatedVisibility(
                    visible = sub.isVisible,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                ) {
                    SettingsItemRow(item = sub, style = SettingsGroupStyle.Grouped)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SettingsItemRow(
    item: SettingsItem,
    style: SettingsGroupStyle,
) {
    // app behaviour can hide the line under each title, holding the row still shows it
    val (hideDescriptions) = com.mochyy.mochymusic.utils.rememberPreference(
        com.mochyy.mochymusic.constants.HideSettingsDescriptionsKey,
        defaultValue = true,
    )
    // a row arrived at from search or a related link claims the request once, scrolls into view and flashes
    val key = item.highlightKey
    val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val settingsScroll = LocalSettingsScroll.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val placement = remember { SettingsPlacement() }
    androidx.compose.runtime.LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        // watched, not keyed on: clearing the request restarted the effect, so the row never scrolled and stayed lit
        androidx.compose.runtime.snapshotFlow { SettingsHighlight.pending }.collect { pending ->
            if (pending != key) return@collect
            SettingsHighlight.pending = null
            SettingsHighlight.active = key
            // let the page finish opening first, or it scrolls while still sliding in
            kotlinx.coroutines.delay(320)
            val target = placement.coords
            if (settingsScroll != null && target != null) settingsScroll.reveal(target, density) else requester.bringIntoView()
            kotlinx.coroutines.delay(2400)
            if (SettingsHighlight.active == key) SettingsHighlight.active = null
        }
    }

    val isFlashing = key != null && SettingsHighlight.active == key
    val highlightAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFlashing) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 450),
        label = "settingsHighlight",
    )
    val highlightColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .onPlaced { placement.coords = it }
            .drawBehind {
                if (highlightAlpha > 0.01f) {
                    drawRect(color = highlightColor.copy(alpha = 0.22f * highlightAlpha))
                }
            }
            .combinedClickable(
                enabled = item.enabled && (item.onClick != null || item.descriptionText != null),
                onClick = { item.onClick?.invoke() },
                // holding a row shows its whole description, since the line under it is cut to one
                onLongClick = (item.detail ?: item.descriptionText)?.let { full ->
                    { TopToaster.show(full, iconRes = R.drawable.info) }
                },
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        item.icon?.let { icon ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFFE5E5EA)),
                contentAlignment = Alignment.Center
            ) {
                if (item.showBadge) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        }
                    ) {
                        if (item.tintIcon) {
                            Icon(
                                painter = icon,
                                contentDescription = null,
                                tint = if (!item.enabled)
                                    Color(0xFF1C1C1E).copy(alpha = 0.38f)
                                else
                                    Color(0xFF1C1C1E),
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Image(
                                painter = icon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                } else {
                    if (item.tintIcon) {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = if (!item.enabled)
                                Color(0xFF1C1C1E).copy(alpha = 0.38f)
                            else
                                Color(0xFF1C1C1E),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Image(
                            painter = icon,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {

            val titleBaseStyle = if (style == SettingsGroupStyle.Grouped) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.titleMedium
            }
            val titleColor = when {
                !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                style == SettingsGroupStyle.Grouped -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
            ProvideTextStyle(titleBaseStyle.copy(color = titleColor)) {
                item.title()
            }

            val descColor = if (!item.enabled) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            if (item.descriptionText != null && !hideDescriptions) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = item.descriptionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = descColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (item.descriptionText == null) {
                item.description?.let { desc ->
                    Spacer(modifier = Modifier.height(2.dp))
                    ProvideTextStyle(
                        MaterialTheme.typography.labelMedium.copy(color = descColor)
                    ) {

                        desc()
                    }
                }
            }
        }

        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

data class SettingsItem(
    val icon: Painter? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,

    val descriptionText: String? = null,
    /** The whole description, shown when the row is held; [descriptionText] is the short line. */
    val detail: String? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val tintIcon: Boolean = true,
    val iconShape: Shape? = null,
    val enabled: Boolean = true,
    val isVisible: Boolean = true,
    val isSubOption: Boolean = false,
    /** Identifier used by settings search to flash this row when the user arrives from a result. */
    val highlightKey: String? = null,
    // the title as shown in the app's language, so a page's own search finds it in any language
    val searchText: String? = null,
    val onClick: (() -> Unit)? = null
)
