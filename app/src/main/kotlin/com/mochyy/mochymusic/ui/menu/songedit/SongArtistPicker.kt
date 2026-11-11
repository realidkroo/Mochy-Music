// SongArtistPicker.kt

package com.mochyy.mochymusic.ui.menu.songedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.EditedArtist
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetSearchField
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.component.bleedHorizontally
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val CircleSize = 76.dp
private const val SearchSettleMs = 350L

// "set artist": a search, then a row of round photos; the picked ones come first with a ring, a tap adds or takes one away
@Composable
internal fun SongArtistPicker(state: SongEditState, sidePadding: Dp) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<EditedArtist>() }

    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length < 2) {
            results.clear()
            return@LaunchedEffect
        }
        delay(SearchSettleMs)
        val found = withContext(Dispatchers.IO) {
            YouTube.search(text, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items.orEmpty()
                .filterIsInstance<ArtistItem>()
                .map { EditedArtist(it.id, it.title, it.thumbnail?.resize(240, 240)) }
        }
        results.clear()
        results.addAll(found)
    }

    // a name nobody has on youtube still works: it becomes an artist of its own, made once per typed name
    val typedName = query.trim()
    val typedArtist = remember(typedName) {
        typedName.takeIf { it.isNotEmpty() }?.let { EditedArtist(ArtistEntity.generateArtistId(), it) }
    }
    val unpicked = results.filter { found -> state.artists.none { it.id == found.id } }
    val offerTyped = typedArtist != null &&
        state.artists.none { it.name.equals(typedName, ignoreCase = true) } &&
        unpicked.none { it.name.equals(typedName, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.song_edit_artist_search),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = sidePadding),
            modifier = Modifier.fillMaxWidth().bleedHorizontally(sidePadding),
        ) {
            items(state.artists.toList(), key = { "picked:${it.id}" }) { artist ->
                ArtistCircle(artist = artist, picked = true, label = artist.name) {
                    if (state.artists.size > 1) {
                        state.artists.remove(artist)
                    } else {
                        TopToaster.show(text = context.getString(R.string.song_edit_artist_pick_one), iconRes = R.drawable.person)
                    }
                }
            }
            items(unpicked, key = { "found:${it.id}" }) { artist ->
                ArtistCircle(artist = artist, picked = false, label = artist.name) { state.artists.add(artist) }
            }
            if (offerTyped && typedArtist != null) {
                item(key = "typed:${typedArtist.id}") {
                    ArtistCircle(
                        artist = typedArtist,
                        picked = false,
                        label = stringResource(R.string.song_edit_artist_new, typedArtist.name),
                    ) {
                        state.artists.add(typedArtist)
                        query = ""
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistCircle(artist: EditedArtist, picked: Boolean, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(CircleSize + 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(CircleSize)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(CircleSize)
                    .clip(CircleShape)
                    .background(MenuRowSurface)
                    .then(if (picked) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
            ) {
                if (artist.thumbnail != null) {
                    AsyncImage(
                        model = artist.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.person),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            if (picked) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = if (picked) 1f else 0.7f),
            fontSize = 11.sp,
            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
