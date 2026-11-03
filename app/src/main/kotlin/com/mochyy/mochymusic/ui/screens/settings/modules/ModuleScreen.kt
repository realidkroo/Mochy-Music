// one module's page: its account, its switch, its choices, its servers or the things people added to it, a playback test for the ones that play songs, and removing one added from a link or a zip

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.ModuleServer
import com.mochyy.mochymusic.module.ServerListModule
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.module.applemusic.AppleMusicModule
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.modules.ModuleOrigin
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabChip
import com.mochyy.mochymusic.ui.screens.audiolab.LabChips
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabSectionLabel
import com.mochyy.mochymusic.ui.screens.audiolab.LabTextAction
import com.mochyy.mochymusic.ui.screens.audiolab.LabToggleRow
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel

@Composable
fun ModuleScreen(navController: NavController, id: String, viewModel: ModulesViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val checking by viewModel.checking.collectAsState()
    val entry = entries.firstOrNull { it.id == id }
    val context = LocalContext.current
    val overlay = LocalZoomOutOverlayState.current

    fun signIn() {
        val module = entry?.module ?: return
        overlay.showModuleSignInSheet(module, viewModel) { state ->
            val message = when (state) {
                is AccountState.SignedIn -> context.getString(R.string.module_signed_in, module.info.name)
                is AccountState.Expired -> context.getString(R.string.module_sign_in_refused, module.info.name, state.reason)
                AccountState.SignedOut -> context.getString(R.string.module_sign_in_refused, module.info.name, "")
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    SubSettingsScaffold(
        title = entry?.module?.info?.name ?: stringResource(R.string.settings_modules_category),
        backLabel = stringResource(R.string.settings_modules_category),
        onBack = { navController.navigateUp() },
    ) {
        if (entry == null) return@SubSettingsScaffold
        AccountCard(
            entry = entry,
            checking = checking,
            onSignIn = ::signIn,
            onCheck = { viewModel.verify(id) },
            onSignOut = { viewModel.signOut(id) },
        )
        Spacer(Modifier.height(12.dp))
        UseCard(entry) { viewModel.setEnabled(id, it) }
        Spacer(Modifier.height(16.dp))
        Choices(entry) { choice, option -> viewModel.setPick(id, choice, option) }
        if (entry.module is ServerListModule) Servers(id, viewModel)
        (entry.module as? ItemListModule)?.let { Items(id, it, viewModel) }
        Pages(entry, viewModel) { page -> navController.navigate(modulePageRoute(id, page)) }
        if (entry.adds(ModuleFeature.AudioSource)) {
            PlaybackTest(entry, viewModel)
            Spacer(Modifier.height(12.dp))
        }
        if (entry.origin is ModuleOrigin.Web || entry.origin is ModuleOrigin.File || entry.origin is ModuleOrigin.Code) {
            LabCard(padding = 10.dp) {
                LabTextAction(stringResource(R.string.module_remove), R.drawable.delete) {
                    viewModel.remove(id)
                    navController.navigateUp()
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (id == AppleMusicModule.Id) Footnote(stringResource(R.string.module_apple_limits))
        Spacer(Modifier.height(120.dp))
    }
}

/** the module, its status, and signing in or checking it */
@Composable
private fun AccountCard(entry: ModuleEntry, checking: Boolean, onSignIn: () -> Unit, onCheck: () -> Unit, onSignOut: () -> Unit) {
    val module = entry.module
    val needsAccount = module.signIn != null
    LabCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleLogo(entry.id, module.info.name, size = 56)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(module.info.name, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Text(moduleStatus(entry), fontFamily = InterFontFamily, fontSize = 13.sp, color = LabColors.Secondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(module.info.description, fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = LabColors.Secondary)
        Spacer(Modifier.height(10.dp))
        FeatureChips(module.adds)
        Spacer(Modifier.height(6.dp))
        val origin = moduleOrigin(entry)
        Text(
            module.info.author?.let { stringResource(R.string.module_by, origin, it) } ?: origin,
            fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Faint,
        )
        // a look from a zip has no server and no account, so there's nothing to check or sign into
        if (entry.origin is ModuleOrigin.File) return@LabCard
        Spacer(Modifier.height(14.dp))
        if (needsAccount && entry.account !is AccountState.SignedIn) {
            PrimaryButton(
                text = if (entry.account is AccountState.Expired) stringResource(R.string.module_sign_in_again)
                else stringResource(R.string.module_sign_in_to, module.info.name),
                onClick = onSignIn,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabTextAction(
                    when {
                        checking -> stringResource(R.string.module_checking)
                        needsAccount -> stringResource(R.string.module_check_account)
                        module is ItemListModule -> stringResource(R.string.module_check_items, module.itemsLabel.lowercase())
                        else -> stringResource(R.string.module_check_server)
                    },
                    R.drawable.refresh,
                    busy = checking,
                    onClick = onCheck,
                )
                Spacer(Modifier.weight(1f))
                if (needsAccount) LabTextAction(stringResource(R.string.module_sign_out), R.drawable.close, onClick = onSignOut)
            }
        }
    }
}

/** the one switch: on, everything the module adds shows up; off, it's all gone again */
@Composable
private fun UseCard(entry: ModuleEntry, onChange: (Boolean) -> Unit) {
    val name = entry.module.info.name
    val needsAccount = entry.module.signIn != null
    val signedIn = entry.account is AccountState.SignedIn
    val what = buildList {
        if (entry.adds(ModuleFeature.AudioSource)) add(stringResource(R.string.module_use_songs, name))
        if (entry.adds(ModuleFeature.Lyrics)) add(stringResource(R.string.module_use_lyrics))
        if (entry.adds(ModuleFeature.Look)) add(stringResource(R.string.module_use_look))
    }
    LabCard(padding = 10.dp) {
        LabToggleRow(
            title = stringResource(R.string.module_use, name),
            subtitle = if (needsAccount && !signedIn) stringResource(R.string.module_use_sign_in_first) else what.joinToString(" "),
            checked = entry.enabled && signedIn,
            enabled = signedIn,
            onCheckedChange = onChange,
        )
    }
}

/** the module's own settings, each a row of chips; a pick the module no longer offers reads as the default */
@Composable
private fun Choices(entry: ModuleEntry, onPick: (choice: String, option: String) -> Unit) {
    entry.module.choices.forEach { choice ->
        if (choice.options.isEmpty()) return@forEach
        val picked = entry.picks[choice.id]?.takeIf { p -> choice.options.any { it.id == p } } ?: choice.options.first().id
        LabSectionLabel(choice.label)
        LabChips(
            chips = choice.options.map { LabChip(it.id, it.label) },
            selectedKey = picked,
            onSelect = { onPick(choice.id, it) },
        )
        choice.options.firstOrNull { it.id == picked }?.let { option ->
            Text(
                option.description, fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary,
                modifier = Modifier.padding(top = 8.dp, start = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** the pages a look module brings, there to open while it's on */
@Composable
private fun Pages(entry: ModuleEntry, viewModel: ModulesViewModel, onOpen: (String) -> Unit) {
    val pages = remember(entry.id) { viewModel.skinOf(entry.id)?.pages.orEmpty() }
    if (pages.isEmpty()) return
    LabSectionLabel(stringResource(R.string.module_pages))
    LabCard(padding = 10.dp) {
        pages.forEach { page ->
            LabTextAction(page.title, R.drawable.navigate_next, enabled = entry.enabled) { onOpen(page.id) }
        }
    }
    if (!entry.enabled) {
        Text(
            stringResource(R.string.module_pages_off), fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp,
            color = LabColors.Secondary, modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
}

/** the servers the module plays from, the ones people added first, and adding another */
@Composable
private fun Servers(id: String, viewModel: ModulesViewModel) {
    val servers by viewModel.servers.collectAsState()
    val overlay = LocalZoomOutOverlayState.current
    LaunchedEffect(id) { viewModel.loadServers(id) }

    LabSectionLabel(stringResource(R.string.module_servers))
    LabCard(padding = 10.dp) {
        servers.forEach { server ->
            ServerRow(server, onRemove = { viewModel.removeServer(id, server.url) })
        }
        LabTextAction(stringResource(R.string.module_server_add), R.drawable.add) {
            overlay.showAddServerSheet(id, viewModel)
        }
    }
    Text(
        stringResource(R.string.module_servers_body), fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp,
        color = LabColors.Secondary, modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp),
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ServerRow(server: ModuleServer, onRemove: () -> Unit) {
    val address = server.url.substringAfter("://")
    // a server found through a site goes by the site's name, with its own address under it
    val detail = when {
        server.builtIn -> stringResource(R.string.module_server_built_in)
        server.name != address -> address
        else -> null
    }
    ListRow(server.name.ifBlank { address }, detail, onRemove.takeIf { !server.builtIn })
}

/** what people added to the module, under the name the module gives them, and adding more from a zip or a link */
@Composable
private fun Items(id: String, module: ItemListModule, viewModel: ModulesViewModel) {
    val items by viewModel.items.collectAsState()
    val overlay = LocalZoomOutOverlayState.current
    LaunchedEffect(id) { viewModel.loadItems(id) }

    LabSectionLabel(module.itemsLabel)
    LabCard(padding = 10.dp) {
        items.forEach { item ->
            ListRow(item.name, item.detail.ifBlank { null }, onRemove = { viewModel.removeItem(id, item.id) })
        }
        LabTextAction(stringResource(R.string.module_items_add), R.drawable.add) {
            overlay.showAddItemsSheet(id, module.info.name, viewModel)
        }
    }
    if (module.itemsAbout.isNotBlank()) {
        Text(
            module.itemsAbout, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp,
            color = LabColors.Secondary, modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
}

/** a server or something added to the module: its name, a line under it, and a cross to remove it when it can go */
@Composable
private fun ListRow(name: String, detail: String?, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            detail?.let { Text(it, fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary) }
        }
        if (onRemove != null) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.close), stringResource(R.string.module_server_remove, name),
                    tint = LabColors.Secondary, modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** every step playback takes, on this phone, with the song that's playing (or a known one) */
@Composable
private fun PlaybackTest(entry: ModuleEntry, viewModel: ModulesViewModel) {
    val steps by viewModel.steps.collectAsState()
    val testing by viewModel.testing.collectAsState()
    val playing = LocalPlayerConnection.current?.mediaMetadata?.collectAsState()?.value
    val signedIn = entry.account is AccountState.SignedIn
    val name = entry.module.info.name

    LabSectionLabel(stringResource(R.string.module_test))
    LabCard(padding = 14.dp) {
        val track = playing?.let {
            TrackQuery(
                title = it.title,
                artists = it.artists.map { a -> a.name },
                album = it.album?.title,
                durationMs = it.duration.takeIf { d -> d > 0 }?.times(1000L),
            )
        } ?: TrackQuery("Espresso", listOf("Sabrina Carpenter"), durationMs = 175_000)
        Text(
            stringResource(if (entry.module.signIn != null) R.string.module_test_body_drm else R.string.module_test_body, track.title),
            fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = LabColors.Secondary,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = stringResource(if (testing) R.string.module_testing else R.string.module_run_test),
            enabled = signedIn && !testing,
        ) {
            viewModel.runTest(entry.id, track)
        }
        Column(Modifier.animateContentSize()) {
            steps.forEach { StepRow(it) }
        }
        if (!testing && steps.isNotEmpty() && steps.all { it.ok == true }) {
            Text(
                stringResource(R.string.module_test_passed, name),
                fontFamily = InterFontFamily, fontSize = 13.sp, color = Color.White,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun StepRow(step: DiagnosticStep) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (step.ok) {
                null -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                true -> Icon(painterResource(R.drawable.check), null, tint = Color(0xFF8FD98B), modifier = Modifier.size(18.dp))
                false -> Icon(painterResource(R.drawable.close), null, tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(step.name, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Text(step.detail, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) Color.White else LabColors.Row)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            color = if (enabled) Color.Black else LabColors.Faint,
        )
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = LabColors.Faint,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}
