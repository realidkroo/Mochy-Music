// "test the module": search any song (or take the one playing) and see every step the module takes with it, songs, lyrics and song info alike

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import com.music.innertube.models.WatchEndpoint

/** a song the test can run with: what the module is asked for, and what the row shows */
private class TestSong(val id: String?, val track: TrackQuery, val cover: String?, val subtitle: String)

@Composable
internal fun ModuleTest(entry: ModuleEntry, viewModel: ModulesViewModel) {
    val steps by viewModel.steps.collectAsState()
    val testing by viewModel.testing.collectAsState()
    val found by viewModel.found.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val connection = LocalPlayerConnection.current
    val playing = connection?.mediaMetadata?.collectAsState()?.value
    val signedIn = entry.account is AccountState.SignedIn
    var query by remember { mutableStateOf("") }
    var tested by remember { mutableStateOf<String?>(null) }

    // before a search: the song that's playing, or one every catalog has
    val suggestions = if (query.isBlank()) {
        listOf(
            playing?.let {
                TestSong(
                    it.id,
                    TrackQuery(it.title, it.artists.map { a -> a.name }, it.album?.title, it.duration.takeIf { d -> d > 0 }?.times(1000L)),
                    it.thumbnailUrl,
                    it.artists.joinToString(", ") { a -> a.name },
                )
            } ?: TestSong(null, TrackQuery("Espresso", listOf("Sabrina Carpenter"), durationMs = 175_000), null, "Sabrina Carpenter"),
        )
    } else {
        found.map { song ->
            TestSong(
                song.id,
                TrackQuery(song.title, song.artists.map { it.name }, song.album?.name, song.duration?.times(1000L)),
                song.thumbnail,
                listOfNotNull(song.artists.joinToString(", ") { it.name }, song.album?.name).joinToString(" · "),
            )
        }
    }

    PageSectionTitle(stringResource(R.string.module_test), stringResource(R.string.module_test_any_song))
    SearchPill(query, searching) {
        query = it
        viewModel.searchSongs(it)
    }
    Spacer(Modifier.height(10.dp))
    Column(Modifier.animateContentSize()) {
        suggestions.forEach { song ->
            val key = song.id ?: song.track.title
            ResultRow(
                song = song,
                running = testing && tested == key,
                enabled = signedIn && !testing,
                onTest = {
                    tested = key
                    viewModel.runTest(entry.id, song.track)
                },
                onPlay = song.id?.let { id -> { connection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = id))) } },
            )
        }
        if (!signedIn) {
            Text(stringResource(R.string.module_use_sign_in_first), fontFamily = InterFontFamily, fontSize = 12.sp, color = PageSoftText, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        }
    }
    if (steps.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        PageCard(padding = 14) {
            Column(Modifier.animateContentSize()) {
                steps.forEach { StepRow(it) }
                if (!testing && steps.all { it.ok == true }) {
                    Text(
                        stringResource(R.string.module_test_passed, entry.module.info.name),
                        fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = Color.White,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPill(query: String, searching: Boolean, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(PageCardColor)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.module_test_search), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (searching) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
    }
}

/** a song to test with: tap it to run the test, the dots for playing it instead */
@Composable
private fun ResultRow(song: TestSong, running: Boolean, enabled: Boolean, onTest: () -> Unit, onPlay: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onTest)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.18f))) {
            song.cover?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.track.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.subtitle, fontFamily = InterFontFamily, fontSize = 12.sp, color = PageSoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (running) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.more_horiz), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.module_run_test)) },
                    enabled = enabled,
                    onClick = {
                        menu = false
                        onTest()
                    },
                )
                if (onPlay != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.module_test_play)) },
                        onClick = {
                            menu = false
                            onPlay()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: DiagnosticStep) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (step.ok) {
                null -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                true -> Icon(painterResource(R.drawable.check), null, tint = StatusOn, modifier = Modifier.size(18.dp))
                false -> Icon(painterResource(R.drawable.close), null, tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(step.name, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Text(step.detail, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = PageSoftText)
        }
    }
}
