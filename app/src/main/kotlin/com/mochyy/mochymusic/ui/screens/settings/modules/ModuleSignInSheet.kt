// a module's sign-in in a sheet over its page: the library import's own browser bar, web view and apple scripts

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.MochyModule
import com.mochyy.mochymusic.module.SignInSpec
import com.mochyy.mochymusic.module.applemusic.AppleMusicModule
import com.mochyy.mochymusic.modules.security.ModuleUrlGuard
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.screens.settings.importsync.CaptureWebContent
import com.mochyy.mochymusic.ui.screens.settings.importsync.onSignInTick
import com.mochyy.mochymusic.ui.screens.setup.onboarding.BrowserSheetBar
import com.mochyy.mochymusic.ui.screens.setup.onboarding.ProviderSquircle
import com.mochyy.mochymusic.viewmodels.ModulesViewModel

// the import browser's own grey, so both sign-ins look the same
private val BrowserSurface = Color(0xFF1C1C1C)

// domains belonging to mochy's own logins that a third-party module can't hijack via shared webview cookies
private val ProtectedSignInHosts = setOf(
    "google.com", "youtube.com", "googleapis.com", "discord.com", "discordapp.com",
    "spotify.com", "dropbox.com", "onedrive.live.com", "live.com", "microsoftonline.com",
    "last.fm", "libre.fm", "mochy-module-feed.mochyy.workers.dev",
)

private fun isAllowedSignInUrl(url: String, builtIn: Boolean): Boolean {
    if (!url.startsWith("https://", ignoreCase = true)) return false
    if (!ModuleUrlGuard.isSafeRemoteUrl(url)) return false
    if (builtIn) return true
    val host = runCatching { java.net.URI(url).host?.lowercase()?.trimEnd('.') }.getOrNull() ?: return false
    return ProtectedSignInHosts.none { blocked -> host == blocked || host.endsWith(".$blocked") }
}

/** opens the module's sign-in; [onDone] hears how it went once the account is in, the sheet closes itself */
fun PopupSheetState.showModuleSignInSheet(module: MochyModule, viewModel: ModulesViewModel, onDone: (AccountState) -> Unit) {
    val spec = module.signIn ?: return
    val builtIn = module.info.id == AppleMusicModule.Id
    if (!isAllowedSignInUrl(spec.startUrl, builtIn) || spec.sessionOrigins.any { !isAllowedSignInUrl(it, builtIn) }) {
        onDone(AccountState.Expired("lol nice try"))
        return
    }
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(fullBleed = true, surface = BrowserSurface) {
        ModuleSignInContent(
            module = module,
            spec = spec,
            viewModel = viewModel,
            onDone = { state ->
                sheet?.dismiss()
                onDone(state)
            },
            onClose = { sheet?.dismiss() },
        )
    }
}

@Composable
private fun ModuleSignInContent(
    module: MochyModule,
    spec: SignInSpec,
    viewModel: ModulesViewModel,
    onDone: (AccountState) -> Unit,
    onClose: () -> Unit,
) {
    val id = module.info.id
    // apple music is the import's apple music: same page, same scripts pressing sign in and reading musickit's tokens
    val importService = if (id == AppleMusicModule.Id) AccountService.APPLE_MUSIC else null
    // tall enough for apple's whole sign-in, short enough to stay a sheet
    val pageHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
    var url by remember { mutableStateOf(spec.startUrl) }

    DisposableEffect(Unit) {
        viewModel.resetSignIn()
        onDispose { }
    }

    Column(Modifier.fillMaxWidth()) {
        BrowserSheetBar(
            icon = {
                if (importService != null) ProviderSquircle(importService, size = 38.dp) else ModuleLogo(id, module.info.name, size = 38)
            },
            title = stringResource(R.string.module_sign_in_to, module.info.name),
            url = url,
            onClose = onClose,
        )
        Spacer(Modifier.height(12.dp))
        CaptureWebContent(
            startUrl = spec.startUrl,
            playerOrigins = spec.sessionOrigins,
            desktopSite = spec.desktopSite,
            onHeader = { name, value, at -> viewModel.onSignInHeader(id, name, value, at, onDone) },
            onTick = { view ->
                importService?.onSignInTick(
                    view,
                    onApplePageProbe = { viewModel.onApplePageProbe(id, it, onDone) },
                    onCookies = { viewModel.onAppleCookies(id, it, onDone) },
                )
            },
            onExit = onClose,
            onUrlChange = { url = it },
            modifier = Modifier.fillMaxWidth().height(pageHeight),
        )
    }
}
