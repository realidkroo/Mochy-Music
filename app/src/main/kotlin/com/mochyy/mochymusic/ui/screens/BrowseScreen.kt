// BrowseScreen.kt

package com.mochyy.mochymusic.ui.screens

 import androidx.compose.foundation.ExperimentalFoundationApi
 import androidx.compose.foundation.combinedClickable
 import androidx.compose.foundation.layout.asPaddingValues
 import androidx.compose.foundation.lazy.grid.GridCells
 import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
 import androidx.compose.foundation.lazy.grid.items
 import androidx.compose.material3.ExperimentalMaterial3Api
 import androidx.compose.material3.Icon
 import androidx.compose.material3.Text
 import androidx.compose.material3.TopAppBar
 import androidx.compose.material3.TopAppBarScrollBehavior
 import androidx.compose.runtime.Composable
 import androidx.compose.runtime.collectAsState
 import androidx.compose.runtime.getValue
 import androidx.compose.runtime.remember
 import androidx.compose.runtime.rememberCoroutineScope
 import androidx.compose.ui.Modifier
 import androidx.compose.ui.res.painterResource
 import androidx.compose.ui.unit.dp
 import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
 import androidx.navigation.NavController
 import com.music.innertube.models.AlbumItem
 import com.music.innertube.models.ArtistItem
 import com.music.innertube.models.PlaylistItem
 import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
 import com.mochyy.mochymusic.LocalPlayerConnection
 import com.mochyy.mochymusic.R
 import com.mochyy.mochymusic.constants.GridItemSize
 import com.mochyy.mochymusic.constants.GridItemsSizeKey
 import com.mochyy.mochymusic.constants.GridThumbnailHeight
 import com.mochyy.mochymusic.ui.component.IconButton
 import com.mochyy.mochymusic.ui.component.LocalMenuState
 import com.mochyy.mochymusic.ui.component.YouTubeGridItem
 import com.mochyy.mochymusic.ui.component.shimmer.GridItemPlaceHolder
 import com.mochyy.mochymusic.ui.component.shimmer.ShimmerHost
 import com.mochyy.mochymusic.ui.menu.YouTubeAlbumMenu
 import com.mochyy.mochymusic.ui.menu.YouTubeArtistMenu
 import com.mochyy.mochymusic.ui.menu.YouTubePlaylistMenu
 import com.mochyy.mochymusic.ui.utils.backToMain
 import com.mochyy.mochymusic.utils.rememberEnumPreference
 import com.mochyy.mochymusic.viewmodels.BrowseViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
 @Composable
 fun BrowseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    browseId: String?,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
     val menuState = LocalMenuState.current
     val playerConnection = LocalPlayerConnection.current ?: return
     val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

     val title by viewModel.title.collectAsState()
     val items by viewModel.items.collectAsState()

     val coroutineScope = rememberCoroutineScope()
     val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

     val distinctItems = remember(items) { items?.distinctBy { it.id } }

     LazyVerticalGrid(
         columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
         contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
     ) {
         distinctItems?.let { items ->
             items(
                 items = items,
                 key = { it.id },
                 contentType = { it::class }
             ) { item ->
                 YouTubeGridItem(
                     item = item,
                     fillMaxWidth = true,
                     coroutineScope = coroutineScope,
                     modifier = Modifier
                         .combinedClickable(
                             onClick = {
                                 when (item) {
                                     is AlbumItem -> navController.navigate("album/${item.id}")
                                     is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                     is ArtistItem -> navController.navigate("artist/${item.id}")
                                     else -> {

                                     }
                                 }
                             },
                             onLongClick = {
                                 menuState.show {
                                     when (item) {
                                         is AlbumItem ->
                                             YouTubeAlbumMenu(
                                                 albumItem = item,
                                                 navController = navController,
                                                 onDismiss = menuState::dismiss
                                             )

                                         is PlaylistItem -> {
                                             YouTubePlaylistMenu(
                                                 playlist = item,
                                                 coroutineScope = coroutineScope,
                                                 onDismiss = menuState::dismiss,
                                                 onImportedPlaylist = { playlistId ->
                                                     navController.navigate("local_playlist/$playlistId")
                                                 }
                                             )
                                         }

                                         is ArtistItem -> {
                                             YouTubeArtistMenu(
                                                 artist = item,
                                                 onDismiss = menuState::dismiss
                                             )
                                         }

                                         else -> {

                                         }
                                     }
                                 }
                             }
                         )
                 )
             }

             if (items.isEmpty()) {
                 items(8) {
                     ShimmerHost {
                         GridItemPlaceHolder(fillMaxWidth = true)
                     }
                 }
             }
         }
     }

     TopAppBar(
         title = { Text(title ?: "") },
         navigationIcon = {
             IconButton(
                 onClick = navController::navigateUp,
                 onLongClick = navController::backToMain
             ) {
                 Icon(
                     painterResource(R.drawable.arrow_back_ios),
                     contentDescription = null
                 )
             }
         }
     )
 }
