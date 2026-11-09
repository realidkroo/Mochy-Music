// the top of the discord page: the switch and a preview of the status, built by PresenceActivity, the same code that sends it

package com.mochyy.mochymusic.ui.screens.settings.integrations.discord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.discord.DiscordProfile
import com.mochyy.mochymusic.discord.NowPlaying
import com.mochyy.mochymusic.discord.PresenceActivity
import com.mochyy.mochymusic.discord.PresenceImages
import com.mochyy.mochymusic.discord.PresenceKind
import com.mochyy.mochymusic.discord.PresencePicture
import com.mochyy.mochymusic.discord.PresenceSettings
import com.mochyy.mochymusic.ui.component.AppSwitch
import kotlinx.coroutines.delay

private val BannerHeight = 104.dp
private val AvatarSize = 72.dp
private val AvatarRing = 5.dp

@Composable
internal fun DiscordToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    status: String,
    enabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DiscordColors.ToggleCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.integration_discord),
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text("Enable rich presence", color = DiscordColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(status, color = DiscordColors.Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** the user's discord profile card, banner to bio, with the status underneath */
@Composable
internal fun DiscordProfileCard(
    profile: DiscordProfile?,
    track: NowPlaying,
    settings: PresenceSettings,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DiscordColors.Profile),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BannerHeight + AvatarSize / 2),
        ) {
            Banner(profile)
            Avatar(
                url = profile?.avatarUrl,
                modifier = Modifier
                    .padding(start = 22.dp)
                    .align(Alignment.BottomStart),
            )
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp)) {
            Text(
                text = profile?.displayName ?: "Not connected",
                color = DiscordColors.Primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = profile?.let { listOfNotNull(it.username, it.pronouns).joinToString(" · ") }
                    ?: "Log in to show your profile here",
                color = DiscordColors.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            profile?.bio?.let { bio ->
                Text(
                    text = bio,
                    color = DiscordColors.Secondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        DiscordActivityPreview(
            track = track,
            settings = settings,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun Banner(profile: DiscordProfile?) {
    val color = profile?.bannerColor?.let { Color(0xFF000000 or it.toLong()) } ?: DiscordColors.Banner
    Box(
        Modifier
            .fillMaxWidth()
            .height(BannerHeight)
            .background(color),
    ) {
        profile?.bannerUrl?.let { url ->
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Avatar(url: String?, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(DiscordColors.Banner)
            .padding(AvatarRing)
            .clip(CircleShape)
            .background(Color(0xFFD9D9D9)),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = "Your Discord picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** the status card itself: listening to ..., the pictures, the lines, the bar and the buttons */
@Composable
internal fun DiscordActivityPreview(
    track: NowPlaying,
    settings: PresenceSettings,
    modifier: Modifier = Modifier,
) {
    // a once-a-second clock moves the bar and the ring, like discord's own card
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val activity = remember(track, settings, now) { PresenceActivity.build(track, settings, now) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DiscordColors.Activity)
            .padding(14.dp),
    ) {
        Text(
            text = (if (activity.kind == PresenceKind.LISTENING) "Listening to " else "Playing ") + activity.name,
            color = DiscordColors.Primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pictures(large = activity.largeImage, small = activity.smallImage)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                activity.details?.let { Line(it, size = 16, weight = FontWeight.Bold, color = DiscordColors.Primary) }
                activity.state?.let { Line(it) }
                activity.largeText?.let { Line(it) }
                Times(activity, now)
            }
        }
        if (activity.buttons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                activity.buttons.forEach { button ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(DiscordColors.Profile),
                    ) {
                        Text(button.label, color = DiscordColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(text: String, size: Int = 11, weight: FontWeight = FontWeight.Normal, color: Color = DiscordColors.Secondary) {
    Text(text, color = color, fontSize = size.sp, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** listening shows a bar from start to end, playing counts up, a paused song shows neither */
@Composable
private fun Times(activity: PresenceActivity, now: Long) {
    val start = activity.startMs ?: return
    val mono = Modifier.padding(top = 6.dp)
    val end = activity.endMs
    if (end == null) {
        Text("${clock(now - start)} elapsed", color = DiscordColors.Secondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = mono)
        return
    }
    val total = (end - start).coerceAtLeast(1)
    val elapsed = (now - start).coerceIn(0, total)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = mono) {
        Text(clock(elapsed), color = DiscordColors.Secondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(elapsed.toFloat() / total)
                    .height(3.dp)
                    .background(Color.White),
            )
        }
        Text(clock(total), color = DiscordColors.Secondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** the big picture with the small round one tucked into its corner, as discord lays them out */
@Composable
private fun Pictures(large: PresencePicture?, small: PresencePicture?) {
    Box(Modifier.size(66.dp)) {
        Box(
            Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DiscordColors.Cover),
        ) {
            large?.let { PictureView(it) }
        }
        if (large != null && small != null) {
            Box(
                Modifier
                    .size(26.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(DiscordColors.Activity)
                    .padding(3.dp)
                    .clip(CircleShape),
            ) {
                PictureView(small)
            }
        }
    }
}

/** one picture as discord shows it, the badge and the spinning disc are drawn here so the preview works before the pictures are uploaded */
@Composable
private fun PictureView(picture: PresencePicture) {
    val fill = Modifier.fillMaxSize()
    when (picture) {
        is PresencePicture.Web -> AsyncImage(model = picture.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = fill)
        is PresencePicture.Local -> AsyncImage(model = picture.sourceUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = fill)
        PresencePicture.Logo -> Box(fill.background(Color(0xFF1A1A1A))) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_mark),
                contentDescription = "Mochy",
                contentScale = ContentScale.Crop,
                // the launcher picture has a safe margin around it, the github copy is cropped the same
                modifier = fill.graphicsLayer {
                    scaleX = 1.5f
                    scaleY = 1.5f
                },
            )
        }
        is PresencePicture.Status -> StatusBadge(picture, fill)
        is PresencePicture.Spinning -> SpinningPicture(picture.sourceUrl, picture.style.secondsPerTurn, fill)
    }
}

/** the same badge scripts/discord_presence_images.py draws, at preview size */
@Composable
private fun StatusBadge(status: PresencePicture.Status, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        drawCircle(Color(0xFF1E1F22))
        val stroke = s * 0.085f
        val inset = s * 0.16f + stroke / 2
        val arcSize = Size(s - inset * 2, s - inset * 2)
        drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        val sweep = 360f * status.step / PresenceImages.StatusSteps
        if (sweep > 0f) {
            drawArc(Color.White, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val glyph = s * 0.30f
        val cx = s / 2
        val cy = s / 2
        if (status.playing) {
            val left = cx - glyph * 0.38f
            val triangle = Path().apply {
                moveTo(left, cy - glyph / 2)
                lineTo(left, cy + glyph / 2)
                lineTo(left + glyph * 0.9f, cy)
                close()
            }
            drawPath(triangle, Color.White)
        } else {
            val bar = glyph * 0.3f
            val gap = glyph * 0.26f
            listOf(cx - gap / 2 - bar, cx + gap / 2).forEach { x ->
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(x, cy - glyph / 2),
                    size = Size(bar, glyph),
                    cornerRadius = CornerRadius(bar / 2),
                )
            }
        }
    }
}

/** the rotating disc, turning at the user's speed like the gif */
@Composable
private fun SpinningPicture(url: String, secondsPerTurn: Int, modifier: Modifier) {
    val turn = rememberInfiniteTransition(label = "spin")
    val degrees by turn.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(secondsPerTurn * 1_000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .graphicsLayer { rotationZ = degrees }
            .clip(CircleShape),
    )
}

/** the status again, floating at the bottom while the user edits further down the page */
@Composable
internal fun DiscordFloatingPreview(
    visible: Boolean,
    track: NowPlaying,
    settings: PresenceSettings,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 3 },
        modifier = modifier,
    ) {
        DiscordActivityPreview(
            track = track,
            settings = settings,
            modifier = Modifier
                .shadow(elevation = 18.dp, shape = RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
        )
    }
}
