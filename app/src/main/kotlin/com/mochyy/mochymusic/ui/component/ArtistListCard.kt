// ArtistListCard.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.models.ArtistGroup
import com.music.innertube.models.YTItem

@Composable
fun ArtistListCard(
    group: ArtistGroup,
    onClick: () -> Unit,
    onItemClick: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tintCovers = remember(group) {
        listOfNotNull(group.artistThumbnailUrl) + group.items.take(2).mapNotNull { it.thumbnail }
    }

    Box(
        modifier = modifier
            .width(276.dp)
            .height(344.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .coverTintBackground(tintCovers, seed = group.artistName)
        )

        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (group.artistThumbnailUrl != null) {
                        AsyncImage(
                            model = group.artistThumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(CircleShape),
                        )
                    } else {
                        AlbumGradient(thumbnailUrl = null, modifier = Modifier.size(52.dp).clip(CircleShape))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Similar to",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                    Text(
                        text = group.artistName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        fontSize = 24.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(start = 24.dp)
                    .wrapContentWidth(unbounded = true, align = Alignment.Start)
            ) {
                group.items.take(3).forEach { item ->
                    Box(
                        modifier = Modifier
                            .requiredSize(96.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        AlbumGradient(thumbnailUrl = item.thumbnail, modifier = Modifier.fillMaxSize())
                        item.thumbnail?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .padding(start = 24.dp)
                    .offset(y = 20.dp)
                    .wrapContentWidth(unbounded = true, align = Alignment.Start)
            ) {
                group.items.drop(3).take(2).forEach { item ->
                    Box(
                        modifier = Modifier
                            .requiredSize(160.dp)
                            .clip(CircleShape)
                    ) {
                        AlbumGradient(thumbnailUrl = item.thumbnail, modifier = Modifier.fillMaxSize())
                        item.thumbnail?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
