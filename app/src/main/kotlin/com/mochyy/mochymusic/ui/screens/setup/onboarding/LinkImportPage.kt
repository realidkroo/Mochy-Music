// the import sheet's paste-a-link page: the field, the playlist the link turned out to be with whoever made it, and the import button

package com.mochyy.mochymusic.ui.screens.setup.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.importer.LinkImporter
import com.mochyy.mochymusic.importer.ParsedImport
import com.mochyy.mochymusic.importer.PlaylistAuthor
import com.mochyy.mochymusic.ui.screens.library.ImportSourceIcon
import com.mochyy.mochymusic.viewmodels.PendingImport

@Composable
internal fun LinkImportPage(
    link: String,
    onLinkChange: (String) -> Unit,
    pending: PendingImport,
    onRead: () -> Unit,
    onImport: (ParsedImport) -> Unit,
    onBack: () -> Unit,
) {
    val ready = pending as? PendingImport.Ready
    val reading = pending is PendingImport.Loading
    // just a few contains() checks, so it's fine to redo as the user types
    val recognised = remember(link) { LinkImporter.detect(link) != null }

    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.link)) },
        title = stringResource(R.string.onb_link_title),
        subtitle = stringResource(R.string.onb_link_subtitle),
        footer = {
            if (ready != null) {
                val songs = ready.parsed.totalSongs
                OnbButton(pluralStringResource(R.plurals.onb_link_import_songs, songs, songs), onClick = { onImport(ready.parsed) })
            } else {
                OnbButton(
                    text = stringResource(if (reading) R.string.onb_link_reading else R.string.onb_link_read),
                    enabled = recognised && !reading,
                    onClick = onRead,
                )
            }
            OnbButton(stringResource(R.string.onb_go_back), onClick = onBack, style = OnbButtonStyle.Secondary)
        },
    ) {
        LinkField(link = link, onLinkChange = onLinkChange, onDone = { if (recognised && !reading && ready == null) onRead() })
        Spacer(Modifier.height(16.dp))
        when (pending) {
            is PendingImport.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.onb_link_reading_body), color = Onb.Subtitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            is PendingImport.Failed -> Text(pending.message, color = Color(0xFFFF8A80), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            is PendingImport.Ready -> LinkPreviewCard(pending.parsed)
            PendingImport.Idle -> if (link.isNotBlank() && !recognised) {
                Text(stringResource(R.string.onb_link_unknown), color = Onb.Muted, fontSize = 14.sp)
            }
        }
    }
}

/** the link field, with a paste chip while it's empty since a link nearly always comes from the clipboard */
@Composable
private fun LinkField(link: String, onLinkChange: (String) -> Unit, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    OnbTextField(
        value = link,
        onValueChange = onLinkChange,
        placeholder = stringResource(R.string.onb_link_hint),
        keyboardType = KeyboardType.Uri,
        onDone = onDone,
        trailing = {
            val showPaste = link.isEmpty()
            Text(
                text = stringResource(if (showPaste) R.string.onb_link_paste else R.string.onb_link_clear),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Onb.ButtonPrimary)
                    .clickable {
                        if (showPaste) {
                            clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let(onLinkChange)
                        } else {
                            onLinkChange("")
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        },
    )
}

/** what the link holds: cover, name, where it's from and how many songs, then who made it */
@Composable
private fun LinkPreviewCard(parsed: ParsedImport) {
    val name = parsed.playlists.keys.firstOrNull().orEmpty()
    val cover = parsed.playlistCovers[name]
    val author = parsed.playlistOrigins[name]?.author
    OnbCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Onb.Page),
                ) {
                    if (cover != null) {
                        AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(painterResource(R.drawable.queue_music), contentDescription = null, tint = Onb.Muted, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, color = Color.White, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        parsed.provider?.let { source ->
                            ImportSourceIcon(source, size = 18.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        val songs = pluralStringResource(R.plurals.onb_songs, parsed.totalSongs, parsed.totalSongs)
                        Text(
                            text = parsed.provider?.let { stringResource(R.string.onb_link_source_songs, it.label, songs) } ?: songs,
                            color = Onb.Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (author != null) {
                Spacer(Modifier.height(14.dp))
                AuthorRow(author)
            }
            parsed.warnings.forEach { warning ->
                Spacer(Modifier.height(12.dp))
                Text(warning, color = Onb.Muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

/** the playlist's maker, their picture or the first letter of their name in a circle */
@Composable
private fun AuthorRow(author: PlaylistAuthor) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Onb.ButtonPrimary),
        ) {
            Text(author.name.take(1).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (author.avatarUrl != null) {
                AsyncImage(model = author.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.onb_link_by, author.name),
            color = Onb.Subtitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
