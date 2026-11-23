package com.mochyy.mochymusic.ui.screens.settings.importsync

import android.view.ContextThemeWrapper
import android.content.Context
import android.view.ViewGroup
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.atomic.AtomicReference
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.importer.account.DESKTOP_USER_AGENT
import com.mochyy.mochymusic.importer.account.WEB_PLAYER_QUERY_HEADER
import kotlinx.coroutines.delay
import timber.log.Timber
import com.mochyy.mochymusic.ui.utils.stableStatusBarsPadding

/** the page-side name of CaptureBridge, scripts the app runs in the page report through it too */
internal const val BRIDGE_NAME = "MochyCapture"

/** headers the web players send with their session: the token itself and spotify's client details */
private val SESSION_HEADERS = setOf("authorization", "media-user-token", "client-token", "app-platform", "spotify-app-version")

/** wraps fetch and xhr so the session headers and spotify's library queries the web player sends reach the app */
private val HOOK_JS = """
(function() {
  if (window.__mochyHooked) return;
  window.__mochyHooked = true;
  window.__mochyQueries = window.__mochyQueries || {};
  var wanted = { 'authorization': 1, 'media-user-token': 1, 'client-token': 1, 'app-platform': 1, 'spotify-app-version': 1 };
  var seen = {};
  var seenCount = 0;
  function report(name, value, url) {
    try {
      name = String(name).toLowerCase();
      if (!wanted[name] || !value) return;
      var key = name + '|' + value;
      if (seen[key]) return;
      if (seenCount > 200) { seen = {}; seenCount = 0; }
      seen[key] = 1; seenCount++;
      window.$BRIDGE_NAME.onHeader(name, String(value), String(url || location.href));
    } catch (e) {}
  }
  // Spotify's web player asks its GraphQL endpoint ("pathfinder") for the library by query name and
  // hash; seeing them here means the app can ask for the next pages the same way.
  function reportQuery(url, body) {
    try {
      url = String(url || '');
      if (url.indexOf('/pathfinder/') < 0) return;
      var address = new URL(url, location.href);
      var name, hash, variables, post;
      if (typeof body === 'string' && body.charAt(0) === '{') {
        var parsed = JSON.parse(body);
        name = parsed.operationName;
        variables = parsed.variables || {};
        hash = parsed.extensions && parsed.extensions.persistedQuery && parsed.extensions.persistedQuery.sha256Hash;
        post = true;
      } else {
        name = address.searchParams.get('operationName');
        variables = JSON.parse(address.searchParams.get('variables') || '{}');
        var extensions = JSON.parse(address.searchParams.get('extensions') || '{}');
        hash = extensions.persistedQuery && extensions.persistedQuery.sha256Hash;
        post = false;
      }
      if (!name || !hash) return;
      window.__mochyQueries[name] = 1;
      address.search = '';
      var key = 'query|' + name + '|' + hash;
      if (seen[key]) return;
      seen[key] = 1; seenCount++;
      var query = { name: name, hash: hash, variables: variables, url: address.toString(), post: post };
      window.$BRIDGE_NAME.onHeader('$WEB_PLAYER_QUERY_HEADER', JSON.stringify(query), address.toString());
    } catch (e) {}
  }
  function scan(headers, url) {
    try {
      if (!headers) return;
      if (typeof Headers !== 'undefined' && headers instanceof Headers) {
        headers.forEach(function(v, k) { report(k, v, url); });
      } else if (Array.isArray(headers)) {
        headers.forEach(function(pair) { report(pair[0], pair[1], url); });
      } else if (typeof headers === 'object') {
        Object.keys(headers).forEach(function(k) { report(k, headers[k], url); });
      }
    } catch (e) {}
  }
  try {
    var originalFetch = window.fetch;
    if (originalFetch) {
      window.fetch = function(input, init) {
        try {
          var url = (typeof input === 'string') ? input : (input && input.url);
          if (input && typeof input === 'object' && input.headers) scan(input.headers, url);
          if (init && init.headers) scan(init.headers, url);
          reportQuery(url, init && init.body);
        } catch (e) {}
        return originalFetch.apply(this, arguments);
      };
    }
  } catch (e) {}
  try {
    var originalOpen = XMLHttpRequest.prototype.open;
    var originalSet = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.open = function(method, url) {
      try { this.__mochyUrl = url; } catch (e) {}
      return originalOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
      report(name, value, this.__mochyUrl);
      return originalSet.apply(this, arguments);
    };
    var originalSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(body) {
      reportQuery(this.__mochyUrl, body);
      return originalSend.apply(this, arguments);
    };
  } catch (e) {}
  // Spotify's web player won't start without DRM (Widevine) and shows "Playback disabled" instead,
  // so it never fetches the session we need. The app grants protected media, which is enough where
  // WebView has Widevine; where it doesn't, the request is answered with a stand-in so the player
  // loads anyway. Nothing is played here, so its keys are never actually needed.
  try {
    var originalAccess = navigator.requestMediaKeySystemAccess;
    if (originalAccess) {
      navigator.requestMediaKeySystemAccess = function(keySystem, configs) {
        return originalAccess.apply(navigator, arguments).catch(function(error) {
          if (!configs || !configs.length) throw error;
          return {
            keySystem: keySystem,
            getConfiguration: function() { return configs[0]; },
            createMediaKeys: function() { return Promise.reject(error); }
          };
        });
      };
    }
  } catch (e) {}
})();
""".trimIndent()

/** the callback must not be named like the method or kotlin calls the method itself and overflows the stack */
private class CaptureBridge(private val report: (String, String, String) -> Unit) {
    @JavascriptInterface
    fun onHeader(name: String?, value: String?, url: String?) {
        if (name.isNullOrBlank() || value.isNullOrBlank()) return
        report(name, value, url.orEmpty())
    }
}

/** the full-screen version, with its own header: import settings → sign in and choose */
@Composable
fun CaptureWebView(
    title: String,
    subtitle: String,
    startUrl: String,
    playerOrigins: Set<String>,
    desktopSite: Boolean,
    onHeader: (name: String, value: String, url: String) -> Unit,
    onTick: (WebView) -> Unit,
    onExit: () -> Unit,
) {
    var pageLoading by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .stableStatusBarsPadding()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        WebLoginHeader(title = title, subtitle = subtitle, onBack = onExit)
        Box(modifier = Modifier.height(2.dp).fillMaxWidth()) {
            if (pageLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        CaptureWebContent(
            startUrl = startUrl,
            playerOrigins = playerOrigins,
            desktopSite = desktopSite,
            onHeader = onHeader,
            onTick = onTick,
            onExit = onExit,
            onLoadingChange = { pageLoading = it },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/** a service's own sign-in page without chrome, session headers go to onHeader and onTick runs on every load and every couple of seconds */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun CaptureWebContent(
    startUrl: String,
    playerOrigins: Set<String>,
    desktopSite: Boolean,
    onHeader: (name: String, value: String, url: String) -> Unit,
    onTick: (WebView) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onUrlChange: (String) -> Unit = {},
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val currentOnHeader by rememberUpdatedState(onHeader)
    val currentOnTick by rememberUpdatedState(onTick)
    val currentOnUrlChange by rememberUpdatedState(onUrlChange)
    val currentOnLoadingChange by rememberUpdatedState(onLoadingChange)
    var mainWebView by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    val currentMainUrl = remember { AtomicReference<String?>(null) }
    val bridge = remember(playerOrigins) {
        CaptureBridge { name, value, url ->
            // Verify both the actual WebView main-frame URL and the reported request URL against playerOrigins
            val activeUrl = currentMainUrl.get()
            if (playerOrigins.isEmpty() || (isPlayerPage(activeUrl, playerOrigins) && isPlayerPage(url, playerOrigins))) {
                currentOnHeader(name, value, url)
            }
        }
    }
    val documentStartSupported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }

    fun WebView.configure(isPopup: Boolean) {
        // match_parent params on purpose, with none the page lays out at zero height and spotify's sign-in shows blank (see docs/notes/settings.md)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // lock down file/content urls so a sketchy sign-in page can't read local app files
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.useWideViewPort = true
        // zoom-to-fit only suits desktop sites, on a phone site one oversized hidden element shrank the page out of sight
        settings.loadWithOverviewMode = desktopSite
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.userAgentString = if (desktopSite) DESKTOP_USER_AGENT else chromeUserAgent(settings.userAgentString)
        disableForcedDarkening()
        hideAppIdentity()
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(bridge, BRIDGE_NAME)
        // only the web player is watched; the sign-in pages themselves are left untouched
        if (documentStartSupported && playerOrigins.isNotEmpty()) {
            runCatching { WebViewCompat.addDocumentStartJavaScript(this, HOOK_JS, playerOrigins) }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url?.scheme?.lowercase()
                return scheme != "https" && request.url?.toString() != "about:blank"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!isPopup) {
                    currentMainUrl.set(url)
                    currentOnLoadingChange(true)
                    url?.let(currentOnUrlChange)
                }
                // without document-start scripts, hook as early as we can; later requests still count
                if (!documentStartSupported && isPlayerPage(url, playerOrigins)) view.evaluateJavascript(HOOK_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (!isPopup) {
                    currentMainUrl.set(url)
                    currentOnLoadingChange(false)
                    url?.let(currentOnUrlChange)
                }
                if (isPlayerPage(url, playerOrigins)) view.evaluateJavascript(HOOK_JS, null)
                currentOnTick(view)
            }

            // when a sign-in page stays blank, these say why (logcat tag SignInWeb, or chrome://inspect)
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) Timber.tag(LOG_TAG).w("page failed: %s %s", error.errorCode, error.description)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) Timber.tag(LOG_TAG).w("page answered HTTP %s: %s", response.statusCode, request.url?.host)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url?.toString().orEmpty()
                if (playerOrigins.isNotEmpty() && !isPlayerPage(url, playerOrigins)) return null
                request.requestHeaders?.forEach { (name, value) ->
                    if (name.lowercase() in SESSION_HEADERS) currentOnHeader(name, value, url)
                }
                return null
            }
        }

        webChromeClient = object : WebChromeClient() {
            // apple's authorize step and continue with google/apple open popups that report back to this page
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                // only windows the user asked for: a script opening one on its own would cover the page
                if (!isUserGesture) {
                    Timber.tag(LOG_TAG).d("blocked a window opened without a tap")
                    return false
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                popupWebView?.let(::destroyLater)
                val popup = WebView(view.context).apply { configure(isPopup = true) }
                popupWebView = popup
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }

            // the web players check for DRM before they start; WebView refuses it unless the app agrees
            override fun onPermissionRequest(request: PermissionRequest) {
                val origin = request.origin?.toString()
                val originAllowed = playerOrigins.isEmpty() || isPlayerPage(origin, playerOrigins)
                val protectedMedia = arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
                if (originAllowed && request.resources.any { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }) {
                    request.grant(protectedMedia)
                } else {
                    request.deny()
                }
            }

            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                    Timber.tag(LOG_TAG).w("%s (%s:%d)", message.message(), message.sourceId(), message.lineNumber())
                }
                return false
            }

            override fun onCloseWindow(window: WebView) {
                if (window == popupWebView) {
                    popupWebView = null
                    destroyLater(window)
                }
            }
        }
    }

    BackHandler {
        val popup = popupWebView
        val main = mainWebView
        when {
            popup != null && popup.canGoBack() -> popup.goBack()
            popup != null -> {
                popupWebView = null
                destroyLater(popup)
            }
            main != null && main.canGoBack() -> main.goBack()
            else -> onExit()
        }
    }

    // single-page apps sign in without a new page load, so look again every couple of seconds
    LaunchedEffect(mainWebView) {
        val view = mainWebView ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            currentOnTick(view)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            popupWebView?.let(::destroyLater)
            popupWebView = null
            mainWebView?.let {
                it.stopLoading()
                destroyLater(it)
            }
            mainWebView = null
        }
    }

    Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(lightThemed(context)).apply {
                        configure(isPopup = false)
                        loadUrl(startUrl)
                        mainWebView = this
                    }
                },
            )
            popupWebView?.let { popup ->
                key(popup) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        factory = { popup },
                    )
                }
            }
    }
}

@Composable
fun WebLoginHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.back_content_desc), tint = Color.White)
        }
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

// exact origin check so "https://music.apple.com.evil.com" never matches "https://music.apple.com"
private fun isPlayerPage(url: String?, playerOrigins: Set<String>): Boolean {
    if (url.isNullOrBlank()) return false
    val target = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val tScheme = target.scheme?.lowercase() ?: return false
    val tHost = target.host?.lowercase() ?: return false
    val tPort = if (target.port > 0) target.port else defaultPort(tScheme)
    return playerOrigins.any { raw ->
        val allowed = runCatching { java.net.URI(raw) }.getOrNull() ?: return@any false
        val aScheme = allowed.scheme?.lowercase() ?: return@any false
        val aHost = allowed.host?.lowercase() ?: return@any false
        val aPort = if (allowed.port > 0) allowed.port else defaultPort(aScheme)
        tScheme == aScheme && tHost == aHost && tPort == aPort
    }
}

private fun defaultPort(scheme: String): Int = when (scheme) {
    "https" -> 443
    "http" -> 80
    else -> -1
}

private const val LOG_TAG = "SignInWeb"

/** strips the wv and version/4.0 bits from webview's user agent, sign-in pages read them as an embedded browser */
internal fun chromeUserAgent(webViewAgent: String): String =
    webViewAgent.replace("; wv", "").replace(Regex("""\s?Version/\d+(\.\d+)*"""), "")

/** true once this view shows desktop sites, from the start or after useDesktopSite */
internal fun WebView.isDesktopSite(): Boolean = settings.userAgentString == DESKTOP_USER_AGENT

/** switches a phone-site view to desktop sites from its next page load on, zoomed out to fit */
internal fun WebView.useDesktopSite() {
    settings.userAgentString = DESKTOP_USER_AGENT
    settings.loadWithOverviewMode = true
}

/** opts out of the x-requested-with header, it is how sites spot an embedded browser */
@SuppressLint("RequiresFeature")
internal fun WebView.hideAppIdentity() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
    }
}

/** sign-in pages draw their own dark mode, webview's automatic darkening (and miui's forced dark) leaves light text on light fields */
@SuppressLint("RequiresFeature")
internal fun WebView.disableForcedDarkening() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) isForceDarkAllowed = false
}

/** the app's context with a light theme, a dark one turns apple music's sign-in frame into dark text on a dark page (see docs/notes/settings.md) */
private fun lightThemed(context: Context): Context =
    ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)

/** lets compose detach the view first, destroying an attached webview logs errors */
private fun destroyLater(webView: WebView) {
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ webView.destroy() }, 500)
}

/** signs this app's web views out of one service, so the next sign-in can use another account */
fun clearWebSession(origins: List<String>) {
    val cookies = CookieManager.getInstance()
    origins.forEach { origin ->
        val host = origin.removePrefix("https://")
        val parentDomain = host.split('.').takeLast(2).joinToString(".")
        cookies.getCookie(origin)?.split(';')?.forEach { pair ->
            val name = pair.substringBefore('=').trim()
            if (name.isNotEmpty()) {
                cookies.setCookie(origin, "$name=; Max-Age=0; Path=/")
                cookies.setCookie(origin, "$name=; Max-Age=0; Path=/; Secure")
                cookies.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=.$parentDomain")
                cookies.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=.$parentDomain; Secure")
            }
        }
        // web players keep their session in local storage too
        WebStorage.getInstance().deleteOrigin(origin)
    }
    cookies.flush()
}
