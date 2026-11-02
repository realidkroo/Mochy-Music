// the proxy from settings for everything that talks to youtube, read on each request so a change needs no restart

package com.mochyy.mochymusic.utils

import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.constants.ProxyEnabledKey
import com.mochyy.mochymusic.constants.ProxyPasswordKey
import com.mochyy.mochymusic.constants.ProxyTypeKey
import com.mochyy.mochymusic.constants.ProxyUrlKey
import com.mochyy.mochymusic.constants.ProxyUsernameKey
import com.mochyy.mochymusic.extensions.toEnum
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

data class ProxyConfig(
    val type: Proxy.Type,
    val address: InetSocketAddress,
    val username: String,
    val password: String,
) {
    val hasLogin: Boolean get() = username.isNotEmpty() || password.isNotEmpty()
}

object AppProxy {
    @Volatile
    var current: ProxyConfig? = null
        private set

    // one pool for every client that follows the proxy, so a new proxy can drop the idle connections that went the old way
    internal val connectionPool = ConnectionPool()

    // with no proxy of ours the phone's own proxy setting still counts, as it did before
    internal val selector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> =
            YouTube.proxy?.let { listOf(it) }
                ?: ProxySelector.getDefault()?.select(uri)
                ?: listOf(Proxy.NO_PROXY)

        override fun connectFailed(uri: URI?, address: SocketAddress?, error: IOException?) = Unit
    }

    /** what the settings say, with or without the switch on, null while the address isn't host:port */
    fun configFrom(settings: Preferences, requireEnabled: Boolean = true): ProxyConfig? {
        if (requireEnabled && settings[ProxyEnabledKey] != true) return null
        val address = parseAddress(settings[ProxyUrlKey].orEmpty()) ?: return null
        return ProxyConfig(
            type = settings[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
            address = address,
            username = settings[ProxyUsernameKey].orEmpty(),
            password = settings[ProxyPasswordKey].orEmpty(),
        )
    }

    /** sends youtube through config, or straight out when it's null, false when nothing changed */
    fun apply(config: ProxyConfig?): Boolean {
        if (config == current) return false
        YouTube.proxyAuth = config?.takeIf { it.type == Proxy.Type.HTTP && it.hasLogin }
            ?.let { Credentials.basic(it.username, it.password) }
        useSocksLogin(config)
        // after the login: the youtube client rebuilds itself here and takes the login with it
        YouTube.proxy = config?.let { Proxy(it.type, it.address) }
        current = config
        connectionPool.evictAll()
        return true
    }

    /** reads "1.2.3.4:8080", "proxy.example.com:1080" or "[2001:db8::1]:3128", skipping a pasted scheme or login in front */
    fun parseAddress(text: String): InetSocketAddress? {
        val hostPort = text.trim().substringAfter("://").substringAfterLast('@').trimEnd('/')
        val port = hostPort.substringAfterLast(':', "").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter('[').substringBefore(']')
        } else {
            hostPort.substringBeforeLast(':')
        }
        if (host.isBlank()) return null
        // unresolved: the proxy's name is looked up when connecting, never here on the main thread
        return InetSocketAddress.createUnresolved(host, port)
    }

    /** the address as people write it, an ipv6 host in brackets so its port stays readable */
    fun addressLabel(address: InetSocketAddress): String =
        if (':' in address.hostString) "[${address.hostString}]:${address.port}" else "${address.hostString}:${address.port}"

    /** asks youtube for an empty page through config, how many ms it took or why it failed */
    suspend fun check(config: ProxyConfig): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            useSocksLogin(config)
            val client = OkHttpClient.Builder()
                .proxy(Proxy(config.type, config.address))
                .proxyAuthenticator { _, response ->
                    if (config.type != Proxy.Type.HTTP || !config.hasLogin || response.request.header(ProxyAuthHeader) != null) {
                        null
                    } else {
                        response.request.newBuilder().header(ProxyAuthHeader, Credentials.basic(config.username, config.password)).build()
                    }
                }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val started = System.nanoTime()
            client.newCall(Request.Builder().url("https://www.youtube.com/generate_204").build()).execute().use { response ->
                if (!response.isSuccessful) throw ProxyRefusedException(response.code)
            }
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        }
    }

    // socks has no header for a login, java asks this one global authenticator instead
    private fun useSocksLogin(config: ProxyConfig?) {
        val login = config?.takeIf { it.type == Proxy.Type.SOCKS && it.hasLogin }
        Authenticator.setDefault(
            login?.let {
                object : Authenticator() {
                    override fun getPasswordAuthentication() = PasswordAuthentication(it.username, it.password.toCharArray())
                }
            },
        )
    }
}

private const val ProxyAuthHeader = "Proxy-Authorization"

// the proxy or youtube answered the check with an error code, 407 means the proxy wants a sign-in
class ProxyRefusedException(val code: Int) : IOException("HTTP $code")

/** sends this client through the proxy from settings, read on every request */
fun OkHttpClient.Builder.followAppProxy(): OkHttpClient.Builder =
    proxySelector(AppProxy.selector)
        .proxyAuthenticator { _, response ->
            val auth = YouTube.proxyAuth
            // already sent once and still refused, asking again would only loop
            if (auth == null || response.request.header(ProxyAuthHeader) != null) {
                null
            } else {
                response.request.newBuilder().header(ProxyAuthHeader, auth).build()
            }
        }
        .connectionPool(AppProxy.connectionPool)
