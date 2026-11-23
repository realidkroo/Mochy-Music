package com.mochyy.mochymusic.cast

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.HttpURLConnection
import java.net.URL

class CastMediaProxyTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var proxy: CastMediaProxy

    @Before
    fun setUp() {
        proxy = CastMediaProxy(tempDir.newFolder("cast-cache"))
    }

    @After
    fun tearDown() {
        proxy.stop()
    }

    @Test
    fun servesHeadFullGetRangeAndDlnaHeaders() {
        val payload = ByteArray(1024) { i -> (i and 0xFF).toByte() }
        val file = tempDir.newFile("track.m4a").apply { writeBytes(payload) }
        val features = "DLNA.ORG_PN=AAC_ISO_320;${CastMediaProxy.DefaultDlnaFeatures}"
        val served = proxy.serveLocalFile("127.0.0.1", file, "audio/mp4", features)

        assertTrue(served.url.endsWith(".m4a"))
        assertEquals(1024L, served.contentLength)

        // 1. HEAD (Samsung TVs issue HEAD right after SetAVTransportURI)
        val headConn = (URL(served.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
        }
        try {
            assertEquals(200, headConn.responseCode)
            assertEquals("audio/mp4", headConn.getHeaderField("Content-Type"))
            assertEquals("1024", headConn.getHeaderField("Content-Length"))
            assertEquals("bytes", headConn.getHeaderField("Accept-Ranges"))
            assertEquals("Streaming", headConn.getHeaderField("transferMode.dlna.org"))
            assertEquals(features, headConn.getHeaderField("contentFeatures.dlna.org"))
            assertEquals("*", headConn.getHeaderField("Access-Control-Allow-Origin"))
        } finally {
            headConn.disconnect()
        }

        // 2. Full GET
        val getConn = (URL(served.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
        }
        try {
            assertEquals(200, getConn.responseCode)
            assertArrayEquals(payload, getConn.inputStream.readBytes())
        } finally {
            getConn.disconnect()
        }

        // 3. Partial Range GET (used for seeking on TVs and Chromecast)
        val rangeConn = (URL(served.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=100-199")
        }
        try {
            assertEquals(206, rangeConn.responseCode)
            assertEquals("bytes 100-199/1024", rangeConn.getHeaderField("Content-Range"))
            assertEquals("100", rangeConn.getHeaderField("Content-Length"))
            assertArrayEquals(payload.copyOfRange(100, 200), rangeConn.inputStream.readBytes())
        } finally {
            rangeConn.disconnect()
        }

        // 4. Open-ended Range GET
        val openRangeConn = (URL(served.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=900-")
        }
        try {
            assertEquals(206, openRangeConn.responseCode)
            assertEquals("bytes 900-1023/1024", openRangeConn.getHeaderField("Content-Range"))
            assertArrayEquals(payload.copyOfRange(900, 1024), openRangeConn.inputStream.readBytes())
        } finally {
            openRangeConn.disconnect()
        }

        // 5. Out-of-bounds Range returns 416
        val badRangeConn = (URL(served.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=5000-6000")
        }
        try {
            assertEquals(416, badRangeConn.responseCode)
        } finally {
            badRangeConn.disconnect()
        }

        // 6. Unknown path returns 404
        val badTokenUrl = served.url.substringBeforeLast('/') + "/deadbeef.m4a"
        val notFoundConn = (URL(badTokenUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
        }
        try {
            assertEquals(404, notFoundConn.responseCode)
        } finally {
            notFoundConn.disconnect()
        }
    }
}
