package com.mochyy.mochymusic.ui.screens.settings.importsync

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AccountChannelHandleKey
import com.mochyy.mochymusic.constants.AccountEmailKey
import com.mochyy.mochymusic.constants.AccountNameKey
import com.mochyy.mochymusic.constants.DataSyncIdKey
import com.mochyy.mochymusic.constants.InnerTubeCookieKey
import com.mochyy.mochymusic.constants.VisitorDataKey
import com.mochyy.mochymusic.ui.utils.stableStatusBarsPadding
import com.mochyy.mochymusic.utils.dataStore
import com.music.innertube.YouTube
import com.music.innertube.models.AccountInfo
import com.music.innertube.utils.parseCookieString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

/** reads the session ids youtube music keeps in its page config */
private const val READ_CONFIG_JS = """
(function() {
  try {
    var get = function(k) {
      if (window.ytcfg && ytcfg.get) { var v = ytcfg.get(k); if (v) return v; }
      if (window.yt && yt.config_ && yt.config_[k]) return yt.config_[k];
      return null;
    };
    return { visitorData: get('VISITOR_DATA'), dataSyncId: get('DATASYNC_ID') };
  } catch (e) { return null; }
})();
"""

/** the full-screen sign-in page used from the sync settings */
@Composable
fun YouTubeLoginScreen(navController: NavController) {
    val context = LocalContext.current
    var pageLoading by remember { mutableStateOf(true) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .stableStatusBarsPadding()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.yt_login_back),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.yt_login_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    stringResource(R.string.yt_login_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        Box(modifier = Modifier.height(2.dp).fillMaxWidth()) {
            if (pageLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        YouTubeLoginWebContent(
            onSignedIn = { info ->
                Toast
                    .makeText(
                        context,
                        info?.name?.let { context.getString(R.string.yt_login_signed_in_as, it) }
                            ?: context.getString(R.string.yt_login_signed_in_general),
                        Toast.LENGTH_SHORT,
                    ).show()
                navController.navigateUp()
            },
            onExit = { navController.navigateUp() },
            onLoadingChange = { pageLoading = it },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/** google sign-in in a webview without chrome, once signed in on music.youtube.com it saves the session where the app reads it */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginWebContent(
    onSignedIn: (AccountInfo?) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onUrlChange: (String) -> Unit = {},
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    val currentOnUrlChange by rememberUpdatedState(onUrlChange)
    val currentOnLoadingChange by rememberUpdatedState(onLoadingChange)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var saving by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun finishLogin(
        view: WebView,
        url: String,
    ) {
        if (saving || finished) return
        val cookie = CookieManager.getInstance().getCookie(url) ?: return
        if ("SAPISID" !in runCatching { parseCookieString(cookie) }.getOrDefault(emptyMap())) return
        saving = true
        view.evaluateJavascript(READ_CONFIG_JS) { raw ->
            val config = runCatching { JSONObject(raw) }.getOrNull()
            val visitorData = config?.optString("visitorData")?.takeIf { it.isNotBlank() && it != "null" }
            val dataSyncId = config?.optString("dataSyncId")?.takeIf { it.isNotBlank() && it != "null" }
            scope.launch(Dispatchers.IO) {
                try {
                    // account details need the new session, the app would set these from prefs anyway
                    YouTube.cookie = cookie
                    visitorData?.let { YouTube.visitorData = it }
                    YouTube.dataSyncId =
                        dataSyncId?.let {
                            it.takeIf { !it.contains("||") }
                                ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                                ?: it.substringAfter("||")
                        }
                    val info = YouTube.accountInfo().getOrNull()
                    context.dataStore.edit { settings ->
                        settings[InnerTubeCookieKey] = cookie
                        visitorData?.let { settings[VisitorDataKey] = it }
                        dataSyncId?.let { settings[DataSyncIdKey] = it }
                        settings[AccountNameKey] = info?.name.orEmpty()
                        settings[AccountEmailKey] = info?.email.orEmpty()
                        settings[AccountChannelHandleKey] = info?.channelHandle.orEmpty()
                    }
                    withContext(Dispatchers.Main) {
                        finished = true
                        currentOnSignedIn(info)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Saving YouTube login failed")
                    withContext(Dispatchers.Main) {
                        saving = false
                        Toast.makeText(context, context.getString(R.string.yt_login_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack() && !saving) view.goBack() else onExit()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                destroy()
            }
            webView = null
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // see CaptureWebContent: wrap_content makes chromium lay pages out at zero height
                    layoutParams =
                        android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    // google refuses sign-in from pages that announce themselves as an embedded webview
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    disableForcedDarkening()
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView,
                                url: String?,
                                favicon: Bitmap?,
                            ) {
                                currentOnLoadingChange(true)
                                url?.let(currentOnUrlChange)
                            }

                            override fun onPageFinished(
                                view: WebView,
                                url: String?,
                            ) {
                                currentOnLoadingChange(false)
                                url?.let(currentOnUrlChange)
                                if (url != null && url.startsWith("https://music.youtube.com")) finishLogin(view, url)
                            }
                        }
                    loadUrl(LOGIN_URL)
                    webView = this
                }
            },
        )
        if (saving) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
