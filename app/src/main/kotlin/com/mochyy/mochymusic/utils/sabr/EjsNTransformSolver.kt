// EjsNTransformSolver.kt

package com.mochyy.mochymusic.utils.sabr

import android.content.Context
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mochyy.mochymusic.utils.cipher.CipherDeobfuscator
import com.mochyy.mochymusic.utils.cipher.PlayerJsFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object EjsNTransformSolver {
    private const val TAG = "mochy_EjsNSolver"

    private var solverWebView: SolverWebView? = null
    private val idleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val idleCloseRunnable = Runnable {
        solverWebView?.close()
        solverWebView = null
    }
    private const val IdleCloseMs = 60_000L

    private fun scheduleIdleClose() {
        idleHandler.removeCallbacks(idleCloseRunnable)
        idleHandler.postDelayed(idleCloseRunnable, IdleCloseMs)
    }

    suspend fun transformNParamInUrl(url: String): String {
        val nMatch = Regex("[?&]n=([^&]+)").find(url)
        if (nMatch == null) {
            Timber.tag(TAG).d("No 'n' parameter in SABR URL")
            return url
        }
        val nValue = Uri.decode(nMatch.groupValues[1])
        Timber.tag(TAG).d("SABR n-param: $nValue")

        return withContext(NonCancellable) {
            val solver = getOrCreateSolver()
            if (solver == null) {
                return@withContext url
            }

            if (!solver.nFunctionAvailable) {
                Timber.tag(TAG).e("EJS n-solver not available")
                return@withContext url
            }

            try {
                val transformed = solver.transformN(nValue)
                scheduleIdleClose()
                Timber.tag(TAG).d("SABR n-param transformed: $nValue -> $transformed")
                url.replaceFirst(
                    Regex("([?&])n=[^&]+"),
                    "$1n=${Uri.encode(transformed)}"
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "SABR n-transform failed: ${e.message}")
                url
            }
        }
    }

    private suspend fun getOrCreateSolver(): SolverWebView? {
        solverWebView?.let { return it }

        return withContext(NonCancellable) {
            solverWebView?.let { return@withContext it }

            val result = PlayerJsFetcher.getPlayerJs(forceRefresh = false)
            if (result == null) {
                Timber.tag(TAG).e("Failed to get player JS for EJS solver")
                return@withContext null
            }
            val (playerJs, hash) = result
            Timber.tag(TAG).d("Creating EJS n-solver (player hash=$hash, ${playerJs.length} chars)")

            try {
                val sv = SolverWebView.create(CipherDeobfuscator.appContext, playerJs)
                solverWebView = sv
                sv
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to create EJS solver: ${e.message}")
                null
            }
        }
    }

    suspend fun close() {
        idleHandler.removeCallbacks(idleCloseRunnable)
        withContext(Dispatchers.Main) {
            solverWebView?.close()
        }
        solverWebView = null
    }

    class SolverWebView private constructor(
        context: Context,
        private val initContinuation: Continuation<SolverWebView>,
    ) {
        private val webView = WebView(context)
        private var nContinuation: Continuation<String>? = null

        @Volatile
        var nFunctionAvailable: Boolean = false
            private set

        @Volatile
        private var virtualPlayerJsBytes: ByteArray = ByteArray(0)

        init {
            val settings = webView.settings
            @Suppress("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            webView.addJavascriptInterface(this, "EjsBridge")

            val assetManager = context.applicationContext.assets
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    val uri = request.url
                    if (uri?.scheme == "https" && uri.host == VIRTUAL_HOST) {
                        val name = uri.path?.removePrefix("/")
                        if (name == "player.js") {
                            return WebResourceResponse(
                                "application/javascript",
                                "utf-8",
                                ByteArrayInputStream(virtualPlayerJsBytes),
                            )
                        }
                        if (name in ALLOWED_SOLVER_ASSETS) {
                            val stream = runCatching { assetManager.open("solver/$name") }.getOrNull()
                            if (stream != null) {
                                return WebResourceResponse("application/javascript", "utf-8", stream)
                            }
                        }
                    }
                    return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    val msg = m.message()
                    if (msg.contains("Uncaught")) {
                        Timber.tag(TAG).e("EJS WebView error: $msg at ${m.sourceId()}:${m.lineNumber()}")
                    }
                    return super.onConsoleMessage(m)
                }
            }
        }

        private fun loadSolverAndPlayer(playerJs: String) {
            virtualPlayerJsBytes = playerJs.toByteArray(Charsets.UTF_8)
            Timber.tag(TAG).d("EJS solver player loaded in memory (${playerJs.length} chars)")

            val html = """<!DOCTYPE html>
<html><head>
<script src="meriyah.js"></script>
<script src="astring.js"></script>
<script src="yt.solver.core.js"></script>
<script>
var _nSolver = null;

function initSolver() {
    try {
        EjsBridge.onLog('Reading player.js via XHR...');
        var xhr = new XMLHttpRequest();
        xhr.open('GET', 'player.js', true);
        xhr.onload = function() {
            try {
                var playerCode = xhr.responseText;
                if (!playerCode || playerCode.length < 1000) {
                    EjsBridge.onSolverError('Player JS too small: ' + (playerCode ? playerCode.length : 0));
                    return;
                }
                EjsBridge.onLog('Preprocessing with EJS solver (' + playerCode.length + ' chars)...');

                var result = jsc({
                    type: 'player',
                    player: playerCode,
                    requests: [],
                    output_preprocessed: true
                });

                if (result.type === 'error') {
                    EjsBridge.onSolverError('EJS preprocess: ' + result.error);
                    return;
                }

                EjsBridge.onLog('Evaluating preprocessed code...');
                var resultObj = {n: null, sig: null};
                (new Function('_result', result.preprocessed_player))(resultObj);

                _nSolver = resultObj.n;
                EjsBridge.onSolverReady((typeof _nSolver === 'function').toString());
            } catch(e) {
                EjsBridge.onSolverError(e.toString() + '\n' + (e.stack || ''));
            }
        };
        xhr.onerror = function() {
            EjsBridge.onSolverError('XHR failed to read player.js');
        };
        xhr.send();
    } catch(e) {
        EjsBridge.onSolverError(e.toString() + '\n' + (e.stack || ''));
    }
}

function transformN(nValue) {
    try {
        if (!_nSolver) {
            EjsBridge.onNError('N solver not available');
            return;
        }
        var result = _nSolver(nValue);
        if (result === undefined || result === null) {
            EjsBridge.onNError('N solver returned null/undefined');
            return;
        }
        EjsBridge.onNResult(String(result));
    } catch(e) {
        EjsBridge.onNError(e.toString() + '\n' + (e.stack || ''));
    }
}
</script>
</head><body onload="initSolver()"></body></html>"""

            webView.loadDataWithBaseURL(
                "https://$VIRTUAL_HOST/",
                html, "text/html", "utf-8", null
            )
        }

        @JavascriptInterface
        fun onLog(message: String) {
            Timber.tag(TAG).d(message)
        }

        @JavascriptInterface
        fun onSolverReady(nAvailable: String) {
            nFunctionAvailable = nAvailable == "true"
            Timber.tag(TAG).d("EJS solver ready: n=$nFunctionAvailable")
            initContinuation.resume(this)
        }

        @JavascriptInterface
        fun onSolverError(error: String) {
            Timber.tag(TAG).e("EJS solver error: $error")
            initContinuation.resume(this)
        }

        suspend fun transformN(nValue: String): String {
            if (!nFunctionAvailable) {
                throw SabrException("EJS n-transform not available")
            }

            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    nContinuation = cont
                    webView.evaluateJavascript(
                        "transformN('${escapeJsString(nValue)}')",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun onNResult(result: String) {
            Timber.tag(TAG).d("N-transform result: ${result.take(50)}")
            nContinuation?.resume(result)
            nContinuation = null
        }

        @JavascriptInterface
        fun onNError(error: String) {
            Timber.tag(TAG).e("N-transform error: $error")
            nContinuation?.resumeWithException(SabrException("EJS n-transform failed: $error"))
            nContinuation = null
        }

        fun close() {
            webView.clearHistory()
            webView.clearCache(true)
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
            Timber.tag(TAG).d("EJS solver WebView closed")
        }

        private fun escapeJsString(s: String): String {
            return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
        }

        companion object {
            private const val VIRTUAL_HOST = "appassets.mochy.local"
            private val ALLOWED_SOLVER_ASSETS = setOf("meriyah.js", "astring.js", "yt.solver.core.js")

            suspend fun create(context: Context, playerJs: String): SolverWebView {
                return withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        val sv = SolverWebView(context, cont)
                        sv.loadSolverAndPlayer(playerJs)
                    }
                }
            }
        }
    }
}
