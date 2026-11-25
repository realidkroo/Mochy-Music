// rich media and interactive blocks for custom module pages: artist banner, bio, playlists, songs, albums, artists, now playing, flowing lyrics, search bar, speed dial, stats, tabs and progressive blur

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.canvas.AppleMusicArtistBackgroundProvider
import com.mochyy.mochymusic.canvas.appleArtworkUrl
import com.mochyy.mochymusic.constants.AlbumSortType
import com.mochyy.mochymusic.constants.ArtistSortType
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.constants.PlaylistSortType
import com.mochyy.mochymusic.constants.SongSortType
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.extensions.metadata
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.modules.skin.MediaListLayout
import com.mochyy.mochymusic.modules.skin.MediaSource
import com.mochyy.mochymusic.modules.skin.PageBlock
import com.mochyy.mochymusic.modules.skin.PlaylistSource
import com.mochyy.mochymusic.modules.skin.SkinBlurDirection
import com.mochyy.mochymusic.modules.skin.SongSource
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.component.MenuState
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import com.mochyy.mochymusic.ui.component.onChrome
import com.mochyy.mochymusic.ui.menu.AlbumMenu
import com.mochyy.mochymusic.ui.menu.ArtistMenu
import com.mochyy.mochymusic.ui.menu.PlaylistMenu
import com.mochyy.mochymusic.ui.menu.SongMenu
import com.mochyy.mochymusic.ui.menu.YouTubeSongMenu
import com.mochyy.mochymusic.ui.player.styles.FlowLyricsView
import com.mochyy.mochymusic.ui.player.styles.rememberLivePosition
import com.mochyy.mochymusic.ui.player.styles.rememberLyricClock
import com.mochyy.mochymusic.ui.screens.artist.ArtistActions
import com.mochyy.mochymusic.ui.screens.artist.ArtistAvatar
import com.mochyy.mochymusic.ui.screens.artist.ArtistBanner
import com.mochyy.mochymusic.ui.screens.artist.ArtistName
import com.mochyy.mochymusic.ui.screens.artist.artistStats
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.search.searchRoute
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.ArtistHeader
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.utils.rememberPreference
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** editorial artist banner with Apple motion over YouTube photo, progressive edge blur, avatar, wordmark logo, stats, and Play/Like/Subscribe */
@Composable
internal fun PageArtistBannerBlock(block: PageBlock.ArtistBanner, page: PageContext) {
    val connection = LocalPlayerConnection.current
    val obscured = LocalAppContentObscured.current
    val disableBlur by rememberPreference(DisableBlurKey, false)
    val isPlaying by (connection?.isPlaying ?: FalseFlow).collectAsState()
    val playingMeta by (connection?.mediaMetadata ?: NullMetaFlow).collectAsState()

    val subjectArtist = page.subject.artist
    val explicitId = block.artistId?.takeIf { it.isNotBlank() }

    // when a block points at a specific artistId (on any custom page), fetch its header + editorial artwork once off the main thread
    val fetched by produceState<ArtistBannerData?>(initialValue = null, explicitId) {
        if (explicitId == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val yt = YouTube.artist(explicitId).getOrNull() ?: return@withContext null
            val name = yt.artist.title
            val editorial = runCatching {
                AppleMusicArtistBackgroundProvider.cached(name)
                    ?: AppleMusicArtistBackgroundProvider.getArtistEditorial(name)
            }.getOrNull()
            val header = ArtistHeader(
                id = explicitId,
                name = name,
                avatarUrl = yt.artist.thumbnail?.resize(544, 544),
                bannerUrl = editorial?.previewFrameUrl?.let { appleArtworkUrl(it, 1200, 1200) }
                    ?: yt.artist.thumbnail?.resize(1200, 1200)
                    ?: editorial?.bannerUrl?.let { appleArtworkUrl(it, 2400, 600) },
                motionUrl = editorial?.motionSquareUrl ?: editorial?.motionVideoUrl,
                logoUrl = editorial?.logoUrl?.let { url ->
                    val aspect = editorial.logoAspect ?: 3f
                    appleArtworkUrl(url, 900, (900 / aspect).toInt().coerceAtLeast(1), format = "webp")
                },
                logoAspect = editorial?.logoAspect,
            )
            val top = yt.sections.firstOrNull { s -> s.items.any { it is SongItem } }
                ?.items?.filterIsInstance<SongItem>().orEmpty()
                .map(SongItem::toMediaMetadata)
            ArtistBannerData(
                id = explicitId,
                name = name,
                header = header,
                subscribers = yt.subscriberCountText,
                monthly = yt.monthlyListenerCount,
                songs = top,
            )
        }
    }

    val header = fetched?.header ?: subjectArtist?.header
    val name = fetched?.name ?: subjectArtist?.name ?: page.subject.title
    val photoUrl = header?.avatarUrl ?: page.subject.cover
    val stillUrl = header?.bannerUrl
    val motionUrl = if (block.motion) header?.motionUrl else null
    val songs = fetched?.songs ?: page.subject.songs
    val artistId = fetched?.id ?: subjectArtist?.id
    val subscribers = fetched?.subscribers ?: subjectArtist?.subscribers
    val monthly = fetched?.monthly ?: subjectArtist?.monthlyListeners

    if (name.isBlank() && photoUrl == null && stillUrl == null) return

    val bgState = remember { mutableStateOf(Color.Black) }
    val shape = RoundedCornerShape(block.corner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(block.height)
            .clip(shape)
            .background(LabColors.Card)
            .then(
                if (explicitId != null) {
                    Modifier.clickable { page.navController.navigate("artist/${Uri.encode(explicitId)}") }
                } else {
                    Modifier
                },
            ),
    ) {
        ArtistBanner(
            photoUrl = photoUrl,
            stillUrl = stillUrl,
            motionUrl = motionUrl,
            playMotion = { block.motion && !obscured.value },
            edgeBlur = block.edgeBlur && !disableBlur,
            height = block.height,
            scrollProgress = { 0f },
            background = bgState,
        )
        if (block.scrim > 0f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = block.scrim)))
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            if (block.showAvatar && photoUrl != null) {
                ArtistAvatar(url = photoUrl, modifier = Modifier.size(64.dp))
            }
            if (name.isNotBlank()) {
                ArtistName(
                    name = name,
                    logoUrl = if (block.showLogo) header?.logoUrl else null,
                    logoAspect = header?.logoAspect,
                )
            }
            if (block.showStats) {
                val stats = artistStats(subscribers, monthly)
                if (stats.isNotBlank()) {
                    Text(
                        stats,
                        fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (block.showActions && (songs.isNotEmpty() || subjectArtist != null)) {
                val artistPlaying = isPlaying && artistId != null && playingMeta?.artists?.any { it.id == artistId } == true
                ArtistActions(
                    isPlaying = artistPlaying,
                    liked = subjectArtist?.liked == true,
                    subscribed = subjectArtist?.subscribed,
                    showSubscribe = subjectArtist?.showSubscribe == true,
                    onPlay = {
                        if (songs.isNotEmpty()) {
                            connection?.playQueue(ListQueue(title = name.ifBlank { null }, items = songs.map { it.toMediaItem() }))
                        }
                    },
                    onLike = { subjectArtist?.onToggleLike?.invoke() },
                    onSubscribe = { subjectArtist?.onToggleSubscribe?.invoke() },
                )
            }
        }
    }
}

private data class ArtistBannerData(
    val id: String,
    val name: String,
    val header: ArtistHeader,
    val subscribers: String?,
    val monthly: String?,
    val songs: List<MediaMetadata>,
)

/** expandable artist bio / description card */
@Composable
internal fun PageBioBlock(block: PageBlock.Bio, page: PageContext) {
    val bio = page.subject.artist?.bio?.trim()?.takeIf { it.isNotEmpty() } ?: return
    var expanded by rememberSaveable(bio) { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LabColors.Card)
            .animateContentSize()
            .then(if (block.expandable) Modifier.clickable { expanded = !expanded } else Modifier)
            .padding(16.dp),
    ) {
        Text(
            block.title?.takeIf { it.isNotBlank() }?.let { page.subject.fill(it, page.entry.values) }
                ?: stringResource(R.string.module_page_about),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White,
        )
        Text(
            bio,
            fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = 0.78f),
            maxLines = if (expanded) Int.MAX_VALUE else block.maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (block.expandable && bio.length > 140) {
            Text(
                stringResource(if (expanded) R.string.module_page_bio_less else R.string.module_page_bio_more),
                fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** upgraded live song list: multiple sources, optional filter search, layouts, and native SongMenu sheet */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PageSongsBlock(block: PageBlock.Songs, page: PageContext, glass: GlassState?) {
    val connection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val playing by (connection?.mediaMetadata ?: NullMetaFlow).collectAsState()

    val sourceSongs: List<MediaMetadata> = when (block.source) {
        SongSource.Subject -> page.subject.songs
        SongSource.Library -> {
            val rows by remember(database) { database.songs(SongSortType.CREATE_DATE, true) }.collectAsState(initial = emptyList())
            remember(rows, block.limit) { rows.take(block.limit).map { it.toMediaMetadata() } }
        }
        SongSource.Liked -> {
            val rows by remember(database) { database.likedSongs(SongSortType.CREATE_DATE, true) }.collectAsState(initial = emptyList())
            remember(rows, block.limit) { rows.take(block.limit).map { it.toMediaMetadata() } }
        }
        SongSource.Downloaded -> {
            val rows by remember(database) { database.downloadedSongs(SongSortType.CREATE_DATE, true) }.collectAsState(initial = emptyList())
            remember(rows, block.limit) { rows.take(block.limit).map { it.toMediaMetadata() } }
        }
        SongSource.Recent -> {
            val rows by remember(database, block.limit) { database.recentlyPlayedSongs(block.limit) }.collectAsState(initial = emptyList())
            remember(rows) { rows.map { it.toMediaMetadata() } }
        }
        SongSource.Top -> {
            val rows by remember(database, block.limit) { database.mostPlayedSongs(0L, block.limit) }.collectAsState(initial = emptyList())
            remember(rows) { rows.map { it.toMediaMetadata() } }
        }
        SongSource.Queue -> {
            val windows by (connection?.queueWindows ?: EmptyQueueFlow).collectAsState()
            remember(windows, block.limit) { windows.mapNotNull { it.mediaItem.metadata }.take(block.limit) }
        }
    }.take(block.limit)

    var filter by remember { mutableStateOf("") }
    val filtered = remember(sourceSongs, filter) {
        val q = filter.trim()
        if (q.isEmpty()) sourceSongs else sourceSongs.filter { s ->
            s.title.contains(q, ignoreCase = true) || s.artists.any { it.name.contains(q, ignoreCase = true) }
        }
    }

    var shown by rememberSaveable(block.source) { mutableIntStateOf(FirstSongRows) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (block.searchable) {
            SettingsBlockSurface(
                corner = 18.dp,
                glass = glass,
                glassRadius = block.glass,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Icon(painterResource(R.drawable.search_filled), null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (filter.isEmpty()) {
                            Text(stringResource(R.string.module_page_search_songs), fontFamily = InterFontFamily, fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                        BasicTextField(
                            value = filter,
                            onValueChange = { filter = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 14.sp, color = Color.White),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (filter.isNotEmpty()) {
                        Icon(
                            painterResource(R.drawable.close), null, tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp).clickable { filter = "" },
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Text(
                block.empty?.takeIf { it.isNotBlank() } ?: stringResource(R.string.module_page_empty_songs),
                fontFamily = InterFontFamily, fontSize = 13.sp, color = LabColors.Secondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
            return
        }

        val playAt: (Int) -> Unit = { idx ->
            if (block.source == SongSource.Queue && connection != null) {
                connection.player.seekToDefaultPosition(idx)
                connection.player.play()
            } else {
                connection?.playQueue(ListQueue(title = page.subject.title.ifBlank { null }, items = filtered.map { it.toMediaItem() }, startIndex = idx))
            }
        }
        val openMenu: (MediaMetadata) -> Unit = { song ->
            openSongMenuSheet(song, database, page.navController, menuState, scope)
        }

        when (block.layout) {
            MediaListLayout.Row -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    filtered.take(shown).forEachIndexed { idx, song ->
                        SongTile(
                            song = song,
                            active = song.id == playing?.id,
                            corner = block.corner,
                            modifier = Modifier.width(136.dp),
                            onClick = { playAt(idx) },
                            onLongClick = if (block.showMenu) { { openMenu(song) } } else null,
                        )
                    }
                }
            }
            MediaListLayout.Grid -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filtered.take(shown).chunked(2).forEachIndexed { rowIdx, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            pair.forEachIndexed { colIdx, song ->
                                val idx = rowIdx * 2 + colIdx
                                SongTile(
                                    song = song,
                                    active = song.id == playing?.id,
                                    corner = block.corner,
                                    modifier = Modifier.weight(1f),
                                    onClick = { playAt(idx) },
                                    onLongClick = if (block.showMenu) { { openMenu(song) } } else null,
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            MediaListLayout.List, MediaListLayout.Compact -> {
                val compact = block.layout == MediaListLayout.Compact
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                    filtered.take(shown).forEachIndexed { idx, song ->
                        SongListRow(
                            index = idx,
                            song = song,
                            block = block,
                            compact = compact,
                            active = song.id == playing?.id,
                            glass = glass,
                            onClick = { playAt(idx) },
                            onMenu = if (block.showMenu) { { openMenu(song) } } else null,
                        )
                    }
                }
            }
        }

        if (filtered.size > shown) {
            PillButton(text = stringResource(R.string.module_page_more_songs), filled = false, onClick = { shown += MoreSongRows })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongListRow(
    index: Int,
    song: MediaMetadata,
    block: PageBlock.Songs,
    compact: Boolean,
    active: Boolean,
    glass: GlassState?,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
) {
    val accent = MaterialTheme.colorScheme.primary
    SettingsBlockSurface(
        corner = if (compact) 14.dp else 16.dp,
        glass = glass,
        glassRadius = block.glass,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMenu),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 10.dp),
        ) {
            if (block.numbers) {
                Text(
                    (index + 1).toString(), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (active) accent else LabColors.Secondary, modifier = Modifier.width(26.dp),
                )
            }
            if (block.covers) {
                AsyncImage(
                    model = song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(if (compact) 36.dp else 44.dp).clip(RoundedCornerShape(block.corner)).background(LabColors.Row),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    song.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 14.sp else 15.sp,
                    color = if (active) accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artists.joinToString(", ") { it.name }, fontFamily = InterFontFamily, fontSize = 12.sp,
                    color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (song.duration > 0) {
                Spacer(Modifier.width(6.dp))
                Text(makeTimeString(song.duration * 1000L), fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary)
            }
            if (onMenu != null) {
                Spacer(Modifier.width(4.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onMenu),
                ) {
                    Icon(painterResource(R.drawable.more_vert), null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongTile(
    song: MediaMetadata,
    active: Boolean,
    corner: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(LabColors.Card)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
    ) {
        AsyncImage(
            model = song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(corner)).background(LabColors.Row),
        )
        Text(
            song.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = if (active) accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artists.joinToString(", ") { it.name }, fontFamily = InterFontFamily, fontSize = 11.sp,
            color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** opens Mochy's real SongMenu (if tracked in DB) or YouTubeSongMenu fallback */
internal fun openSongMenuSheet(
    song: MediaMetadata,
    database: MusicDatabase,
    navController: NavController,
    menuState: MenuState,
    scope: CoroutineScope,
) {
    scope.launch {
        val dbSong = withContext(Dispatchers.IO) { database.song(song.id).firstOrNull() }
        menuState.show {
            if (dbSong != null) {
                SongMenu(originalSong = dbSong, navController = navController, onDismiss = { menuState.dismiss() })
            } else {
                val ytItem = SongItem(
                    id = song.id,
                    title = song.title,
                    artists = song.artists.map { com.music.innertube.models.Artist(name = it.name, id = it.id) },
                    album = song.album?.let { com.music.innertube.models.Album(name = it.title, id = it.id) },
                    duration = song.duration.takeIf { it > 0 },
                    thumbnail = song.thumbnailUrl.orEmpty(),
                    explicit = song.explicit,
                )
                YouTubeSongMenu(song = ytItem, navController = navController, onDismiss = { menuState.dismiss() })
            }
        }
    }
}

/** live playlist list/grid/row with tap-to-open and real PlaylistMenu sheet */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagePlaylistsBlock(block: PageBlock.Playlists, page: PageContext) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()

    val localPlaylists by remember(database) { database.playlists(PlaylistSortType.CREATE_DATE, true) }.collectAsState(initial = emptyList())
    val likedTitle = stringResource(R.string.module_page_auto_liked)
    val downloadedTitle = stringResource(R.string.module_page_auto_downloaded)
    val topTitle = stringResource(R.string.module_page_auto_top)
    val cachedTitle = stringResource(R.string.module_page_auto_cached)

    val autoItems = remember(likedTitle, downloadedTitle, topTitle, cachedTitle) {
        listOf(
            PlaylistEntry("auto_liked", likedTitle, null, null, "auto_playlist/liked", null),
            PlaylistEntry("auto_downloaded", downloadedTitle, null, null, "auto_playlist/downloaded", null),
            PlaylistEntry("auto_top", topTitle, null, null, "top_playlist/50", null),
            PlaylistEntry("auto_cached", cachedTitle, null, null, "cache_playlist/cached", null),
        )
    }

    val entries: List<PlaylistEntry> = when (block.source) {
        PlaylistSource.Auto -> autoItems.take(block.limit)
        PlaylistSource.Library -> localPlaylists.take(block.limit).map { pl ->
            PlaylistEntry(
                id = pl.id,
                title = pl.playlist.name,
                subtitle = if (block.showCount) pluralStringResource(R.plurals.n_song, pl.songCount, pl.songCount) else null,
                cover = pl.playlist.thumbnailUrl ?: pl.thumbnails.firstOrNull(),
                route = "local_playlist/${Uri.encode(pl.id)}",
                entity = pl,
            )
        }
        PlaylistSource.Subject -> remember(page.subject.artist, block.showCount, block.limit) {
            page.subject.artist?.playlists.orEmpty().take(block.limit).map { pl ->
                PlaylistEntry(
                    id = pl.id,
                    title = pl.title,
                    subtitle = listOfNotNull(pl.author?.name, pl.songCountText.takeIf { block.showCount }).joinToString(" · ").ifBlank { null },
                    cover = pl.thumbnail,
                    route = "online_playlist/${Uri.encode(pl.id)}",
                    entity = null,
                )
            }
        }
        PlaylistSource.All -> {
            val remaining = (block.limit - autoItems.size).coerceAtLeast(0)
            (autoItems + localPlaylists.take(remaining).map { pl ->
                PlaylistEntry(
                    id = pl.id,
                    title = pl.playlist.name,
                    subtitle = if (block.showCount) pluralStringResource(R.plurals.n_song, pl.songCount, pl.songCount) else null,
                    cover = pl.playlist.thumbnailUrl ?: pl.thumbnails.firstOrNull(),
                    route = "local_playlist/${Uri.encode(pl.id)}",
                    entity = pl,
                )
            }).take(block.limit)
        }
    }

    if (entries.isEmpty()) {
        Text(
            block.empty?.takeIf { it.isNotBlank() } ?: stringResource(R.string.module_page_empty_playlists),
            fontFamily = InterFontFamily, fontSize = 13.sp, color = LabColors.Secondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        )
        return
    }

    val openMenu: (Playlist) -> Unit = { pl ->
        menuState.show {
            PlaylistMenu(playlist = pl, coroutineScope = scope, onDismiss = { menuState.dismiss() }, navController = page.navController)
        }
    }

    when (block.layout) {
        MediaListLayout.Grid -> {
            val cols = block.columns.coerceIn(2, 4)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                entries.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { item ->
                            MediaCardTile(
                                title = item.title,
                                subtitle = item.subtitle,
                                cover = item.cover,
                                fallbackIcon = R.drawable.queue_music,
                                corner = block.corner,
                                circle = false,
                                modifier = Modifier.weight(1f),
                                onClick = { page.navController.navigate(item.route) },
                                onLongClick = if (block.showMenu && item.entity != null) { { openMenu(item.entity) } } else null,
                            )
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        MediaListLayout.Row -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                entries.forEach { item ->
                    MediaCardTile(
                        title = item.title,
                        subtitle = item.subtitle,
                        cover = item.cover,
                        fallbackIcon = R.drawable.queue_music,
                        corner = block.corner,
                        circle = false,
                        modifier = Modifier.width(140.dp),
                        onClick = { page.navController.navigate(item.route) },
                        onLongClick = if (block.showMenu && item.entity != null) { { openMenu(item.entity) } } else null,
                    )
                }
            }
        }
        MediaListLayout.List, MediaListLayout.Compact -> {
            val compact = block.layout == MediaListLayout.Compact
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                entries.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(LabColors.Card)
                            .combinedClickable(
                                onClick = { page.navController.navigate(item.route) },
                                onLongClick = if (block.showMenu && item.entity != null) { { openMenu(item.entity) } } else null,
                            )
                            .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
                    ) {
                        if (block.covers) {
                            MediaThumb(item.cover, R.drawable.queue_music, if (compact) 40.dp else 48.dp, block.corner, circle = false)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            item.subtitle?.let {
                                Text(it, fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (block.showMenu && item.entity != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { openMenu(item.entity) },
                            ) {
                                Icon(painterResource(R.drawable.more_vert), null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Icon(painterResource(R.drawable.navigate_next), null, tint = LabColors.Secondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class PlaylistEntry(
    val id: String,
    val title: String,
    val subtitle: String?,
    val cover: String?,
    val route: String,
    val entity: Playlist?,
)

/** live album list/grid/carousel with AlbumMenu sheet */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PageAlbumsBlock(block: PageBlock.Albums, page: PageContext) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val libAlbums by remember(database) { database.albums(AlbumSortType.CREATE_DATE, true) }.collectAsState(initial = emptyList())

    val items: List<AlbumEntry> = remember(block.source, block.limit, page.subject.artist, libAlbums) {
        when (block.source) {
            MediaSource.Subject -> page.subject.artist?.albums.orEmpty().take(block.limit).map { a ->
                AlbumEntry(a.browseId, a.title, listOfNotNull(a.artists?.joinToString(", ") { it.name }, a.year?.toString()).joinToString(" · ").ifBlank { null }, a.thumbnail, null)
            }
            MediaSource.Library -> libAlbums.take(block.limit).map { a ->
                AlbumEntry(a.id, a.album.title, listOfNotNull(a.artists.joinToString(", ") { it.name }.ifBlank { null }, a.album.year?.toString()).joinToString(" · ").ifBlank { null }, a.album.thumbnailUrl, a)
            }
            MediaSource.Liked -> libAlbums.asSequence().filter { it.album.bookmarkedAt != null }.take(block.limit).map { a ->
                AlbumEntry(a.id, a.album.title, listOfNotNull(a.artists.joinToString(", ") { it.name }.ifBlank { null }, a.album.year?.toString()).joinToString(" · ").ifBlank { null }, a.album.thumbnailUrl, a)
            }.toList()
        }
    }

    if (items.isEmpty()) {
        Text(
            block.empty?.takeIf { it.isNotBlank() } ?: stringResource(R.string.module_page_empty_albums),
            fontFamily = InterFontFamily, fontSize = 13.sp, color = LabColors.Secondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        )
        return
    }

    val openMenu: (Album) -> Unit = { album ->
        menuState.show {
            AlbumMenu(originalAlbum = album, navController = page.navController, onDismiss = { menuState.dismiss() })
        }
    }

    when (block.layout) {
        MediaListLayout.Grid -> {
            val cols = block.columns.coerceIn(2, 4)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { item ->
                            MediaCardTile(
                                title = item.title, subtitle = item.subtitle, cover = item.cover,
                                fallbackIcon = R.drawable.album, corner = block.corner, circle = false,
                                modifier = Modifier.weight(1f),
                                onClick = { page.navController.navigate("album/${Uri.encode(item.id)}") },
                                onLongClick = if (block.showMenu && item.entity != null) { { openMenu(item.entity) } } else null,
                            )
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        MediaListLayout.Row -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                items.forEach { item ->
                    MediaCardTile(
                        title = item.title, subtitle = item.subtitle, cover = item.cover,
                        fallbackIcon = R.drawable.album, corner = block.corner, circle = false,
                        modifier = Modifier.width(140.dp),
                        onClick = { page.navController.navigate("album/${Uri.encode(item.id)}") },
                        onLongClick = if (block.showMenu && item.entity != null) { { openMenu(item.entity) } } else null,
                    )
                }
            }
        }
        MediaListLayout.List, MediaListLayout.Compact -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(LabColors.Card)
                            .combinedClickable(
                                onClick = { page.navController.navigate("album/${Uri.encode(item.id)}") },
                                onLongClick = if (block.showMenu && item.entity != null) { { openMenu(item.entity) } } else null,
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        MediaThumb(item.cover, R.drawable.album, 48.dp, block.corner, circle = false)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            item.subtitle?.let { Text(it, fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        if (block.showMenu && item.entity != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { openMenu(item.entity) },
                            ) {
                                Icon(painterResource(R.drawable.more_vert), null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Icon(painterResource(R.drawable.navigate_next), null, tint = LabColors.Secondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class AlbumEntry(val id: String, val title: String, val subtitle: String?, val cover: String?, val entity: Album?)

/** live artist list/grid/carousel with ArtistMenu sheet */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PageArtistsBlock(block: PageBlock.Artists, page: PageContext) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val libArtists by remember(database) { database.artists(ArtistSortType.CREATE_DATE, true) }.collectAsState(initial = emptyList())

    val items: List<ArtistEntry> = remember(block.source, block.limit, page.subject.artist, libArtists) {
        when (block.source) {
            MediaSource.Subject -> page.subject.artist?.similarArtists.orEmpty().take(block.limit).map { a ->
                ArtistEntry(a.id, a.title, a.thumbnail, null)
            }
            MediaSource.Library -> libArtists.take(block.limit).map { a ->
                ArtistEntry(a.id, a.artist.name, a.artist.thumbnailUrl, a)
            }
            MediaSource.Liked -> libArtists.asSequence().filter { it.artist.bookmarkedAt != null }.take(block.limit).map { a ->
                ArtistEntry(a.id, a.artist.name, a.artist.thumbnailUrl, a)
            }.toList()
        }
    }

    if (items.isEmpty()) {
        Text(
            block.empty?.takeIf { it.isNotBlank() } ?: stringResource(R.string.module_page_empty_artists),
            fontFamily = InterFontFamily, fontSize = 13.sp, color = LabColors.Secondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        )
        return
    }

    val openMenu: (Artist) -> Unit = { artist ->
        menuState.show {
            ArtistMenu(originalArtist = artist, coroutineScope = scope, onDismiss = { menuState.dismiss() })
        }
    }

    when (block.layout) {
        MediaListLayout.Grid -> {
            val cols = block.columns.coerceIn(2, 4)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { item ->
                            MediaCardTile(
                                title = item.name, subtitle = null, cover = item.avatar,
                                fallbackIcon = R.drawable.artist, corner = 16.dp, circle = true,
                                modifier = Modifier.weight(1f),
                                onClick = { page.navController.navigate("artist/${Uri.encode(item.id)}") },
                                onLongClick = item.entity?.let { e -> { openMenu(e) } },
                            )
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        MediaListLayout.Row -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                items.forEach { item ->
                    MediaCardTile(
                        title = item.name, subtitle = null, cover = item.avatar,
                        fallbackIcon = R.drawable.artist, corner = 16.dp, circle = true,
                        modifier = Modifier.width(116.dp),
                        onClick = { page.navController.navigate("artist/${Uri.encode(item.id)}") },
                        onLongClick = item.entity?.let { e -> { openMenu(e) } },
                    )
                }
            }
        }
        MediaListLayout.List, MediaListLayout.Compact -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(LabColors.Card)
                            .combinedClickable(
                                onClick = { page.navController.navigate("artist/${Uri.encode(item.id)}") },
                                onLongClick = item.entity?.let { e -> { openMenu(e) } },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        MediaThumb(item.avatar, R.drawable.artist, 46.dp, 23.dp, circle = true)
                        Spacer(Modifier.width(12.dp))
                        Text(item.name, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(painterResource(R.drawable.navigate_next), null, tint = LabColors.Secondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private data class ArtistEntry(val id: String, val name: String, val avatar: String?, val entity: Artist?)

/** live now-playing card embedded inside any custom page */
@Composable
internal fun PageNowPlayingBlock(block: PageBlock.NowPlaying, glass: GlassState?) {
    val connection = LocalPlayerConnection.current
    val playerSheet = LocalPlayerBottomSheetState.current
    val database = LocalDatabase.current
    val song by (connection?.mediaMetadata ?: NullMetaFlow).collectAsState()
    val playing by (connection?.isPlaying ?: FalseFlow).collectAsState()
    val currentSong by (connection?.currentSong ?: NullDbSongFlow).collectAsState(initial = null)

    val lyricsRow by remember(database, song?.id) {
        song?.id?.let { database.lyrics(it) } ?: flowOf(null)
    }.collectAsState(initial = null)
    val parsedLyrics = remember(lyricsRow?.lyrics) {
        lyricsRow?.lyrics?.takeIf { it.isNotBlank() }?.let { runCatching { LyricsUtils.parseLyricsCached(it) }.getOrDefault(emptyList()) }.orEmpty()
    }
    val position = if (block.showLyrics && parsedLyrics.isNotEmpty()) rememberLivePosition(animated = true) else null
    // derivedStateOf ensures PageNowPlayingBlock only recomposes when the active lyric line changes, not at 60-120 Hz
    val activeLineIndex by remember(position, parsedLyrics) {
        androidx.compose.runtime.derivedStateOf {
            if (position != null && parsedLyrics.isNotEmpty()) {
                LyricsUtils.findCurrentLineIndex(parsedLyrics, position.positionMs())
            } else -1
        }
    }
    val activeLine = parsedLyrics.getOrNull(activeLineIndex)?.text

    SettingsBlockSurface(
        corner = block.corner,
        glass = glass,
        glassRadius = block.glass,
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (song != null) playerSheet?.expandSoft() },
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                MediaThumb(song?.thumbnailUrl, R.drawable.music_note, 52.dp, 12.dp, circle = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title ?: stringResource(R.string.module_page_nothing_playing),
                        fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song?.artists?.joinToString(", ") { it.name }.orEmpty(),
                        fontFamily = InterFontFamily, fontSize = 12.sp,
                        color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (block.showControls && song != null && connection != null) {
                    val liked = currentSong?.song?.liked == true
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(34.dp).clip(CircleShape).clickable { connection.toggleLike() },
                        ) {
                            Icon(
                                painterResource(if (liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill),
                                null,
                                tint = if (liked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White).clickable {
                                if (playing) connection.player.pause() else connection.player.play()
                            },
                        ) {
                            Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(34.dp).clip(CircleShape).clickable { connection.seekToNext() },
                        ) {
                            Icon(painterResource(R.drawable.skip_next), null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            if (!activeLine.isNullOrBlank()) {
                Text(
                    activeLine,
                    fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** embedded synced flowing lyrics inside any custom page */
@Composable
internal fun PageLyricsBlock(block: PageBlock.Lyrics, page: PageContext) {
    val connection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val song by (connection?.mediaMetadata ?: NullMetaFlow).collectAsState()
    val lyricsRow by remember(database, song?.id) {
        song?.id?.let { database.lyrics(it) } ?: flowOf(null)
    }.collectAsState(initial = null)
    val lines = remember(lyricsRow?.lyrics) {
        lyricsRow?.lyrics?.takeIf { it.isNotBlank() }?.let { runCatching { LyricsUtils.parseLyricsCached(it) }.getOrDefault(emptyList()) }.orEmpty()
    }
    if (lines.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(block.height.coerceAtMost(96.dp)).clip(RoundedCornerShape(18.dp)).background(LabColors.Card),
        ) {
            Text(stringResource(R.string.skin_bar_no_lyrics), fontFamily = InterFontFamily, fontSize = 13.sp, color = LabColors.Secondary)
        }
        return
    }
    val position = rememberLivePosition(animated = true)
    val clock = rememberLyricClock(lines, position)
    val accent = MaterialTheme.colorScheme.primary
    val baseColor = block.color.onChrome(accent, page.entry.values)
    val activeColor = block.active?.onChrome(accent, page.entry.values) ?: accent
    FlowLyricsView(
        lines = lines,
        position = position,
        clock = clock,
        height = block.height,
        fontSize = block.size,
        color = baseColor,
        activeColor = activeColor,
        blur = block.blur,
        sweep = block.sweep,
        translation = block.translation,
        onSeek = if (block.seek && connection != null) { ms -> connection.seekTo(ms) } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** interactive search field submitting directly to Mochy's search screen */
@Composable
internal fun PageSearchBarBlock(block: PageBlock.SearchBar, page: PageContext, glass: GlassState?) {
    var query by remember { mutableStateOf("") }
    val submit: () -> Unit = {
        val clean = query.trim()
        if (clean.isNotEmpty()) {
            page.navController.navigate(searchRoute(clean))
        } else {
            page.navController.navigate("search_input")
        }
    }
    SettingsBlockSurface(
        corner = block.corner,
        glass = glass,
        glassRadius = block.glass,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(painterResource(R.drawable.search_filled), null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        block.placeholder?.takeIf { it.isNotBlank() } ?: stringResource(R.string.module_page_search_hint),
                        fontFamily = InterFontFamily, fontSize = 15.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 15.sp, color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    painterResource(R.drawable.close), null, tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp).clip(CircleShape).clickable { query = "" },
                )
            }
        }
    }
}

/** pinned Speed Dial items from the user's library */
@Composable
internal fun PageSpeedDialBlock(block: PageBlock.SpeedDial, page: PageContext) {
    val database = LocalDatabase.current
    val connection = LocalPlayerConnection.current
    val items by remember(database) { database.speedDialDao.getAll() }.collectAsState(initial = emptyList())
    val visible = remember(items, block.limit) { items.take(block.limit) }
    if (visible.isEmpty()) {
        Text(
            block.empty?.takeIf { it.isNotBlank() } ?: stringResource(R.string.module_page_empty_speed_dial),
            fontFamily = InterFontFamily, fontSize = 13.sp, color = LabColors.Secondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        )
        return
    }
    val openItem: (com.mochyy.mochymusic.db.entities.SpeedDialItem) -> Unit = { item ->
        when (item.type) {
            "SONG" -> connection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id)))
            "ALBUM" -> page.navController.navigate("album/${Uri.encode(item.id)}")
            "ARTIST" -> page.navController.navigate("artist/${Uri.encode(item.id)}")
            "PLAYLIST" -> page.navController.navigate("online_playlist/${Uri.encode(item.id)}")
        }
    }
    when (block.layout) {
        MediaListLayout.Row -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                visible.forEach { item ->
                    MediaCardTile(
                        title = item.title, subtitle = item.subtitle, cover = item.thumbnailUrl,
                        fallbackIcon = R.drawable.music_note, corner = block.corner, circle = item.type == "ARTIST",
                        modifier = Modifier.width(120.dp), onClick = { openItem(item) }, onLongClick = null,
                    )
                }
            }
        }
        MediaListLayout.List, MediaListLayout.Compact -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LabColors.Card).clickable { openItem(item) }.padding(10.dp),
                    ) {
                        MediaThumb(item.thumbnailUrl, R.drawable.music_note, 42.dp, block.corner, circle = item.type == "ARTIST")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            item.subtitle?.let { Text(it, fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
        MediaListLayout.Grid -> {
            val cols = block.columns.coerceIn(2, 4)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visible.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { item ->
                            MediaCardTile(
                                title = item.title, subtitle = null, cover = item.thumbnailUrl,
                                fallbackIcon = R.drawable.music_note, corner = block.corner, circle = item.type == "ARTIST",
                                modifier = Modifier.weight(1f), onClick = { openItem(item) }, onLongClick = null,
                            )
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** live library & listening stats tiles */
@Composable
internal fun PageStatsBlock(block: PageBlock.Stats, glass: GlassState?) {
    val database = LocalDatabase.current
    val plays by remember(database) { database.eventCount() }.collectAsState(initial = 0)
    val songCount by remember(database, block.showLibrary) {
        if (block.showLibrary) database.allSongCount() else ZeroIntFlow
    }.collectAsState(initial = 0)
    val likedCount by remember(database, block.showLibrary) {
        if (block.showLibrary) database.likedSongsCount() else ZeroIntFlow
    }.collectAsState(initial = 0)
    val playlistCount by remember(database, block.showLibrary) {
        if (block.showLibrary) database.playlistCount() else ZeroIntFlow
    }.collectAsState(initial = 0)

    val tiles = buildList {
        add(stringResource(R.string.module_page_stats_plays) to plays.toString())
        if (block.showLibrary) {
            add(stringResource(R.string.module_page_stats_songs) to songCount.toString())
            add(stringResource(R.string.module_page_stats_liked) to likedCount.toString())
            add(stringResource(R.string.module_page_stats_playlists) to playlistCount.toString())
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        tiles.forEach { (label, value) ->
            SettingsBlockSurface(
                corner = block.corner,
                glass = glass,
                glassRadius = block.glass,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
                ) {
                    Text(value, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary, maxLines = 1)
                }
            }
        }
    }
}

/** segmented tab bar bound to a module setting key so pages can switch views via "when" conditions */
@Composable
internal fun PageTabsBlock(block: PageBlock.Tabs, page: PageContext, glass: GlassState?) {
    val current = page.entry.values[block.settingKey]?.takeIf { it.isNotBlank() } ?: block.items.firstOrNull()?.first.orEmpty()
    val accent = MaterialTheme.colorScheme.primary
    SettingsBlockSurface(
        corner = block.corner,
        glass = glass,
        glassRadius = block.glass,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),
        ) {
            block.items.forEach { (value, label) ->
                val lit = current.equals(value, ignoreCase = true)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(block.corner))
                        .background(if (lit) accent else Color.Transparent)
                        .clickable { page.onField(page.entry.id, block.settingKey, value) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(
                        label,
                        fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = if (lit) Color.Black else Color.White.copy(alpha = 0.8f), maxLines = 1,
                    )
                }
            }
        }
    }
}

/** standalone uniform, progressive or radial blur strip over the page backdrop */
@Composable
internal fun PageBlurBlock(block: PageBlock.Blur, page: PageContext, glass: GlassState?) {
    val shape = RoundedCornerShape(block.corner)
    val accent = MaterialTheme.colorScheme.primary
    val colors = block.colors.map { it.onChrome(accent, page.entry.values) }
    val disableBlur by rememberPreference(DisableBlurKey, false)
    Box(Modifier.fillMaxWidth().height(block.height).clip(shape)) {
        if (glass != null && !disableBlur) {
            val radiusPx = with(LocalDensity.current) { block.radius.dp.toPx() }
            val tint = colors.singleOrNull() ?: Color.Transparent
            when (block.direction) {
                SkinBlurDirection.TopToBottom -> ProgressiveGlassBackground(
                    state = glass, maxBlurRadius = { radiusPx }, tint = tint,
                    direction = BlurDirection.TopToBottom, steps = block.steps, fallbackBlur = true, modifier = Modifier.matchParentSize(),
                )
                SkinBlurDirection.BottomToTop -> ProgressiveGlassBackground(
                    state = glass, maxBlurRadius = { radiusPx }, tint = tint,
                    direction = BlurDirection.BottomToTop, steps = block.steps, fallbackBlur = true, modifier = Modifier.matchParentSize(),
                )
                SkinBlurDirection.Radial -> {
                    val radial = remember { Brush.radialGradient(0f to Color.Black, 0.72f to Color.Black, 1f to Color.Transparent) }
                    GlassPillBackground(
                        state = glass, blurRadius = { radiusPx }, tint = tint, mask = radial,
                        tileMode = android.graphics.Shader.TileMode.CLAMP, bleed = true,
                        fallbackBlur = true, fallbackMaskMode = 3, fallbackMaskParam = 0.72f,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                null -> GlassPillBackground(
                    state = glass, blurRadius = { radiusPx }, tint = tint,
                    tileMode = android.graphics.Shader.TileMode.CLAMP, bleed = true,
                    fallbackBlur = true, modifier = Modifier.matchParentSize(),
                )
            }
            if (colors.size > 1) Box(Modifier.matchParentSize().background(pageBackgroundBrush(colors)))
        } else {
            val fill = colors.ifEmpty { listOf(Color.White.copy(alpha = 0.1f)) }
            Box(Modifier.matchParentSize().background(pageBackgroundBrush(fill)))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCardTile(
    title: String,
    subtitle: String?,
    cover: String?,
    fallbackIcon: Int,
    corner: androidx.compose.ui.unit.Dp,
    circle: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = if (circle) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(LabColors.Card)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(if (circle) CircleShape else RoundedCornerShape(corner))
                .background(LabColors.Row),
        ) {
            if (cover != null) {
                AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(painterResource(fallbackIcon), null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
            }
        }
        Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MediaThumb(url: String?, fallbackIcon: Int, size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp, circle: Boolean) {
    val shape = if (circle) CircleShape else RoundedCornerShape(corner)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(shape).background(LabColors.Row),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(fallbackIcon), null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(size * 0.48f))
        }
    }
}

private val NullMetaFlow = MutableStateFlow<MediaMetadata?>(null)
private val NullDbSongFlow = MutableStateFlow<com.mochyy.mochymusic.db.entities.Song?>(null)
private val FalseFlow = MutableStateFlow(false)
private val ZeroIntFlow = MutableStateFlow(0)
private val EmptyQueueFlow = MutableStateFlow<List<androidx.media3.common.Timeline.Window>>(emptyList())
private const val FirstSongRows = 30
private const val MoreSongRows = 50
