package com.mochyy.mochymusic.ui.screens.settings.importsync

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.rememberAsyncImagePainter
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.YtmSyncKey
import com.mochyy.mochymusic.importer.ResyncStart
import com.mochyy.mochymusic.importer.account.AccountImportState
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.importer.account.WEB_PLAYER_QUERY_HEADER
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.menu.ResyncLinkDialog
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.AccountImportViewModel
import com.mochyy.mochymusic.viewmodels.ImportSyncViewModel
import timber.log.Timber

const val AccountImportRoutePattern = "account_import/{service}?resync={resync}&then={then}"

fun accountImportRoute(service: AccountService) = "account_import/${service.route}"

/** the same sign-in, opened by a playlist's re-sync so it reads just that playlist again */
fun accountResyncRoute(
    service: AccountService,
    playlistId: String,
) = "account_import/${service.route}?resync=$playlistId"

/** what the page's resync argument says for re-sync all */
const val ResyncAllArg = "all"

/** re-sync all through each service's sign-in in turn, services first to last */
fun accountResyncAllRoute(services: List<AccountService>) =
    "account_import/${services.first().route}?resync=$ResyncAllArg&then=${services.drop(1).joinToString(",") { it.route }}"

/** reads musickit's own tokens once the user has signed in on music.apple.com */
internal const val ApplePageProbeJs = """
(function() {
  try {
    if (!window.MusicKit || !MusicKit.getInstance) return null;
    var m = MusicKit.getInstance();
    if (!m) return null;
    return { dev: m.developerToken || null, user: m.musicUserToken || null };
  } catch (e) { return null; }
})();
"""

/** music.apple.com opens signed out, so press its sign in button once per page to bring the form up straight away */
internal const val AppleAutoSignInJs = """
(function() {
  try {
    if (window.__mochySignInPressed) return 'pressed';
    var m = window.MusicKit && MusicKit.getInstance && MusicKit.getInstance();
    if (m && m.isAuthorized) return 'authorized';
    var button = document.querySelector('[data-testid="sign-in-button"]');
    if (!button) return 'waiting';
    window.__mochySignInPressed = true;
    button.click();
    return 'pressed';
  } catch (e) { return 'error'; }
})();
"""

private const val SpotifyPlayerOrigin = "https://open.spotify.com"
private const val SpotifyLibraryUrl = "https://open.spotify.com/collection/tracks"

/** spotify's sign-in cookie, set on .spotify.com once the account is signed in */
private const val SpotifySignedInCookie = "sp_dc"

/** reads the library query names and hashes out of the web player's own script, see docs/notes/settings.md */
private val SpotifyFindQueriesJs = """
(function() {
  try {
    if (window.__mochyQueriesFound) return 'done';
    if (window.__mochyFinding) return 'finding';
    var scanned = window.__mochyScanned = window.__mochyScanned || {};
    var sources = [];
    [].forEach.call(document.scripts, function(s) { if (s.src) sources.push(s.src); });
    (performance.getEntriesByType('resource') || []).forEach(function(e) { sources.push(e.name); });
    sources = sources.filter(function(src, i) {
      return /spotifycdn\.com\/cdn\/build\/web-player\/.*\.js/.test(src) && !scanned[src] && sources.indexOf(src) === i;
    });
    if (!sources.length) return 'waiting';
    window.__mochyFinding = true;
    var found = {};
    Promise.all(sources.map(function(src) {
      scanned[src] = true;
      return fetch(src).then(function(r) { return r.text(); }).then(function(text) {
        var wanted = /"(libraryV\d+|fetchLibraryTracks|fetchPlaylistContents|fetchPlaylist)","query","([0-9a-f]{64})"/g;
        var m;
        while ((m = wanted.exec(text))) found[m[1]] = m[2];
      }).catch(function() {});
    })).then(function() {
      Object.keys(found).forEach(function(name) {
        var query = { name: name, hash: found[name], url: 'https://api-partner.spotify.com/pathfinder/v2/query', post: true, guessed: true };
        window.$BRIDGE_NAME.onHeader('$WEB_PLAYER_QUERY_HEADER', JSON.stringify(query), location.href);
      });
      if (found.fetchLibraryTracks) window.__mochyQueriesFound = true;
      window.__mochyFinding = false;
    });
    return 'finding';
  } catch (e) { return 'error'; }
})();
"""

/** the phone site has no library, so once signed in the view switches to the desktop site, see docs/notes/settings.md */
private fun openSpotifyLibraryWhenSignedIn(view: WebView) {
    if (view.isDesktopSite()) {
        if (view.url?.startsWith(SpotifyPlayerOrigin) == true) view.evaluateJavascript(SpotifyFindQueriesJs, null)
        return
    }
    val host = view.url?.let(Uri::parse)?.host ?: return
    // still on the login, captcha or another site's sign-in page: let it finish first
    if (!host.endsWith("spotify.com") || host == "accounts.spotify.com" || host == "challenge.spotify.com") return
    val cookies = CookieManager.getInstance().getCookie(SpotifyPlayerOrigin).orEmpty()
    if (cookies.split(';').none { it.substringBefore('=').trim() == SpotifySignedInCookie }) return
    Timber.tag("SignInWeb").d("Spotify signed in, opening the desktop library")
    view.useDesktopSite()
    view.loadUrl(SpotifyLibraryUrl)
}

/** everything a sign-in page's periodic tick does for this service */
internal fun AccountService.onSignInTick(
    view: WebView,
    onApplePageProbe: (String?) -> Unit,
    onCookies: (String?) -> Unit,
) {
    when (this) {
        AccountService.SPOTIFY -> {
            openSpotifyLibraryWhenSignedIn(view)
        }

        AccountService.APPLE_MUSIC -> {
            view.evaluateJavascript(AppleAutoSignInJs, null)
            view.evaluateJavascript(ApplePageProbeJs, onApplePageProbe)
            onCookies(CookieManager.getInstance().getCookie("https://music.apple.com"))
        }

        AccountService.TIDAL, AccountService.YOUTUBE_MUSIC -> {
            Unit
        }
    }
}

@Composable
fun AccountImportScreen(
    navController: NavController,
    viewModel: AccountImportViewModel = hiltViewModel(),
    importViewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val service = viewModel.service
    val state by viewModel.state.collectAsState()
    val ready by viewModel.ready.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val resyncStart by viewModel.resyncStart.collectAsState()
    val signedInToYouTube = rememberYouTubeSignedIn()
    val context = LocalContext.current
    var askingForLink by remember { mutableStateOf(false) }

    // a re-sync carries on in the background once its songs are read, the toast at the end says how it went
    LaunchedEffect(resyncStart) {
        if (resyncStart != ResyncStart.Started) return@LaunchedEffect
        val missing = viewModel.missingFromAccount
        val started =
            context.getString(R.string.account_resync_looking, service.label) +
                if (missing.isEmpty()) "" else context.getString(R.string.account_resync_missing, missing.joinToString(", "))
        Toast.makeText(context, started, Toast.LENGTH_LONG).show()
        navController.navigateUp()
        // re-sync all: on to the next service that needs signing in
        if (viewModel.nextServices.isNotEmpty()) navController.navigate(accountResyncAllRoute(viewModel.nextServices))
    }

    // read and ready: on to the same review dialog every import uses
    LaunchedEffect(ready) {
        ready?.let {
            importViewModel.offer(it, service.label)
            viewModel.consumeReady()
        }
    }

    if (service == AccountService.YOUTUBE_MUSIC) {
        LaunchedEffect(signedInToYouTube) {
            if (signedInToYouTube && viewModel.state.value is AccountImportState.NeedsYouTubeSignIn) viewModel.refreshYouTube()
        }
    }

    fun signInAgain() {
        clearWebSession(service.webOrigins)
        viewModel.signInAgain()
    }

    when (val current = state) {
        AccountImportState.SigningIn -> {
            CaptureWebView(
                title = stringResource(R.string.account_import_signin_title, service.label),
                subtitle =
                    if (viewModel.isResync) {
                        stringResource(R.string.account_import_resync_subtitle, service.label)
                    } else {
                        stringResource(R.string.account_import_signin_subtitle, service.label)
                    },
                startUrl = service.loginUrl.orEmpty(),
                playerOrigins = service.playerOrigins,
                desktopSite = service.desktopSite,
                onHeader = viewModel::onHeader,
                onTick = { view -> service.onSignInTick(view, viewModel::onApplePageProbe, viewModel::onCookies) },
                onExit = { navController.navigateUp() },
            )
        }

        else -> {
            SubSettingsScaffold(
                title = service.label,
                onBack = { navController.navigateUp() },
            ) {
                when (current) {
                    AccountImportState.SigningIn -> {
                        Unit
                    }

                    AccountImportState.NeedsYouTubeSignIn -> {
                        ImportInfoCard {
                            Text(
                                stringResource(R.string.account_import_ytm_needs_signin),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        SettingsGroup(
                            style = SettingsGroupStyle.Grouped,
                            items =
                                listOf(
                                    SettingsItem(
                                        title = { Text(stringResource(R.string.yt_sync_sign_in_title)) },
                                        icon = painterResource(R.drawable.login),
                                        iconShape = CircleShape,
                                        onClick = { navController.navigate(YouTubeLoginRoute) },
                                    ),
                                ),
                        )
                    }

                    is AccountImportState.Loading -> {
                        BusyCard(current.label, fraction = null)
                    }

                    is AccountImportState.Reading -> {
                        BusyCard(
                            label =
                                "${current.label} " +
                                    stringResource(
                                        R.string.account_import_reading_count,
                                        current.done + 1,
                                        current.total,
                                    ),
                            fraction = current.done.toFloat() / current.total.coerceAtLeast(1),
                        )
                        Spacer(Modifier.height(16.dp))
                        SettingsGroup(
                            style = SettingsGroupStyle.Grouped,
                            items =
                                listOf(
                                    SettingsItem(
                                        title = { Text(stringResource(R.string.account_import_stop)) },
                                        icon = painterResource(R.drawable.close),
                                        iconShape = CircleShape,
                                        onClick = viewModel::cancelReading,
                                    ),
                                ),
                        )
                    }

                    is AccountImportState.Failed -> {
                        ImportInfoCard {
                            Text(current.message, style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(16.dp))
                        SettingsGroup(
                            style = SettingsGroupStyle.Grouped,
                            items =
                                buildList {
                                    if (!current.signedOut) {
                                        add(
                                            SettingsItem(
                                                title = { Text(stringResource(R.string.account_import_try_again)) },
                                                icon = painterResource(R.drawable.refresh),
                                                iconShape = CircleShape,
                                                onClick = viewModel::retry,
                                            ),
                                        )
                                    }
                                    add(
                                        SettingsItem(
                                            title = { Text(stringResource(R.string.account_import_signin_again)) },
                                            icon = painterResource(R.drawable.login),
                                            iconShape = CircleShape,
                                            onClick = {
                                                if (service == AccountService.YOUTUBE_MUSIC) {
                                                    navController.navigate(YouTubeLoginRoute)
                                                } else {
                                                    signInAgain()
                                                }
                                            },
                                        ),
                                    )
                                },
                        )
                    }

                    is AccountImportState.Choosing -> {
                        if (current.pickingFor != null) {
                            PickForResyncContent(
                                service = service,
                                state = current,
                                name = current.pickingFor,
                                onPick = viewModel::pickForResync,
                                onPasteLink = { askingForLink = true },
                            )
                        } else {
                            ChoosingContent(
                                service = service,
                                state = current,
                                onToggle = viewModel::toggle,
                                onSelectAll = viewModel::selectAll,
                                onSelectNone = viewModel::selectNone,
                                onImport = viewModel::readSelected,
                                onSwitchAccount = {
                                    if (service == AccountService.YOUTUBE_MUSIC) navController.navigate(YouTubeSyncRoute) else signInAgain()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    notice?.let { message ->
        DefaultDialog(
            onDismiss = viewModel::clearNotice,
            buttons = { TextButton(onClick = viewModel::clearNotice) { Text(stringResource(R.string.import_sync_btn_ok)) } },
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }

    (resyncStart as? ResyncStart.Failed)?.let { failed ->
        DefaultDialog(
            onDismiss = viewModel::clearResyncStart,
            buttons = { TextButton(onClick = viewModel::clearResyncStart) { Text(stringResource(R.string.import_sync_btn_ok)) } },
        ) {
            Text(failed.message, style = MaterialTheme.typography.bodyLarge)
        }
    }

    if (askingForLink) {
        ResyncLinkDialog(
            sourceLabel = service.label,
            onDismiss = { askingForLink = false },
            onDone = viewModel::resyncFromLink,
        )
    }

    PendingImportHost(viewModel = importViewModel, navController = navController)
}

/** a re-sync that couldn't find its playlist by name, the user says which one in the account it is */
@Composable
private fun PickForResyncContent(
    service: AccountService,
    state: AccountImportState.Choosing,
    name: String,
    onPick: (String) -> Unit,
    onPasteLink: () -> Unit,
) {
    ImportInfoCard {
        Text(
            stringResource(R.string.account_import_pick_resync_prompt, name, service.label),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    Spacer(Modifier.height(16.dp))
    SettingsGroup(
        style = SettingsGroupStyle.Grouped,
        items =
            listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.account_import_not_in_account)) },
                    descriptionText = stringResource(R.string.account_import_paste_link_instead),
                    icon = painterResource(R.drawable.link),
                    iconShape = CircleShape,
                    onClick = onPasteLink,
                ),
            ),
    )
    if (state.playlists.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    SettingsGroup(
        title = stringResource(R.string.account_import_in_library_count, state.playlists.size),
        style = SettingsGroupStyle.Grouped,
        items =
            state.playlists.map { playlist ->
                SettingsItem(
                    title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    descriptionText =
                        listOfNotNull(
                            playlist.trackCount?.let {
                                if (it ==
                                    1
                                ) {
                                    stringResource(R.string.account_import_song_single)
                                } else {
                                    stringResource(R.string.account_import_songs_multiple, it)
                                }
                            },
                            playlist.subtitle,
                        ).joinToString(" · ").ifEmpty { null },
                    icon = playlist.coverUrl?.let { rememberAsyncImagePainter(it) } ?: painterResource(R.drawable.playlist_play),
                    tintIcon = playlist.coverUrl == null,
                    iconShape = CircleShape,
                    onClick = { onPick(playlist.id) },
                )
            },
    )
}

@Composable
private fun BusyCard(
    label: String,
    fraction: Float?,
) {
    ImportInfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (fraction != null) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ChoosingContent(
    service: AccountService,
    state: AccountImportState.Choosing,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onImport: () -> Unit,
    onSwitchAccount: () -> Unit,
) {
    val autoSync by rememberPreference(YtmSyncKey, true)
    val selectedCount = state.selected.size
    val allSelected = selectedCount == state.playlists.size && selectedCount > 0

    SettingsGroup(
        style = SettingsGroupStyle.Grouped,
        items =
            listOf(
                SettingsItem(
                    title = {
                        Text(
                            state.accountName?.let { stringResource(R.string.yt_login_signed_in_as, it) }
                                ?: stringResource(R.string.account_import_signed_in_service, service.label),
                        )
                    },
                    descriptionText =
                        if (service ==
                            AccountService.YOUTUBE_MUSIC
                        ) {
                            stringResource(R.string.account_import_manage_on_sync)
                        } else {
                            stringResource(R.string.account_import_use_different)
                        },
                    icon = painterResource(R.drawable.account),
                    iconShape = CircleShape,
                    onClick = onSwitchAccount,
                ),
                SettingsItem(
                    title = {
                        Text(
                            when (selectedCount) {
                                0 -> stringResource(R.string.account_import_choose_what)
                                1 -> stringResource(R.string.account_import_btn_single)
                                else -> stringResource(R.string.account_import_btn_multiple, selectedCount)
                            },
                        )
                    },
                    descriptionText =
                        if (selectedCount ==
                            0
                        ) {
                            stringResource(R.string.account_import_tick_below)
                        } else {
                            stringResource(R.string.account_import_next_add_yt)
                        },
                    icon = painterResource(R.drawable.playlist_add),
                    iconShape = CircleShape,
                    enabled = selectedCount > 0,
                    onClick = onImport,
                ),
                SettingsItem(
                    title = {
                        Text(
                            if (allSelected) {
                                stringResource(
                                    R.string.account_import_select_none,
                                )
                            } else {
                                stringResource(R.string.account_import_select_all)
                            },
                        )
                    },
                    icon = painterResource(R.drawable.check),
                    iconShape = CircleShape,
                    onClick = if (allSelected) onSelectNone else onSelectAll,
                ),
            ),
    )

    if (service == AccountService.YOUTUBE_MUSIC && autoSync) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.account_import_ytm_sync_on_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(16.dp))

    if (state.playlists.isEmpty()) {
        ImportInfoCard {
            Text(stringResource(R.string.account_import_no_playlists), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    SettingsGroup(
        title = stringResource(R.string.account_import_in_library_count, state.playlists.size),
        style = SettingsGroupStyle.Grouped,
        items =
            state.playlists.map { playlist ->
                val checked = playlist.id in state.selected
                SettingsItem(
                    title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    descriptionText =
                        listOfNotNull(
                            playlist.trackCount?.let {
                                if (it ==
                                    1
                                ) {
                                    stringResource(R.string.account_import_song_single)
                                } else {
                                    stringResource(R.string.account_import_songs_multiple, it)
                                }
                            },
                            playlist.subtitle,
                        ).joinToString(" · ").ifEmpty { null },
                    // the playlist's own cover from the service, when it has one
                    icon =
                        playlist.coverUrl?.let { rememberAsyncImagePainter(it) }
                            ?: painterResource(if (playlist.isLiked) R.drawable.favorite else R.drawable.playlist_play),
                    tintIcon = playlist.coverUrl == null,
                    iconShape = CircleShape,
                    onClick = { onToggle(playlist.id) },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle(playlist.id) }) },
                )
            },
    )
}
