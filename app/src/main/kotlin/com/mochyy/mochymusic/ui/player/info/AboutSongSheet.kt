// AboutSongSheet.kt

package com.mochyy.mochymusic.ui.player.info

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AboutSongSourceKey
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.player.menu.LocalMenuSheetStack
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.player.menu.MenuSheetSurface
import com.mochyy.mochymusic.ui.player.menu.SongHeader
import com.mochyy.mochymusic.ui.player.menu.rememberMenuSheetStack
import com.mochyy.mochymusic.ui.screens.setup.onboarding.ProviderSquircle
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** where the "about" text comes from; the chip beside the title swaps it */
enum class AboutSource(val id: String, val label: String) {
    Musixmatch("musixmatch", "Musixmatch"),
    Genius("genius", "Genius"),
    Wikipedia("wikipedia", "Wikipedia"),
    YouTube("youtube", "YouTube"),
    // the song facts modules that are on, only offered while one is
    Modules("modules", "Modules"),
}

/** "about song": the song's facts, what it's about, who made it and where else it plays */
@Composable
fun AboutSongSheet(
    song: MediaMetadata,
    about: SongAbout,
    onDismiss: () -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val (sourceId, setSourceId) = rememberPreference(AboutSongSourceKey, AboutSource.Musixmatch.id)
    val source = AboutSource.entries.firstOrNull { it.id == sourceId } ?: AboutSource.Musixmatch
    var showSources by remember { mutableStateOf(false) }
    val stack = LocalMenuSheetStack.current ?: rememberMenuSheetStack()

    CompositionLocalProvider(LocalMenuSheetStack provides stack) {
        MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.72f, fullDetent = 0.94f) { _ ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
            ) {
                // a tablet or a phone on its side keeps the column readable instead of stretching it
                InfoColumn(modifier = Modifier.widthIn(max = 600.dp)) {
                    InfoSheetTitle("About song")
                    Spacer(Modifier.height(16.dp))
                    SongHeader(song)
                    InfoDivider()
                    GeneralInformation(about)
                    InfoDivider()
                    InfoSectionTitle("About") {
                        SourceChip(source) { showSources = true }
                    }
                    Spacer(Modifier.height(12.dp))
                    AboutCard(about, source, onSwitch = { setSourceId(it.id) })
                    if (about.artists.isNotEmpty()) {
                        InfoDivider()
                        InfoSectionTitle("By")
                        Spacer(Modifier.height(14.dp))
                        ArtistRow(about.artists, onOpenArtist)
                    }
                    if (about.links.isNotEmpty()) {
                        InfoDivider()
                        InfoSectionTitle("Also available on")
                        Spacer(Modifier.height(14.dp))
                        StoreRow(about.links)
                    }
                }
            }
        }

        if (showSources) {
            AboutSourceSheet(
                selected = source,
                about = about,
                onPick = { setSourceId(it.id) },
                onDismiss = { showSources = false },
            )
        }
    }
}

@Composable
private fun GeneralInformation(about: SongAbout) {
    InfoSectionTitle("General Information")
    Spacer(Modifier.height(8.dp))
    // a fact still on its way gets a placeholder; one nobody had is left out rather than shown as "-"
    val waiting = !about.factsDone
    val genre = about.genre
    if (genre != null) InfoBullet("Genre", genre) else if (waiting) LoadingBullet("Genre")
    val released = about.released
    if (released != null) InfoBullet("Date released", released) else if (waiting) LoadingBullet("Date released")
    about.album?.let { InfoBullet("Album", it) }
    about.artist?.let { InfoBullet("Artist", it) }
    about.writtenBy?.let { InfoBullet("Written by", it) }
    about.producedBy?.let { InfoBullet("Produced by", it) }
    about.label?.let { InfoBullet("Label", it) }
    about.lengthSeconds?.let { InfoBullet("Length", makeTimeString(it * 1000L)) }
}

@Composable
private fun LoadingBullet(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(InfoMuted))
        Spacer(Modifier.width(9.dp))
        Text(text = label, color = InfoMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        LoadingBar(width = 64.dp, height = 10.dp)
    }
}

@Composable
private fun SourceChip(source: AboutSource, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(26.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp),
    ) {
        Text(text = source.label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.SwapVert, contentDescription = "Change source", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
    }
}

/** the text of the picked source, or a placeholder while it loads, or a nudge to the other one */
@Composable
private fun AboutCard(about: SongAbout, source: AboutSource, onSwitch: (AboutSource) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val text = textFor(about, source)
    val done = doneFor(about, source)
    InfoCard {
        // no weight here: inside the scrolling sheet a weighted text got only the card's minimum height and long ones were cut off
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.heightIn(min = 130.dp)) {
            AnimatedContent(
                targetState = Triple(source, done, text),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "aboutText",
            ) { (shownSource, shownDone, shownText) ->
                Column {
                    when {
                        !shownDone -> {
                            LoadingBar(width = 240.dp)
                            Spacer(Modifier.height(10.dp))
                            LoadingBar(width = 200.dp)
                            Spacer(Modifier.height(10.dp))
                            LoadingBar(width = 220.dp)
                        }
                        shownText != null -> Text(
                            text = shownText,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        else -> {
                            Text(
                                text = stringResource(R.string.about_song_nothing_yet, shownSource.label),
                                color = InfoMuted,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                            // the first other source that does have something, so the button never leads to another empty card
                            val other = AboutSource.entries.firstOrNull { it != shownSource && textFor(about, it) != null }
                            if (other != null) {
                                Spacer(Modifier.height(12.dp))
                                InfoPill(text = "Read it on ${other.label}", filled = true, onClick = { onSwitch(other) })
                            }
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(18.dp))
                val pageUrl = pageFor(about, source)
                Text(
                    text = stringResource(R.string.about_song_provided_by, source.label.lowercase()),
                    color = InfoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (pageUrl != null) {
                                Modifier.clickable(interactionSource = null, indication = RemoteFocus) { runCatching { uriHandler.openUri(pageUrl) } }
                            } else Modifier,
                        ),
                )
            }
        }
    }
}

internal fun textFor(about: SongAbout, source: AboutSource): String? = when (source) {
    AboutSource.Musixmatch -> about.musixmatch?.meaning
    AboutSource.Genius -> about.genius?.about
    AboutSource.Wikipedia -> about.wiki?.about
    AboutSource.YouTube -> about.youtubeDescription
    AboutSource.Modules -> about.module?.about
}

internal fun doneFor(about: SongAbout, source: AboutSource): Boolean = when (source) {
    AboutSource.Musixmatch -> about.musixmatchPageDone
    AboutSource.Genius -> about.geniusDone
    AboutSource.Wikipedia -> about.wikiDone
    AboutSource.YouTube -> about.youtubeDone
    AboutSource.Modules -> about.moduleDone
}

// where "provided by" leads: the page the text came from (wikipedia's text is shared on the condition it's credited this way)
private fun pageFor(about: SongAbout, source: AboutSource): String? = when (source) {
    AboutSource.Musixmatch -> about.musixmatch?.pageUrl
    AboutSource.Genius -> about.genius?.pageUrl?.takeIf { it.isNotEmpty() }
    AboutSource.Wikipedia -> about.wiki?.articleUrl
    AboutSource.YouTube -> null
    AboutSource.Modules -> null
}

@Composable
private fun ArtistRow(artists: List<AboutArtist>, onOpenArtist: (String) -> Unit) = BoxWithConstraints(Modifier.fillMaxWidth()) {
    // about three across on a phone, a little bigger on a tablet, never so big a name has no room
    val circle = ((maxWidth - ArtistGap * 2) / 2.9f).coerceIn(80.dp, 128.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(ArtistGap),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        artists.forEach { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(circle)
                    .clickable(
                        enabled = !artist.id.isNullOrBlank(),
                        interactionSource = null,
                        indication = RemoteFocus,
                    ) { artist.id?.let(onOpenArtist) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(circle).clip(CircleShape).background(MenuRowSurface),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.person),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(36.dp),
                    )
                    if (artist.imageUrl != null) {
                        AsyncImage(
                            model = artist.imageUrl,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(circle),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = artist.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StoreRow(links: List<StoreLink>) = BoxWithConstraints(Modifier.fillMaxWidth()) {
    val uriHandler = LocalUriHandler.current
    // three and a bit showing, like the mock, so it's plain the row scrolls to the rest
    val tile = ((maxWidth - StoreGap * 3) / 3.4f).coerceIn(72.dp, 120.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(StoreGap),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        links.forEach { link ->
            Column(
                modifier = Modifier
                    .width(tile)
                    .clickable(interactionSource = null, indication = RemoteFocus) { runCatching { uriHandler.openUri(link.url) } },
            ) {
                ProviderSquircle(service = link.service, size = tile)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = link.service.label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val ArtistGap = 20.dp
private val StoreGap = 12.dp
