package com.music.musixmatch

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/** a hidden browser tab: musixmatch's song pages, and a web search when plain http is turned away, load in it like in a browser */
internal object MusixmatchBrowser {
    private const val Site = "https://www.musixmatch.com"
    private const val LoadTimeoutMs = 25_000L
    private const val PollMs = 100L
    private const val EvaluateTimeoutMs = 5_000L
    private const val IdleMs = 60_000L

    private val mutex = Mutex()
    private val main = Handler(Looper.getMainLooper())
    private val releaseWhenIdle = Runnable { release() }
    private val tabClient = TabClient()
    private var webView: WebView? = null

    // the warm-up outlives the lookup that asked for it, so the song's page finds the tab ready
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** opens the tab and gets it past the bot check in the background, so the song page after it loads in one go */
    fun warmUp(context: Context) {
        background.launch {
            // robots.txt is tiny, and it gets the same check as every other page
            runCatching { withTab(context) { view -> load(view, "$Site/robots.txt", ready = "!document.getElementById('challenge-container')") } }
        }
    }

    /** the whole page at [url] once it has loaded, or null when it never did */
    suspend fun html(context: Context, url: String): String? = withTab(context) { view ->
        if (load(view, url, ready = "true")) read(view, "document.documentElement.outerHTML") else null
    }

    /** the page's next.js data once [path] has loaded past the check, or null when it never did */
    suspend fun nextData(context: Context, path: String): String? = withTab(context) { view ->
        val loaded = load(view, "$Site$path", ready = "!!document.getElementById('__NEXT_DATA__')")
        val data = if (loaded) read(view, "document.getElementById('__NEXT_DATA__').textContent") else null
        if (data == null) android.util.Log.w("Musixmatch", "$path didn't load in the browser")
        data
    }

    private suspend fun <T> withTab(context: Context, block: suspend (WebView) -> T): T = mutex.withLock {
        withContext(Dispatchers.Main) {
            main.removeCallbacks(releaseWhenIdle)
            try {
                block(webView ?: createTab(context))
            } finally {
                main.postDelayed(releaseWhenIdle, IdleMs)
            }
        }
    }

    // true once the new page has fully arrived and [ready] holds on it; false when it failed, hit the sign-in wall, or ran out of time
    private suspend fun load(view: WebView, url: String, ready: String): Boolean {
        // the tab is reused: a mark on the old page tells it apart from the new one, even when musixmatch redirects
        if (view.url != null) evaluate(view, "window.mochyOld=true")
        val host = Uri.parse(url).host
        tabClient.failedHost = null
        view.loadUrl(url)
        val script = "(function(){if(window.mochyOld)return 'wait';var h=location.hostname;if(h=='auth.musixmatch.com')return 'wall';" +
            "return h==${JsonPrimitive(host)}&&document.readyState!='loading'&&($ready)?'ready':'wait';})()"
        val outcome = withTimeoutOrNull(LoadTimeoutMs) {
            var state = "wait"
            while (state == "wait") {
                delay(PollMs)
                // a dns block or a dead connection gives up at once instead of waiting out the timeout
                state = if (tabClient.failedHost == host) "failed" else evaluate(view, script)?.trim('"') ?: "wait"
            }
            state
        }
        return outcome == "ready"
    }

    // evaluateJavascript hands strings back as json; unwrapping a whole page is kept off the main thread
    private suspend fun read(view: WebView, expression: String): String? {
        val raw = evaluate(view, expression) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching { (Json.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull }.getOrNull()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun createTab(context: Context): WebView {
        // the webview's first start is heavy: the sheet that asked gets to finish opening first
        delay(600)
        CookieManager.getInstance().setAcceptCookie(true)
        return WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = tabClient
            // never shown, but a zero-size view with paused timers can stall the check's script
            layout(0, 0, 360, 640)
            onResume()
            resumeTimers()
        }.also { webView = it }
    }

    // capped, so a stuck renderer can't hold the tab (and every lookup waiting on it) forever
    private suspend fun evaluate(view: WebView, script: String): String? = withTimeoutOrNull(EvaluateTimeoutMs) {
        suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resume(result) }
        }
    }

    private fun release() {
        main.removeCallbacks(releaseWhenIdle)
        val view = webView ?: return
        webView = null
        runCatching {
            view.stopLoading()
            view.destroy()
        }
    }

    private class TabClient : WebViewClient() {
        /** the host whose page couldn't load: no connection, a dns block's wrong certificate, or an error page */
        @Volatile
        var failedHost: String? = null

        private fun isAllowedHost(uri: Uri?): Boolean {
            if (uri == null || !uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
            return host == "musixmatch.com" || host.endsWith(".musixmatch.com") ||
                host == "awswaf.com" || host.endsWith(".awswaf.com")
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isAllowedHost(request.url)

        // only the page itself and the bot check's script get through; the site's pictures, scripts and ads would just slow the tab
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val uri = request.url
            val host = uri?.host?.lowercase()?.trimEnd('.').orEmpty()
            val isAwsWaf = uri?.scheme.equals("https", ignoreCase = true) && (host == "awswaf.com" || host.endsWith(".awswaf.com"))
            val allow = (request.isForMainFrame && isAllowedHost(uri)) || isAwsWaf
            return if (allow) null else WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) failedHost = request.url.host
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (request.isForMainFrame && response.statusCode >= 400) failedHost = request.url.host
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            failedHost = Uri.parse(error.url).host
        }
    }
}
