// LibraryTabs.kt
//
// The All / Local / Cached / Imported switch at the top of the Library, and the small pieces the
// tabs share: the two-wide card grid, the caps section label and the "Manage" pill of a tab.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.ui.screens.search.SearchHorizontalPadding
import com.mochyy.mochymusic.viewmodels.LibraryShelfKind

/**
 * What the Library shows: everything, what plays from the phone, what the player has cached, or
 * what was imported. [shelf] is the slice of the library a tab is built from, if it is one.
 */
enum class LibraryTab(val labelRes: Int, val shelf: LibraryShelfKind? = null) {
    All(R.string.library_tab_all),
    Local(R.string.library_tab_local, LibraryShelfKind.Local),
    Cached(R.string.cached, LibraryShelfKind.Cached),
    Imported(R.string.library_tab_imported),
}

private val ChipTrack = Color(0xFF161618)
private val Chip = Color(0xFF232326)
private val ChipSelected = Color(0xFF3D3D41)

private val ChipTrackPadding = 3.dp
private val ChipGap = 4.dp
private val ChipRowGap = 6.dp

/** The tabs as small pills in a dark track, the picked one a step lighter. */
@Composable
fun LibraryTabChips(
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // with a big font or display size the pills that don't fit wrap to a row below, each row with its own track so no empty dark patch is left beside it
    Layout(
        modifier = modifier,
        content = {
            LibraryTab.entries.forEach { _ -> Box(Modifier.background(ChipTrack, RoundedCornerShape(percent = 50))) }
            LibraryTab.entries.forEach { tab -> LibraryTabChip(tab, isSelected = tab == selected, onSelect = onSelect) }
        },
    ) { measurables, constraints ->
        val tracks = measurables.take(LibraryTab.entries.size)
        val pad = ChipTrackPadding.roundToPx()
        val gap = ChipGap.roundToPx()
        val rowGap = ChipRowGap.roundToPx()
        val room = if (constraints.hasBoundedWidth) (constraints.maxWidth - pad * 2).coerceAtLeast(0) else Constraints.Infinity
        val chips = measurables.drop(LibraryTab.entries.size).map { it.measure(Constraints(maxWidth = room)) }

        // a pill stays on the current row while it fits, otherwise it starts the next one
        val rows = mutableListOf(mutableListOf<Placeable>())
        var rowWidth = 0
        chips.forEach { chip ->
            val row = rows.last()
            val widthWithChip = if (row.isEmpty()) chip.width else rowWidth + gap + chip.width
            if (row.isNotEmpty() && widthWithChip > room) {
                rows += mutableListOf(chip)
                rowWidth = chip.width
            } else {
                row += chip
                rowWidth = widthWithChip
            }
        }

        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val trackPlaceables = rows.mapIndexed { index, row ->
            val width = row.sumOf { it.width } + gap * (row.size - 1) + pad * 2
            tracks[index].measure(Constraints.fixed(width, rowHeights[index] + pad * 2))
        }
        val width = trackPlaceables.maxOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = trackPlaceables.sumOf { it.height } + rowGap * (rows.size - 1)

        layout(width, height) {
            var y = 0
            rows.forEachIndexed { index, row ->
                trackPlaceables[index].placeRelative(0, y)
                var x = pad
                row.forEach { chip ->
                    chip.placeRelative(x, y + pad + (rowHeights[index] - chip.height) / 2)
                    x += chip.width + gap
                }
                y += trackPlaceables[index].height + rowGap
            }
        }
    }
}

@Composable
private fun LibraryTabChip(tab: LibraryTab, isSelected: Boolean, onSelect: (LibraryTab) -> Unit) {
    val background by animateColorAsState(
        targetValue = if (isSelected) ChipSelected else Chip,
        animationSpec = tween(220),
        label = "libraryTabChip",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .clickable(
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onSelect(tab) },
            )
            .heightIn(min = 28.dp)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            text = stringResource(tab.labelRes),
            color = if (isSelected) SearchColors.Primary else SearchColors.Primary.copy(alpha = 0.72f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** "ALL MUSIC", "PLAYLISTS": a small caps label over a group of cards. */
@Composable
fun LibraryCapsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = SearchColors.Primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        modifier = modifier.padding(horizontal = SearchHorizontalPadding),
    )
}

/** The small centred pill at the bottom of a tab that opens its Manage sheet. */
@Composable
fun LibraryManagePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFF2E2E31))
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 30.dp)
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                color = SearchColors.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** One card in a [LibraryCardGrid]. */
@Immutable
class LibraryCardEntry(
    val title: String,
    val count: Int,
    val covers: List<String?>,
    val onClick: () -> Unit,
    val roundCovers: Boolean = false,
    val icon: (@Composable () -> Unit)? = null,
)

/** Cards two to a row, the last one keeping its half width when it is alone. */
@Composable
fun LibraryCardGrid(entries: List<LibraryCardEntry>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { entry ->
                    LibraryCategoryCard(
                        title = entry.title,
                        count = entry.count,
                        covers = entry.covers,
                        onClick = entry.onClick,
                        icon = entry.icon,
                        roundCovers = entry.roundCovers,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
