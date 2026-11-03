# Making modules for Mochy

A **module** adds something to Mochy without an app update. One module can add any mix of these:

| It adds | What Mochy does with it |
|---|---|
| **Songs** | Asks it for the song that's playing (in the provider order), and plays the stream itself, so every Audio Lab effect still works. |
| **Lyrics** | Puts it first among the lyrics sources while it's on. |
| **A look** | Restyles Mochy while it's on: its settings (player style, colours, blur, corners…), **a player of its own**, **its own font**, **pictures** and **pages**. Everything goes back the way it was when it's turned off. |

One switch turns a module on or off. Off means gone everywhere: from Audio Quality, the provider order, the download order, the lyrics sources and the look. Every new module starts **off**, so adding one never changes anything until the user turns it on.

---

## Contents

1. [Pick the kind of module you need](#1-pick-the-kind-of-module-you-need)
2. [Your first module in two minutes: a look in a zip](#2-your-first-module-in-two-minutes-a-look-in-a-zip)
3. [manifest.json, field by field](#3-manifestjson-field-by-field)
4. [Changing the interface: looks, players, fonts and pages](#4-changing-the-interface-looks-players-fonts-and-pages)
5. [A module that plays songs or lyrics (a web module)](#5-a-module-that-plays-songs-or-lyrics-a-web-module)
6. [Zip modules](#6-zip-modules)
7. [Putting your module in the catalog](#7-putting-your-module-in-the-catalog)
8. [Testing your module](#8-testing-your-module)
9. [How Mochy uses your module](#9-how-mochy-uses-your-module)
10. [Rules to follow](#10-rules-to-follow)
11. [For Mochy developers: changing the module interface](#11-for-mochy-developers-changing-the-module-interface)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Pick the kind of module you need

| You want to… | Make a… | Needs a server? | Who can make it |
|---|---|---|---|
| Restyle Mochy, design your own player, add pages | **Look**, as a zip (or a `manifest.json` link) | No | Anyone |
| Add songs or lyrics from a service | **Web module**: a link to a small HTTP service | Yes | Anyone |
| Share a web module as one file | **Zip with a `url`** | Yes (the service it names) | Anyone |
| Add songs from a JavaScript script (`searchTracks` + `getTrackStreamUrl`) | **Script** for the **JS Modules** module: a `.js` in a zip or a link, added on its page | No (it reaches its own services) | Anyone |
| Add another Monochrome server | Not a module: Monochrome's page, **Servers** | No | Anyone |
| Ship code that runs inside Mochy | **Zip with its own code**, **built-in module** or **module app** | No | Mochy developers only |

Nothing from a look, a web module or a plain zip runs inside Mochy. They only hand back JSON, stream links and look values, so anyone can make one and nobody has to be trusted with the app. Code that runs inside Mochy (a zip with its own code, built-in modules and module apps) is only loaded when it's signed with Mochy's own key, see [section 6.1](#61-zips-with-their-own-code) and [section 11](#11-for-mochy-developers-changing-the-module-interface). Scripts for JS Modules are JavaScript that runs sandboxed inside that module, with no access to the phone or the app, only to the internet through Mochy ([section 6.2](#62-scripts-for-js-modules)).

---

## 2. Your first module in two minutes: a look in a zip

1. Make a folder called `midnight` and put one file in it, `manifest.json`:

   ```json
   {
     "id": "midnight",
     "name": "Midnight",
     "version": "1.0",
     "description": "Pure black, sharp corners, the turntable player",
     "author": "you",
     "look": {
       "playerStyle": "TURNTABLE",
       "pureBlack": "true",
       "thumbnailCornerRadius": "4"
     }
   }
   ```

2. Zip it:
   - **macOS:** right-click the folder → *Compress "midnight"*
   - **Windows:** right-click the folder → *Send to* → *Compressed (zipped) folder*
   - **Terminal:** `cd midnight && zip midnight.zip manifest.json`

3. Send `midnight.zip` to your phone. In Mochy go to **Settings → Modules → Find more sources → Add from a file → Choose a .zip**.

4. Mochy shows what the module adds. Tap **Add**, open its page and turn it on. Turn it off and your own settings come back.

That's a whole module

---

## 3. manifest.json, field by field

Every module, whether it comes as a link or a zip, describes itself with one `manifest.json`

| Field | Required | What it's for |
|---|---|---|
| `id` | `id` or `name` | A short stable name, like `my-flac`. Mochy turns it into `web.my-flac` (link) or `local.my-flac` (zip), so it can never replace a built-in module. **Keep it the same forever**: adding a module with the same id again updates it instead of adding a second copy. |
| `name` | `id` or `name` | What people see. |
| `version` | No | Shown on the module's page. Bump it when you change anything. |
| `description` | No | One line about what it does. |
| `author` | No | You. |
| `resources` | No | What it does: `"search"` + `"stream"` make it play songs, `"lyrics"` makes it a lyrics source. Plain names or `{ "name": "search" }` both work. |
| `look` | No | Look settings, see [section 4.1](#41-look-settings). |
| `player` | No | A player of your own, see [section 4.2](#42-a-player-of-your-own). |
| `font` | No | Your own font, zip only, see [section 4.3](#43-your-own-font). |
| `pages` | No | Pages of your own, see [section 4.5](#45-pages). |
| `settings` | No | Your service's own settings, see [section 5](#5-a-module-that-plays-songs-or-lyrics-a-web-module). |
| `url` | Zip only | The service a zip module plays from. A link module doesn't need it, since its link *is* the service. |

Two rules worth knowing:

- **A manifest with no `resources` and nothing that changes the look (`look`, `player`, `font`, `pages`) is read as a song source**, because that's what most services of this kind are.
- **A website's own `manifest.json` is turned away.** Websites have one for "install to home screen", with `start_url`, `display`, `short_name` or `icons`. If your manifest has any of those and none of `resources`, `look`, `player`, `font` or `pages`, Mochy says "That's a website, not a module". Don't use those field names in a module manifest.

Fields Mochy doesn't know are ignored, btw

---

## 4. Changing the interface: looks, players, fonts and pages

A look can do five things, in any mix:

| | Where it goes in `manifest.json` | Works from |
|---|---|---|
| [4.1 Change Mochy's settings](#41-look-settings) | `look` | a zip / a link |
| [4.2 Bring a player of its own](#42-a-player-of-your-own) | `player` | a zip / a link |
| [4.3 Bring its own font](#43-your-own-font) | `font` | a zip only |
| [4.4 Use its own pictures](#44-pictures) | paths in `player` and `pages` | a zip, or a link with pictures beside its manifest |
| [4.5 Add pages](#45-pages) | `pages` | a zip / a link |

How looks behave:

- **Only one look is on at a time** Turning a second one on turns the first off and gives its settings back first. A module with *only* pages doesn't count as a look here, so it can stay on next to one
- **The user always wins.** If someone changes a setting by hand while your look is on, they keep their value when your look goes off.
- **It survives restarts.** Mochy remembers what each look replaced, so turning it off after a restart still gives the right settings back
- **Nothing runs.** A look is a description. Mochy draws it with its own parts, so it stays smooth on slow phones and can't break anything

### 4.1 Look settings

A look may change **only** these settings, so a module can restyle Mochy but never reach accounts, the network or playback. Values are written as text (`"true"`, `"12"`, `"FLAGSHIP"`). Keys must be spelled exactly as below, while style names may be any case. A value that's out of range is simply ignored

| Key | Values | What it changes |
|---|---|---|
| `playerStyle` | `FLAGSHIP`, `CLASSIC`, `TURNTABLE`, `VIDEO`, `IPOD`, `POSTER`, `TIMELINE`, `LYRICS`, `BEDSIDE`, `BEDSIDE_TILES`, `MODULE` | The now-playing screen. `MODULE` is your own player from 4.2, and is picked for you when you bring one. |
| `homeStyle` | `MOCHYY`, `LISTEN_NOW`, `HOP_IN` | The home page layout |
| `playlistStyle` | `FLOW`, `CLASSIC`, `MINIMAL` | How playlists and albums are laid out |
| `thumbnailCornerRadius` | `0` to `45` | How round covers are |
| `lyricsMotionStyle` | `MOTION`, `LEGACY`, `BORING`, `PLAIN` | How lyrics animate |
| `accentColor` | `"#RRGGBB"` | The app's accent colour. Setting it also turns off "colours from the cover", unless you set `dynamicTheme` yourself. |
| `dynamicTheme` | `true`, `false` | The app's colours following the playing cover |
| `pureBlack` | `true`, `false` | True black backgrounds |
| `flagshipBackground` | `AMBIENT`, `APPLE_MUSIC`, `COVER_GRADIENT`, `DARK_GRADIENT`, `SOLID` | The Flagship player's background |
| `playerBackground` | the same names | The Classic player's background |
| `ipodTheme` | `SILVER`, `BLACK`, `U2`, `TRUE_DARK` | The iPod player's body |
| `ipodWheelColor` | `THEME`, `WHITE`, `BLACK`, `RED` | The iPod's click wheel |
| `blurQuality` | `LOW`, `HIGH` | Cheaper or nicer glass |
| `blurStrengthPx` | `10` to `50` | How strong the glass blur is |
| `enableProgressiveBlur` | `true`, `false` | Blur that grows towards edges |
| `elementBlur` | `true`, `false` | Blur on moving elements |

A look made only of settings can also be added from a link to its `manifest.json`, for example a raw GitHub link like `https://raw.githubusercontent.com/you/midnight/main/manifest.json`. Want a setting that isn't in this list? It has to be added to Mochy first, see [section 11](#adding-a-look-setting).

### 4.2 A player of your own

`player` describes a whole now-playing screen. While your look is on, it's the first style in the player's style selector (hold the player to open it) under the name you give it. Mochy switches to it when your look turns on, and back to the user's style when it goes off.

Your player gets everything the other styles have, for free: swipe sideways to change song (your cover and words ride along), tap to play or pause, swipe down to close, the ••• menu (lyrics, song info, turning sideways), and its clocks resting while it's hidden.

```json
"player": {
  "name": "Vinyl",
  "background": { "type": "blurredCover", "dim": 0.35 },
  "padding": 28,
  "align": "center",
  "blocks": [
    { "type": "space", "grow": true },
    { "type": "cover", "shape": "circle", "size": 0.78, "spin": true },
    { "type": "space", "size": 28 },
    { "type": "title", "size": 26, "weight": "bold" },
    { "type": "artist" },
    { "type": "space", "size": 20 },
    { "type": "progress", "style": "line" },
    { "type": "space", "size": 12 },
    { "type": "controls", "size": 56 },
    { "type": "space", "grow": true }
  ]
}
```

The blocks are laid out top to bottom, in order. Use `"space"` with `"grow": true` to push things apart, two growing spaces around everything centre it on the screen

**The player itself**

| Field | Values | Default |
|---|---|---|
| `name` | Shown in the style selector | your module's name |
| `background` | see below | `ambient` |
| `padding` | Space at the sides, `0` to `64` | `24` |
| `align` | `start`, `center`, `end`: where blocks and text sit | `center` |
| `blocks` | The list of blocks below. At least one. Up to 40. | |

**Backgrounds** (`"background": { "type": … }`)

| `type` | What it is | Extra fields |
|---|---|---|
| `ambient` | The cover's colours drifting slowly (the Flagship's) | |
| `blurredCover` | The cover, blurred across the screen | |
| `darkGradient` | A dark gradient | |
| `solid` | Plain dark | |
| `tones` | The song's two tones, split where the song is (like Timeline) | |
| `color` | One colour | `color` |
| `gradient` | Top-to-bottom gradient | `colors`: 2 to 6 colours |
| `image` | Your picture, cropped to fill | `image` (see [4.4](#44-pictures)) |

Every background takes `"dim"`, from `0` to `0.9`, a black veil over it so text stays readable.

**Blocks**

| `type` | What it shows | Fields (default) |
|---|---|---|
| `cover` | The song's cover | `shape`: `rounded`, `circle`, `square` (`rounded`) · `corner`: `0`–`80` (`18`) · `size`: share of the width, `0.1`–`1` (`0.8`) · `spin`: turns like a record while playing (`false`) · `frame`: a picture drawn over it, which stays still while the cover spins · `shadow` (`false`) |
| `title` | The song's title | text fields (`24`, `bold`, `ink`) |
| `artist` | The artists | text fields (`16`, `medium`, `inkSoft`) |
| `album` | The album | text fields (`14`, `medium`, `inkSoft`) |
| `text` | Your own words | `text`, plus text fields (`14`, `medium`, `inkSoft`) |
| `image` | Your picture | `image` · `height`: `8`–`600` (`120`) · `width` (full width) · `corner` (`12`) |
| `progress` | Where the song is, tap or drag to seek | `style`: `line`, `thick`, `pill` (`line`) · `color` (`ink`) · `track` (`chip`) · `times`: show the times (`true`) · `seek` (`true`) |
| `controls` | Previous, play/pause, next | `size`: `28`–`96` (`48`) · `style`: `circles` or `plain` (`circles`) · `color`: the circles' fill · `icon`: the icons' colour |
| `lyrics` | The song's synced lyrics, sung through | `height`: `60`–`600` (`160`) · `size`: `14`–`60` (`28`) · `color` (`ink`) |
| `space` | Empty space | `size`: `0`–`300` (`16`) · `grow`: take all the space left (`false`) |
| `row` | Its own `blocks`, side by side | `blocks` · `gap` (`12`). Words, bars and columns share the width; covers, pictures and buttons keep their size. |
| `column` | Its own `blocks`, stacked (handy inside a row) | `blocks` · `gap` (`12`) |

Rows and columns can go three deep. A block Mochy doesn't know is skipped, so a newer layout still opens on an older Mochy.

**Text fields** (for `title`, `artist`, `album`, `text`): `size` (`8`–`72`), `weight` (`thin`, `light`, `normal`, `medium`, `semibold`, `bold`, `black`), `color`, `lines` (`1`–`4`, longer text ends in "…"), `upper` (`true` for CAPITALS), `align` (`start`, `center`, `end`).

**Colours**: a fixed colour like `"#FFFFFF"`, `"#80FFFFFF"` (with transparency), or one that follows the song:

| Name | What it is |
|---|---|
| `ink` | The style's main text colour |
| `inkSoft` | Softer text |
| `played` / `remaining` | The song's two tones, from its cover |
| `chip` | The faint fill behind buttons |
| `cover` | The cover's main colour |
| `accent` | The app's accent colour |

A small example of a row, the cover beside the words:

```json
{ "type": "row", "gap": 14, "blocks": [
  { "type": "cover", "size": 0.22, "corner": 10 },
  { "type": "column", "gap": 2, "blocks": [
    { "type": "title", "size": 18, "align": "start" },
    { "type": "artist", "size": 14, "align": "start" }
  ]}
]}
```

### 4.3 Your own font

```json
"font": { "regular": "fonts/Lora-Regular.ttf", "bold": "fonts/Lora-Bold.ttf" }
```

While your look is on, the whole app uses this font, your player included. `.ttf` and `.otf` work. `bold` is optional: without it, bold text is drawn thicker from the regular one. On Android 7 only the regular file is used. A font has to be a file on the phone, so it only works from a zip. A broken font file is skipped, and the app keeps its own.

### 4.4 Pictures

Anywhere a block or background takes a picture (`image`, `frame`), you can write:

- **A path in your zip**, relative to `manifest.json`: `"images/wood.webp"`. Pictures in `.png`, `.jpg`, `.webp` and `.gif` are unpacked when the zip is added.
- **A link**: `"https://example.com/wood.webp"`.
- **For a module added from a link**, a path is read next to its manifest. With `https://you.github.io/vinyl/manifest.json`, `"images/wood.webp"` is `https://you.github.io/vinyl/images/wood.webp`.

Keep pictures small. A phone screen is about 1080 pixels wide, so a 1080×2400 background is plenty, and `.webp` is the smallest.

### 4.5 Pages

A look can add pages of its own: an about page, a "start here", your favourite playlists. They're listed on your module's page under **Pages** while it's on, built from these blocks:

```json
"pages": [
  {
    "id": "about",
    "title": "Vinyl Night",
    "blocks": [
      { "type": "image", "image": "images/banner.webp", "height": 160 },
      { "type": "heading", "text": "Welcome" },
      { "type": "text", "text": "A warm look for late nights." },
      { "type": "playlist", "id": "PLxxxxxxxx", "title": "Late night picks", "subtitle": "40 songs" },
      { "type": "song", "id": "dQw4w9WgXcQ", "title": "A song to start with", "artist": "Someone" },
      { "type": "button", "text": "Join us on Discord", "link": "https://discord.gg/NTmqVphbRY" }
    ]
  }
]
```

| `type` | What it shows | Fields |
|---|---|---|
| `heading` | A big line | `text` |
| `text` | A paragraph | `text` (up to 2000 characters) |
| `image` | A picture, full width | `image` · `height`: `40`–`400` (`160`) · `corner` (`16`) |
| `button` | A button that opens a web page | `text` · `link` (must start with `https://` or `http://`) |
| `song` | Plays a song | `id` (its YouTube Music video id) · `title` · `artist` |
| `playlist` | Opens a playlist | `id` (its YouTube Music playlist id) · `title` · `subtitle` |
| `album` | Opens an album | `id` (its browse id, `MPREb_…`) · `title` · `subtitle` |
| `artist` | Opens an artist | `id` (its channel id, `UC…`) · `title` |
| `search` | Opens a search | `query` · `title` |
| `divider` | A thin line | |
| `space` | Empty space | `size`: `0`–`120` (`16`) |

A page `id` uses `a`–`z`, `0`–`9`, `-` and `_`. You can have up to 12 pages with 80 blocks each. The ids are in the song's, playlist's or album's YouTube Music link: `music.youtube.com/watch?v=`**`id`**, `music.youtube.com/playlist?list=`**`id`**, `music.youtube.com/browse/`**`id`**, `music.youtube.com/channel/`**`id`**.

### 4.6 Putting it together

```
vinyl-night.zip
└── vinyl-night/
    ├── manifest.json
    ├── fonts/
    │   ├── Lora-Regular.ttf
    │   └── Lora-Bold.ttf
    └── images/
        ├── wood.webp
        ├── rim.png
        └── banner.webp
```

```json
{
  "id": "vinyl-night",
  "name": "Vinyl Night",
  "version": "1.0",
  "description": "A record player in warm wood, with its own font",
  "author": "you",
  "look": { "accentColor": "#E8B86B", "pureBlack": "true" },
  "font": { "regular": "fonts/Lora-Regular.ttf", "bold": "fonts/Lora-Bold.ttf" },
  "player": {
    "name": "Vinyl Night",
    "background": { "type": "image", "image": "images/wood.webp", "dim": 0.35 },
    "blocks": [
      { "type": "space", "grow": true },
      { "type": "cover", "shape": "circle", "size": 0.8, "spin": true, "frame": "images/rim.png" },
      { "type": "space", "size": 28 },
      { "type": "title", "size": 26, "weight": "bold", "color": "#F6E7CF" },
      { "type": "artist", "color": "#C9B79C" },
      { "type": "space", "size": 18 },
      { "type": "progress", "color": "accent", "track": "#33FFFFFF" },
      { "type": "space", "size": 10 },
      { "type": "controls", "size": 58, "color": "#22E8B86B", "icon": "#F6E7CF" },
      { "type": "space", "grow": true }
    ]
  },
  "pages": [
    { "id": "about", "title": "Vinyl Night", "blocks": [
      { "type": "image", "image": "images/banner.webp" },
      { "type": "text", "text": "Made for late nights. Turn it off any time and your own look comes back." }
    ]}
  ]
}
```

---

## 5. A module that plays songs or lyrics (a web module)

A web module is a small HTTP service. Mochy calls it with plain **GET** requests and reads JSON back. People add it by pasting its base link (`https://my-flac.example`) or the link to its manifest (`https://my-flac.example/manifest.json`). Both work.

Every request carries `Accept: application/json` and `User-Agent: Mochy`.

### `GET {base}/manifest.json`

```json
{
  "id": "my-flac",
  "name": "My FLAC",
  "version": "1.2",
  "description": "My own library, lossless",
  "author": "someone",
  "resources": ["search", "stream", "lyrics"],
  "settings": [
    { "key": "quality", "default": "lossless", "options": ["lossless", "hires", "high"] }
  ]
}
```

### `GET {base}/search?q=<artist title>&quality=…`

Mochy searches with the **first artist** and the **title without extras**: no "(feat. …)", "(Official Video)" or "- Remastered". For example, `q=benny%20blanco%20eastside`.

```json
{
  "tracks": [
    {
      "id": "123",
      "title": "Eastside",
      "artist": "benny blanco, Halsey, Khalid",
      "album": "Eastside",
      "duration": 173,
      "isrc": "USUM71809132",
      "streamURL": "https://…/123.flac"
    }
  ]
}
```

- `id` and `title` are required. Give `artist`, `album`, `duration` and `isrc` too: Mochy picks the right recording with its own matcher, and these are how it tells the studio song from a remix, a live take or a sped-up version.
- `duration` is in **seconds** (milliseconds also work, anything over 10000 is read as ms).
- `streamURL` is optional. When it's there, Mochy skips the stream call below.
- Return a handful of results (5–10), best first.

### `GET {base}/stream/{id}?quality=…`

```json
{
  "url": "https://…/123.flac",
  "format": "flac",
  "codec": "flac",
  "quality": "LOSSLESS",
  "sampleRate": 44100,
  "bitDepth": 16
}
```

- **What kind of stream:** `format` or `manifest` saying `hls`/`m3u8` plays it as HLS, `dash`/`mpd` as DASH. Anything else plays as one file. Byte ranges are welcome, since seeking uses them.
- **Codec:** `codec` names it for the format badge: `flac`, `alac`, `aac`, `opus`, `mp3`, `eac3`, `eac3-joc`/`atmos`.
- **Encrypted streams:** a stream marked `"encrypted": true` is passed over, because Mochy has no keys for it.
- **Sample rate:** give `sampleRate` and `bitDepth`. A FLAC the phone can't decode at that rate is passed over instead of playing silence. Without them, Mochy reads "96 kHz" and "24-bit" out of `quality`.
- **Redirects:** `url` may redirect (a 302 or 307 to a CDN is fine).

### Quality and Dolby Atmos

A web module that plays songs gets two choices on its page:

- **Stream:** Lossless (default), Hi-Res, High or Low, sent as `quality=`. If your manifest declares a `quality` setting, Mochy sends the option whose name matches best (`lossless`, `flac`, `hires`, `24bit`, `high`, `320`, `low`…). If it declares none, Mochy sends TIDAL's names: `LOSSLESS`, `HI_RES_LOSSLESS`, `HIGH`, `LOW`.
- **Dolby Atmos:** Stereo (default) or Atmos. Atmos is only asked for when the phone can decode it (`atmos=auto`, or your declared `atmos` setting's "on" option). With Stereo, a declared `atmos` setting is sent as its "off" option.
- In search results, mark an Atmos mix with `"atmos": true` (or `atmos`/`joc`/`ec+3` in `audioMode`, `audioModes`, `audioQuality` or `format`). Mochy then picks the Atmos row or the stereo row to match the user's choice.

Every other declared setting is sent as its `default` on every search and stream call. Mochy has no form for them.

### `GET {base}/lyrics?title=&artist=&album=&duration=`

`artist` is every artist joined with `, `, and `duration` is in seconds. Answer with any of these:

```json
{ "syncedLyrics": "[00:12.34] first line\n[00:15.80] second line" }
```

```json
{ "plainLyrics": "first line\nsecond line" }
```

`lrc` and `lyrics` are read too, and so is a plain-text body. Timed (LRC) lyrics are always preferred.

### A whole web module in 30 lines

A Cloudflare Worker that serves your own files (free, no server to look after):

```js
// worker.js: a tiny Mochy module
const SONGS = [
  { id: "1", title: "My Song", artist: "Me", album: "Demo", duration: 201, url: "https://example.com/my-song.flac" },
];

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/manifest.json") {
      return json({ id: "my-songs", name: "My Songs", version: "1.0", description: "My own FLACs", resources: ["search", "stream"] });
    }
    if (url.pathname === "/search") {
      const words = (url.searchParams.get("q") || "").toLowerCase().split(" ");
      const found = SONGS.filter((s) => words.every((w) => `${s.artist} ${s.title}`.toLowerCase().includes(w)));
      return json({ tracks: found.map(({ url, ...track }) => track) });
    }
    const stream = url.pathname.match(/^\/stream\/(.+)$/);
    if (stream) {
      const song = SONGS.find((s) => s.id === decodeURIComponent(stream[1]));
      return song ? json({ url: song.url, codec: "flac", quality: "LOSSLESS" }) : json({ error: "not found" }, 404);
    }
    return json({ error: "not found" }, 404);
  },
};
```

Deno Deploy, Vercel and Netlify work the same way. **GitHub alone can't host a song or lyrics module.** It only serves files, and Mochy needs an answer that depends on the song it asks for. GitHub is fine for looks.

---

## 6. Zip modules

A zip is a module someone can send as a file, through Discord, Telegram or a download.

```
my-module.zip
└── manifest.json        ← at the root
```

or

```
my-module.zip
└── my-module/
    └── manifest.json    ← or inside one folder, the way most zip tools pack a folder
```

- **Pictures and fonts are unpacked, nothing else.** `.png`, `.jpg`, `.jpeg`, `.webp`, `.gif`, `.ttf` and `.otf` files in the manifest's folder (and the folders under it) are copied into Mochy when the zip is added, keeping their paths. Everything else, like loose code or text files, is ignored, apart from the signed code a `code` field names ([6.1](#61-zips-with-their-own-code)). Hidden macOS files (`__MACOSX`, `._…`) are skipped.
- **A zip without a `url` lives on the phone.** It never touches the network, and its page has no server to check.
- **A zip that plays songs or lyrics needs a `url`:**

  ```json
  {
    "id": "my-flac",
    "name": "My FLAC",
    "url": "https://my-flac.example",
    "resources": ["search", "stream"]
  }
  ```

  It's then added just like pasting `https://my-flac.example`: the same calls, the same quality choices. The difference is that the manifest comes from the file. Without a `url`, Mochy says the zip "doesn't say where from".
- **Limits:** the manifest must be under 256 KB, and Mochy only looks through the first 200 entries for it. Pictures and fonts: up to 200 files, 8 MB each and 30 MB in all; a file over the limit is left out.
- **Removing** the module deletes its unpacked pictures and fonts too.
- **Updating:** importing a zip with the same `id` again updates the module.
- **Backups:** modules added from a zip are kept in Mochy's backup like every other module.

### 6.1 Zips with their own code

A zip can also bring a whole module written in Kotlin against `:modules:api`, the same kind as a built-in one. Mochy only runs that code when it's signed with Mochy's own key, so only Mochy's developers can make one ([section 11](#zip-modules-with-their-own-code) says how).

```
js-modules.zip
├── manifest.json     ← says "code": "module.apk"
└── module.apk        ← the module's code, signed like Mochy, never installed
```

```json
{
  "id": "addons",
  "name": "JS Modules",
  "description": "Plays from the JavaScript scripts you add as zips or links.",
  "resources": ["search", "stream"],
  "code": "module.apk"
}
```

- `name` and `resources` only fill in the sheet before it's added. Once it's in, the module says what it is and what it adds itself.
- **Signed like your Mochy:** a debug Mochy takes a zip built with the debug key, a release Mochy one built with the release key. Any other zip gets "it has to be signed with the same key as your Mochy".
- **It runs on its own copy of everything:** its own Kotlin, coroutines and libraries, in a class loader that can only see Android, Java and Mochy's bridge. Every call between the two goes across as JSON, so a different Kotlin version or Mochy's R8 can't break it.
- **Updating:** adding a newer zip with the same `id` swaps the running copy straight away. Its switch and choices stay.
- **Removing** deletes its code and everything it kept.
- It takes the place of a built-in module with the same `id`, the way a module app does.
- **Backups** carry it, since its code sits in Mochy's files. It's checked again every time it's loaded.

### 6.2 Scripts for JS Modules

**JS Modules** is a zip module that runs JavaScript scripts. It lives in its own project outside this repo (`mochy-js-modules`, next to the Mochy folder). A script exports two functions and fetches whatever it needs itself. Once JS Modules is added, add scripts on its page with **Add from a file or a link**: pick a zip, or paste a link to a zip, a script or an index. One zip can hold many scripts, and the module can hold as many as you like; every song asks them all at once.

**The script:**

```js
// module.exports, export async function, export default {...} and export { a as b } all work
module.exports = {
  async searchTracks(query, limit, context) {
    const quality = context.settings.quality.value;   // LOSSLESS, HI_RES_LOSSLESS, HIGH or LOW
    const res = await fetch('https://my-flac.example/search?q=' + encodeURIComponent(query));
    const data = await res.json();
    return { tracks: data.items.map(t => ({
      id: String(t.id), title: t.title, artist: t.artist, album: t.album,
      duration: t.seconds,                 // seconds
      audioQuality: 'LOSSLESS'             // optional, a row that says it's lossless wins a tie
    })) };
  },
  async getTrackStreamUrl(id, quality, context) {
    const res = await fetch('https://my-flac.example/stream/' + id + '?quality=' + quality);
    const data = await res.json();
    return { streamUrl: data.url, track: { mimeType: 'audio/flac', bitDepth: 16, sampleRate: 44100 } };
  }
};
```

- `searchTracks` answers `{ tracks: [...] }`; `artists: [...]` and `durationMs` are read too.
- `getTrackStreamUrl` answers `{ streamUrl, track }`, or just the link as a string. An `.m3u8` or
  `.mpd` link (or an HLS/DASH `mimeType`) plays as that kind of stream. `"encrypted": true` is passed
  over.
- The script can use `fetch` (text, JSON and binary bodies both ways: `arrayBuffer()`, `blob()`, a
  `Uint8Array` as the body), `setTimeout`, `setInterval`, `console`, `URL`, `URLSearchParams`,
  `Headers`, `AbortController`, `TextEncoder`/`TextDecoder`, `atob`/`btoa`, `crypto.randomUUID`,
  `queueMicrotask`, `performance.now` and `structuredClone`. There's no `require` and no `import`.
- A call that runs past 20 s is stopped. Timers never hold an answer up: one that isn't due yet runs
  the next time the script is used.
- Keep a login token in a top-level variable: the script keeps running between calls. A script gets
  up to three interpreters side by side when several songs or downloads need it at once, and each
  one starts the script once.

**The zip:**

```
my-scripts.zip
├── index.json        ← optional: names, authors, versions
├── tidal.js
└── qobuz.js
```

`index.json` lists the scripts under any `"category:…"` key:

```json
{ "category:music": [
  { "id": "tidal", "name": "Tidal", "author": "someone", "version": "1.2",
    "tags": ["LOSSLESS", "HI-RES"], "download": "tidal.js" }
] }
```

`download` can also be a link, and then the script is fetched when it's added. Without an index,
every `.js` that has both functions becomes a script named after its file. A `manifest.json` whose
`main` names the script works too. Adding a script with the same id again updates it. A zip or link
with no script but a web module's `manifest.json` is added as its own module instead.

---

## 7. Putting your module in the catalog

"Find more sources" lists the modules in [`catalog.json`](catalog.json), read straight from GitHub, so the list grows without an app update. To get listed, open a pull request adding an entry:

```json
{
  "modules": [
    {
      "name": "My FLAC",
      "description": "My own library, lossless",
      "url": "https://my-flac.example",
      "adds": ["audio", "lyrics", "look"],
      "author": "someone"
    }
  ]
}
```

- `adds` is what the sheet shows before anything is checked: `audio` (or `songs`), `lyrics`, `look`.
- Tapping a catalog entry still checks the real manifest and shows what it adds before anything is installed.
- Pull requests are reviewed by hand. A module that doesn't work, describes itself wrongly, pretends to be someone else or does anything shady isn't merged.

Want to share a module without the catalog? Post its link or its zip in [Mochy's Bar on Discord](https://discord.gg/NTmqVphbRY). The banner at the top of the Modules page goes there.

---

## 8. Testing your module

**From a computer**, check every call with `curl` before you touch the phone:

```sh
curl https://my-flac.example/manifest.json
curl "https://my-flac.example/search?q=benny%20blanco%20eastside&quality=LOSSLESS"
curl "https://my-flac.example/stream/123?quality=LOSSLESS"
curl -I "<the url your stream call returned>"     # should be 200 or 206, an audio type, ideally Accept-Ranges
curl "https://my-flac.example/lyrics?title=Eastside&artist=benny%20blanco&duration=173"
```

**In Mochy**, open the module's page:

- **Check server** asks for your manifest again and says whether it answered. The icon spins while it checks.
- **Test playback** runs every step on the phone with the song that's playing (or a known one): finding the song, opening the stream, getting a license if the stream needs one, and decoding two seconds, muted. Each step says what went wrong.
- After turning it on, play anything: a toast says where each song came from.

**With logs** (phone connected, USB debugging on):

```sh
adb logcat -s ModuleTest SourceModules
```

`SourceModules` prints, for every song, which provider was asked, what it answered, and why it was passed over.

---

## 9. How Mochy uses your module

### Playing

**Settings → Audio Lab → Audio Quality → Provider** is the provider order. Each song plays from the first provider that's on and has it. YouTube Music can sit anywhere in the order, or be switched off.

For each song, Mochy:

1. Searches your module (`/search`), and picks the matching recording with its matcher.
2. Asks for the stream (`/stream/{id}`), unless the search already gave a `streamURL`.
3. Passes over a stream the phone can't decode, and one that's encrypted.
4. Plays it, or moves on to the next provider.

Mochy remembers a song your module doesn't have for 30 minutes, so it doesn't search for it again on every play. A module that doesn't answer is left alone for 10 minutes, then tried again.

### Downloading

**Audio Quality → Download order** is a separate order, just for downloads. It starts as a copy of the provider order. Each download comes from the first provider that can give the song as **one whole file** this phone can play.

- HLS/DASH streams and streams that need a license are skipped for downloads (they still *play* fine).
- YouTube is always available for downloads, even when it's off for playing. The Download setting (High, Medium, Low) is its quality.
- A download that fails from your module retries from the next provider down.
- Give `codec` in your stream answer: the downloaded song's format badge comes from it.

---

## 10. Rules to follow

For everyone making a module:

- [ ] **Use https** for every link: manifest, service and streams.
- [ ] **Answer fast.** Mochy waits 12 seconds to connect and 20 seconds for an answer, then moves on. A slow module makes every song wait.
- [ ] **Answer with JSON** in the shapes above, with `Content-Type: application/json`. Return an error status (404, 503…) when something's wrong, never a web page with 200.
- [ ] **Keep ids stable.** The `id` you return from search must work in `/stream/{id}`, and your manifest's `id` must never change.
- [ ] **Be honest in search results.** Real titles, every artist, real durations. A wrong duration makes Mochy pick the wrong recording or none.
- [ ] **No encrypted streams.** They're skipped anyway.
- [ ] **Bump `version`** whenever you change your manifest.
- [ ] **Never put secrets in a manifest you share.** If your link carries a personal key (some debrid services put an API key in the path), that link *is* a password. Anyone who has it can use your account. Don't post it publicly, and get a new key if you did.
- [ ] **Don't pretend to be someone else.** No other service's name or logo as your own.
- [ ] **Looks only use the keys in [section 4](#41-look-settings).** Anything else is ignored.

---

## 11. For Mochy developers: changing the module interface

This part is for people working on Mochy itself. Follow the repo's `CLAUDE.md` everywhere: one-line lowercase code comments, and text people see goes in `res/values/*.xml` (English only, never touch `values-*/`).

### Where things live

| What | Where |
|---|---|
| The contract every module follows | `modules/api/src/main/kotlin/com/mochyy/mochymusic/module/MochyModule.kt` |
| The matcher every module uses | `modules/api/.../module/TrackMatcher.kt` |
| Built-in modules | `modules/applemusic`, `modules/monochrome`, `modules/jiosaavn` |
| The bridge to a zip's code: what crosses, the JSON, both sides | `modules/bridge` (`shared/Shared.kt`, `Wire.kt`, `ModuleSide.kt`, `BridgedModule.kt`) |
| Checking, unpacking and starting a zip's code | `app/.../modules/code/CodeModules.kt` |
| JS Modules: reading zips, the sandbox, asking every script | Not in this repo: its own project, `mochy-js-modules` next to the Mochy folder (`module/` and `zip/`) |
| Every module, its switch and choices; adding from a link or a zip | `app/.../modules/ModuleRegistry.kt` |
| Provider order, download order, and the walk down each | `app/.../modules/SourceModules.kt` |
| Link modules (manifest + HTTP calls) | `app/.../modules/web/WebModuleManifest.kt`, `WebModule.kt` |
| Zip modules without a service | `app/.../modules/local/LocalModule.kt` |
| Skins: the model, the parser, unpacking a zip, the look that's on | `app/.../modules/skin/` (`ModuleSkin.kt`, `SkinParser.kt`, `SkinFiles.kt`, `ActiveSkin.kt`) |
| The module player style | `app/.../ui/player/styles/ModulePlayerStyle.kt` |
| Module pages | `app/.../ui/screens/settings/modules/ModulePageScreen.kt` |
| A look's font across the app | `LookFont` in `app/.../ui/theme/Type.kt` |
| Looks: applying and giving settings back | `app/.../modules/ModuleLooks.kt` |
| The catalog | `modules/catalog.json` (read by `app/.../modules/ModuleCatalog.kt`) |
| Pages | `app/.../ui/screens/settings/modules/` |
| Downloads | `app/.../playback/DownloadUtil.kt` |

### The contract and its version

`:modules:api` is plain Kotlin on purpose: no Android, no Media3, no HTTP library. That way a module can be built as its own APK and loaded at runtime. It has a version, `MODULE_API_VERSION` (now `3`). A module app built for a different version is skipped with a log line, so it can't crash Mochy.

**When to bump `MODULE_API_VERSION`:**

| Change | Bump? |
|---|---|
| Add a new optional interface (like `ServerListModule`) | No |
| Add a field with a default to a class only Mochy creates (like `HttpResponse.headers`) | No |
| Rename, remove or change the type of anything a module calls or implements | **Yes** |
| Add a method to an interface modules implement, even one with a default body | **Yes**, an older module app was built without it |
| Change what a value means (units, ranges) | **Yes** |

After a bump, every module app has to be rebuilt against the new `:modules:api`. Built-in modules are rebuilt with Mochy anyway.

### Adding a look setting

1. Add the key to `LookSetting` in `MochyModule.kt`, with a one-line comment saying its values.
2. Add it to `ModuleLooks.Settings` in the app, with the preference key it writes and a parser that turns away anything out of range (`enumName<…>`, `number(it, min..max)` or `bool`).
3. Add a row to the table in [section 4](#41-look-settings).

A look can never reach anything that isn't in `ModuleLooks.Settings`. That's the safety line, so keep it to look settings: no accounts, no network, no playback.

### Adding a player block, a background or a page block

1. Add it to the model in `ModuleSkin.kt` (`SkinBlock`, `SkinBackground` or `PageBlock`).
2. Read it in `SkinParser.kt`. Give every field a default, and clamp every number, so a typo can't break the player.
3. Draw it in `ModulePlayerStyle.kt` or `ModulePageScreen.kt`. Follow `CLAUDE.md`'s smoothness rules: read the playback position and anything else that changes every frame inside `drawBehind`/`graphicsLayer`, never in composition; pause any loop while the style is hidden (`rememberStyleShowing`, `LocalAppContentObscured`) and wrap it in `damageBoundary()`; use the GlassKit helpers for blur.
4. Add a case to `app/src/test/.../modules/skin/SkinParserTest.kt`, and a row to [section 4](#4-changing-the-interface-looks-players-fonts-and-pages).

An older Mochy skips a block it doesn't know, so adding one never breaks older layouts. Renaming or removing one does, so don't.

### Changing what a link or zip module can do

- New manifest fields go in `WebModuleManifest`, always with a default, so every manifest out there keeps working.
- New calls or answer fields go in `WebModule`. Read new fields as optional, and keep reading the old ones.
- Update this README in the same change.

### Adding a built-in module

1. Make a Gradle module `modules/<name>` that depends only on `:modules:api` (plus `kotlinx-serialization-json` if you need it). Add it to `settings.gradle.kts` and as a dependency of `:app`.
2. Implement `SourceModule`, `LyricsModule` and/or `LookModule`. Do all HTTP through `host.http(…)`, keep small state in `host.storage` and bigger files in `host.folder`.
3. Add it to the built-in list in `ModuleRegistry.load()`.
4. Give it a logo in `ModuleParts.logoFor` and `providerLogo` in `AudioQualityScreen.kt`, or it shows its first letter.
5. Write tests with a fake host, the way `modules/monochrome/src/test/.../MonochromeModuleTest.kt` does, and run them with `./gradlew :modules:<name>:test`.

### Zip modules with their own code

These live outside this repo, so Mochy never compiles them. `mochy-js-modules` (next to the Mochy folder) is the one to copy:

1. Its `mochy-api` and `mochy-bridge` projects compile Mochy's `modules/api` and `modules/bridge` straight from a Mochy checkout (`mochyDir` in its `gradle.properties`), so the zip always matches the Mochy it's for.
2. `module/` is the module, written like a built-in one against the api, with a no-argument constructor.
3. `zip/` is an Android app build that depends on `:module` and `:mochy-bridge`, with a manifest whose `<meta-data android:name="com.mochyy.mochymusic.module.entry">` names the module class, and a `manifest.json` with `"code": "module.apk"`.
4. `./gradlew :zip:moduleZipDebug` makes the zip for a debug Mochy, `:zip:moduleZipRelease` (with the release key's environment set) the one for a release Mochy. It lands in `zip/build/outputs/module/`.

Keep R8 off in the packing project: Mochy makes `ModuleSide` and your module class by name. Only `com.mochyy.mochymusic.module.bridge.shared` crosses between Mochy's class loader and the module's. It's kept by name in Mochy's R8 rules, and changing it means bumping `MODULE_API_VERSION`.

### Module apps

A module app is an APK whose code runs inside Mochy. It needs:

- a `<service>` answering the intent `com.mochyy.mochymusic.SOURCE_MODULE`
- `<meta-data android:name="com.mochyy.mochymusic.module.entry" android:value="your.module.EntryClass" />` on that service
- an entry class with a no-argument constructor that implements `MochyModule`
- to be **signed with Mochy's own key**, and built for the same `MODULE_API_VERSION`

Build against `:modules:api` as `compileOnly`, since Mochy already has those classes. A module app replaces the built-in module with the same id, so a built-in module can be updated on its own.

### Checking your change

- `./gradlew :app:compileArm64FossDebugKotlin` for the app (announce it first if someone else shares the checkout).
- `./gradlew :modules:<name>:test` for a module's tests.

---

## 12. Troubleshooting

| What you see | Why, and what to do |
|---|---|
| "That's a website, not a module" | You pasted a website. Mochy needs a module's link or its `manifest.json`. A Monochrome site goes on Monochrome's page under **Servers**. |
| "That link answered, but it isn't a module Mochy can use" | The manifest has no `id` and no `name`, isn't valid JSON, or adds nothing. |
| "That file isn't a module" | The zip has no `manifest.json` at its root or one folder in, or isn't a zip. |
| "…its .zip doesn't say where from" | Your zip lists `search`, `stream` or `lyrics` but has no `url`. |
| "…it has to be signed with the same key as your Mochy" | The zip's code was built with another key, like a debug zip on a release Mochy. Build it with the same build type and key as the Mochy it's for. |
| "This module's code didn't start" | It was built for another `MODULE_API_VERSION`, or its `module.apk` has no entry `meta-data`. `adb logcat -s CodeModules` says which. |
| The test finds the song but it never plays from your module | Check the stream call with `curl -I`: it must give audio (200 or 206), not a web page or an error. Check that the codec and sample rate are ones the phone can decode. |
| Songs come from YouTube instead | Your module is below YouTube in the provider order, it's switched off, or it was skipped for 10 minutes after not answering. `adb logcat -s SourceModules` says which. |
| Downloads don't use your module | Check the **download order**, not the provider order. Also, an HLS/DASH stream can't be downloaded: give one whole file. |
| The look didn't change something | The key isn't in [section 4.1](#41-look-settings), the value is out of range, or the user changed that setting by hand. |
| My player doesn't show up | Your look has to be **on**. `player` needs at least one block Mochy knows. Then hold the player: your style is the first in the selector. |
| A block is missing from my player | Its `type` is misspelled or unknown, so it was skipped. Check the tables in [4.2](#42-a-player-of-your-own). |
| My picture doesn't show | The path is relative to `manifest.json` and case-sensitive, and the file must be `.png`, `.jpg`, `.webp` or `.gif` under 8 MB. For a link module, the picture must be reachable next to the manifest. |
| My font isn't used | Fonts only work from a zip, as `.ttf` or `.otf`, and the `regular` path must point at the file. A broken font is skipped. |
| My page isn't listed | Its `id` may only use `a`–`z`, `0`–`9`, `-` and `_`, and it needs at least one block. Pages only open while the module is on. |
| "… didn't answer" under the module's name | Your server didn't answer. It's tried again after 10 minutes, or right away with **Check server**. |
