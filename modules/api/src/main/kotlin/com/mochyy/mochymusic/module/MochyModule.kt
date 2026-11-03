// what mochy and its modules agree on, kept free of android so a module app loads at runtime; change a shape and bump the version (docs/notes/modules.md)

package com.mochyy.mochymusic.module

import java.io.File

const val MODULE_API_VERSION = 3

data class ModuleInfo(
    /** stable, lowercase: "applemusic" */
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val apiVersion: Int = MODULE_API_VERSION,
    /** where the module's own code lives, for the player's "star on github"; null when it isn't public */
    val sourceUrl: String? = null,
    /** who made it, shown on its page */
    val author: String? = null,
)

/** what a module brings to mochy, one module can bring several */
enum class ModuleFeature { AudioSource, Lyrics, Look }

/** What Mochy gives a module. */
interface ModuleHost {
    /** small key-value storage private to this module: tokens, choices */
    val storage: ModuleStorage

    /** HTTP through Mochy's own client, so its proxy, DNS and IP-version settings apply. Blocking. */
    fun http(request: HttpRequest): HttpResponse

    fun log(message: String)

    /** a folder only this module uses, for things too big for [storage]: downloaded scripts, packs */
    val folder: File
}

interface ModuleStorage {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
)

// headers come with lowercase names, read them through header()
class HttpResponse(val code: Int, val body: ByteArray, val headers: Map<String, String> = emptyMap()) {
    val ok: Boolean get() = code in 200..299
    val text: String get() = body.decodeToString()

    fun header(name: String): String? = headers[name.lowercase()]
}

/** The track Mochy wants to play, as Mochy knows it. */
data class TrackQuery(
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
)

/**
 * How a module's account is signed into: Mochy opens [startUrl] in its sign-in browser and
 * passes every request header the service's own web page sends to [MochyModule.onSignInHeader].
 */
data class SignInSpec(
    val startUrl: String,
    /** the sites the sign-in may pass through */
    val origins: List<String>,
    /** the site whose requests carry the session (headers are read only there) */
    val sessionOrigins: Set<String>,
    val desktopSite: Boolean,
    val title: String,
    val subtitle: String,
)

sealed interface AccountState {
    data object SignedOut : AccountState
    data class SignedIn(val label: String) : AccountState
    data class Expired(val reason: String) : AccountState
}

/** One of a choice's options, best or most usual first. */
data class StreamOption(val id: String, val label: String, val description: String)

/**
 * A setting the module offers, shown as a row of chips: the stream quality, the catalog region.
 * The first option is the default; Mochy keeps what was picked and passes it to every call.
 */
data class ModuleChoice(val id: String, val label: String, val options: List<StreamOption>)

enum class StreamFormat { Progressive, Hls, Dash }

data class MatchedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    /** 0..1, how sure the module is it's the same recording */
    val confidence: Float,
)

/** Something the player can open. */
data class SourceStream(
    val url: String,
    val format: StreamFormat,
    val headers: Map<String, String> = emptyMap(),
    val drm: StreamDrm? = null,
    val sourceLabel: String,
    val qualityLabel: String,
    val matched: MatchedTrack,
    /** the audio's codec as listeners know it ("AAC", "HE-AAC", "FLAC"), for the player's format badge */
    val codec: String? = null,
    /**
     * the audio's sample rate, when the module knows it (Monochrome reads it from the FLAC's
     * header): the app passes over a stream the phone has no decoder for at that rate
     */
    val sampleRateHz: Int? = null,
)

sealed interface StreamDrm {
    /**
     * Widevine: the device's own CDM decrypts, inside the platform, as on the service's web
     * player. [license] trades a CDM challenge for a license; it's called on the player's DRM
     * thread and may block. [initData] are the stream's PSSH boxes, for checking a license can be
     * had before anything plays.
     */
    class Widevine(val license: LicenseServer, val initData: List<ByteArray>) : StreamDrm
}

fun interface LicenseServer {
    fun acquire(challenge: ByteArray): ByteArray
}

sealed interface ResolveResult {
    data class Found(val stream: SourceStream) : ResolveResult
    /** the service doesn't have this song */
    data class NotFound(val reason: String) : ResolveResult
    /** the service couldn't be asked: signed out, offline, region */
    data class Unavailable(val reason: String) : ResolveResult
}

/** One line of a module's self-test. [ok] null while the step runs. */
data class DiagnosticStep(val name: String, val ok: Boolean?, val detail: String)

/** every module: its name, what it adds, and its account when it needs one */
interface MochyModule {
    val info: ModuleInfo

    /** mochy only asks a module for the things listed here */
    val adds: Set<ModuleFeature>

    /** called once, before anything else */
    fun attach(host: ModuleHost)

    /** null for a module that needs no account */
    val signIn: SignInSpec? get() = null

    fun accountState(): AccountState = AccountState.SignedIn(NO_ACCOUNT_LABEL)

    /** a header the sign-in page sent; true once there's everything needed to sign in */
    fun onSignInHeader(name: String, value: String, url: String): Boolean = false

    /** asks the service whether the saved account still works */
    suspend fun verifyAccount(): AccountState = accountState()

    fun signOut() {}

    val choices: List<ModuleChoice> get() = emptyList()
}

/** what a module that needs no account says about it */
const val NO_ACCOUNT_LABEL = "No account needed"

/** a place songs play from */
interface SourceModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.AudioSource)

    /** [picks]: choice id to option id, for the choices the user changed */
    suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult

    /** the module's own checks, step by step, for the test button; the host adds the CDM part */
    suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult
}

/** a server a module plays from, as its page lists it; the built-in ones can't be removed */
data class ModuleServer(val url: String, val name: String, val builtIn: Boolean)

/** why a link didn't become one of a module's servers */
enum class ServerProblem { NotALink, Unreachable, NotAServer, AlreadyAdded }

/** a module that plays from several servers, people can add the ones they find */
interface ServerListModule : MochyModule {
    val servers: List<ModuleServer>

    /** checks the link, a server's own or a site that plays from one, and adds what it finds; null once it's in */
    suspend fun addServer(link: String): ServerProblem?

    fun removeServer(url: String)
}

/** one thing people added to a module, as its page lists it */
data class ModuleItem(val id: String, val name: String, val detail: String)

/** why a zip or a link added nothing */
enum class ItemProblem { NotAZip, NotALink, Unreachable, NothingFound, Broken, TooBig }

/** what adding a zip or a link came to */
sealed interface ItemsAdded {
    /** the names of what came in, one already there is updated in place */
    data class Added(val names: List<String>) : ItemsAdded
    data class Problem(val problem: ItemProblem) : ItemsAdded
}

/** a module that holds things people bring as zips or links, as many as they like: scripts, packs */
interface ItemListModule : MochyModule {
    /** what the module calls them, the heading of their list on its page */
    val itemsLabel: String

    /** a line under the list saying what they do, blank for none */
    val itemsAbout: String

    val items: List<ModuleItem>

    /** reads everything it can use in the zip and keeps it; [fileName] is only for its row */
    suspend fun addZip(fileName: String, bytes: ByteArray): ItemsAdded

    /** the same from a link: a zip, or one file */
    suspend fun addLink(link: String): ItemsAdded

    fun removeItem(id: String)
}

/** a lyrics source, it joins the ones mochy already asks while the module is on */
interface LyricsModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Lyrics)

    /** the song's lyrics as lrc ("[01:02.34] a line"), or plain lines when they aren't timed; null when it has none */
    suspend fun lyrics(track: TrackQuery): String?
}

/** a look: values for mochy's look settings, put in place while the module is on and given back when it goes off */
interface LookModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Look)

    /** a [LookSetting] name to its value, anything else is ignored */
    val look: Map<String, String>
}

/** the only settings a look may change, so a module can restyle mochy but never touch accounts, network or playback */
object LookSetting {
    /** a player style's name: FLAGSHIP, CLASSIC, TURNTABLE, VIDEO, IPOD, POSTER, TIMELINE, LYRICS */
    const val PLAYER_STYLE = "playerStyle"
    /** MOCHYY, LISTEN_NOW or HOP_IN */
    const val HOME_STYLE = "homeStyle"
    /** FLOW, CLASSIC or MINIMAL */
    const val PLAYLIST_STYLE = "playlistStyle"
    /** how round covers are, 0 to 45 */
    const val COVER_ROUNDING = "thumbnailCornerRadius"
    /** MOTION, LEGACY, BORING or PLAIN */
    const val LYRICS_MOTION = "lyricsMotionStyle"
    /** LOW or HIGH */
    const val BLUR_QUALITY = "blurQuality"
    /** glass blur radius in px, 10 to 50 */
    const val BLUR_STRENGTH = "blurStrengthPx"
    /** true or false */
    const val PROGRESSIVE_BLUR = "enableProgressiveBlur"
    /** true or false */
    const val ELEMENT_BLUR = "elementBlur"
    /** true or false */
    const val PURE_BLACK = "pureBlack"
    /** the app's accent as "#RRGGBB"; a look that sets it also turns the cover-coloured theme off unless it says otherwise */
    const val ACCENT_COLOR = "accentColor"
    /** true or false: the app's colours following the playing cover */
    const val DYNAMIC_THEME = "dynamicTheme"
    /** the classic player's background: COVER_GRADIENT, SOLID, DARK_GRADIENT, APPLE_MUSIC, AMBIENT */
    const val PLAYER_BACKGROUND = "playerBackground"
    /** the flagship's background, the same names */
    const val FLAGSHIP_BACKGROUND = "flagshipBackground"
    /** SILVER, BLACK, U2 or TRUE_DARK */
    const val IPOD_THEME = "ipodTheme"
    /** THEME, WHITE, BLACK or RED */
    const val IPOD_WHEEL = "ipodWheelColor"
}
