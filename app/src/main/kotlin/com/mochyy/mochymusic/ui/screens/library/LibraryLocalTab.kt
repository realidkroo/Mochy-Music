// LibraryLocalTab.kt
//
// The Library's Local tab: everything that plays from this phone, songs downloaded for offline and
// audio files imported from storage. At the top, the ways to bring files in; under them the same
// cards as All, counting only what is local; at the bottom, Manage local.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.ui.screens.search.SearchHorizontalPadding
import com.mochyy.mochymusic.ui.theme.InterFontFamily

internal fun LazyListScope.libraryLocalTab(
    data: ShelfTabData,
    hasStorageAccess: Boolean,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onManage: () -> Unit,
    navController: NavController,
) {
    item(key = "local_import") {
        LocalImportCard(
            count = data.files.size,
            onClick = onImport,
            modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
        )
    }

    // once Mochy may read the storage, the import card itself lists the phone's songs
    if (!hasStorageAccess) {
        item(key = "local_storage") {
            Text(
                text = stringResource(R.string.lib_local_or),
                color = SearchColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = SearchHorizontalPadding + 2.dp, vertical = 10.dp),
            )
            LocalCard(onClick = onScan, modifier = Modifier.padding(horizontal = SearchHorizontalPadding)) {
                LocalCardTitle(stringResource(R.string.lib_local_let_read))
            }
        }
    }

    // Nothing downloaded and nothing imported: every card below would be an empty shell linking to
    // an empty page, and the import card above already says what to do.
    if (data.songs.isNotEmpty()) {
        libraryShelfCards(data, navController, spaceAbove = 16.dp)
    }

    item(key = "local_manage") {
        Spacer(modifier = Modifier.height(12.dp))
        LibraryManagePill(text = stringResource(R.string.lib_manage_local_pill), onClick = onManage)
    }
}

/** How many files came in from the phone, in a big light number, under the way to add more. */
@Composable
private fun LocalImportCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LocalCard(onClick = onClick, modifier = modifier) {
        LocalCardTitle(stringResource(R.string.lib_local_import_here))
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = count.toString(),
                color = SearchColors.Secondary,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Thin,
                    fontSize = 44.sp,
                    lineHeight = 44.sp,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.lib_local_imported_from_local),
                color = SearchColors.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
    }
}

@Composable
private fun LocalCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SearchColors.Tile)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        content()
    }
}

@Composable
private fun LocalCardTitle(text: String) {
    Text(
        text = text,
        color = SearchColors.Primary,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp,
        fontWeight = FontWeight.Bold,
    )
}
