// PlaylistResync.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ImportedPlaylist
import com.mochyy.mochymusic.extensions.isUserLoggedIn
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.importer.account.ResyncTarget
import com.mochyy.mochymusic.importer.account.YouTubeMusicLibrary
import com.mochyy.mochymusic.importer.account.named
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** the service an imported playlist re-syncs with, null for one made here, from a file or youtube's own (a playlist with a browseId follows youtube through sync, and so does one sync brought in) */
fun resyncSourceOf(imported: ImportedPlaylist?, browseId: String?): ImportSource? {
    if (imported == null) return null
    val source = ImportSource.fromKey(imported.source)?.takeIf { it.isListed } ?: return null
    val followsYouTube = source == ImportSource.YOUTUBE_MUSIC &&
        (browseId != null || imported.sessionId == null && imported.remoteId == null)
    return source.takeUnless { followsYouTube }
}

/** how a re-sync gets going, for the menu that asked for it */
sealed interface ResyncStart {
    /** reading in the background now; a toast says what changed */
    data object Started : ResyncStart

    /** only the signed-in account can read it: open the service's sign-in page in re-sync mode */
    data class ThroughAccount(val service: AccountService) : ResyncStart

    /** nothing says where it lives, so the user pastes its link */
    data object NeedsLink : ResyncStart

    data class Failed(val message: String) : ResyncStart
}

/** how far re-sync all got on its own. busy: an import was running, so nothing started */
data class ResyncAllStart(
    val started: Int,
    val throughAccount: List<AccountService>,
    val needLink: Int,
    val busy: Boolean = false,
)

/** works out how to read an imported playlist again (its link or youtube id, or the service's sign-in page), old imports saved neither so they're found by name, deezer ones ask for a link */
@Singleton
class PlaylistResync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val importService: MusicImportService,
) {
    suspend fun start(playlistId: String): ResyncStart = withContext(Dispatchers.IO) {
        val found = lookUp(playlistId) ?: return@withContext ResyncStart.Failed(NOT_IMPORTED)
        when (val route = routeOf(found)) {
            is Route.Direct -> launch(listOf(route.request))
            is Route.ThroughAccount -> ResyncStart.ThroughAccount(route.service)
            Route.NeedsLink -> ResyncStart.NeedsLink
        }
    }

    /** re-sync all as far as it goes without the user: playlists that can be read straight away start now, the rest (sign-in pages, old imports needing a link) are left for the caller */
    suspend fun startAll(): ResyncAllStart = withContext(Dispatchers.IO) {
        val direct = mutableListOf<ResyncRequest>()
        val accounts = linkedSetOf<AccountService>()
        var needLink = 0
        for (imported in database.importedPlaylists().first()) {
            val found = lookUp(imported.playlistId) ?: continue
            when (val route = routeOf(found)) {
                is Route.Direct -> direct += route.request
                is Route.ThroughAccount -> accounts += route.service
                Route.NeedsLink -> needLink++
            }
        }
        val failed = direct.isNotEmpty() && launch(direct) is ResyncStart.Failed
        ResyncAllStart(started = if (failed) 0 else direct.size, throughAccount = accounts.toList(), needLink = needLink, busy = failed)
    }

    /** with the playlist's link, pasted by the user for an import that didn't save one */
    suspend fun startFromLink(playlistId: String, link: String): ResyncStart = withContext(Dispatchers.IO) {
        val found = lookUp(playlistId) ?: return@withContext ResyncStart.Failed(NOT_IMPORTED)
        val linkSource = LinkImporter.detect(link)?.importSource()
            ?: return@withContext ResyncStart.Failed("Paste a link to the playlist on ${found.source.label}.")
        if (linkSource != found.source) {
            return@withContext ResyncStart.Failed("That's a ${linkSource.label} link, but this playlist came from ${found.source.label}.")
        }
        launch(listOf(request(found) { readLink(link, found.source) }))
    }

    /** with songs the account page has already read, by the playlist they're for */
    suspend fun startWithReads(reads: Map<String, ResyncRead>): ResyncStart = withContext(Dispatchers.IO) {
        val requests = reads.mapNotNull { (playlistId, read) -> lookUp(playlistId)?.let { request(it) { read } } }
        if (requests.isEmpty()) ResyncStart.Failed(NOT_IMPORTED) else launch(requests)
    }

    /** what the account page looks for once the user is signed in */
    suspend fun target(playlistId: String): ResyncTarget? = withContext(Dispatchers.IO) {
        lookUp(playlistId)?.target()
    }

    /** every imported playlist only service's signed-in account can read, for re-sync all */
    suspend fun accountTargets(service: AccountService): List<ResyncTarget> = withContext(Dispatchers.IO) {
        database.importedPlaylists().first()
            .mapNotNull { lookUp(it.playlistId) }
            .filter { (routeOf(it) as? Route.ThroughAccount)?.service == service }
            .map { it.target() }
    }

    private class Found(val imported: ImportedPlaylist, val source: ImportSource, val name: String) {
        fun target() = ResyncTarget(imported.playlistId, name, imported.remoteId)
    }

    private sealed interface Route {
        class Direct(val request: ResyncRequest) : Route
        class ThroughAccount(val service: AccountService) : Route
        data object NeedsLink : Route
    }

    /** how found can be read again: straight from its link or youtube id, or through an account */
    private suspend fun routeOf(found: Found): Route {
        val url = found.imported.remoteUrl
        return when {
            // a link only shows spotify's first 100 songs, so a playlist that long needs the account
            found.source == ImportSource.SPOTIFY && url != null &&
                database.importedPlaylistTrackCount(found.imported.playlistId) >= LinkImporter.SPOTIFY_EMBED_LIMIT ->
                Route.ThroughAccount(AccountService.SPOTIFY)

            url != null -> Route.Direct(request(found) { readLink(url, found.source) })
            found.source == ImportSource.YOUTUBE_MUSIC ->
                Route.Direct(request(found) { readYouTube(found.imported.remoteId, found.name) })
            else -> accountFor(found.source)?.let { Route.ThroughAccount(it) } ?: Route.NeedsLink
        }
    }

    private suspend fun lookUp(playlistId: String): Found? {
        val imported = database.getImportedPlaylist(playlistId) ?: return null
        val playlist = database.playlist(playlistId).first()?.playlist ?: return null
        val source = resyncSourceOf(imported, playlist.browseId) ?: return null
        return Found(imported, source, playlist.name)
    }

    private fun request(found: Found, read: suspend () -> ResyncRead) =
        ResyncRequest(found.imported.playlistId, found.name, found.source, read)

    private fun launch(requests: List<ResyncRequest>): ResyncStart =
        if (importService.startResync(requests)) ResyncStart.Started else ResyncStart.Failed(BUSY)

    private suspend fun readLink(link: String, source: ImportSource): ResyncRead {
        val parsed = LinkImporter.fetch(link)
        val tracks = parsed.playlists.values.firstOrNull().orEmpty()
        val note = if (source == ImportSource.SPOTIFY && tracks.size >= LinkImporter.SPOTIFY_EMBED_LIMIT) {
            "Spotify only shares a playlist's first ${LinkImporter.SPOTIFY_EMBED_LIMIT} songs by link. Re-sync again to sign in and check the rest."
        } else {
            parsed.warnings.firstOrNull()
        }
        return ResyncRead(tracks, parsed.playlistOrigins.values.firstOrNull() ?: PlaylistOrigin(remoteUrl = link), note)
    }

    /** by its youtube id, or for an account import that saved none, by its name in the account */
    private suspend fun readYouTube(remoteId: String?, name: String): ResyncRead {
        if (remoteId != null) {
            val parsed = if (remoteId.startsWith("MPREb_")) {
                LinkImporter.fetch("https://music.youtube.com/browse/$remoteId")
            } else {
                LinkImporter.fetchYouTubePlaylist(remoteId)
            }
            val author = parsed.playlistOrigins.values.firstOrNull()?.author
            return ResyncRead(parsed.playlists.values.firstOrNull().orEmpty(), PlaylistOrigin(remoteId = remoteId, author = author))
        }
        if (!context.isUserLoggedIn()) throw LinkImportException("Sign in to YouTube first, then try again.")
        val library = YouTubeMusicLibrary(accountName = null)
        val remote = library.playlists().filterNot { it.isLiked }.named(name)
            ?: throw LinkImportException("Couldn't find \"$name\" in your YouTube Music playlists.")
        return ResyncRead(library.tracks(remote), PlaylistOrigin(remoteId = remote.id))
    }

    private fun accountFor(source: ImportSource): AccountService? = when (source) {
        ImportSource.SPOTIFY -> AccountService.SPOTIFY
        ImportSource.APPLE_MUSIC -> AccountService.APPLE_MUSIC
        ImportSource.TIDAL -> AccountService.TIDAL
        else -> null
    }

    private fun LinkSource.importSource(): ImportSource = when (this) {
        LinkSource.SPOTIFY -> ImportSource.SPOTIFY
        LinkSource.APPLE_MUSIC -> ImportSource.APPLE_MUSIC
        LinkSource.DEEZER -> ImportSource.DEEZER
        LinkSource.YOUTUBE -> ImportSource.YOUTUBE_MUSIC
        LinkSource.SOUNDCLOUD -> ImportSource.SOUNDCLOUD
    }

    private companion object {
        const val NOT_IMPORTED = "This playlist didn't come from a music service, so there's nothing to re-sync."
        const val BUSY = "An import is still running. Try again once it's done."
    }
}
