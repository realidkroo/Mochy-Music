// SpeedDialGridItem.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

internal val SpeedDialTileShape = RoundedCornerShape(12.dp)

// dark only where the title sits, so the cover stays bright above it
private val TitleFade = Brush.verticalGradient(
    0.4f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.85f),
)

private val TileTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.02).em,
)

/** a speed dial tile: the cover, with its title on a dark fade at the bottom */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialGridItem(
    item: YTItem,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: () -> Boolean = { false },
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.94f)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(SpeedDialTileShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = RemoteFocus,
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        ItemThumbnail(
            thumbnailUrl = item.thumbnail,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = SpeedDialTileShape,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TitleFade)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 7.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = item.title,
                style = TileTitleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // albums, artists and playlists open a page instead of playing
            if (item !is SongItem) {
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (isPinned) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_push_pin),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
