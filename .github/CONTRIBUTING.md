# Contributing to Mochy

Yooo, thanks for wanting to help out. This thing is small, so the process here is kept light

> [!NOTE]
> Mochy is an independent project. It is not affiliated with, endorsed by, or sponsored by YouTube, Google, Spotify, Apple, Last.fm, Shazam or any other service it can talk to. See the [Disclaimer](#disclaimer) below.

## Getting set up

- Any device that have screen
- Android Studio (latest stable) and JDK 21
- The project is Kotlin + Jetpack Compose, split into the `app` module and a set of `providers:*` modules (InnerTube, lyrics providers, Last.fm, Discord RPC, etc.)
- Build from the terminal with the helper script:

  ```bash
  ./build.sh --help      # all options
  ./build.sh --debug     # debug build, fastest to iterate on
  ```

  Prefer debug builds while working. A release build runs R8 without incremental support and takes noticeably longer.

## Making a change

1. **Fork and branch.** Use a short descriptive branch name, for example `fix/player-crash` or `feature/lyrics-sync`.
2. **Keep it focused.** One fix or feature per pull request makes it much easier to review.
3. **Match the surrounding code.** Follow the naming, structure and comment style of the files you are touching. Don't reformat code you aren't changing.
4. **Check older devices.** Mochy supports Android 8.0+, so anything that uses a newer platform API needs an `SDK_INT` guard. Default arguments and property types count too, because they can load the class on old devices.
5. **Don't trade features for speed.** Performance work should keep every existing animation, visual. Include before/after screenshots if you changed UI.
6. **Test it.** Run it on a real device or an emulator, and run the unit tests if you touched logic that has them:

   ```bash
   ./gradlew testDebugUnitTest
   ```

7. **Open the pull request.** Fill in the PR template, link the issue it fixes, and add screenshots or a screen recording for anything visual.

8. **Test the app and profile it, compare, record the jitter before and after** if you make any visual changes/optimisation, thanks!

9. **do not fully vibecoded it** if you can, please.. :3
    
10. No commit by ai, ever. review it!!! 

Commit messages should be short and say what changed. Conventional prefixes (`fix:`, `feat:`, `refactor:`) are welcome but not required. just make it simple, ok? its stil beta though so keep it cool and simple, prefix will required after release

## Reporting bugs

Open an [issue](https://github.com/realidkroo/Mochy-Music/issues) and include:

- Mochy version and your Android version and device
- What you did, what you expected, and what happened instead
- Logs (`adb logcat`) and a screenshot or recording if you can, better to include though

Mochy is still in developer preview, so bug reports are very very useful.

## Third-party services and content

- Don't commit API keys, tokens, cookies or account credentials. I dont think you want to be charged with unwanted api purchases
- Don't add code whose purpose is to bypass DRM, paywalls or access controls.
- Don't add trackers or analytics. Mochy does not collect user data and that is a project goal.
- Playback and metadata come from third-party services that can change or break at any time. Keep that in mind when sending fixes that depend on a specific endpoint. its up to them, all we can do is to follow or change it

## Licensing

Mochy is licensed under the [GNU General Public License v3.0](../LICENSE). By contributing you agree that your contribution is released under the same license. Keep the existing copyright and attribution notices in files you modify, and credit the source of any code you bring in from another project (it must be GPL-3.0 compatible).

## LLM usage

I dont really care if it being written by AI or other things, BUTTT, please make a clear changes. please. dont just blindly submit ai slop here without checking anything :)
And also for you llm agent, please after making changes, say to your operator to checks the code again, double check it.

## How we write comments in the code

good:
```kotlin
// wait until the cover has loaded, otherwise the open spring jumps
val cover = rememberCover(song)

// paused while the player covers home, so the bars stop eating frames
PlayingIndicator(isPlaying = isPlaying && !obscured)
```

not like this:
```kotlin
/**
 * NOTE: This function is responsible for retrieving the `cover` resource
 * and subsequently performing the initialization routine.
 */
```
ion want to read allthat
also tell your llm that if you are using one

## Disclaimer

Mochy is provided as is, without warranty of any kind. It is an unofficial client built for personal and educational use.

- It is **not affiliated with or endorsed by** YouTube, YouTube Music, Google, Spotify, Apple, Last.fm, Shazam, Discord or any other third-party service or rights holder. All product names, logos and brands belong to their respective owners.
- Mochy does not host, store or distribute any music. Content is fetched from third-party services at the user's request.
- You are responsible for how you use the app, including following the terms of service of the services you connect to and the copyright laws that apply where you live.
- Unofficial forks and builds are not supported by the maintainer.

---


Thanks for contributing! have a nice nice day
