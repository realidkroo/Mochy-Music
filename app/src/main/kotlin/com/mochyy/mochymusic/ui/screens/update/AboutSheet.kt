// about app, upstream credits and license sheets pushed from the version sheet or the mochy page

package com.mochyy.mochymusic.ui.screens.update

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.GithubOwner
import com.mochyy.mochymusic.core.updater.GithubRepo
import com.mochyy.mochymusic.core.updater.GithubRepoUrl
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.screens.donate.showSupportSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

@Immutable
private data class LeadDevEntry(
    val name: String,
    val githubUser: String,
    val devSpaceIndex: Int,
    @DrawableRes val fallbackAvatar: Int,
)

@Immutable
private data class CreditEntry(
    val name: String,
    val subtitle: String,
    val githubUser: String,
    val url: String,
)

@Immutable
private data class LicenseEntry(
    val tag: String,
    val title: String,
    val subtitle: String,
    val url: String,
)

@Immutable
private data class ContributorEntry(
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
)

private val LeadDevs = listOf(
    LeadDevEntry(
        name = "Roo",
        githubUser = "realidkroo",
        devSpaceIndex = 0,
        fallbackAvatar = R.drawable.frame_51_3,
    ),
    LeadDevEntry(
        name = "key",
        githubUser = "realkeichi",
        devSpaceIndex = 1,
        fallbackAvatar = R.drawable.dev_keycii,
    ),
)

private val BaseCredits = listOf(
    CreditEntry(
        name = "vivizzz007",
        subtitle = "Vivimusic dev, The base of this app",
        githubUser = "vivizzz007",
        url = "https://github.com/vivizzz007/vivi-music",
    ),
    CreditEntry(
        name = "Mostafa Alagamy",
        subtitle = "Metrolist dev, The base of vivmusic too",
        githubUser = "mostafaalagamy",
        url = "https://github.com/mostafaalagamy/Metrolist",
    ),
)

private val MoreCredits = listOf(
    CreditEntry(
        name = "maxrave-dev",
        subtitle = "SimpMusic · lyrics API provider",
        githubUser = "maxrave-dev",
        url = "https://github.com/maxrave-dev/SimpMusic",
    ),
    CreditEntry(
        name = "Rukamori",
        subtitle = "ArchiveTune · design & architecture inspiration",
        githubUser = "rukamori",
        url = "https://github.com/rukamori/ArchiveTune",
    ),
    CreditEntry(
        name = "fast4x",
        subtitle = "RiMusic · YouTube Music InnerTube service wrapper",
        githubUser = "fast4x",
        url = "https://github.com/fast4x/RiMusic",
    ),
    CreditEntry(
        name = "tranxuanthang",
        subtitle = "LRCLIB · synchronized LRC lyrics provider",
        githubUser = "tranxuanthang",
        url = "https://github.com/tranxuanthang/lrclib",
    ),
    CreditEntry(
        name = "monochrome-music",
        subtitle = "Monochrome Music · lossless FLAC streams",
        githubUser = "monochrome-music",
        url = "https://github.com/monochrome-music/monochrome",
    ),
    CreditEntry(
        name = "Jaakko Pasanen",
        subtitle = "AutoEq · headphone equalizer database",
        githubUser = "jaakkopasanen",
        url = "https://github.com/jaakkopasanen/AutoEq",
    ),
    CreditEntry(
        name = "Ibra Al Tabian",
        subtitle = "LyricsPlus · open source lyrics server",
        githubUser = "ibratabian17",
        url = "https://github.com/ibratabian17/LyricsPlus",
    ),
    CreditEntry(
        name = "Boidushya",
        subtitle = "Better Lyrics API · synced syllable lyrics",
        githubUser = "boidushya",
        url = "https://github.com/better-lyrics/api",
    ),
)

private val Licenses = listOf(
    LicenseEntry(
        tag = "GPL-3.0",
        title = "Mochy Music · GNU General Public License v3.0",
        subtitle = "Free and open source software, source code on GitHub",
        url = "$GithubRepoUrl/blob/main/LICENSE",
    ),
    LicenseEntry(
        tag = "GPL-3.0",
        title = "ViviMusic · GNU General Public License v3.0",
        subtitle = "by vivizzz007 · base project this app was forked from",
        url = "https://github.com/vivizzz007/vivi-music/blob/main/LICENSE",
    ),
    LicenseEntry(
        tag = "GPL-3.0",
        title = "Metrolist · GNU General Public License v3.0",
        subtitle = "by Mostafa Alagamy · base framework",
        url = "https://github.com/mostafaalagamy/Metrolist/blob/main/LICENSE",
    ),
    LicenseEntry(
        tag = "GPL-3.0",
        title = "SimpMusic · GNU General Public License v3.0",
        subtitle = "by maxrave-dev · lyrics provider integration",
        url = "https://github.com/maxrave-dev/SimpMusic/blob/dev/LICENSE",
    ),
    LicenseEntry(
        tag = "GPL-3.0",
        title = "ArchiveTune · GNU General Public License v3.0",
        subtitle = "by Rukamori · design and architectural inspiration",
        url = "https://github.com/rukamori/ArchiveTune/blob/main/LICENSE",
    ),
    LicenseEntry(
        tag = "GPL-3.0",
        title = "RiMusic · GNU General Public License v3.0",
        subtitle = "by fast4x · InnerTube API wrapper",
        url = "https://github.com/fast4x/RiMusic/blob/master/LICENSE",
    ),
    LicenseEntry(
        tag = "MIT",
        title = "LRCLIB · MIT License",
        subtitle = "by tranxuanthang · synchronized lyrics database",
        url = "https://github.com/tranxuanthang/lrclib/blob/main/LICENSE",
    ),
    LicenseEntry(
        tag = "MIT",
        title = "AutoEq · MIT License",
        subtitle = "by Jaakko Pasanen · headphone correction profiles",
        url = "https://github.com/jaakkopasanen/AutoEq/blob/master/LICENSE",
    ),
)

//  this guy promote the app
private val ExtraContributors = listOf(
    ContributorEntry(
        login = "swtstvn",
        avatarUrl = githubAvatarUrl("swtstvn"),
        profileUrl = "https://github.com/swtstvn",
    ),
)

private val FallbackContributors = listOf(
    ContributorEntry(
        login = "realidkroo",
        avatarUrl = githubAvatarUrl("realidkroo"),
        profileUrl = "https://github.com/realidkroo",
    ),
    ContributorEntry(
        login = "realkeichi",
        avatarUrl = githubAvatarUrl("realkeichi"),
        profileUrl = "https://github.com/realkeichi",
    ),
) + ExtraContributors

// kept in memory for the session so reopening the about sheet never hits github twice
@Volatile
private var cachedContributors: List<ContributorEntry>? = null

// github redirects username.png straight to the user's current avatar without counting against the api rate limit
private fun githubAvatarUrl(githubUser: String): String =
    "https://github.com/$githubUser.png?size=160"

private suspend fun fetchContributors(): List<ContributorEntry> {
    cachedContributors?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/$GithubOwner/$GithubRepo/contributors?per_page=30")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "mochy-android")
            }
            try {
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val type = obj.optString("type", "User")
                        if (type.equals("Bot", ignoreCase = true)) continue
                        val login = obj.optString("login").takeIf { it.isNotBlank() } ?: continue
                        if (login.endsWith("[bot]", ignoreCase = true)) continue
                        val avatar = obj.optString("avatar_url").ifBlank { githubAvatarUrl(login) }
                        val html = obj.optString("html_url").ifBlank { "https://github.com/$login" }
                        add(ContributorEntry(login = login, avatarUrl = avatar, profileUrl = html))
                    }
                }.ifEmpty { FallbackContributors }
                    .plus(ExtraContributors)
                    .distinctBy { it.login.lowercase() }
                    .also { cachedContributors = it }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(FallbackContributors)
    }
}

fun PopupSheetState.showAboutSheet(onOpenDevSpace: (Int) -> Unit = {}) {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        AboutSheetContent(popup = popup, onOpenDevSpace = onOpenDevSpace)
    }
}

private fun PopupSheetState.showCreditsSheet() {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        CreditsSheetContent()
    }
}

private fun PopupSheetState.showLicenseSheet() {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        LicenseSheetContent()
    }
}

@Composable
private fun ColumnScope.AboutSheetContent(
    popup: PopupSheetState,
    onOpenDevSpace: (Int) -> Unit,
) {
    val context = LocalContext.current
    val installed = shortVersion(BuildConfig.VERSION_NAME)
    val scrollState = rememberScrollState()
    val ringScrollRangePx = with(LocalDensity.current) { 180.dp.toPx() }
    var contributors by remember { mutableStateOf(cachedContributors ?: FallbackContributors) }

    LaunchedEffect(Unit) {
        contributors = fetchContributors()
    }

    Box(Modifier.fillMaxWidth()) {
        // zero-size layout places the spinning ring behind the top icon without pushing the column down
        VersionRing(
            text = "MOCHY $installed",
            scrollProgress = { scrollState.value / ringScrollRangePx },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = 0,
                            minHeight = 0,
                            maxWidth = Constraints.Infinity,
                            maxHeight = Constraints.Infinity,
                        ),
                    )
                    layout(0, 0) {
                        placeable.place(x = -placeable.width / 2, y = (-166).dp.roundToPx())
                    }
                },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            Spacer(Modifier.height(18.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MenuRowSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mochy_mark),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "About app",
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Mochy Music | $installed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MenuRowSurface)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Mochy Music is a music app but\nkinda cool.",
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("actions")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutPillRow(
                    text = "Support me please :3 \u2192",
                    onClick = { popup.showSupportSheet() },
                )
                AboutPillRow(
                    text = "Mochy Music github \u2192",
                    onClick = { context.openUrl(GithubRepoUrl) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("meet the lead dev")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MenuRowSurface)
                    .padding(vertical = 6.dp),
            ) {
                LeadDevs.forEach { dev ->
                    PersonRow(
                        name = dev.name,
                        subtitle = null,
                        avatarUrl = githubAvatarUrl(dev.githubUser),
                        fallbackRes = dev.fallbackAvatar,
                        showArrow = true,
                        onClick = { onOpenDevSpace(dev.devSpaceIndex) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("we wont exist without these guys")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MenuRowSurface)
                    .padding(vertical = 6.dp),
            ) {
                BaseCredits.forEach { credit ->
                    PersonRow(
                        name = credit.name,
                        subtitle = credit.subtitle,
                        avatarUrl = githubAvatarUrl(credit.githubUser),
                        showArrow = false,
                        onClick = { context.openUrl(credit.url) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            AndMoreRow(
                avatars = remember { MoreCredits.take(3).map { githubAvatarUrl(it.githubUser) } },
                onClick = { popup.showCreditsSheet() },
            )

            Spacer(Modifier.height(18.dp))
            SectionHeader("License")
            Spacer(Modifier.height(8.dp))
            AboutCardButton(
                text = "More about license",
                onClick = { popup.showLicenseSheet() },
            )

            Spacer(Modifier.height(18.dp))
            SectionHeader("Cool contributors")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MenuRowSurface)
                    .padding(vertical = 6.dp),
            ) {
                contributors.forEach { contributor ->
                    PersonRow(
                        name = contributor.login,
                        subtitle = null,
                        avatarUrl = contributor.avatarUrl,
                        showArrow = false,
                        onClick = { context.openUrl(contributor.profileUrl) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColumnScope.CreditsSheetContent() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Credits",
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.4).sp,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MenuRowSurface)
                .padding(vertical = 6.dp),
        ) {
            (BaseCredits + MoreCredits).forEach { credit ->
                PersonRow(
                    name = credit.name,
                    subtitle = credit.subtitle,
                    avatarUrl = githubAvatarUrl(credit.githubUser),
                    showArrow = true,
                    onClick = { context.openUrl(credit.url) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ColumnScope.LicenseSheetContent() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "License",
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.4).sp,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Licenses.forEach { entry ->
                LicenseCard(
                    entry = entry,
                    onClick = { context.openUrl(entry.url) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        color = Color.White.copy(alpha = 0.55f),
    )
}

@Composable
private fun AboutPillRow(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AboutCardButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PersonRow(
    name: String,
    subtitle: String?,
    avatarUrl: String,
    showArrow: Boolean,
    onClick: () -> Unit,
    @DrawableRes fallbackRes: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.98f)
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        GithubAvatar(
            url = avatarUrl,
            size = 44.dp,
            fallbackRes = fallbackRes,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showArrow) {
            Spacer(Modifier.width(10.dp))
            ArrowBadge()
        }
    }
}

@Composable
private fun AndMoreRow(
    avatars: List<String>,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val circleSize = 22.dp
    val step = 13.dp
    val stackWidth = circleSize + step * (avatars.size - 1).coerceAtLeast(0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.width(stackWidth).height(circleSize),
        ) {
            avatars.forEachIndexed { index, url ->
                GithubAvatar(
                    url = url,
                    size = circleSize,
                    modifier = Modifier
                        .offset(x = step * index)
                        .border(1.5.dp, MenuRowSurface, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "And more \u2192",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        )
    }
}

@Composable
private fun LicenseCard(
    entry: LicenseEntry,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.98f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = Color.White.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.subtitle,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.width(12.dp))
        ArrowBadge()
    }
}

@Composable
private fun ArrowBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f)),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun GithubAvatar(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
    @DrawableRes fallbackRes: Int? = null,
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build()
    }
    val fallback = fallbackRes?.let { painterResource(it) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFD9D9D9)),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            placeholder = fallback,
            error = fallback,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
