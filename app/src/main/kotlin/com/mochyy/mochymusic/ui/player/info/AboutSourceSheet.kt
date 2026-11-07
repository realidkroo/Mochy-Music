// AboutSourceSheet.kt

package com.mochyy.mochymusic.ui.player.info

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.ui.player.menu.LocalMenuSheetClose
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.player.menu.MenuSheetSurface
import com.mochyy.mochymusic.ui.screens.setup.onboarding.ProviderSquircle
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** picks where "about" reads from; each row previews what that source says, so the choice is an easy one */
@Composable
fun AboutSourceSheet(
    selected: AboutSource,
    about: SongAbout,
    onPick: (AboutSource) -> Unit,
    onDismiss: () -> Unit,
) {
    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        val close = LocalMenuSheetClose.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        ) {
            InfoColumn(modifier = Modifier.widthIn(max = 600.dp)) {
                InfoSheetTitle("About from")
                Spacer(Modifier.height(16.dp))
                AboutSource.entries.filter { it != AboutSource.Modules || com.mochyy.mochymusic.modules.ModuleMetadata.sources.isNotEmpty() }.forEach { source ->
                    SourceRow(
                        source = source,
                        preview = previewOf(about, source),
                        isSelected = source == selected,
                        moduleName = about.moduleName,
                        onClick = {
                            onPick(source)
                            close()
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun previewOf(about: SongAbout, source: AboutSource): String = when {
    !doneFor(about, source) -> "Looking…"
    else -> textFor(about, source)?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim() ?: "Nothing about this song yet"
}

@Composable
private fun SourceRow(source: AboutSource, preview: String, isSelected: Boolean, onClick: () -> Unit, moduleName: String? = null) {
    val checkAlpha by animateFloatAsState(if (isSelected) 1f else 0f, label = "sourceCheck")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        when (source) {
            AboutSource.YouTube -> ProviderSquircle(service = AccountService.YOUTUBE_MUSIC, size = 40.dp)
            AboutSource.Musixmatch -> SourceMark("M", Color(0xFFFF6050), Color.White)
            AboutSource.Genius -> SourceMark("G", Color(0xFFFFFF64), Color.Black)
            AboutSource.Wikipedia -> SourceMark("W", Color.White, Color.Black)
            AboutSource.Modules -> SourceMark(moduleName?.take(1)?.uppercase() ?: "M", Color(0xFF8E8CF8), Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = source.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = preview,
                color = InfoMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { alpha = checkAlpha }
                .clip(CircleShape)
                .background(Color.White),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
        }
    }
}

// the app has no logos for these, so a plain lettermark in each one's colour stands in rather than a redrawn logo
@Composable
private fun SourceMark(letter: String, background: Color, foreground: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(background),
    ) {
        Text(text = letter, color = foreground, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}
