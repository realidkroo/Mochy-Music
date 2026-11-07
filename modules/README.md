# Making modules for Mochy

A **module** adds something to Mochy without an app update. One module can add any mix of these:

| It adds | What Mochy does with it |
|---|---|
| **Songs** | Asks it for the song that's playing (in the provider order), and plays the stream itself, so every Audio Lab effect still works. |
| **Lyrics** | Puts it first among the lyrics sources while it's on. |
| **Song info** | Asks it about the song in **About song**: genre, release date, album, who wrote and produced it, the label, and what the song is about. Its answers come before Mochy's own sources. |
| **A look** | Restyles Mochy while it's on: its settings (player style, colours, blur, corners…), **a player of its own**, **its own font**, **a glass of its own for the bottom bar** (an AGSL shader), **pictures**, **pages** (even **in Settings**) and **rows in the player's menu**. Everything goes back the way it was when it's turned off. |

Every module can also bring **an icon**, **a banner** and **settings of its own** that people change on its page.

One switch turns a module on or off. Off means gone everywhere: from Audio Quality, the provider order, the download order, the lyrics sources, About song, Settings, the player's menu and the look. Every new module starts **off**, so adding one never changes anything until the user turns it on.

---

## Contents

1. [Pick the kind of module you need](#1-pick-the-kind-of-module-you-need)
2. [Your first module in two minutes: a look in a zip](#2-your-first-module-in-two-minutes-a-look-in-a-zip)
3. [manifest.json, field by field](#3-manifestjson-field-by-field)
4. [Icons and banners](#4-icons-and-banners)
5. [Settings people can change](#5-settings-people-can-change)
6. [Changing the interface: looks, players, fonts, pages and menu rows](#6-changing-the-interface-looks-players-fonts-pages-and-menu-rows)
7. [A module that plays songs, lyrics or song info (a web module)](#7-a-module-that-plays-songs-lyrics-or-song-info-a-web-module)
8. [Zip modules](#8-zip-modules)
9. [Scripts for JS Modules](#9-scripts-for-js-modules)
10. [Putting your module in the catalog](#10-putting-your-module-in-the-catalog)
11. [Testing your module](#11-testing-your-module)
12. [How Mochy uses your module](#12-how-mochy-uses-your-module)
13. [Rules to follow](#13-rules-to-follow)
14. [For Mochy developers: changing the module interface](#14-for-mochy-developers-changing-the-module-interface)
15. [Troubleshooting](#15-troubleshooting)

---

## 1. Pick the kind of module you need

| You want to… | Make a… | Needs a server? | Who can make it |
|---|---|---|---|
| Restyle Mochy, give the bottom bar a glass of your own, design your own player, add pages or menu rows | **Look**, as a zip (or a `manifest.json` link; a glass or a font needs the zip) | No | Anyone |
| Add songs, lyrics or song info from a service | **Web module**: a link to a small HTTP service | Yes | Anyone |
| Share a web module as one file | **Zip with a `url`** | Yes (the service it names) | Anyone |
| Add songs, lyrics or song info from a JavaScript script | **Script** for the **JS Modules** module: a `.js` in a zip or a link, added on its page | No (it reaches its own services) | Anyone |
| Add another Monochrome server | Not a module: Monochrome's page, **Servers** | No | Anyone |
| Ship code that runs inside Mochy | **Zip with its own code**, **built-in module** or **module app** | No | Mochy developers only |

Nothing from a look, a web module or a plain zip runs inside Mochy. They only hand back JSON, stream links and look values, so anyone can make one and nobody has to be trusted with the app. Code that runs inside Mochy (a zip with its own code, built-in modules and module apps) is only loaded when it's signed with Mochy's own key, see [section 8.1](#81-zips-with-their-own-code) and [section 14](#14-for-mochy-developers-changing-the-module-interface). Scripts for JS Modules are JavaScript that runs sandboxed inside that module, with no access to the phone or the app, only to the internet through Mochy ([section 9](#9-scripts-for-js-modules)).

**Where people see modules.** Settings → Modules lists them in two groups: **Look modules** (ones that only change how Mochy looks) and **JS & source modules** (anything that brings songs, lyrics or song info). Each row shows the module's icon, name, a dot for how it's doing (green on, grey off, amber when it needs something), what it adds and its version. People drag a row by its lines to reorder it and swipe it left to delete it. Tapping a row opens the module's own page.

---

## 2. Your first module in two minutes: a look in a zip

1. Make a folder called `midnight` with an `icon.png` (a square picture, 512×512 is plenty) and this `manifest.json`:

   ```json
   {
     "id": "midnight",
     "name": "Midnight",
     "version": "1.0",
     "description": "Pure black, sharp corners, the turntable player",
     "author": "you",
     "icon": "icon.png",
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
   - **Terminal:** `cd midnight && zip midnight.zip manifest.json icon.png`

3. Send `midnight.zip` to your phone. In Mochy go to **Settings → Modules → Add a module → Add from a file → Choose a .zip**.

4. Mochy shows what the module adds. Tap **Add**, open its page and turn it on. Turn it off and your own settings come back.

That's a whole module. A bigger one that uses almost everything in this guide is **Liquid Glass**, see [section 6.8](#68-putting-it-together).

---

## 3. manifest.json, field by field

Every module, whether it comes as a link or a zip, describes itself with one `manifest.json`.

| Field | Required | What it's for |
|---|---|---|
| `id` | `id` or `name` | A short stable name, like `my-flac`. Mochy turns it into `web.my-flac` (link) or `local.my-flac` (zip), so it can never replace a built-in module. **Keep it the same forever**: adding a module with the same id again updates it instead of adding a second copy. |
| `name` | `id` or `name` | What people see. |
| `version` | No | Shown on the module's row and page. Bump it when you change anything. |
| `description` | No | One line about what it does, shown under its name on its page. |
| `author` | No | You. Shown under **Module info**. |
| `icon` | No | A square picture for its row and page, see [section 4](#4-icons-and-banners). |
| `banner` | No | A wide picture across the top of its page, see [section 4](#4-icons-and-banners). |
| `resources` | No | What it does: `"search"` + `"stream"` make it play songs, `"lyrics"` makes it a lyrics source, `"metadata"` makes it a source of song info. Plain names or `{ "name": "search" }` both work. |
| `settings` | No | Settings people change on its page, see [section 5](#5-settings-people-can-change). |
| `look` | No | Look settings, see [section 6.1](#61-look-settings). |
| `player` | No | A player of your own, see [section 6.2](#62-a-player-of-your-own). |
| `font` | No | Your own font, zip only, see [section 6.3](#63-your-own-font). |
| `pages` | No | Pages of your own, see [section 6.5](#65-pages). |
| `actions` | No | Rows in the player's menu, see [section 6.6](#66-rows-in-the-players-menu). |
| `glass` | No | A glass of your own for the bottom bar, zip only, see [section 6.7](#67-a-glass-for-the-bottom-bar). |
| `url` | Zip only | The service a zip module plays from. A link module doesn't need it, since its link *is* the service. |

Two rules worth knowing:

- **A manifest with no `resources` and nothing that changes the look (`look`, `player`, `font`, `pages`, `actions`, `glass`, or settings that carry a look or a glass) is read as a song source**, because that's what most services of this kind are.
- **A website's own `manifest.json` is turned away.** Websites have one for "install to home screen", with `start_url`, `display`, `short_name` or `icons`. If your manifest has any of those and none of `resources`, `look`, `player`, `font`, `pages`, `actions` or `glass`, Mochy says "That's a website, not a module". Don't use those field names in a module manifest.

Fields Mochy doesn't know are ignored, so a newer manifest still opens on an older Mochy.

---

## 4. Icons and banners

```json
"icon": "icon.png",
"banner": "images/banner.webp"
```

- **The icon** is square. It's shown on the module's row in Settings → Modules, big on its own page, and next to its pages in Settings. Mochy rounds the corners for you, so give it square corners. 512×512 is plenty.
- **The banner** is wide, about 2:1 (1200×600 is plenty). It sits across the top of the module's page and fades into it, and **the page takes its colour from it**. Keep the bottom third calm: the icon and name sit over it.
- Without a banner, the page uses your icon, blown up and softly blurred. Without either, Mochy draws your first letter on a colour of its own.

Both can be:

- **A path in your zip**, relative to `manifest.json`: `"images/banner.webp"`. Pictures are unpacked when the zip is added.
- **A link**: `"https://example.com/banner.webp"`.
- **For a module added from a link**, a path next to its manifest. With `https://you.github.io/glass/manifest.json`, `"icon.png"` is `https://you.github.io/glass/icon.png`.

`.png`, `.jpg`, `.webp` and `.gif` work. `.webp` is the smallest.

---

## 5. Settings people can change

`settings` lists your module's own options. They show under **Options** on its page, and people change them right there.

```json
"settings": [
  { "key": "region", "label": "Region", "type": "select", "default": "us",
    "options": [
      { "value": "us", "label": "United States" },
      { "value": "jp", "label": "Japan", "description": "Songs from the Japanese catalog" }
    ] },
  { "key": "token", "label": "Your API key", "type": "text", "hint": "Paste it here",
    "description": "Find it in your account page" },
  { "key": "explicit", "label": "Allow explicit songs", "type": "toggle", "default": "true" }
]
```

| Field | What it's for |
|---|---|
| `key` | The setting's name, the same forever. It's also the name of the query parameter a service gets. |
| `label` | What people see. Without it, the key is shown. |
| `type` | `select` (a list to pick from), `text` (a line to type in) or `toggle` (a switch). Left out, Mochy works it out: options make it a `select`, a `"true"`/`"false"` default a `toggle`, anything else `text`. |
| `default` | What it starts as. A select starts on its first option without one, a toggle on off. |
| `options` | For a `select`: plain values (`["us", "jp"]`) or objects with `value`, `label`, `description`, `look` and `glass`. |
| `hint` | For `text`: the grey words in the empty field. |
| `description` | A line under the setting. A picked option's own `description` shows under the list. |
| `look` | For a `toggle`: the look it puts on while it's on, see below. |
| `glass` | For a `toggle`: what it changes about your glass while it's on, see below. |

**Where the values go:**

- **A web module** gets every setting as a query parameter on every call (`/search`, `/stream`, `/lyrics`, `/metadata`): `?region=jp&token=abc&explicit=true`. A value nobody set goes as its `default`.
- **A module that plays songs** keeps its `quality` and `atmos` settings for Mochy's own **Stream** and **Dolby Atmos** choices ([section 7](#quality-and-dolby-atmos)), so those two never show as options.
- **A zip with its own code** gets them through `ModuleFields.onFields` ([section 14](#settings-for-modules-with-their-own-code)).

**Settings that change the look.** An option, or a switched-on toggle, can carry a `look` of its own. It's laid over the module's `look` while the module is on, and when someone picks something else, Mochy gives the old values back and puts the new ones on.

**Settings that change the glass.** The same way, an option or a switched-on toggle can carry a `glass`: a `blur`, a `margin` and `uniforms` laid over your [glass](#67-a-glass-for-the-bottom-bar) while it's picked. That's how one glass offers "Liquid" or "Clear", and how an option can hand the bars back to Mochy's frost:

```json
"settings": [
  { "key": "style", "label": "Glass", "type": "select", "default": "liquid", "options": [
    { "value": "liquid", "label": "Liquid glass", "glass": { "blur": 7, "uniforms": { "tintAlpha": 0.42 } } },
    { "value": "clear",  "label": "Clear glass",  "glass": { "blur": 2.5, "uniforms": { "tintAlpha": 0.16 } } },
    { "value": "frost",  "label": "Mochy's frost", "look": { "bottomBar": "FROSTED" } }
  ] },
  { "key": "fringe", "label": "Colour fringe", "type": "toggle", "default": "true",
    "glass": { "uniforms": { "spread": 0.06 } } },
  { "key": "softCovers", "label": "Softer covers", "type": "toggle", "default": "false",
    "look": { "thumbnailCornerRadius": "22" } }
]
```

A module whose settings carry a look or a glass counts as a look, even with no `look` of its own.

---

## 6. Changing the interface: looks, players, fonts, pages and menu rows

A look can do eight things, in any mix:

| | Where it goes in `manifest.json` | Works from |
|---|---|---|
| [6.1 Change Mochy's settings](#61-look-settings) | `look`, and looks inside `settings` | a zip / a link |
| [6.2 Bring a player of its own](#62-a-player-of-your-own) | `player` | a zip / a link |
| [6.3 Bring its own font](#63-your-own-font) | `font` | a zip only |
| [6.4 Use its own pictures](#64-pictures) | paths in `player`, `pages` and `actions` | a zip, or a link with pictures beside its manifest |
| [6.5 Add pages, even in Settings](#65-pages) | `pages` | a zip / a link |
| [6.6 Add rows to the player's menu](#66-rows-in-the-players-menu) | `actions` | a zip / a link |
| [6.7 Bring a glass for the bottom bar](#67-a-glass-for-the-bottom-bar) | `glass`, and glass changes inside `settings` | a zip only |
| [6.8 Put it all together](#68-putting-it-together) | | |

How looks behave:

- **Only one look is on at a time.** Turning a second one on turns the first off and gives its settings back first. A module with *only* pages or menu rows doesn't count as a look here, so it can stay on next to one; a glass does count.
- **The user always wins.** If someone changes a setting by hand while your look is on, they keep their value when your look goes off.
- **It survives restarts.** Mochy remembers what each look replaced, so turning it off after a restart still gives the right settings back.
- **Nothing runs.** A look is a description. Mochy draws it with its own parts, so it stays smooth on slow phones and can't break anything.

### 6.1 Look settings

A look may change **only** these settings, so a module can restyle Mochy but never reach accounts, the network or playback. Values are written as text (`"true"`, `"12"`, `"FLAGSHIP"`). Keys must be spelled exactly as below, while style names may be any case. A value that's out of range is simply ignored.

| Key | Values | What it changes |
|---|---|---|
| `bottomBar` | `FROSTED`, `MODULE` | The glass of the nav bar, the wide bar and the mini player. `MODULE` is your own glass from 6.7, and is picked for you when you bring one; `FROSTED` is Mochy's own. |
| `playerStyle` | `FLAGSHIP`, `CLASSIC`, `TURNTABLE`, `VIDEO`, `IPOD`, `POSTER`, `TIMELINE`, `LYRICS`, `BEDSIDE`, `BEDSIDE_TILES`, `MODULE` | The now-playing screen. `MODULE` is your own player from 6.2, and is picked for you when you bring one. |
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

A look made only of settings can also be added from a link to its `manifest.json`, for example a raw GitHub link like `https://raw.githubusercontent.com/you/midnight/main/manifest.json`. Want a setting that isn't in this list? It has to be added to Mochy first, see [section 14](#adding-a-look-setting).

### 6.2 A player of your own

`player` describes a whole now-playing screen. While your look is on, it's the first style in the player's style selector (hold the player to open it) under the name you give it. Mochy switches to it when your look turns on, and back to the user's style when it goes off.

Your player gets everything the other styles have, for free: swipe sideways to change song (your cover and words ride along), tap to play or pause, swipe down to close, the ••• menu (lyrics, song info, turning sideways, and your own menu rows), and its clocks resting while it's hidden.

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

The blocks are laid out top to bottom, in order. Use `"space"` with `"grow": true` to push things apart; two growing spaces around everything centre it on the screen.

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
| `image` | Your picture, cropped to fill | `image` (see [6.4](#64-pictures)) |

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

### 6.3 Your own font

```json
"font": { "regular": "fonts/Lora-Regular.ttf", "bold": "fonts/Lora-Bold.ttf" }
```

While your look is on, the whole app uses this font, your player included. `.ttf` and `.otf` work. `bold` is optional: without it, bold text is drawn thicker from the regular one. On Android 7 only the regular file is used. A font has to be a file on the phone, so it only works from a zip. A broken font file is skipped, and the app keeps its own.

### 6.4 Pictures

Anywhere a block, a background, a page or a menu row takes a picture (`image`, `frame`, `icon`), you can write:

- **A path in your zip**, relative to `manifest.json`: `"images/wood.webp"`. Pictures in `.png`, `.jpg`, `.webp` and `.gif` are unpacked when the zip is added.
- **A link**: `"https://example.com/wood.webp"`.
- **For a module added from a link**, a path is read next to its manifest. With `https://you.github.io/vinyl/manifest.json`, `"images/wood.webp"` is `https://you.github.io/vinyl/images/wood.webp`.

Keep pictures small. A phone screen is about 1080 pixels wide, so a 1080×2400 background is plenty, and `.webp` is the smallest.

### 6.5 Pages

A look can add pages of its own: an about page, a "start here", your favourite playlists, or **a settings page that shows up in Mochy's own Settings**. They're listed on your module's page under **Pages** while it's on, built from these blocks:

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

**A page in Settings.** Add `"inSettings": true` and the page gets its own row on the main Settings screen, right under **Modules**, for as long as your module is on. Give it a `subtitle` for the line under its title and an `icon` (a picture, see [6.4](#64-pictures)); without one it uses your module's icon. Fill it with `setting` blocks and it becomes your module's settings page:

```json
{ "id": "glass", "title": "Liquid Glass", "subtitle": "How your glass looks", "inSettings": true, "blocks": [
  { "type": "heading", "text": "Your glass" },
  { "type": "setting", "key": "glass" },
  { "type": "setting", "key": "softCovers" }
]}
```

| `type` | What it shows | Fields |
|---|---|---|
| `heading` | A big line | `text` |
| `text` | A paragraph | `text` (up to 2000 characters) |
| `image` | A picture, full width | `image` · `height`: `40`–`400` (`160`) · `corner` (`16`) |
| `button` | A button that opens a web page | `text` · `link` (must start with `https://` or `http://`) |
| `setting` | One of your module's settings ([section 5](#5-settings-people-can-change)), changed right there | `key` |
| `song` | Plays a song | `id` (its YouTube Music video id) · `title` · `artist` |
| `playlist` | Opens a playlist | `id` (its YouTube Music playlist id) · `title` · `subtitle` |
| `album` | Opens an album | `id` (its browse id, `MPREb_…`) · `title` · `subtitle` |
| `artist` | Opens an artist | `id` (its channel id, `UC…`) · `title` |
| `search` | Opens a search | `query` · `title` |
| `divider` | A thin line | |
| `space` | Empty space | `size`: `0`–`120` (`16`) |

A page `id` uses `a`–`z`, `0`–`9`, `-` and `_`. You can have up to 12 pages with 80 blocks each. The ids are in the song's, playlist's or album's YouTube Music link: `music.youtube.com/watch?v=`**`id`**, `music.youtube.com/playlist?list=`**`id`**, `music.youtube.com/browse/`**`id`**, `music.youtube.com/channel/`**`id`**.

### 6.6 Rows in the player's menu

`actions` adds rows to the player's ••• menu, under **edit player**, while your module is on. Each row does one thing with the song that's playing:

```json
"actions": [
  { "title": "Find the lyrics on Genius", "icon": "images/genius.png", "link": "https://genius.com/search?q={artist}%20{title}" },
  { "title": "Open my picks", "page": "picks" },
  { "title": "Find live versions", "search": "{title} {artist} live" }
]
```

| Field | What it's for |
|---|---|
| `title` | The row's words, up to 40 characters. |
| `icon` | A small picture beside it ([6.4](#64-pictures)). Without one, Mochy's puzzle piece. |
| `link` | Opens a web page. It must start with `https://` or `http://`, so a row can never open another app. |
| `page` | Opens one of your module's pages, by its `id`. |
| `search` | Opens a search in Mochy for these words. |

Give exactly one of `link`, `page` or `search`. In a `link` or a `search`, `{title}`, `{artist}` (every artist, joined with commas), `{album}` and `{id}` (its YouTube Music video id) are filled in with the song's own. In a link they're made safe for a URL; write spaces between them as `%20`. Up to 6 rows per module.

### 6.7 A glass for the bottom bar

A look can replace Mochy's frosted nav bar, wide bar and mini player with a glass of its own: an [AGSL](https://developer.android.com/develop/ui/views/graphics/agsl) shader in your zip. Mochy captures what's under each bar (with some room around it), blurs it, runs your shader over it and clips the result to the bar. Your shader decides everything else: how it bends, tints, shines and lights its rim. While your look is on, Mochy switches `bottomBar` to `MODULE` for you.

```json
"glass": {
  "shader": "glass.agsl",
  "blur": 7,
  "margin": 22,
  "uniforms": { "tintAlpha": 0.42, "bevel": 18, "strength": 14 }
}
```

| Field | What it's for | Default |
|---|---|---|
| `shader` | The `.agsl` (or `.sksl`) file in your zip, up to 64 KB | |
| `blur` | How much Mochy blurs what's under the bar before your shader sees it, in dp, `0`–`40` | `6` |
| `margin` | How far past the bar your shader may read, in dp, `0`–`64`. Mochy adds room for the blur on top | `16` |
| `uniforms` | Your shader's own `float` uniforms by name, up to 24. Settings can change them ([section 5](#5-settings-people-can-change)) | |

**What Mochy gives your shader.** Declare the ones you use; a uniform you don't declare is simply skipped, and so is one of yours the shader doesn't have.

| Uniform | What it is |
|---|---|
| `uniform shader content;` | What's under the bar, already blurred. `content.eval(p)` reads it |
| `uniform float2 size;` | The whole picture in pixels: the bar plus `margin` on every side. The bar is centred in it |
| `uniform float margin;` | That margin, in pixels |
| `uniform float radius;` | The bar's corner radius in pixels (half its height: the bars are stadiums) |
| `uniform float density;` | Pixels per dp, to turn your dp values into pixels |
| `uniform float quality;` | `1` on High blur quality, `0` on Low: skip your extras on Low |
| `layout(color) uniform half4 chrome;` | Mochy's bar colour: `#131313`, or black with pure black on |

A small glass to start from, a tinted pane with a lit edge:

```glsl
uniform shader content;
uniform float2 size;
uniform float margin;
uniform float radius;
uniform float density;
layout(color) uniform half4 chrome;
uniform float tintAlpha;

float roundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 p) {
    float2 c = size * 0.5;
    float d = roundedBox(p - c, c - margin, radius);
    float4 color = float4(content.eval(p));
    color.rgb = mix(color.rgb, float4(chrome).rgb * color.a, tintAlpha);
    float edge = 1.0 - smoothstep(0.0, 1.2 * density, -d);
    color.rgb = mix(color.rgb, float3(color.a), edge * 0.4);
    return half4(color);
}
```

- **Android 13 or newer** runs it. Older phones, and anyone who switched blur off, keep Mochy's frost.
- **A shader that doesn't compile** leaves the frost in place, and your module's page says why under **Module info**.
- **Keep it light.** It runs for every pixel of every bar, every frame what's under the bar moves, on phones with weak GPUs. No loops over the picture: a few reads of `content` per pixel at most.
- A glass only comes from a zip, like a font. It counts as a look, so only one is on at a time.

### 6.8 Putting it together

**Liquid Glass** is a complete look that uses an icon, a banner, a glass with settings that change it, a page in Settings and a menu row. It lives in its own project next to the Mochy folder, `mochy-liquid-glass`, and `./build.sh` there makes the zip:

```
liquid-glass.zip
├── manifest.json
├── glass.agsl
├── icon.png
└── banner.webp
```

```json
{
  "id": "liquid-glass",
  "name": "Liquid Glass",
  "version": "1.0",
  "description": "Turns the nav bar and the mini player into glass that bends what's under it",
  "author": "Mochy",
  "icon": "icon.png",
  "banner": "banner.webp",
  "glass": {
    "shader": "glass.agsl", "blur": 7, "margin": 22,
    "uniforms": { "tintAlpha": 0.42, "bevel": 18, "strength": 14, "spread": 0, "saturation": 1.35, "rimAlpha": 0.55, "sheen": 0.1 }
  },
  "settings": [
    { "key": "style", "label": "Glass", "type": "select", "default": "liquid", "options": [
      { "value": "liquid", "label": "Liquid glass", "description": "A light tint, with a lens along the edges.",
        "glass": { "blur": 7, "uniforms": { "tintAlpha": 0.42, "bevel": 18, "strength": 14, "saturation": 1.35, "rimAlpha": 0.55 } } },
      { "value": "clear", "label": "Clear glass", "description": "Almost no tint, lovely over bright covers.",
        "glass": { "blur": 2.5, "uniforms": { "tintAlpha": 0.16, "bevel": 22, "strength": 18, "saturation": 1.2, "rimAlpha": 0.7 } } },
      { "value": "frost", "label": "Mochy's frost", "description": "The usual frosted bars, keeping the rest of this look.",
        "look": { "bottomBar": "FROSTED" } }
    ] },
    { "key": "fringe", "label": "Colour fringe", "type": "toggle", "default": "true",
      "description": "A thin rainbow where the rim bends light. Left out on Low blur quality.", "glass": { "uniforms": { "spread": 0.06 } } },
    { "key": "softCovers", "label": "Softer covers", "type": "toggle", "default": "false",
      "description": "Rounder cover corners, to match the glass.", "look": { "thumbnailCornerRadius": "22" } }
  ],
  "pages": [
    { "id": "glass", "title": "Liquid Glass", "subtitle": "How your glass looks", "inSettings": true, "blocks": [
      { "type": "image", "image": "banner.webp", "height": 150 },
      { "type": "heading", "text": "Your glass" },
      { "type": "text", "text": "Pick how the nav bar and the mini player look. Turn the module off and your frosted glass comes back." },
      { "type": "setting", "key": "style" },
      { "type": "setting", "key": "fringe" },
      { "type": "setting", "key": "softCovers" }
    ] }
  ],
  "actions": [
    { "title": "Find the lyrics on Genius", "link": "https://genius.com/search?q={artist}%20{title}" }
  ]
}
```

Its `glass.agsl` bends the rim like a lens (reading from further out along the edge on a circular profile), adds a colour fringe on High, lifts the saturation, tints with `chrome`, and lights the edge toward the top left with a soft sheen down from the top.

A look with its own player and font, for comparison:

```json
{
  "id": "vinyl-night",
  "name": "Vinyl Night",
  "version": "1.0",
  "description": "A record player in warm wood, with its own font",
  "author": "you",
  "icon": "images/icon.png",
  "banner": "images/banner.webp",
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
  }
}
```

---

## 7. A module that plays songs, lyrics or song info (a web module)

A web module is a small HTTP service. Mochy calls it with plain **GET** requests and reads JSON back. People add it by pasting its base link (`https://my-flac.example`) or the link to its manifest (`https://my-flac.example/manifest.json`). Both work.

Every request carries `Accept: application/json` and `User-Agent: Mochy`, plus your [settings](#5-settings-people-can-change) as query parameters.

### `GET {base}/manifest.json`

```json
{
  "id": "my-flac",
  "name": "My FLAC",
  "version": "1.2",
  "description": "My own library, lossless",
  "author": "someone",
  "icon": "https://my-flac.example/icon.png",
  "resources": ["search", "stream", "lyrics", "metadata"],
  "settings": [
    { "key": "quality", "default": "lossless", "options": ["lossless", "hires", "high"] },
    { "key": "region", "label": "Catalog", "options": ["us", "jp"] }
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
- **Data saver:** while it's working, Mochy asks for **High** instead of Lossless or Hi-Res ([section 12](#data-saver)).

Every other declared setting is a field on the module's page ([section 5](#5-settings-people-can-change)) and goes with every call.

### `GET {base}/lyrics?title=&artist=&album=&duration=&isrc=`

`artist` is every artist joined with `, `, and `duration` is in seconds. Answer with any of these:

```json
{ "syncedLyrics": "[00:12.34] first line\n[00:15.80] second line" }
```

```json
{ "plainLyrics": "first line\nsecond line" }
```

`lrc` and `lyrics` are read too, and so is a plain-text body. Timed (LRC) lyrics are always preferred.

### `GET {base}/metadata?title=&artist=&album=&duration=&isrc=`

Song info for **About song**. The same question as `/lyrics`. Answer with whatever you know; leave out what you don't:

```json
{
  "album": "Short n' Sweet",
  "releaseDate": "2024-04-11",
  "genres": ["Pop", "Disco"],
  "writers": ["Sabrina Carpenter", "Amy Allen", "Steph Jones", "Julian Bunetta"],
  "producers": ["Julian Bunetta"],
  "label": "Island",
  "about": "A playful summer song about being someone's favourite drink.",
  "coverUrl": "https://…/cover-3000.jpg",
  "isrc": "USUM72401994"
}
```

- `releaseDate` is `"2024-04-11"`, `"2024-04"` or `"2024"`. `released` and `year` are read too.
- `genres`, `writers` and `producers` can be a list or one string with commas. A single `genre` works.
- `about` is a paragraph or two; `description` is read too. It shows under **About** when people pick **Modules** as the source.
- `coverUrl` (or `cover`, `artwork`) must be a `https://` link.
- Answer `404`, or `{}`, when you don't know the song.

### A whole web module in 30 lines

A Cloudflare Worker that serves your own files (free, no server to look after):

```js
// worker.js: a tiny Mochy module
const SONGS = [
  { id: "1", title: "My Song", artist: "Me", album: "Demo", duration: 201, url: "https://example.com/my-song.flac", genre: "Indie" },
];

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/manifest.json") {
      return json({ id: "my-songs", name: "My Songs", version: "1.0", description: "My own FLACs", resources: ["search", "stream", "metadata"] });
    }
    if (url.pathname === "/search") {
      const words = (url.searchParams.get("q") || "").toLowerCase().split(" ");
      const found = SONGS.filter((s) => words.every((w) => `${s.artist} ${s.title}`.toLowerCase().includes(w)));
      return json({ tracks: found.map(({ url, genre, ...track }) => track) });
    }
    if (url.pathname === "/metadata") {
      const song = SONGS.find((s) => s.title.toLowerCase() === (url.searchParams.get("title") || "").toLowerCase());
      return song ? json({ album: song.album, genres: [song.genre] }) : json({}, 404);
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

Deno Deploy, Vercel and Netlify work the same way. **GitHub alone can't host a song, lyrics or song info module.** It only serves files, and Mochy needs an answer that depends on the song it asks for. GitHub is fine for looks.

---

## 8. Zip modules

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

- **Pictures, fonts and glass shaders are unpacked, nothing else.** `.png`, `.jpg`, `.jpeg`, `.webp`, `.gif`, `.ttf`, `.otf`, `.agsl` and `.sksl` files in the manifest's folder (and the folders under it) are copied into Mochy when the zip is added, keeping their paths. Everything else, like loose code or text files, is ignored, apart from the signed code a `code` field names ([8.1](#81-zips-with-their-own-code)). Hidden macOS files (`__MACOSX`, `._…`) are skipped.
- **A zip without a `url` lives on the phone.** It never touches the network, and its page has no server to check.
- **A zip that plays songs, lyrics or song info needs a `url`:**

  ```json
  {
    "id": "my-flac",
    "name": "My FLAC",
    "icon": "icon.png",
    "url": "https://my-flac.example",
    "resources": ["search", "stream"]
  }
  ```

  It's then added just like pasting `https://my-flac.example`: the same calls, the same choices. The difference is that the manifest (and its pictures) come from the file. Without a `url`, Mochy says the zip "doesn't say where from".
- **Limits:** the manifest must be under 256 KB, and Mochy only looks through the first 200 entries for it. Pictures and fonts: up to 200 files, 8 MB each and 30 MB in all; a file over the limit is left out.
- **Removing** the module (swipe it left on the Modules page, or **Remove this module** on its page) deletes its unpacked pictures, fonts, shaders and settings too.
- **Updating:** importing a zip with the same `id` again updates the module. Its switch and settings stay.
- **Backups:** modules added from a zip are kept in Mochy's backup like every other module.

### 8.1 Zips with their own code

A zip can also bring a whole module written in Kotlin against `:modules:api`, the same kind as a built-in one. Mochy only runs that code when it's signed with Mochy's own key, so only Mochy's developers can make one ([section 14](#zip-modules-with-their-own-code) says how).

```
js-modules.zip
├── manifest.json     ← says "code": "module.apk"
└── module.apk        ← the module's code, signed like Mochy, never installed
```

```json
{
  "id": "addons",
  "name": "JS Modules",
  "description": "Your addon links and scripts in one place: songs, lyrics and song info.",
  "resources": ["search", "stream", "lyrics", "metadata"],
  "code": "module.apk"
}
```

- `name` and `resources` only fill in the sheet before it's added. Once it's in, the module says what it is, what it adds, its icon, its banner and its settings itself.
- **Signed like your Mochy:** a debug Mochy takes a zip built with the debug key, a release Mochy one built with the release key. Any other zip gets "it has to be signed with the same key as your Mochy".
- **It runs on its own copy of everything:** its own Kotlin, coroutines and libraries, in a class loader that can only see Android, Java and Mochy's bridge. Every call between the two goes across as JSON, so a different Kotlin version or Mochy's R8 can't break it.
- **Updating:** adding a newer zip with the same `id` swaps the running copy straight away. Its switch, choices and settings stay.
- **Removing** deletes its code and everything it kept.
- It takes the place of a built-in module with the same `id`, the way a module app does.
- **Backups** carry it, since its code sits in Mochy's files. It's checked again every time it's loaded.

---

## 9. Scripts for JS Modules

**JS Modules** is a zip module that runs JavaScript scripts. It lives in its own project outside this repo (`mochy-js-modules`, next to the Mochy folder). Once JS Modules is added, add scripts on its page under **Sources** with **Add from a file or a link**: pick a zip, or paste a link to a zip, a script, an index or an addon link. One zip can hold many scripts, and the module can hold as many as you like.

Each source on its page has its icon, a dot for whether it worked the last time it was asked (green or amber), and a line saying what it is and what it gives (*Script · Songs · Lyrics · v1.2 · someone*). Swipe a source left to remove it. **Check sources** asks every one of them once.

### What a script exports

A script exports any of these four functions. **Songs** need the first two together; **lyrics** and **song info** can come with them or on their own.

```js
// module.exports, export async function, export default {...} and export { a as b } all work
module.exports = {
  // songs: search, then the stream of the row Mochy picked
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
  },

  // lyrics: timed lyrics as LRC, or plain lines
  async getTrackLyrics(track, context) {
    const res = await fetch('https://my-lyrics.example/get?artist=' + encodeURIComponent(track.artists[0]) + '&title=' + encodeURIComponent(track.title));
    const data = await res.json();
    return { syncedLyrics: data.lrc };     // or { plainLyrics }, or just the text as a string
  },

  // song info for About song
  async getTrackMetadata(track, context) {
    return { genres: ['Pop'], releaseDate: '2024-04-11', writers: ['Amy Allen'], about: 'A summer song.' };
  }
};
```

- `searchTracks` answers `{ tracks: [...] }`; `artists: [...]` and `durationMs` are read too.
- `getTrackStreamUrl` answers `{ streamUrl, track }`, or just the link as a string. An `.m3u8` or `.mpd` link (or an HLS/DASH `mimeType`) plays as that kind of stream. `"encrypted": true` is passed over.
- `getTrackLyrics(track, context)` answers `{ syncedLyrics }`, `{ lrc }`, `{ lyrics }`, `{ plainLyrics }` or a string. Every lyrics script is asked at once, and the first one on the list with timed lyrics wins.
- `getTrackMetadata(track, context)` answers the same object as a web module's [`/metadata`](#get-basemetadatatitleartistalbumdurationisrc). Every song info script is asked at once, and each fact comes from the first one on the list that knows it.
- `track` is `{ title, artist, artists, album, duration, durationMs, isrc }`: `artist` is every artist joined with `, `, `artists` the list, `duration` seconds.
- A function that throws, or answers nothing, just means that script didn't have it. Return `null` when you don't know the song.

### What a script can use

- `fetch` (text, JSON and binary bodies both ways: `arrayBuffer()`, `blob()`, a `Uint8Array` as the body), `setTimeout`, `setInterval`, `console`, `URL`, `URLSearchParams`, `Headers`, `AbortController`, `TextEncoder`/`TextDecoder`, `atob`/`btoa`, `crypto.randomUUID`, `queueMicrotask`, `performance.now` and `structuredClone`. There's no `require` and no `import`.
- A call that runs past 20 s is stopped. Timers never hold an answer up: one that isn't due yet runs the next time the script is used.
- Keep a login token in a top-level variable: the script keeps running between calls. A script gets up to three interpreters side by side when several songs or downloads need it at once, and each one starts the script once.

### The zip

```
my-scripts.zip
├── index.json        ← optional: names, icons, authors, versions
├── tidal.js
└── lyrics.js
```

`index.json` lists the scripts under any `"category:…"` key:

```json
{ "category:music": [
  { "id": "tidal", "name": "Tidal", "author": "someone", "version": "1.2",
    "icon": "https://example.com/tidal.png",
    "tags": ["LOSSLESS", "HI-RES"], "download": "tidal.js" },
  { "id": "lyrics", "name": "My Lyrics", "download": "lyrics.js" }
] }
```

- `icon` is a link, or a path next to the index when the index came from a link. It's shown on the script's row.
- `download` can also be a link, and then the script is fetched when it's added.
- Without an index, every `.js` that has any of the four functions becomes a script named after its file. A `manifest.json` whose `main` names the script works too.
- Adding a script with the same id again updates it.

**Addon links** (a web module's link pasted on JS Modules' page) stay in its list instead of becoming modules of their own. Their `/search`, `/stream`, `/lyrics` and `/metadata` are asked with the scripts, going by the `resources` and `icon` in their manifest. A zip or link with no script, but a web module's `manifest.json` that names a `url`, is added as its own module instead.

---

## 10. Putting your module in the catalog

**Settings → Modules → Add a module** lists the modules in [`catalog.json`](catalog.json), read straight from GitHub, so the list grows without an app update. To get listed, open a pull request adding an entry:

```json
{
  "modules": [
    {
      "name": "My FLAC",
      "description": "My own library, lossless",
      "url": "https://my-flac.example",
      "icon": "https://my-flac.example/icon.png",
      "adds": ["audio", "lyrics", "metadata", "look"],
      "author": "someone"
    }
  ]
}
```

- `adds` is what the sheet shows before anything is checked: `audio` (or `songs`), `lyrics`, `metadata` (or `info`), `look`.
- `icon` must be a `https://` link. It's shown on the module's row in the sheet.
- Tapping a catalog entry still checks the real manifest and shows what it adds before anything is installed.
- Pull requests are reviewed by hand. A module that doesn't work, describes itself wrongly, pretends to be someone else or does anything shady isn't merged.

Want to share a module without the catalog? Post its link or its zip in [Mochy's Bar on Discord](https://discord.gg/NTmqVphbRY). The card at the top of the Modules page goes there.

---

## 11. Testing your module

**From a computer**, check every call with `curl` before you touch the phone:

```sh
curl https://my-flac.example/manifest.json
curl "https://my-flac.example/search?q=benny%20blanco%20eastside&quality=LOSSLESS"
curl "https://my-flac.example/stream/123?quality=LOSSLESS"
curl -I "<the url your stream call returned>"     # should be 200 or 206, an audio type, ideally Accept-Ranges
curl "https://my-flac.example/lyrics?title=Eastside&artist=benny%20blanco&duration=173"
curl "https://my-flac.example/metadata?title=Eastside&artist=benny%20blanco&duration=173"
```

**In Mochy**, open the module's page:

- **Check server** asks for your manifest again and says whether it answered.
- **Test the module** at the bottom of the page: **search any song**, or take the one that's playing, and tap it. Mochy runs everything your module adds with that song, on this phone, and shows each step:
  - **Songs:** finding the song, the stream, a license if the stream needs one, and decoding two seconds, muted.
  - **Lyrics:** how many lines came back, timed or plain, and the first one.
  - **Song info:** the facts it gave.
  - The ••• beside a song plays it instead.
- After turning it on, play anything: a toast says where each song came from, and **About song** shows your facts.

**With logs** (phone connected, USB debugging on):

```sh
adb logcat -s ModuleTest SourceModules ModuleMetadata
```

`SourceModules` prints, for every song, which provider was asked, what it answered, and why it was passed over.

---

## 12. How Mochy uses your module

### Playing

**Settings → Audio Lab → Audio Quality → Provider** is the provider order. Each song plays from the first provider that's on and has it. YouTube Music can sit anywhere in the order, or be switched off. Dragging modules into order on the Modules page changes this order too.

For each song, Mochy:

1. Searches your module (`/search`), and picks the matching recording with its matcher.
2. Asks for the stream (`/stream/{id}`), unless the search already gave a `streamURL`.
3. Passes over a stream the phone can't decode, and one that's encrypted.
4. Plays it, or moves on to the next provider.

Mochy remembers a song your module doesn't have for 30 minutes, so it doesn't search for it again on every play. A module that doesn't answer is left alone for 10 minutes, then tried again.

### Lyrics and song info

Lyrics modules that are on come first among the lyrics sources, in the order of the Modules page. Song info modules are all asked together when **About song** opens (they get 12 seconds); each fact (genre, release date, album, writers, producers, label) comes from the first module on the list that knows it, then from Mochy's own sources. What your module says the song is about is one of the sources under **About**, listed as **Modules**.

### Downloading

**Audio Quality → Download order** is a separate order, just for downloads. It starts as a copy of the provider order. Each download comes from the first provider that can give the song as **one whole file** this phone can play.

- HLS/DASH streams and streams that need a license are skipped for downloads (they still *play* fine).
- YouTube is always available for downloads, even when it's off for playing. The Download setting (High, Medium, Low) is its quality.
- A download that fails from your module retries from the next provider down.
- Give `codec` in your stream answer: the downloaded song's format badge comes from it.
- Data saver never changes downloads.

### Data saver

**Settings → App behaviour → Data saver** cuts what Mochy uses on mobile data (or everywhere, with **Only on mobile data** off). While it's working:

- A module's **Stream** choice steps down to its `high` option, or one step below its best when it has no option called `high`. A web module is asked for `quality=HIGH` (or your declared "high" word) instead of Lossless or Hi-Res. A pick that was already lighter stays.
- YouTube plays its lightest stream.
- Canvas covers and video backgrounds stay off. The full video mode still plays when someone opens it.
- The module's page shows a **Data saver is on** card saying what it does to that module. A module with no lighter stream plays as usual.

---

## 13. Rules to follow

For everyone making a module:

- [ ] **Use https** for every link: manifest, service, streams and pictures.
- [ ] **Answer fast.** Mochy waits 12 seconds to connect and 20 seconds for an answer, then moves on. A slow module makes every song wait.
- [ ] **Answer with JSON** in the shapes above, with `Content-Type: application/json`. Return an error status (404, 503…) when something's wrong, never a web page with 200.
- [ ] **Keep ids stable.** The `id` you return from search must work in `/stream/{id}`, your manifest's `id` must never change, and neither should your settings' `key`s.
- [ ] **Be honest in search results and song info.** Real titles, every artist, real durations, real credits. A wrong duration makes Mochy pick the wrong recording or none.
- [ ] **No encrypted streams.** They're skipped anyway.
- [ ] **Bump `version`** whenever you change your manifest.
- [ ] **Never put secrets in a manifest you share.** If your link carries a personal key (some debrid services put an API key in the path), that link *is* a password. Anyone who has it can use your account. Don't post it publicly, and get a new key if you did. A `text` setting is the place for a key: it stays on the phone it was typed on.
- [ ] **Don't pretend to be someone else.** No other service's name or logo as your own icon or banner.
- [ ] **Menu rows open what they say.** A row's title must say where it goes.
- [ ] **Looks only use the keys in [section 6.1](#61-look-settings).** Anything else is ignored.
- [ ] **Keep a glass shader light.** It runs on every bar, every frame, on slow phones too ([6.7](#67-a-glass-for-the-bottom-bar)).

---

## 14. For Mochy developers: changing the module interface

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
| Every module, its switch, choices, settings, order and added date; adding from a link or a zip | `app/.../modules/ModuleRegistry.kt` |
| Provider order, download order, data saver's lighter picks, and the walk down each | `app/.../modules/SourceModules.kt` |
| Song info from modules, merged for About song | `app/.../modules/ModuleMetadata.kt`, read by `ui/player/info/SongAbout.kt` |
| Link modules (manifest + HTTP calls) | `app/.../modules/web/WebModuleManifest.kt`, `WebModule.kt` |
| A manifest's settings as fields and the looks they bring; resolving pictures | `app/.../modules/web/ManifestSettings.kt` |
| Zip modules without a service | `app/.../modules/local/LocalModule.kt` |
| Skins: the model, the parser, unpacking a zip, the look that's on, menu rows | `app/.../modules/skin/` (`ModuleSkin.kt`, `SkinParser.kt`, `SkinFiles.kt`, `ActiveSkin.kt`, `ModuleMenu.kt`) |
| The module player style | `app/.../ui/player/styles/ModulePlayerStyle.kt` |
| Menu rows in the player | `app/.../ui/player/menu/ModuleMenuRows.kt` |
| A look's own glass: compiling it, drawing it, the switch | `ActiveSkin.kt` (compiles off the main thread), `app/.../ui/component/ModuleGlass.kt`, `FrostedGlass.kt` (`docs/notes/module-glass.md`) |
| Data saver | `app/.../utils/DataSaver.kt` |
| A look's font across the app | `LookFont` in `app/.../ui/theme/Type.kt` |
| Looks: applying and giving settings back | `app/.../modules/ModuleLooks.kt` |
| The catalog | `modules/catalog.json` (read by `app/.../modules/ModuleCatalog.kt`) |
| Pages: the modules list, a module's page, its parts, module pages, settings rows | `app/.../ui/screens/settings/modules/` |
| Downloads | `app/.../playback/DownloadUtil.kt` |

### The contract and its version

`:modules:api` is plain Kotlin on purpose: no Android, no Media3, no HTTP library. That way a module can be built as its own APK and loaded at runtime. It has a version, `MODULE_API_VERSION` (now `3`). A module app built for a different version is skipped with a log line, so it can't crash Mochy.

**When to bump `MODULE_API_VERSION`:**

| Change | Bump? |
|---|---|
| Add a new optional interface (like `ServerListModule`, `ModuleArt`, `ModuleFields`, `MetadataModule`) | No |
| Add a field with a default to a class only Mochy creates (like `HttpResponse.headers`) | No |
| Add a field with a default to the end of a class modules create, with `@JvmOverloads` on its constructor so the old constructor stays (like `ModuleItem.icon`) | No |
| Add a value to `ModuleFeature` | No, a module only lists the ones it adds |
| Rename, remove or change the type of anything a module calls or implements | **Yes** |
| Add a method to an interface modules implement, even one with a default body | **Yes**, an older module app was built without it |
| Change what a value means (units, ranges) | **Yes** |

After a bump, every module app has to be rebuilt against the new `:modules:api`. Built-in modules are rebuilt with Mochy anyway.

### What a module can implement

| Interface | What it gives Mochy |
|---|---|
| `SourceModule` | `resolve` and `diagnose` for songs |
| `LyricsModule` | `lyrics(track)` as LRC or plain lines |
| `MetadataModule` | `metadata(track)`, a `TrackMetadata` |
| `LookModule` | `look`, a `LookSetting` name to its value |
| `ModuleArt` | `icon` and `banner`: a link, or a file path (in `host.folder` for a module with its own code) |
| `ModuleFields` | `fields` (each a `ModuleField`: `Text`, `Select` or `Toggle`), and `onFields(values)` |
| `ServerListModule`, `ItemListModule` | The lists on its page; `ModuleItem` can carry an `icon` link and `working` |

### Settings for modules with their own code

Implement `ModuleFields`. Mochy keeps each field's value (its `default` until someone changes it) and calls `onFields` with every value once right after `attach`, and again each time one changes. Keep them in a field and read them in your calls. Over the bridge, `BridgedModule` always implements `ModuleFields`, `ModuleArt` and `MetadataModule`; a zip packed before they existed simply has no fields, no pictures and no song info.

### Adding a look setting

1. Add the key to `LookSetting` in `MochyModule.kt`, with a one-line comment saying its values.
2. Add it to `ModuleLooks.Settings` in the app, with the preference key it writes and a parser that turns away anything out of range (`enumName<…>`, `number(it, min..max)` or `bool`).
3. Add a row to the table in [section 6.1](#61-look-settings).

A look can never reach anything that isn't in `ModuleLooks.Settings`. That's the safety line, so keep it to look settings: no accounts, no network, no playback.

### Adding a player block, a background, a page block or a menu row target

1. Add it to the model in `ModuleSkin.kt` (`SkinBlock`, `SkinBackground`, `PageBlock` or `ActionTarget`).
2. Read it in `SkinParser.kt`. Give every field a default, and clamp every number, so a typo can't break the player.
3. Draw it in `ModulePlayerStyle.kt`, `ModulePageScreen.kt` or `ModuleMenuRows.kt`. Follow `CLAUDE.md`'s smoothness rules: read the playback position and anything else that changes every frame inside `drawBehind`/`graphicsLayer`, never in composition; pause any loop while the style is hidden (`rememberStyleShowing`, `LocalAppContentObscured`) and wrap it in `damageBoundary()`; use the GlassKit helpers for blur.
4. Add a case to `app/src/test/.../modules/skin/SkinParserTest.kt` or `modules/web/ManifestSettingsTest.kt`, and a row to [section 6](#6-changing-the-interface-looks-players-fonts-pages-and-menu-rows).

An older Mochy skips a block it doesn't know, so adding one never breaks older layouts. Renaming or removing one does, so don't.

### Changing what a link or zip module can do

- New manifest fields go in `WebModuleManifest`, always with a default, so every manifest out there keeps working.
- New calls or answer fields go in `WebModule`. Read new fields as optional, and keep reading the old ones.
- Update this README in the same change, then run `python3 docs/modules/embed_readme.py` so `docs/modules/index.html`, the same guide as a page, carries it too.

### Adding a built-in module

1. Make a Gradle module `modules/<name>` that depends only on `:modules:api` (plus `kotlinx-serialization-json` if you need it). Add it to `settings.gradle.kts` and as a dependency of `:app`.
2. Implement `SourceModule`, `LyricsModule`, `MetadataModule` and/or `LookModule`. Do all HTTP through `host.http(…)`, keep small state in `host.storage` and bigger files in `host.folder`.
3. Add it to the built-in list in `ModuleRegistry.load()`.
4. Give it a logo in `ModuleParts.logoFor` and `providerLogo` in `AudioQualityScreen.kt`, or implement `ModuleArt`, or it shows its first letter.
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
- `./gradlew :modules:bridge:test` for the bridge, `./gradlew :modules:<name>:test` for a module's tests.
- `./gradlew :module:test` inside `mochy-js-modules` for JS Modules.

---

## 15. Troubleshooting

| What you see | Why, and what to do |
|---|---|
| "That's a website, not a module" | You pasted a website. Mochy needs a module's link or its `manifest.json`. A Monochrome site goes on Monochrome's page under **Servers**. |
| "That link answered, but it isn't a module Mochy can use" | The manifest has no `id` and no `name`, isn't valid JSON, or adds nothing. |
| "That file isn't a module" | The zip has no `manifest.json` at its root or one folder in, or isn't a zip. |
| "…its .zip doesn't say where from" | Your zip lists `search`, `stream`, `lyrics` or `metadata` but has no `url`. |
| "…it has to be signed with the same key as your Mochy" | The zip's code was built with another key, like a debug zip on a release Mochy. Build it with the same build type and key as the Mochy it's for. |
| "This module's code didn't start" | It was built for another `MODULE_API_VERSION`, or its `module.apk` has no entry `meta-data`. `adb logcat -s CodeModules` says which. |
| My icon or banner doesn't show | The path is relative to `manifest.json` and case-sensitive, and the file must be `.png`, `.jpg`, `.webp` or `.gif` under 8 MB. For a link module, it must be reachable next to the manifest. |
| My settings don't show | `settings` must be a list of objects, each with a `key`. A song module's `quality` and `atmos` show as Mochy's **Stream** and **Dolby Atmos** choices instead. |
| Changing a setting doesn't change the look | The module has to be **on**, the option's `look` must use keys from [6.1](#61-look-settings), and only one look is on at a time. |
| My glass doesn't show | The module has to be on, and an option may have set `bottomBar` back to `FROSTED`. It needs Android 13 and blur switched on, and only works from a zip. If the shader didn't compile, the module's page says why under **Module info**. |
| My page isn't in Settings | It needs `"inSettings": true`, and the module has to be on. Its `id` may only use `a`–`z`, `0`–`9`, `-` and `_`, and it needs at least one block. |
| My menu row doesn't show | The module has to be on. A row needs a `title` and one of `link` (https), `page` (an id that exists) or `search`. Rows only show while a song is loaded. |
| The test finds the song but it never plays from your module | Check the stream call with `curl -I`: it must give audio (200 or 206), not a web page or an error. Check that the codec and sample rate are ones the phone can decode. |
| Songs come from YouTube instead | Your module is below YouTube in the provider order, it's switched off, or it was skipped for 10 minutes after not answering. `adb logcat -s SourceModules` says which. |
| Songs play in lower quality than picked | Data saver is working. The module's page says so in its **Data saver is on** card. |
| About song doesn't show my facts | The module has to be on and list `metadata`. It gets 12 seconds; a slower answer is left out. Pick **Modules** under **About** to read your `about` text. |
| Downloads don't use your module | Check the **download order**, not the provider order. Also, an HLS/DASH stream can't be downloaded: give one whole file. |
| The look didn't change something | The key isn't in [section 6.1](#61-look-settings), the value is out of range, or the user changed that setting by hand. |
| My player doesn't show up | Your look has to be **on**. `player` needs at least one block Mochy knows. Then hold the player: your style is the first in the selector. |
| A block is missing from my player | Its `type` is misspelled or unknown, so it was skipped. Check the tables in [6.2](#62-a-player-of-your-own). |
| My font isn't used | Fonts only work from a zip, as `.ttf` or `.otf`, and the `regular` path must point at the file. A broken font is skipped. |
| "… didn't answer" under the module's name | Your server didn't answer. It's tried again after 10 minutes, or right away with **Check server**. |
| A script's row has an amber dot | It failed the last time it was asked. The line under its name says why. **Check sources** asks it again. |
