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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.screens.donate.showSupportSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import com.mochyy.mochymusic.ui.component.sheetHandleRoom

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
    val assetPath: String? = null,
)

private const val Gpl3Asset = "licenses/gpl-3.0.txt"
private const val Gpl2Asset = "licenses/gpl-2.0.txt"
private const val Lgpl21Asset = "licenses/lgpl-2.1.txt"
private const val Ofl11Asset = "licenses/ofl-1.1.txt"
private const val ThirdPartyNoticesAsset = "licenses/third_party_notices.txt"
private val LicenseParagraphSplit = Regex("\\r?\\n\\r?\\n")

@Immutable
private data class ContributorEntry(
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
)

private val LeadDevs =
    listOf(
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

private val BaseCredits =
    listOf(
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

private val MoreCredits =
    listOf(
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
        CreditEntry(
            name = "James Fung",
            subtitle = "JamesDSP · Liveprog's EEL2 engine and the Progenitor reverb",
            githubUser = "james34602",
            url = "https://github.com/james34602/JamesDSPManager",
        ),
        CreditEntry(
            name = "Tim Schneeberger",
            subtitle = "JDSP4Linux & RootlessJamesDSP · Liveprog scripts and knobs",
            githubUser = "ThePBone",
            url = "https://github.com/Audio4Linux/JDSP4Linux",
        ),
        CreditEntry(
            name = "tryptz",
            subtitle = "Tryptify · mixer, FX chain, decay graph & USB DAC inspiration",
            githubUser = "tryptz",
            url = "https://github.com/tryptz/Tryptify",
        ),
    )

private val Licenses =
    listOf(
        LicenseEntry(
            tag = "GPL-3.0 · Offline",
            title = "Mochy Music · GNU General Public License v3.0",
            subtitle = "Free and open source software, full license bundled offline",
            url = "$GithubRepoUrl/blob/main/LICENSE",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "Offline",
            title = "Third-party notices & source links",
            subtitle = "Bundled copyrights, MIT/ISC/BSD notices and upstream repos",
            url = "$GithubRepoUrl/blob/main/app/src/main/assets/licenses/third_party_notices.txt",
            assetPath = ThirdPartyNoticesAsset,
        ),
        LicenseEntry(
            tag = "GPL-3.0",
            title = "ViviMusic · GNU General Public License v3.0",
            subtitle = "by vivizzz007 · base project this app was forked from",
            url = "https://github.com/vivizzz007/vivi-music/blob/main/LICENSE",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "GPL-3.0",
            title = "Metrolist · GNU General Public License v3.0",
            subtitle = "by Mostafa Alagamy · base framework",
            url = "https://github.com/mostafaalagamy/Metrolist/blob/main/LICENSE",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "GPL-3.0",
            title = "SimpMusic · GNU General Public License v3.0",
            subtitle = "by maxrave-dev · lyrics provider integration",
            url = "https://github.com/maxrave-dev/SimpMusic/blob/dev/LICENSE",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "GPL-3.0",
            title = "ArchiveTune · GNU General Public License v3.0",
            subtitle = "by Rukamori · design and architectural inspiration",
            url = "https://github.com/rukamori/ArchiveTune/blob/main/LICENSE",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "GPL-3.0",
            title = "RiMusic · GNU General Public License v3.0",
            subtitle = "by fast4x · InnerTube API wrapper",
            url = "https://github.com/fast4x/RiMusic/blob/master/LICENSE",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "GPL-3.0+",
            title = "NewPipeExtractor · GNU General Public License v3.0",
            subtitle = "by TeamNewPipe · stream extraction library",
            url = "https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/LICENSE",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "GPL-3.0",
            title = "InnertubeX · GNU General Public License v3.0",
            subtitle = "by MetrolistGroup · YouTube stream extraction library",
            url = "https://github.com/MetrolistGroup/innertubex",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "GPL-3.0",
            title = "Jellyfin Media3 FFmpeg · GNU General Public License v3.0",
            subtitle = "by Jellyfin · prebuilt FFmpeg audio decoder and source scripts",
            url = "https://github.com/jellyfin/jellyfin-androidx-media",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "GPL-3.0",
            title = "JDSP4Linux · GNU General Public License v3.0",
            subtitle = "by Tim Schneeberger · the libjamesdsp Liveprog runs from",
            url = "https://github.com/Audio4Linux/JDSP4Linux/blob/master/LICENSE",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "GPL-2.0",
            title = "libjamesdsp · GNU General Public License v2.0",
            subtitle = "by James Fung · EEL2 extensions from JDSP4Linux subtree",
            url = "https://github.com/james34602/JamesDSPManager",
            assetPath = Gpl2Asset,
        ),
        LicenseEntry(
            tag = "zlib",
            title = "EEL2 · zlib-style license",
            subtitle = "by Cockos & Nullsoft · the script language Liveprog runs",
            url = "https://github.com/justinfrankel/WDL",
            assetPath = ThirdPartyNoticesAsset,
        ),
        LicenseEntry(
            tag = "MIT",
            title = "sndfilter · MIT License",
            subtitle = "by Sean Connelly · Progenitor 2 reverb",
            url = "https://github.com/voidqk/sndfilter/blob/master/LICENSE",
            assetPath = ThirdPartyNoticesAsset,
        ),
        LicenseEntry(
            tag = "MIT",
            title = "LRCLIB · MIT License",
            subtitle = "by tranxuanthang · synchronized lyrics database",
            url = "https://github.com/tranxuanthang/lrclib/blob/main/LICENSE",
        ),
        LicenseEntry(
            tag = "GPL-3.0",
            title = "Tryptify · GNU General Public License v3.0",
            subtitle = "by tryptz · mixer, FX chain, decay graph & USB DAC inspiration",
            url = "https://github.com/tryptz/Tryptify/blob/main/LICENSE",
            assetPath = Gpl3Asset,
        ),
        LicenseEntry(
            tag = "LGPL-2.1+",
            title = "libusb · GNU Lesser General Public License v2.1",
            subtitle = "by libusb · optional USB DAC transport (conveyed under GPL-3.0)",
            url = "https://github.com/libusb/libusb/blob/master/COPYING",
            assetPath = Lgpl21Asset,
        ),
        LicenseEntry(
            tag = "Unlicense / MIT / ISC",
            title = "yt-dlp/ejs, astring & meriyah",
            subtitle = "bundled YouTube JS challenge solver libraries",
            url = "https://github.com/yt-dlp/ejs",
            assetPath = ThirdPartyNoticesAsset,
        ),
        LicenseEntry(
            tag = "OFL-1.1",
            title = "Inter & BBH Bartle · SIL Open Font License 1.1",
            subtitle = "bundled app fonts by The Inter Project & Studio DRAMA",
            url = "https://openfontlicense.org",
            assetPath = Ofl11Asset,
        ),
        LicenseEntry(
            tag = "Attribution",
            title = "MIT KEMAR HRTF · MIT Media Laboratory",
            subtitle = "by Bill Gardner & Keith Martin · 3D Sims ear measurements",
            url = "https://sound.media.mit.edu/resources/KEMAR.html",
            assetPath = ThirdPartyNoticesAsset,
        ),
        LicenseEntry(
            tag = "MIT",
            title = "AutoEq · MIT License",
            subtitle = "by Jaakko Pasanen · headphone correction profiles",
            url = "https://github.com/jaakkopasanen/AutoEq/blob/master/LICENSE",
        ),
    )

//  this guy promote the app
private val ExtraContributors =
    listOf(
        ContributorEntry(
            login = "swtstvn",
            avatarUrl = githubAvatarUrl("swtstvn"),
            profileUrl = "https://github.com/swtstvn",
        ),
    )

private val FallbackContributors =
    listOf(
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
private fun githubAvatarUrl(githubUser: String): String = "https://github.com/$githubUser.png?size=160"

private suspend fun fetchContributors(): List<ContributorEntry> {
    cachedContributors?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/$GithubOwner/$GithubRepo/contributors?per_page=30")
            val conn =
                (url.openConnection() as HttpURLConnection).apply {
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
    show(buttonBar = { SheetPrimaryButton(text = stringResource(R.string.done), onClick = popup::dismiss) }) {
        AboutSheetContent(popup = popup, onOpenDevSpace = onOpenDevSpace)
    }
}

private fun PopupSheetState.showCreditsSheet() {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = stringResource(R.string.done), onClick = popup::dismiss) }) {
        CreditsSheetContent()
    }
}

private fun PopupSheetState.showLicenseSheet() {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = stringResource(R.string.done), onClick = popup::dismiss) }) {
        LicenseSheetContent(popup = popup)
    }
}

private fun PopupSheetState.showLicenseTextSheet(
    tag: String,
    title: String,
    assetPath: String,
    url: String? = null,
) {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = stringResource(R.string.done), onClick = popup::dismiss) }) {
        LicenseTextSheetContent(tag = tag, title = title, assetPath = assetPath, url = url)
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

    // the page starts under the sheet's handle now, so the ring and the first row sit that much lower
    val room = sheetHandleRoom()
    Box(Modifier.fillMaxWidth()) {
        // zero-size layout places the spinning ring behind the top icon without pushing the column down
        VersionRing(
            text = "MOCHY $installed",
            scrollProgress = { scrollState.value / ringScrollRangePx },
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .layout { measurable, constraints ->
                        val placeable =
                            measurable.measure(
                                constraints.copy(
                                    minWidth = 0,
                                    minHeight = 0,
                                    maxWidth = Constraints.Infinity,
                                    maxHeight = Constraints.Infinity,
                                ),
                            )
                        layout(0, 0) {
                            placeable.place(x = -placeable.width / 2, y = (room - 166.dp).roundToPx())
                        }
                    },
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(top = room),
        ) {
            Spacer(Modifier.height(18.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier =
                        Modifier
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
                    text = stringResource(R.string.about_app),
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_mochy_music_version, installed),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MenuRowSurface)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_app_description),
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(stringResource(R.string.about_actions))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutPillRow(
                    text = stringResource(R.string.about_support_me),
                    onClick = { popup.showSupportSheet() },
                )
                AboutPillRow(
                    text = stringResource(R.string.about_github),
                    onClick = { context.openUrl(GithubRepoUrl) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(stringResource(R.string.about_meet_lead_dev))
            Spacer(Modifier.height(8.dp))
            Column(
                modifier =
                    Modifier
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
            SectionHeader(stringResource(R.string.about_we_wont_exist))
            Spacer(Modifier.height(8.dp))
            Column(
                modifier =
                    Modifier
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
            SectionHeader(stringResource(R.string.about_license))
            Spacer(Modifier.height(8.dp))
            val offlineGplTitle = stringResource(R.string.about_license_offline_title)
            val offlineGplEntry =
                LicenseEntry(
                    tag = "GPL-3.0",
                    title = offlineGplTitle,
                    subtitle = stringResource(R.string.about_license_offline_subtitle),
                    url = "$GithubRepoUrl/blob/main/LICENSE",
                    assetPath = Gpl3Asset,
                )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LicenseCard(
                    entry = offlineGplEntry,
                    onClick = {
                        popup.showLicenseTextSheet(
                            tag = offlineGplEntry.tag,
                            title = offlineGplTitle,
                            assetPath = Gpl3Asset,
                            url = offlineGplEntry.url,
                        )
                    },
                )
                AboutCardButton(
                    text = stringResource(R.string.about_more_license),
                    onClick = { popup.showLicenseSheet() },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(stringResource(R.string.about_cool_contributors))
            Spacer(Modifier.height(8.dp))
            Column(
                modifier =
                    Modifier
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
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = sheetHandleRoom()),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_credits),
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.4).sp,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier =
                Modifier
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
private fun ColumnScope.LicenseSheetContent(popup: PopupSheetState) {
    val context = LocalContext.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = sheetHandleRoom()),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_license),
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
                    onClick = {
                        val asset = entry.assetPath
                        if (asset != null) {
                            popup.showLicenseTextSheet(
                                tag = entry.tag,
                                title = entry.title,
                                assetPath = asset,
                                url = entry.url,
                            )
                        } else {
                            context.openUrl(entry.url)
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// reads a bundled license from assets off the main thread and splits paragraphs so long texts scroll smoothly
@Composable
private fun ColumnScope.LicenseTextSheetContent(
    tag: String,
    title: String,
    assetPath: String,
    url: String?,
) {
    val context = LocalContext.current
    var paragraphs by remember(assetPath) { mutableStateOf<List<String>?>(null) }
    var failed by remember(assetPath) { mutableStateOf(false) }

    LaunchedEffect(assetPath) {
        failed = false
        val loaded =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open(assetPath).bufferedReader().use { it.readText() }
                        .trim()
                        .split(LicenseParagraphSplit)
                        .filter { it.isNotBlank() }
                }.getOrNull()
            }
        if (loaded != null) {
            paragraphs = loaded
        } else {
            failed = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        contentPadding = PaddingValues(top = sheetHandleRoom(), bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tag,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    color = Color.White.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = title,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                )
                if (!url.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    AboutPillRow(
                        text = stringResource(R.string.about_license_open_online),
                        onClick = { context.openUrl(url) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        val itemsList = paragraphs
        when {
            failed -> {
                item(key = "failed") {
                    Text(
                        text = stringResource(R.string.about_license_failed),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
            itemsList == null -> {
                item(key = "loading") {
                    Text(
                        text = stringResource(R.string.about_license_loading),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
            else -> {
                itemsIndexed(itemsList, key = { index, _ -> index }) { _, paragraph ->
                    Text(
                        text = paragraph,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
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
private fun AboutPillRow(
    text: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            Modifier
                .pressBounce(interaction, pressedScale = 0.97f)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MenuRowSurface)
                .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
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
private fun AboutCardButton(
    text: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            Modifier
                .pressBounce(interaction, pressedScale = 0.97f)
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MenuRowSurface)
                .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
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
        modifier =
            Modifier
                .pressBounce(interaction, pressedScale = 0.98f)
                .fillMaxWidth()
                .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
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
        modifier =
            Modifier
                .pressBounce(interaction, pressedScale = 0.97f)
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MenuRowSurface)
                .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
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
                    modifier =
                        Modifier
                            .offset(x = step * index)
                            .border(1.5.dp, MenuRowSurface, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.about_and_more),
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
        modifier =
            Modifier
                .pressBounce(interaction, pressedScale = 0.98f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MenuRowSurface)
                .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
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
        modifier =
            Modifier
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
    val request =
        remember(url) {
            ImageRequest
                .Builder(context)
                .data(url)
                .crossfade(true)
                .build()
        }
    val fallback = fallbackRes?.let { painterResource(it) }
    Box(
        modifier =
            modifier
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
