// the people on dev spaces and the top half of each page: banner, avatar, name, a line about them and their links

package com.mochyy.mochymusic.ui.screens.settings.devspace

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.DiscordUrl
import com.mochyy.mochymusic.core.updater.InstagramUrl
import com.mochyy.mochymusic.core.updater.ThreadsUrl
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.screens.donate.showDonateSheet
import com.mochyy.mochymusic.ui.utils.SoftBlurTransformation
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.rememberResourcePainter

internal data class DevSocial(val label: String, val url: String)

internal data class DevPerson(
    val name: String,
    val bio: String,
    @DrawableRes val avatar: Int,
    /** the main dev takes donations, the donate section only shows for them */
    val takesDonations: Boolean = false,
    val socials: List<DevSocial> = emptyList(),
)

internal val DevPeople = listOf(
    DevPerson(
        name = "idkroo",
        bio = "ui ux guy here uhh idk what to write here, pls star the repo",
        avatar = R.drawable.frame_51_3,
        takesDonations = true,
        socials = listOf(
            DevSocial("Instagram", InstagramUrl),
            DevSocial("Discord", DiscordUrl),
            DevSocial("Threads", ThreadsUrl),
        ).filter { it.url.isNotBlank() },
    ),
    DevPerson(
        name = "Keycii",
        bio = "Hi hello hi",
        avatar = R.drawable.dev_keycii,
    ),
)

/** someone's picture, round, on the mock's light grey */
@Composable
internal fun DevAvatar(person: DevPerson, modifier: Modifier = Modifier) {
    Image(
        painter = rememberResourcePainter(person.avatar),
        contentDescription = person.name,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape).background(Color(0xFFD9D9D9)),
    )
}

private val AvatarSize = 84.dp
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** one person's whole page, its sections rise in one after another the first time it shows */
@Composable
internal fun DevProfilePage(
    person: DevPerson,
    scrollState: ScrollState,
    bannerHeight: Dp,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(900, easing = Emphasized)) }
    // each section starts a little after the one above it
    fun Modifier.rise(step: Int): Modifier = graphicsLayer {
        val p = ((reveal.value - step * 0.08f) / 0.6f).coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * 26.dp.toPx()
    }

    Column(modifier.fillMaxSize().verticalScroll(scrollState)) {
        ProfileHeader(person, scrollState, bannerHeight)

        Column(Modifier.padding(horizontal = DevSideInset)) {
            Text(
                person.name,
                style = devText(40f, tracking = -1.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp).rise(0),
            )
            Column(Modifier.rise(1)) {
                Rule(Modifier.padding(top = 16.dp))
                Text(
                    person.bio,
                    style = devText(15f, FontWeight.SemiBold, DevLinkText, -0.4f),
                    modifier = Modifier.padding(vertical = 22.dp),
                )
                Rule()
            }

            if (person.takesDonations || person.socials.isNotEmpty()) {
                Column(Modifier.rise(2).padding(top = 22.dp)) {
                    if (person.takesDonations) {
                        val popup = LocalZoomOutOverlayState.current
                        SectionTitle("Donate")
                        DevLinkRow("Donate me!") { popup.showDonateSheet() }
                        Spacer(Modifier.height(14.dp))
                    }
                    if (person.socials.isNotEmpty()) {
                        val context = LocalContext.current
                        SectionTitle("Socials")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            person.socials.forEach { social ->
                                DevLinkRow(social.label) { context.openDevUrl(social.url) }
                            }
                        }
                    }
                    Rule(Modifier.padding(top = 22.dp))
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.rise(3).padding(top = 22.dp),
                content = below,
            )
        }
    }
}

/** the banner is the person's own picture, blurred and dimmed, so every page gets its own colour */
@Composable
private fun ProfileHeader(person: DevPerson, scrollState: ScrollState, bannerHeight: Dp) {
    val context = LocalContext.current
    // blurred once, small and off the main thread, so it costs the same on every phone
    val banner = remember(person.avatar) {
        ImageRequest.Builder(context)
            .data(person.avatar)
            .size(192)
            .transformations(SoftBlurTransformation())
            .build()
    }
    Box(Modifier.fillMaxWidth().height(bannerHeight + AvatarSize / 2)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .graphicsLayer {
                    translationY = scrollState.value * 0.45f
                    alpha = 1f - (scrollState.value / bannerHeight.toPx()).coerceIn(0f, 1f) * 0.6f
                }
                .background(Color(0xFF4A4A4A)),
        ) {
            AsyncImage(
                model = banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = 0.32f))
                        // melts into the black page so the avatar's lower half sits on one colour
                        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black))
                    },
            )
        }
        DevAvatar(
            person,
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = DevSideInset - 2.dp)
                .size(AvatarSize)
                .graphicsLayer {
                    val p = (scrollState.value / bannerHeight.toPx()).coerceIn(0f, 1f)
                    val s = 1f - 0.18f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 1f)
                },
        )
    }
}

@Composable
private fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(DevRule))
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = devText(17f, tracking = -0.6f), modifier = Modifier.padding(bottom = 10.dp))
}

/** a dark row with a grey label as in the mock, it squishes a little under the finger */
@Composable
private fun DevLinkRow(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.96f)
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DevCard)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Text(label, style = devText(15f, FontWeight.SemiBold, DevLinkText, -0.4f), maxLines = 1)
    }
}
