package com.mochyy.mochymusic.security

import com.mochyy.mochymusic.backup.WebDavCloud
import com.mochyy.mochymusic.cast.CastMediaProxy
import com.mochyy.mochymusic.constants.DiscordLoginStateKey
import com.mochyy.mochymusic.constants.DiscordLoginVerifierKey
import com.mochyy.mochymusic.constants.MonochromeTurnstileJwtExpiryKey
import com.mochyy.mochymusic.constants.MonochromeTurnstileJwtKey
import com.mochyy.mochymusic.core.updater.ApkVerifier
import com.mochyy.mochymusic.importer.transfer.TransferArchive
import com.mochyy.mochymusic.link.LinkAuth
import com.mochyy.mochymusic.link.LinkCredentials
import com.mochyy.mochymusic.link.LinkRequest
import com.mochyy.mochymusic.modules.security.SecretGuard
import com.mochyy.mochymusic.ui.screens.settings.importsync.isYouTubeMusicOrigin
import com.mochyy.mochymusic.utils.CrashHandler
import com.mochyy.mochymusic.utils.LanHttpClient
import com.mochyy.mochymusic.utils.SecretPreferences
import com.mochyy.mochymusic.utils.SecretVault
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class AppSecurityHardeningTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Before
    fun setUp() {
        SecretVault.useTestKey(ByteArray(32) { (it * 11 + 5).toByte() })
        SecretGuard.clear("test_crash")
    }

    @After
    fun tearDown() {
        SecretVault.useTestKey(null)
        SecretGuard.clear("test_crash")
    }

    @Test
    fun lanHttpClientPrivateAddressValidation() {
        val allowed = listOf(
            "10.0.0.1",
            "10.255.255.254",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.0.1",
            "192.168.1.50",
            "fc00::1",
            "fd12:3456:789a::1",
            "fe80::1",
        )
        allowed.forEach { ip ->
            assertTrue("expected private LAN: $ip", LanHttpClient.isPrivateLanAddress(InetAddress.getByName(ip)))
        }

        val blocked = listOf(
            "127.0.0.1",
            "::1",
            "0.0.0.0",
            "169.254.169.254",
            "100.64.0.1",
            "172.15.255.254",
            "172.32.0.1",
            "8.8.8.8",
            "1.1.1.1",
            "239.255.255.250",
            "2001:4860:4860::8888",
        )
        blocked.forEach { ip ->
            assertFalse("expected blocked non-LAN: $ip", LanHttpClient.isPrivateLanAddress(InetAddress.getByName(ip)))
        }

        assertTrue(LanHttpClient.isLocalOrLoopbackAddress(InetAddress.getByName("127.0.0.1")))
        assertTrue(LanHttpClient.isLocalOrLoopbackAddress(InetAddress.getByName("::1")))
        assertTrue(LanHttpClient.isLocalOrLoopbackAddress(InetAddress.getByName("192.168.1.10")))
        assertFalse(LanHttpClient.isLocalOrLoopbackAddress(InetAddress.getByName("8.8.8.8")))
        assertFalse(LanHttpClient.isLocalOrLoopbackAddress(InetAddress.getByName("169.254.169.254")))
    }

    @Test
    fun lanHttpClientValidateUrlBlocksSsrfAndSenderMismatch() {
        val ok = LanHttpClient.validateLanUrl("http://192.168.1.42:8080/desc.xml", expectedHostIp = "192.168.1.42")
        assertEquals("192.168.1.42", ok.host)
        assertEquals(8080, ok.port)
        assertEquals("/desc.xml", ok.pathAndQuery)

        val rejected = listOf(
            { LanHttpClient.validateLanUrl("http://192.168.1.42:8080/desc.xml", expectedHostIp = "192.168.1.99") },
            { LanHttpClient.validateLanUrl("http://127.0.0.1:47801/library") },
            { LanHttpClient.validateLanUrl("http://169.254.169.254/latest/meta-data/") },
            { LanHttpClient.validateLanUrl("http://user:pass@192.168.1.42/desc.xml") },
            { LanHttpClient.validateLanUrl("file:///etc/hosts") },
            { LanHttpClient.validateLanUrl("http://1.1.1.1/") },
        )
        rejected.forEach { call ->
            var threw = false
            try {
                call()
            } catch (_: IOException) {
                threw = true
            }
            assertTrue("Expected IOException for unsafe LAN URL", threw)
        }
    }

    @Test
    fun lanHttpClientRoundTripsContentLengthAndChunked() {
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val worker = thread(isDaemon = true) {
                // Request 1: Content-Length
                server.accept().use { s ->
                    val input = s.getInputStream().bufferedReader()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 5\r\n\r\nhello"
                    s.getOutputStream().write(resp.toByteArray(Charsets.US_ASCII))
                    s.getOutputStream().flush()
                }
                // Request 2: Chunked
                server.accept().use { s ->
                    val input = s.getInputStream().bufferedReader()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val resp = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nmochy\r\n6\r\n-music\r\n0\r\n\r\n"
                    s.getOutputStream().write(resp.toByteArray(Charsets.US_ASCII))
                    s.getOutputStream().flush()
                }
            }

            LanHttpClient.execute("GET", "http://127.0.0.1:$port/ping", allowLoopback = true).use { res ->
                assertEquals(200, res.code)
                assertEquals("hello", res.body.string())
            }
            LanHttpClient.execute("GET", "http://127.0.0.1:$port/chunked", allowLoopback = true).use { res ->
                assertEquals(200, res.code)
                assertEquals("mochy-music", res.body.string())
            }
            worker.join(3000)
        }
    }

    @Test
    fun castMediaProxyScopesYouTubeCookieStrictly() {
        assertTrue(CastMediaProxy.shouldAttachYouTubeCookie("https://rr1---sn-abc.googlevideo.com/videoplayback?id=1"))
        assertTrue(CastMediaProxy.shouldAttachYouTubeCookie("https://googlevideo.com/videoplayback"))
        assertTrue(CastMediaProxy.shouldAttachYouTubeCookie("https://music.youtube.com/stream"))

        assertFalse(CastMediaProxy.shouldAttachYouTubeCookie("http://rr1---sn-abc.googlevideo.com/videoplayback"))
        assertFalse(CastMediaProxy.shouldAttachYouTubeCookie("https://evilgooglevideo.com/stream"))
        assertFalse(CastMediaProxy.shouldAttachYouTubeCookie("https://youtube.com.attacker.example/stream"))
        assertFalse(CastMediaProxy.shouldAttachYouTubeCookie("https://aac.saavncdn.com/song.m4a"))
        assertFalse(CastMediaProxy.shouldAttachYouTubeCookie("https://api.monochrome.st/stream.flac"))
    }

    @Test
    fun apkVerifierUrlAllowlistAndSafeFilename() {
        assertTrue(ApkVerifier.isAllowedUpdateUrl("https://github.com/realidkroo/Mochy-Music/releases/download/v8.4.0/mochy.apk"))
        assertTrue(ApkVerifier.isAllowedUpdateUrl("https://objects.githubusercontent.com/github-production-release-asset/mochy.apk"))
        assertTrue(ApkVerifier.isAllowedUpdateUrl("https://release-assets.githubusercontent.com/abc/mochy.apk"))

        assertFalse(ApkVerifier.isAllowedUpdateUrl("http://github.com/realidkroo/Mochy-Music/releases/download/v8.4.0/mochy.apk"))
        assertFalse(ApkVerifier.isAllowedUpdateUrl("https://github.com/attacker/Mochy-Music/releases/download/v9.0/evil.apk"))
        assertFalse(ApkVerifier.isAllowedUpdateUrl("https://evil.example/mochy.apk"))
        assertFalse(ApkVerifier.isAllowedUpdateUrl("https://user:pass@github.com/realidkroo/Mochy-Music/releases/download/v1/a.apk"))
        assertFalse(ApkVerifier.isAllowedUpdateUrl("https://nightly.link/realidkroo/Mochy-Music/workflows/nightly.yml/main/app.zip"))
        assertTrue(ApkVerifier.isAllowedUpdateUrl("https://nightly.link/realidkroo/Mochy-Music/workflows/nightly.yml/main/app.zip", allowNightlyLink = true))

        assertTrue(ApkVerifier.isSafeExternalWebUrl("https://github.com/realidkroo/Mochy-Music/releases"))
        assertFalse(ApkVerifier.isSafeExternalWebUrl("http://github.com/realidkroo/Mochy-Music/releases"))
        assertFalse(ApkVerifier.isSafeExternalWebUrl("javascript:alert(1)"))
        assertFalse(ApkVerifier.isSafeExternalWebUrl("intent://evil/#Intent;end"))
        assertFalse(ApkVerifier.isSafeExternalWebUrl("file:///etc/hosts"))

        val updatesDir = tempDir.newFolder("updates")
        val safe = ApkVerifier.safeApkFileIn(updatesDir, "../../etc/evil.apk")
        assertTrue(safe.canonicalPath.startsWith(updatesDir.canonicalPath))
        assertEquals("evil.apk", safe.name)
    }

    @Test
    fun webDavCloudEnforcesHttpsOnly() {
        assertNull(WebDavCloud.addressOf("http://dav.example.com/remote.php/dav"))
        assertNull(WebDavCloud.addressOf("http://192.168.1.50/webdav"))
        assertEquals("https://dav.example.com/remote.php/dav/", WebDavCloud.addressOf("https://dav.example.com/remote.php/dav")?.toString())
        assertEquals("https://dav.example.com/webdav/", WebDavCloud.addressOf("dav.example.com/webdav")?.toString())
    }

    @Test
    fun linkAuthRestrictsQuerySignedLinksToFileReads() {
        val creds = LinkCredentials("tv-1", "super-secret-shared-key-hex")
        val verifier = LinkAuth.Verifier()
        val future = System.currentTimeMillis() + 60_000L

        val fileQuery = LinkAuth.parseQuery(LinkAuth.signedQuery(creds, "/file/song123", future))
        assertEquals(
            "tv-1",
            verifier.verify(LinkRequest("GET", "/file/song123", fileQuery, emptyMap(), ByteArray(0), "192.168.1.5")) { creds.key },
        )
        assertEquals(
            "tv-1",
            verifier.verify(LinkRequest("HEAD", "/file/song123", fileQuery, emptyMap(), ByteArray(0), "192.168.1.5")) { creds.key },
        )
        assertNull(
            verifier.verify(LinkRequest("POST", "/file/song123", fileQuery, emptyMap(), ByteArray(0), "192.168.1.5")) { creds.key },
        )

        val libQuery = LinkAuth.parseQuery(LinkAuth.signedQuery(creds, "/library", future))
        assertNull(
            verifier.verify(LinkRequest("GET", "/library", libQuery, emptyMap(), ByteArray(0), "192.168.1.5")) { creds.key },
        )

        val unlinkQuery = LinkAuth.parseQuery(LinkAuth.signedQuery(creds, "/unlink", future))
        assertNull(
            verifier.verify(LinkRequest("POST", "/unlink", unlinkQuery, emptyMap(), ByteArray(0), "192.168.1.5")) { creds.key },
        )
    }

    @Test
    fun crashHandlerRedactsSecretsAndAuthPatterns() {
        SecretGuard.register("test_crash", "MyRegisteredSecretToken999")
        val sample = """
            java.io.IOException: failed calling https://alice:SuperPass42@dav.example.com/backup
            Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.secretpayload.sig
            Cookie: SAPISID=abc123456789/xyz; __Secure-3PSID=g.a000SecretCookie
            sp_dc=AQBSpotifySecretCookieValue123
            refresh_token=discord_refresh_secret_123
            leaked=MyRegisteredSecretToken999
        """.trimIndent()
        val cleaned = CrashHandler.sanitizeCrashLog(sample)
        assertFalse(cleaned.contains("SuperPass42"))
        assertFalse(cleaned.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(cleaned.contains("abc123456789"))
        assertFalse(cleaned.contains("g.a000SecretCookie"))
        assertFalse(cleaned.contains("AQBSpotifySecretCookieValue123"))
        assertFalse(cleaned.contains("discord_refresh_secret_123"))
        assertFalse(cleaned.contains("MyRegisteredSecretToken999"))
    }

    @Test
    fun secretPreferencesAndTransferArchiveCoverTurnstileAndModules() {
        assertTrue(MonochromeTurnstileJwtKey in SecretPreferences.SecretKeys)
        assertTrue(DiscordLoginStateKey in SecretPreferences.SecretKeys)
        assertTrue(DiscordLoginVerifierKey in SecretPreferences.SecretKeys)

        assertTrue(MonochromeTurnstileJwtKey.name in TransferArchive.LoginKeys)
        assertTrue(MonochromeTurnstileJwtExpiryKey.name in TransferArchive.LoginKeys)

        assertFalse(TransferArchive.carriesFile("code_modules"))
        assertFalse(TransferArchive.carriesFile("module_files"))
        assertFalse(TransferArchive.carriesFile("module_data"))
        assertTrue(TransferArchive.carriesFile("local_artwork"))

        val legacyLinkSealed = SecretVault.seal("pair-key-abc", SecretVault.DefaultDomain)
        assertNull(SecretVault.open(legacyLinkSealed, SecretVault.LinkDomain))
        assertEquals("pair-key-abc", SecretVault.openWithFallbackDomain(legacyLinkSealed, SecretVault.LinkDomain, SecretVault.DefaultDomain))
    }

    @Test
    fun youtubeLoginOriginCheckBlocksPrefixSpoofing() {
        assertTrue(isYouTubeMusicOrigin("https://music.youtube.com"))
        assertTrue(isYouTubeMusicOrigin("https://music.youtube.com/library"))
        assertFalse(isYouTubeMusicOrigin("https://music.youtube.com.evil.com/"))
        assertFalse(isYouTubeMusicOrigin("http://music.youtube.com/"))
        assertFalse(isYouTubeMusicOrigin("https://music.youtube.com:8443/"))
    }
}
