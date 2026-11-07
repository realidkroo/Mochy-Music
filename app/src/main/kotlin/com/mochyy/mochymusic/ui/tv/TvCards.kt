// TvCards.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.LocalItem
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.db.entities.Song

internal val TvCardSize = 112.dp

// a square cover with its name under it, the home rows' card
@Composable
internal fun TvItemCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    round: Boolean = false,
    size: Dp = TvCardSize,
    coverModifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = if (round) CircleShape else RoundedCornerShape(16.dp)
    Column(Modifier.width(size)) {
        TvCover(
            url = thumbnailUrl,
            size = size,
            shape = shape,
            modifier = coverModifier
                .size(size)
                .tvClickable(shape = shape, onFocused = onFocused, onLongClick = onLongClick, onClick = onClick),
        )
        Spacer(Modifier.height(8.dp))
        Text(title, style = TvText.Item, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = TvText.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// the line under a youtube card: who made it, or how many songs
internal fun YTItem.tvSubtitle(): String? = when (this) {
    is SongItem -> artists.joinToString { it.name }
    is AlbumItem -> artists?.joinToString { it.name } ?: year?.toString()
    is PlaylistItem -> author?.name ?: songCountText
    is ArtistItem -> null
}

// a titled row of youtube cards; the row keeps its focused card near the start as the remote moves along
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvShelf(
    title: String,
    items: List<YTItem>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = TvSpace.Start, end = TvSpace.End, top = 10.dp, bottom = 10.dp),
    // how far in the title starts, the page's margin unless the shelf sits inside a padded column
    titleInset: Dp = TvSpace.Start,
    onFocused: ((YTItem) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val actions = LocalTvActions.current
    Column(modifier) {
        if (title.isNotBlank()) TvShelfTitle(title, inset = titleInset)
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvRowPivot) {
            LazyRow(
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.id }, contentType = { it::class.simpleName }) { item ->
                    TvItemCard(
                        title = item.title,
                        subtitle = item.tvSubtitle(),
                        thumbnailUrl = item.thumbnail,
                        round = item is ArtistItem,
                        onFocused = onFocused?.let { { it(item) } },
                        onLongClick = (item as? SongItem)?.let { song -> { actions.openMenu(song.tvRef()) } },
                        onClick = { actions.open(item) },
                    )
                }
            }
        }
    }
}

// the line under a library card
internal fun LocalItem.tvSubtitle(): String? = when (this) {
    is Song -> artists.joinToString { it.name }
    is Album -> artists.joinToString { it.name }.ifBlank { album.year?.toString() }
    is Artist -> null
    is Playlist -> null
}

@Composable
internal fun TvShelfTitle(title: String, modifier: Modifier = Modifier, inset: Dp = TvSpace.Start) {
    Row(modifier.padding(start = inset, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TvText.Section)
        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = Color(0xFFBDBDBD),
            modifier = Modifier.padding(start = 4.dp).size(12.dp),
        )
    }
}

// one song in a list: cover, title over artist, and the menu dots at the far end like the concept
@Composable
internal fun TvSongRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onMenu: () -> Unit,
    onClick: () -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .tvClickable(shape = RoundedCornerShape(12.dp), look = TvFocusLook.Fill, onLongClick = onMenu, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvCover(url = thumbnailUrl, size = 30.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = TvText.Item.copy(color = if (isCurrent) Color(0xFF9AD0FF) else Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, style = TvText.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        TvIconButton(R.drawable.more_horiz, onClick = onMenu, size = 28.dp)
    }
}

// a round icon button for the remote: a quiet circle until it's focused
@Composable
internal fun TvIconButton(
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = Color.White,
    filled: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .size(size)
            .tvClickable(shape = CircleShape, look = TvFocusLook.Invert, interactionSource = interaction, onClick = onClick)
            .clip(CircleShape)
            .background(
                when {
                    focused -> Color.White
                    filled -> Color.White.copy(alpha = 0.14f)
                    else -> Color.Transparent
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (focused) Color.Black else tint, modifier = Modifier.size(size * 0.5f))
    }
}

// a grey pill with words, like the concept's "View →"
@Composable
internal fun TvPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    bright: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .tvClickable(shape = CircleShape, look = TvFocusLook.Invert, interactionSource = interaction, onClick = onClick)
            .clip(CircleShape)
            .background(
                when {
                    focused -> Color.White
                    bright -> Color.White.copy(alpha = 0.3f)
                    else -> Color.White.copy(alpha = 0.14f)
                },
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (focused) Color.Black else Color.White
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = TvText.Item.copy(color = color))
    }
}
