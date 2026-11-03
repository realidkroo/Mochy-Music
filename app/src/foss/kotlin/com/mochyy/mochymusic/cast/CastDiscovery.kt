// CastDiscovery.kt

package com.mochyy.mochymusic.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Xml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** a receiver found on the wifi */
sealed interface FoundReceiver {
    val id: String
    val name: String
    val detail: String

    data class Chromecast(
        override val id: String,
        override val name: String,
        override val detail: String,
        val host: String,
        val port: Int,
    ) : FoundReceiver

    data class Dlna(
        override val id: String,
        override val name: String,
        override val detail: String,
        val avTransportUrl: String,
        val renderingControlUrl: String?,
    ) : FoundReceiver
}

/** looks for chromecasts (mdns) and dlna renderers (ssdp) while asked to; what it found stays listed between looks */
class CastDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val chromecasts = ConcurrentHashMap<String, FoundReceiver.Chromecast>()
    // a lost service only says its service name, so each is remembered against the id it got
    private val serviceIds = ConcurrentHashMap<String, String>()
    private val renderers = ConcurrentHashMap<String, FoundReceiver.Dlna>()
    private val describing = ConcurrentHashMap.newKeySet<String>()

    private val _found = MutableStateFlow<List<FoundReceiver>>(emptyList())
    val found: StateFlow<List<FoundReceiver>> = _found.asStateFlow()

    private var ssdpJob: Job? = null
    private var listening = false

    // nsd resolves one service at a time, a second resolve while one runs just fails, so they queue
    private val toResolve = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    private val mdns = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) = queueResolve(info)
        override fun onServiceLost(info: NsdServiceInfo) {
            serviceIds.remove(info.serviceName)?.let { chromecasts.remove(it) }
            publish()
        }
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Timber.w("mdns discovery didn't start: $errorCode")
            listening = false
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
    }

    @Synchronized
    fun start() {
        if (!listening) {
            listening = true
            runCatching { nsd.discoverServices(ChromecastService, NsdManager.PROTOCOL_DNS_SD, mdns) }
                .onFailure { listening = false; Timber.w(it, "mdns discovery didn't start") }
        }
        if (ssdpJob?.isActive != true) ssdpJob = scope.launch { searchRenderers() }
    }

    @Synchronized
    fun stop() {
        if (listening) {
            listening = false
            runCatching { nsd.stopServiceDiscovery(mdns) }
        }
        ssdpJob?.cancel()
        ssdpJob = null
    }

    @Synchronized
    private fun queueResolve(info: NsdServiceInfo) {
        toResolve.addLast(info)
        if (!resolving) resolveNext()
    }

    @Synchronized
    private fun resolveNext() {
        val next = toResolve.removeFirstOrNull()
        if (next == null) {
            resolving = false
            return
        }
        resolving = true
        @Suppress("DEPRECATION")
        runCatching {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = resolveNext()
                override fun onServiceResolved(info: NsdServiceInfo) {
                    onChromecast(info)
                    resolveNext()
                }
            })
        }.onFailure { resolveNext() }
    }

    private fun onChromecast(info: NsdServiceInfo) {
        @Suppress("DEPRECATION")
        val host = info.host?.hostAddress ?: return
        val attributes = info.attributes
        fun attribute(key: String) = attributes[key]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
        val name = attribute("fn") ?: info.serviceName
        val model = attribute("md") ?: "Chromecast"
        val id = "cc:" + (attribute("id") ?: info.serviceName)
        serviceIds[info.serviceName] = id
        chromecasts[id] = FoundReceiver.Chromecast(id, name, model, host, info.port)
        publish()
    }

    // asks every renderer on the network to answer, a few times while the sheet is open, since a tv can miss one call
    private suspend fun searchRenderers() {
        val search = (
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
            ).toByteArray(Charsets.US_ASCII)
        val socket = runCatching { DatagramSocket().apply { soTimeout = 1000 } }.getOrNull() ?: return
        try {
            val group = InetAddress.getByName("239.255.255.250")
            val buffer = ByteArray(4096)
            while (currentCoroutineContext().isActive) {
                repeat(2) { runCatching { socket.send(DatagramPacket(search, search.size, group, 1900)) } }
                val until = System.currentTimeMillis() + ListenMs
                while (System.currentTimeMillis() < until) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val reply = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val location = header(reply, "LOCATION") ?: continue
                    if (describing.add(location)) scope.launch { describe(location) }
                }
                delay(SearchGapMs)
            }
        } catch (e: Exception) {
            Timber.w(e, "ssdp search stopped")
        } finally {
            socket.close()
        }
    }

    private fun header(reply: String, name: String): String? =
        reply.lineSequence().firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()

    // reads a renderer's description: its name, and where its transport and volume controls answer
    private fun describe(location: String) {
        try {
            val connection = URL(location).openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            val parser = Xml.newPullParser()
            connection.inputStream.use { stream ->
                parser.setInput(stream, null)
                var friendlyName: String? = null
                var model: String? = null
                var udn: String? = null
                var base: String? = null
                var serviceType: String? = null
                var avTransport: String? = null
                var renderingControl: String? = null
                var text = ""
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.TEXT -> text = parser.text.trim()
                        XmlPullParser.START_TAG -> text = ""
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "friendlyName" -> if (friendlyName == null) friendlyName = text
                            "modelName" -> if (model == null) model = text
                            "UDN" -> if (udn == null) udn = text
                            "URLBase" -> base = text
                            "serviceType" -> serviceType = text
                            "controlURL" -> when {
                                serviceType?.contains(":AVTransport:") == true && avTransport == null -> avTransport = text
                                serviceType?.contains(":RenderingControl:") == true && renderingControl == null -> renderingControl = text
                            }
                        }
                    }
                }
                val transport = avTransport ?: return
                val root = URL(base?.takeIf { it.isNotBlank() } ?: location)
                val id = "dlna:" + (udn ?: location)
                renderers[id] = FoundReceiver.Dlna(
                    id = id,
                    name = friendlyName?.takeIf { it.isNotBlank() } ?: (model ?: "TV"),
                    detail = model?.takeIf { it.isNotBlank() } ?: "DLNA",
                    avTransportUrl = URL(root, transport).toString(),
                    renderingControlUrl = renderingControl?.let { URL(root, it).toString() },
                )
                publish()
            }
            connection.disconnect()
        } catch (e: Exception) {
            Timber.d(e, "Couldn't read the renderer at $location")
            // asked again on the next search, a tv waking up can take a moment to answer
            describing.remove(location)
        }
    }

    private fun publish() {
        _found.update { (chromecasts.values + renderers.values).sortedBy { it.name.lowercase() } }
    }

    private companion object {
        const val ChromecastService = "_googlecast._tcp"
        const val ListenMs = 3000L
        const val SearchGapMs = 7000L
    }
}
