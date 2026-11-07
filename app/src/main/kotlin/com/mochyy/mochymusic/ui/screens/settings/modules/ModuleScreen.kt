// one module's page: its banner and icon, the switch, what it is, data saver, its options and lists, a test with any song, and removing it

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.ServerListModule
import com.mochyy.mochymusic.module.applemusic.AppleMusicModule
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.coverTintBackground
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import com.mochyy.mochymusic.viewmodels.ModulesViewModel

private val BannerHeight = 260.dp

@Composable
fun ModuleScreen(navController: NavController, id: String, viewModel: ModulesViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val entry = entries.firstOrNull { it.id == id }
    val context = LocalContext.current
    val overlay = LocalZoomOutOverlayState.current
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

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

    // the page takes its colour from the module's banner or icon, darkened so the white text stays readable
    val art = listOfNotNull(entry?.banner ?: entry?.icon).map { if (it.startsWith("/")) "file://$it" else it }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .coverTintBackground(art, seed = id),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (entry == null) {
                Spacer(Modifier.height(BannerHeight))
                return@Column
            }
            ModuleBanner(entry)
            Column(Modifier.padding(horizontal = 20.dp)) {
                ModuleHeader(entry)
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(1.dp).background(Color.White.copy(alpha = 0.3f)))
                Spacer(Modifier.height(20.dp))
                EnableCard(entry) { viewModel.setEnabled(id, it) }
                Spacer(Modifier.height(10.dp))
                InfoCard(entry)
                AccountActions(
                    entry = entry,
                    checking = viewModel.checking.collectAsState().value,
                    onSignIn = ::signIn,
                    onCheck = { viewModel.verify(id) },
                    onSignOut = { viewModel.signOut(id) },
                )
                if (entry.adds(ModuleFeature.AudioSource)) DataSaverCard(entry)
                ModuleOptions(
                    entry = entry,
                    onPick = { choice, option -> viewModel.setPick(id, choice, option) },
                    onField = { field, value -> viewModel.setField(id, field, value) },
                )
                if (entry.module is ServerListModule) ModuleServers(id, viewModel)
                (entry.module as? ItemListModule)?.let { ModuleItems(id, it, viewModel) }
                ModulePages(entry, viewModel) { page -> navController.navigate(modulePageRoute(id, page)) }
                if (entry.adds(ModuleFeature.AudioSource) || entry.adds(ModuleFeature.Lyrics) || entry.adds(ModuleFeature.Metadata)) {
                    ModuleTest(entry, viewModel)
                }
                if (entry.removable) {
                    Spacer(Modifier.height(18.dp))
                    RemoveButton {
                        viewModel.remove(id)
                        navController.navigateUp()
                    }
                }
                if (id == AppleMusicModule.Id) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.module_apple_limits), fontFamily = InterFontFamily, fontSize = 12.sp,
                        lineHeight = 17.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                Spacer(Modifier.height(bottom + 40.dp))
            }
        }
        BackButton(onClick = { navController.navigateUp() })
    }
}

/** the module's banner, or its icon blown up and blurred when it has none, fading into the page */
@Composable
private fun ModuleBanner(entry: ModuleEntry) {
    val fade = Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent)
    Box(
        Modifier
            .fillMaxWidth()
            .height(BannerHeight)
            // one offscreen pass for a still picture, so its bottom melts into whatever colour the page took
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(fade, blendMode = BlendMode.DstIn)
            },
    ) {
        val banner = entry.banner
        val icon = entry.icon
        when {
            banner != null -> AsyncImage(
                model = artModel(banner), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // blur only exists on android 12+, older phones would get a giant sharp icon instead
            icon != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> AsyncImage(
                model = artModel(icon), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f }.blur(48.dp),
            )
        }
    }
}

/** icon, name with its status dot, and what it's for, pulled up over the banner like the mock */
@Composable
private fun ModuleHeader(entry: ModuleEntry) {
    val info = entry.module.info
    Column(Modifier.pullUp(78.dp)) {
        ModuleLogo(entry.id, info.name, size = 92, art = entry.icon, corner = 0.22f)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                info.name, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp,
                letterSpacing = (-1).sp, lineHeight = 36.sp, color = Color.White, modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            StatusDot(statusColor(entry), size = 18.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            info.description, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            lineHeight = 20.sp, color = Color.White.copy(alpha = 0.92f),
        )
        Spacer(Modifier.height(8.dp))
        FeatureChips(entry.module.adds)
    }
}

// lays its content [by] higher than it would sit and takes that much less room, so it overlaps what's above
private fun Modifier.pullUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val lift = by.roundToPx()
    layout(placeable.width, (placeable.height - lift).coerceAtLeast(0)) { placeable.place(0, -lift) }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val top = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .padding(top = top + 14.dp, start = 18.dp)
            .size(40.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.arrow_back_ios), stringResource(R.string.back_content_desc),
            tint = Color.White, modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x33FF453A), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
    ) {
        Icon(painterResource(R.drawable.delete), null, tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.module_remove), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFFFF8A80))
    }
}
