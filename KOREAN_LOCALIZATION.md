# Korean Localization Notes

Base commit: `7e590802345329bb108d74f337af1ea9fe407d2a`
Working branch: `korean-localization`

## Localization approach

The existing `values-ko` resources remain the shared Korean translation. Missing entries from the default English resources were merged into that directory, and English overrides in `values-ko-rKR` were removed. Existing translations were preserved, except that the outdated app name in `wrapped_intro_title` was corrected to `mochy`.

Korean coverage includes 4,309 original resource keys and 94 keys added while extracting hardcoded UI text, for a total of 4,403 keys. Brand names, codecs, units, and formatting-only values account for 120 entries intentionally identical to the source. Key counts are not sentence counts; individual plural quantities are not counted separately.

Coverage includes home, search, library, player, lyrics, settings, Audio Lab, equalizer, onboarding, profile, statistics, imports, device linking, TV, car integration, and update messages. Hardcoded text in donation screens, song selection, artist information, Cast controls, accessibility descriptions, and import/update notifications was also moved into resources.

Built-in Apple Music, Monochrome, and JioSaavn descriptions and options are localized in the presentation layer. Localization checks both the `BuiltIn` origin and implementation type. Stored identifiers and option values are not translated. External modules and unknown options retain their existing presentation.

## Translation integrity checks

```sh
python scripts/test_korean_resources.py
```

The checks cover default/Korean key correspondence, duplicate XML entries, resource types, formatting arguments for each plural quantity, the required `other` quantity, regional English overrides, and Korean language availability. The check is included in PR CI, and matching Korean regression cases were added to the JVM resource tests.

Resource coverage alone does not establish translation quality. Cross-review and automated checks do not replace device inspection of line wrapping, font fallback, truncation, or wording in context.

## Final build and verification

The validated build is `8.4.2 build#1268`, versionCode `73`, containing Korean localization and profileable loading diagnostics.

```sh
./gradlew :app:assembleUniversalFossProfileable
./gradlew :app:testUniversalFossDebugUnitTest --tests com.mochyy.mochymusic.diagnostics.LoadingTraceTest
python scripts/test_korean_resources.py
```

The repository's existing `profileable` variant inherits the release configuration: R8 optimization, resource shrinking, and a non-debuggable application. Its application ID is `com.mochyy.mochymusic.profileable`. It uses the local debug signing configuration and installs alongside the original application. Builds used JDK 21 and the Gradle Wrapper specified by the project.

Loading diagnostics run only in profileable builds. Fixed phase names identify Application initialization, home-cache restoration, local database work, provider calls, state publication, and image fetch/decode. Trace arguments do not accept URLs, accounts, cookies, media identifiers, or exception messages. API 29 and later emit asynchronous Perfetto sections prefixed with `MochyLoading/`; supported older versions use local logging. Normal release/debug variants do not install the image tracing listener.

Home's fully drawn report indicates basic interaction readiness after local data preparation and the launch intro. It does not wait for every online recommendation or cover image. Coil completion indicates delivery to the target, not the first rendered pixel. Existing request scheduling, filters, and animations are preserved.

The final configuration passed eight loading-trace core JVM tests and four Korean resource integrity tests. On a Galaxy A33 5G running Android 16, initial display took 1,147 ms for an empty-cache online launch and 1,145 ms for a process restart with caches retained. These are two diagnostic observations, not a statistical performance comparison or a guarantee for every screen. The first run's Perfetto time to basic UI readiness was approximately 1,346 ms.

The local APK passed v2 signature and 16 KB alignment verification. Its SHA-256 was:

```text
10e61799443214e200bd8681e43bc90f442cfdd3e721176dcee38c32bc77e41a
```

Only the original application and the final test build were retained on the A33; intermediate test applications and inspection tools were uninstalled. APKs, signing keys, private build settings, and raw device logs are not included in this repository.

Two known failures remain in the full JVM suite. The passing results above apply only to the listed checks; the full suite is not claimed to pass. Existing SDK/R8 compatibility warnings also remain.

## Earlier debug-build verification

Earlier device testing used `universalFossDebug`, application ID `com.mochyy.mochymusic.debug`, which installs separately from the original application. The build produced through the contribution guide's `build.sh --debug` command was `8.4.2 build#1266`, versionCode `73`. The helper incremented that build number.

On November 18, 2026, the A33 5G running Android 16 was used to verify update installation, Korean text, search, playback, pause, restart, and English/Korean switching. Device testing identified and led to fixes for hardcoded English onboarding dates and duplicated profile dates. Detailed raw evidence was retained in local verification artifacts rather than published in this repository.

A local test signing key was supplied through an external Gradle init script. Production signing, minimum SDK, and target SDK settings were not changed. The FOSS APK does not include GMS Google Cast.

## Limitations

- Song titles, artist names, lyrics, and release notes returned by remote services retain their original text.
- External web sign-in pages and arbitrary UI or diagnostic text from external modules are not translated.
- Some dynamic diagnostics and account-status messages in built-in modules retain their original text where stable message identifiers are unavailable.
- Most existing Korean wording was preserved during the merge. Older and newer terminology can coexist; further consistency edits should be reviewed in screen context.
- Device checks do not establish that every screen has been verified. Automated integrity checks, device observations, and unverified areas must be considered separately.
