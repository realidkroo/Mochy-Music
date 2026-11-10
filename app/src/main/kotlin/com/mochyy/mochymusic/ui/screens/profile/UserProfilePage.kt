// UserProfilePage.kt
//
// The profile page from the mock: the photo, the name in big type with the card number on its
// baseline, "Edit profile", then listening totals, what you've been playing, your playlists and the
// artists you play most. It opens out of the ID card on the Mochy page (see ProfileMorph.kt), so
// the photo, name and number stay hidden until the flying copies have landed on them.

package com.mochyy.mochymusic.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.HomeCardCornerRadius
import com.mochyy.mochymusic.ui.component.HomeCardHighlight
import com.mochyy.mochymusic.ui.component.HomeCardSpacing
import com.mochyy.mochymusic.ui.component.HomeContentInset
import com.mochyy.mochymusic.ui.component.HomeCoverCard
import com.mochyy.mochymusic.ui.component.HomeSectionTitle
import com.mochyy.mochymusic.ui.component.LikedSongsThumbnail
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.OdometerNumber
import com.mochyy.mochymusic.ui.component.PlaylistThumbnail
import com.mochyy.mochymusic.ui.component.homeCardSize
import com.mochyy.mochymusic.ui.menu.ArtistMenu
import com.mochyy.mochymusic.ui.menu.SongMenu
import com.mochyy.mochymusic.ui.screens.settings.figureSp
import com.mochyy.mochymusic.ui.screens.setup.onboarding.idCardName
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.stableSystemBars
import com.mochyy.mochymusic.viewmodels.ListenTotal
import com.mochyy.mochymusic.viewmodels.UserProfileViewModel

internal val ProfilePhotoSize = 98.dp
internal val ProfilePhotoGrey = Color(0xFF4F4F4F)

// both match the ID card's name and number (weight, tracking), so the copies flying between them
// are the same text at both ends, only bigger or smaller
internal val ProfileNameStyle =
    TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 60.sp,
        lineHeight = 1.1.em,
        letterSpacing = (-0.05).em,
        color = Color.White,
    )

internal val ProfileTagStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = (-0.05).em,
        color = Color(0xFFB5B5B5),
    )

internal fun profileTag(cardNumber: String) = "PROFILE USR#$cardNumber"

private val NameTagGap = 10.dp
private val PillGrey = Color(0xFF2E2E2E)
private val DividerGrey = Color(0xFF262626)
private val ListensCardGrey = Color(0xFF111111)
private val FigureGrey = Color(0xFF707070)

private val EmptyStyle =
    TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = Color.White.copy(alpha = 0.45f),
    )

@Composable
internal fun UserProfilePage(
    state: ProfileMorphState,
    username: String,
    photo: Any?,
    cardNumber: String,
    nameStyle: TextStyle,
    navController: NavController,
    viewModel: UserProfileViewModel,
) {
    val popup = LocalZoomOutOverlayState.current
    val statusBarTop = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val name = idCardName(username)
    val openEditor = { popup.showEditProfileSheet(currentName = username, currentPhoto = photo) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(statusBarTop + 64.dp))
        Box(
            modifier =
                Modifier
                    .padding(start = HomeContentInset)
                    .size(ProfilePhotoSize)
                    .onPlaced { state.target.photo = it }
                    .landedOnly(state)
                    .clip(CircleShape)
                    .background(ProfilePhotoGrey)
                    .clickable(onClick = openEditor),
        ) {
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = stringResource(R.string.profile_picture_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        NameLine(
            modifier = Modifier.padding(horizontal = HomeContentInset),
            name = {
                Text(
                    text = name,
                    style = nameStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { state.target.nameLayout = it },
                    modifier =
                        Modifier
                            .onPlaced { state.target.name = it }
                            .landedOnly(state),
                )
            },
            tag = {
                Text(
                    text = stringResource(R.string.profile_tag, cardNumber),
                    style = ProfileTagStyle,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = { state.target.numberLayout = it },
                    modifier =
                        Modifier
                            .onPlaced { state.target.number = it }
                            .landedOnly(state),
                )
            },
        )
        Spacer(Modifier.height(18.dp))
        EditProfilePill(
            onClick = openEditor,
            modifier =
                Modifier
                    .padding(start = HomeContentInset)
                    .reveal(state, row = 0),
        )
        Spacer(Modifier.height(40.dp))
        Box(
            modifier =
                Modifier
                    .reveal(state, row = 1)
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerGrey),
        )
        Spacer(Modifier.height(24.dp))
        ListensRow(state, viewModel, onOpenStats = { navController.navigate("stats") })
        Spacer(Modifier.height(30.dp))
        RecentSection(state, viewModel, navController)
        Spacer(Modifier.height(24.dp))
        PlaylistsSection(state, viewModel, navController)
        Spacer(Modifier.height(24.dp))
        ArtistsSection(state, viewModel, navController)
        // room to scroll the last row clear of the mini player and the nav bar
        Spacer(Modifier.height(180.dp))
    }
}

/** Hidden until the flying copy of this part has landed on it. */
private fun Modifier.landedOnly(state: ProfileMorphState): Modifier =
    graphicsLayer {
        alpha = if (state.isOpen) 1f else 0f
    }

/** The name with the card number on its baseline beside it, or tucked under it when there isn't room. */
@Composable
private fun NameLine(
    name: @Composable () -> Unit,
    tag: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(name, tag), modifier = modifier) { (nameMeasurables, tagMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val namePlaceable = nameMeasurables.first().measure(loose)
        val tagPlaceable = tagMeasurables.first().measure(loose)
        val gap = NameTagGap.roundToPx()
        val nameBaseline = namePlaceable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: namePlaceable.height
        val tagBaseline = tagPlaceable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: tagPlaceable.height

        if (namePlaceable.width + gap + tagPlaceable.width <= constraints.maxWidth) {
            val nameY = (tagBaseline - nameBaseline).coerceAtLeast(0)
            val tagY = nameY + nameBaseline - tagBaseline
            val height = maxOf(nameY + namePlaceable.height, tagY + tagPlaceable.height)
            layout(constraints.maxWidth, height) {
                namePlaceable.place(0, nameY)
                tagPlaceable.place(namePlaceable.width + gap, tagY)
            }
        } else {
            layout(constraints.maxWidth, namePlaceable.height + tagPlaceable.height) {
                namePlaceable.place(0, 0)
                tagPlaceable.place(0, namePlaceable.height)
            }
        }
    }
}

@Composable
private fun EditProfilePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.profile_edit_button),
        style =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                letterSpacing = (-0.03).em,
                color = Color.White,
            ),
        modifier =
            modifier
                .clip(CircleShape)
                .background(PillGrey)
                .clickable(onClick = onClick)
                .padding(horizontal = 15.dp, vertical = 8.dp),
    )
}

// ---- Total listens ----------------------------------------------------------------------------

@Composable
private fun ListensRow(
    state: ProfileMorphState,
    viewModel: UserProfileViewModel,
    onOpenStats: () -> Unit,
) {
    val totals by viewModel.listens.collectAsState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // the next card peeks in from the right, so it's clear the row scrolls
        val cardWidth = (maxWidth - HomeContentInset - 66.dp).coerceIn(240.dp, 380.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = HomeContentInset),
        ) {
            (totals ?: ListenTotal.Placeholder).forEachIndexed { index, total ->
                ListensCard(
                    total = total,
                    width = cardWidth,
                    onClick = onOpenStats,
                    modifier = Modifier.reveal(state, row = 2, order = index),
                )
            }
        }
    }
}

@Composable
private fun ListensCard(
    total: ListenTotal,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (timeValue, timeUnit) = total.timeFigure()
    Column(
        modifier =
            modifier
                .width(width)
                .clip(RoundedCornerShape(18.dp))
                .background(ListensCardGrey)
                .clickable(onClick = onClick)
                .padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
    ) {
        Row {
            Text(
                text = stringResource(R.string.profile_total_listens),
                style =
                    TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        letterSpacing = (-0.04).em,
                        color = Color.White,
                    ),
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = total.label,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, color = Color(0xFF8A8A8A)),
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row {
            ListensFigure(total.songs.toString(), pluralStringResource(R.plurals.profile_unit_songs, total.songs), Modifier.weight(1f))
            ListensFigure(timeValue, timeUnit, Modifier.weight(1f))
        }
    }
}

/** A big grey number with its unit. Long numbers step down a size so the unit keeps its room. */
@Composable
private fun ListensFigure(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val k =
        when {
            value.length >= 5 -> 0.7f
            value.length == 4 -> 0.8f
            value.length == 3 -> 0.9f
            else -> 1f
        }
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        OdometerNumber(
            value = value,
            fontSize = figureSp(46f * k),
            fontWeight = FontWeight.Bold,
            fontFamily = InterFontFamily,
            color = FigureGrey,
            letterSpacing = (-2).sp,
        )
        Text(
            text = unit,
            style =
                TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = figureSp(19f * k),
                    letterSpacing = (-0.6).sp,
                    color = FigureGrey,
                ),
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, bottom = (7 * k).dp),
        )
    }
}

/** "26 Hours", or "42 Mins" while it's still under an hour. */
@Composable
private fun ListenTotal.timeFigure(): Pair<String, String> {
    val minutes = playTimeMs / 60_000L
    if (minutes < 60) {
        val count = minutes.toInt()
        return minutes.toString() to pluralStringResource(R.plurals.profile_unit_minutes, count)
    }
    val hours = minutes / 60
    val count = hours.toInt()
    return hours.toString() to pluralStringResource(R.plurals.profile_unit_hours, count)
}

// ---- Rows of covers ---------------------------------------------------------------------------

/** A titled row of covers, the same cards and spacing as Home. */
@Composable
private fun <T> ProfileSection(
    title: String,
    state: ProfileMorphState,
    row: Int,
    items: List<T>?,
    key: (T) -> Any,
    emptyText: String,
    card: @Composable (item: T, order: Int) -> Unit,
) {
    HomeSectionTitle(title = title, modifier = Modifier.reveal(state, row))
    when {
        // not loaded yet: hold the row's height so nothing under it jumps when it arrives
        items == null -> {
            Spacer(Modifier.height(homeCardSize() + 44.dp))
        }

        items.isEmpty() -> {
            Text(
                text = emptyText,
                style = EmptyStyle,
                modifier =
                    Modifier
                        .reveal(state, row, order = 1)
                        .padding(horizontal = HomeContentInset, vertical = 6.dp),
            )
        }

        else -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = HomeContentInset),
                horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
            ) {
                itemsIndexed(items, key = { _, item -> key(item) }) { index, item -> card(item, index) }
            }
        }
    }
}

@Composable
private fun RecentSection(
    state: ProfileMorphState,
    viewModel: UserProfileViewModel,
    navController: NavController,
) {
    val songs by viewModel.recentSongs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playing by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    ProfileSection(
        title = stringResource(R.string.profile_listening_to),
        state = state,
        row = 3,
        items = songs,
        key = { it.id },
        emptyText = stringResource(R.string.profile_recent_empty),
    ) { song, order ->
        HomeCoverCard(
            title = song.title,
            subtitle = song.artists.joinToString(", ") { it.name },
            thumbnailUrl = song.thumbnailUrl,
            isActive = song.id == playing?.id,
            isPlaying = isPlaying,
            modifier =
                Modifier
                    .reveal(state, row = 3, order = order)
                    .combinedClickable(
                        interactionSource = null,
                        indication = HomeCardHighlight,
                        onClick = {
                            if (song.id == playing?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
                            }
                        },
                    ),
        )
    }
}

@Composable
private fun PlaylistsSection(
    state: ProfileMorphState,
    viewModel: UserProfileViewModel,
    navController: NavController,
) {
    val playlists by viewModel.playlists.collectAsState()
    val likedCount by viewModel.likedCount.collectAsState()
    // Liked always comes first, with its own cover; null stands for it in the row
    val entries = remember(playlists) { playlists?.let { listOf<Playlist?>(null) + it } }
    val shape = RoundedCornerShape(HomeCardCornerRadius)

    ProfileSection(
        title = stringResource(R.string.profile_your_playlists),
        state = state,
        row = 4,
        items = entries,
        key = { it?.id ?: "liked" },
        emptyText = "",
    ) { playlist, order ->
        val songCount = playlist?.songCount ?: likedCount
        HomeCoverCard(
            title = playlist?.title ?: stringResource(R.string.liked_songs),
            subtitle = pluralStringResource(R.plurals.n_song, songCount, songCount),
            thumbnailUrl = playlist?.thumbnails?.firstOrNull(),
            sharedElementKey = "playlist-${playlist?.id ?: "liked"}",
            cover = { size ->
                if (playlist == null) {
                    LikedSongsThumbnail(size = size, shape = shape)
                } else {
                    PlaylistThumbnail(
                        thumbnails = playlist.thumbnails,
                        size = size,
                        shape = shape,
                        placeHolder = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(size / 3),
                            )
                        },
                    )
                }
            },
            modifier =
                Modifier
                    .reveal(state, row = 4, order = order)
                    .clickable(
                        interactionSource = null,
                        indication = HomeCardHighlight,
                        onClick = {
                            navController.navigate(if (playlist == null) "auto_playlist/liked" else "local_playlist/${playlist.id}")
                        },
                    ),
        )
    }
}

@Composable
private fun ArtistsSection(
    state: ProfileMorphState,
    viewModel: UserProfileViewModel,
    navController: NavController,
) {
    val artists by viewModel.topArtists.collectAsState()
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    ProfileSection(
        title = stringResource(R.string.profile_top_artists),
        state = state,
        row = 5,
        items = artists,
        key = { it.id },
        emptyText = stringResource(R.string.profile_top_artists_empty),
    ) { artist, order ->
        HomeCoverCard(
            title = artist.title,
            subtitle = null,
            thumbnailUrl = artist.thumbnailUrl,
            circular = true,
            modifier =
                Modifier
                    .reveal(state, row = 5, order = order)
                    .combinedClickable(
                        interactionSource = null,
                        indication = HomeCardHighlight,
                        onClick = { navController.navigate("artist/${artist.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                ArtistMenu(originalArtist = artist, coroutineScope = scope, onDismiss = menuState::dismiss)
                            }
                        },
                    ),
        )
    }
}
