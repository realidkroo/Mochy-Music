// PlaylistResyncMenu.kt
//
// "Re-sync with Spotify" in an imported playlist's menus. It reads the playlist again on the
// service it came from and adds the songs that are new there. Most start straight away; one that
// came from an account opens that service's sign-in page first, and an old import that never
// saved where it came from asks for its link.

package com.mochyy.mochymusic.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.di.PlaylistResyncEntryPoint
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.importer.PlaylistResync
import com.mochyy.mochymusic.importer.ResyncAllStart
import com.mochyy.mochymusic.importer.ResyncStart
import com.mochyy.mochymusic.importer.resyncSourceOf
import com.mochyy.mochymusic.ui.component.Material3MenuItemData
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import com.mochyy.mochymusic.ui.screens.settings.importsync.accountResyncAllRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.accountResyncRoute
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One playlist's Re-sync, for its menu. [source] is null while it isn't known to have come from a service. */
class PlaylistResyncState internal constructor(
    private val playlistId: String,
    private val resync: PlaylistResync,
    private val scope: CoroutineScope,
    private val context: Context,
    private val navController: NavController?,
    private val onDismiss: () -> Unit,
) {
    /** Set on every composition from the database, which recomposes the menu when it changes. */
    var source: ImportSource? = null
        internal set

    /** An old import with nothing saved about where it lives: its link is asked for. */
    var askingForLink by mutableStateOf(false)
        private set

    fun start() {
        scope.launch { handle(resync.start(playlistId)) }
    }

    fun startFromLink(link: String) {
        askingForLink = false
        scope.launch { handle(resync.startFromLink(playlistId, link)) }
    }

    fun cancelLink() {
        askingForLink = false
    }

    /** "Re-sync with Spotify"; null for a playlist made here. */
    val title: String? get() = source?.let { "Re-sync with ${it.label}" }

    /** The menu row; null for a playlist made here. */
    fun menuItem(): Material3MenuItemData? {
        val from = source ?: return null
        return Material3MenuItemData(
            title = { Text("Re-sync with ${from.label}") },
            description = { Text("Add the songs that are new on ${from.label}") },
            icon = { Icon(painter = painterResource(R.drawable.sync), contentDescription = null) },
            onClick = ::start,
        )
    }

    private fun handle(start: ResyncStart) {
        when (start) {
            ResyncStart.Started -> {
                toast("Looking for new songs on ${source?.label ?: "the service"}…")
                onDismiss()
            }
            is ResyncStart.ThroughAccount -> {
                onDismiss()
                navController?.navigate(accountResyncRoute(start.service, playlistId))
                    ?: toast("Open the playlist and re-sync it from its menu there.")
            }
            ResyncStart.NeedsLink -> askingForLink = true
            is ResyncStart.Failed -> toast(start.message)
        }
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

/** Also shows the link dialog when a Re-sync asks for one, over the menu. */
@Composable
fun rememberPlaylistResync(
    playlistId: String,
    browseId: String?,
    navController: NavController?,
    onDismiss: () -> Unit,
): PlaylistResyncState {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val imported by remember(database, playlistId) { database.importedPlaylist(playlistId) }.collectAsState(initial = null)
    val state = remember(playlistId) {
        val resync = EntryPointAccessors.fromApplication(context.applicationContext, PlaylistResyncEntryPoint::class.java)
            .playlistResync()
        PlaylistResyncState(playlistId, resync, scope, context, navController, onDismiss)
    }
    state.source = resyncSourceOf(imported, browseId)
    if (state.askingForLink) {
        ResyncLinkDialog(sourceLabel = state.source?.label ?: "music service", onDismiss = state::cancelLink, onDone = state::startFromLink)
    }
    return state
}

/**
 * Re-sync all, for the Library's Manage imports: starts every imported playlist it can read
 * straight away, then opens the sign-in page of each service that needs one, one after another.
 */
@Composable
fun rememberResyncAll(navController: NavController): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resync = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, PlaylistResyncEntryPoint::class.java).playlistResync()
    }
    return remember(navController, resync) {
        {
            scope.launch {
                val start = resync.startAll()
                resyncAllMessage(start)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                if (!start.busy && start.throughAccount.isNotEmpty()) {
                    navController.navigate(accountResyncAllRoute(start.throughAccount))
                }
            }
        }
    }
}

private fun resyncAllMessage(start: ResyncAllStart): String? {
    if (start.busy) return "An import is still running. Try again once it's done."
    if (start.started == 0 && start.throughAccount.isEmpty() && start.needLink == 0) {
        return "None of your playlists came from a music service, so there's nothing to re-sync."
    }
    val started = when (start.started) {
        0 -> null
        1 -> "Looking for new songs in 1 playlist…"
        else -> "Looking for new songs in ${start.started} playlists…"
    }
    val needLink = when (start.needLink) {
        0 -> null
        1 -> "1 older import needs its link, so re-sync it from its own menu."
        else -> "${start.needLink} older imports need their link, so re-sync them from their own menus."
    }
    return listOfNotNull(started, needLink).joinToString(" ").ifEmpty { null }
}

/** Asks for a playlist's link, for a Re-sync that can't find it any other way. */
@Composable
fun ResyncLinkDialog(sourceLabel: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.link), contentDescription = null) },
        title = { Text("Paste its $sourceLabel link") },
        placeholder = { Text("https://…") },
        keyboardType = KeyboardType.Uri,
        onDone = onDone,
        onDismiss = onDismiss,
        extraContent = {
            Text(
                "This playlist was imported before Mochy saved where it came from. Its link is how Re-sync finds it again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        },
    )
}
