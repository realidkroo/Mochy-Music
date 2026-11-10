// YouTubeArtistMenu.kt

package com.mochyy.mochymusic.ui.menu

import com.mochyy.mochymusic.ui.component.SheetHeaderGap
import com.mochyy.mochymusic.ui.component.SheetHeaderButton
import com.mochyy.mochymusic.ui.component.SheetHeader
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.innertube.models.ArtistItem
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.SpeedDialItem
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.Material3MenuGroup
import com.mochyy.mochymusic.ui.component.Material3MenuItemData
import com.mochyy.mochymusic.ui.component.NewAction
import com.mochyy.mochymusic.ui.component.NewActionGrid
import com.mochyy.mochymusic.ui.component.YouTubeListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun YouTubeArtistMenu(
    artist: ArtistItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val libraryArtist by database.artist(artist.id).collectAsState(initial = null)
    val isPinned by database.speedDialDao.isPinned(artist.id).collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()

    SheetHeader(
        thumbnailUrl = artist.thumbnail,
        title = artist.title,
        subtitle = stringResource(R.string.recent_search_artist),
    )

    Spacer(Modifier.height(SheetHeaderGap))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = buildList {
                    if (true) {
                        // Shuffle and radio first: they're the two cards, and what an artist's menu is for.
                        artist.shuffleEndpoint?.let { shuffleEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.shuffle),
                                            contentDescription = null,
                                        )
                                    },
                                    text = stringResource(R.string.shuffle),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                        artist.radioEndpoint?.let { watchEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.radio),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = stringResource(R.string.start_an_radio),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(watchEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }

                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(if (isPinned) R.drawable.remove else R.drawable.add),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(if (isPinned) R.string.pc_unpin else R.string.pc_pin),
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (isPinned) {
                                        database.speedDialDao.delete(artist.id)
                                    } else {
                                        database.pinToSpeedDial(SpeedDialItem.fromYTItem(artist))
                                    }
                                }
                                onDismiss()
                            }
                        )
                    )

                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.share),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.share),
                            onClick = {
                                val intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, artist.shareLink)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                                onDismiss()
                            }
                        )
                    )
                },
                modifier = Modifier.padding(bottom = 12.dp),
                columns = if (false) 1 else 3
            )
        }

        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = {
                            Text(text = if (libraryArtist?.artist?.bookmarkedAt != null) stringResource(R.string.subscribed) else stringResource(R.string.subscribe))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(
                                    if (libraryArtist?.artist?.bookmarkedAt != null) {
                                        R.drawable.subscribed
                                    } else {
                                        R.drawable.subscribe
                                    }
                                ),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            database.query {
                                val libraryArtist = libraryArtist
                                if (libraryArtist != null) {
                                    update(libraryArtist.artist.toggleLike())
                                } else {
                                    insert(
                                        ArtistEntity(
                                            id = artist.id,
                                            name = artist.title,
                                            channelId = artist.channelId,
                                            thumbnailUrl = artist.thumbnail,
                                        ).toggleLike()
                                    )
                                }
                            }
                        }
                    )
                )
            )
        }
    }
}
