package com.mochyy.mochymusic.ui.player.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.constants.DiscLabelContent
import com.mochyy.mochymusic.constants.DiscLabelContentKey
import com.mochyy.mochymusic.constants.DiscNameKey
import com.mochyy.mochymusic.constants.PlayerCoverStyle
import com.mochyy.mochymusic.constants.PlayerEndTimestamp
import com.mochyy.mochymusic.constants.PlayerEndTimestampKey
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.ButtonStyle
import com.mochyy.mochymusic.constants.FlagshipTransportLayout
import com.mochyy.mochymusic.ui.player.flagship.TransportLayoutPreview
import com.mochyy.mochymusic.ui.component.PlayerTransportButtons
import com.mochyy.mochymusic.ui.component.SeekBarStyle
import com.mochyy.mochymusic.ui.component.SeekBarTrack
import com.mochyy.mochymusic.ui.component.TransportColors
import com.mochyy.mochymusic.ui.player.customize.CoverOption
import com.mochyy.mochymusic.ui.player.customize.options
import com.mochyy.mochymusic.ui.player.menu.LocalMenuSheetClose
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.player.menu.MenuSheetSurface
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference

/** the sheet the editor has open, if any */
@Composable
fun StudioSheets(
    studio: PlayerStudioState,
    coverStyle: PlayerCoverStyle,
    buttonStyle: ButtonStyle,
    onButtonStyle: (ButtonStyle) -> Unit,
    seekBarStyle: SeekBarStyle,
    onSeekBarStyle: (SeekBarStyle) -> Unit,
    menuLayout: PlayerMenuLayout,
    transportColors: TransportColors,
    // set on the new flagship, whose controls sheet picks its button layout instead of a button style
    transportLayout: FlagshipTransportLayout? = null,
    onTransportLayout: (FlagshipTransportLayout) -> Unit = {},
) {
    val sheet = studio.sheet ?: return
    MenuSheetSurface(onDismiss = { studio.sheet = null }, wrapHeight = true) { _ ->
        // every text in the sheets takes the studio's type (Inter at the app's tracking)
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides studioText(StudioType.Body),
        ) {
        when (sheet) {
            StudioSheet.CONTROLS -> if (transportLayout != null) OptionSheet("Controls") {
                FlagshipTransportLayout.entries.forEach { layout ->
                    OptionRow(selected = layout == transportLayout, onClick = { onTransportLayout(layout) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TransportLayoutPreview(layout)
                            TwoLines(
                                stringResource(if (layout == FlagshipTransportLayout.THREE) R.string.flagship_layout_three else R.string.flagship_layout_ten),
                                stringResource(if (layout == FlagshipTransportLayout.THREE) R.string.flagship_layout_three_caption else R.string.flagship_layout_ten_caption),
                            )
                        }
                    }
                }
            } else OptionSheet("Controls") {
                ButtonStyle.entries.forEach { style ->
                    OptionRow(selected = style == buttonStyle, onClick = { onButtonStyle(style) }, caption = style.displayName) {
                        if (style == ButtonStyle.DEFAULT) {
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf(R.drawable.skip_previous, R.drawable.pause, R.drawable.skip_next).forEach { icon ->
                                    Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(if (icon == R.drawable.pause) 30.dp else 26.dp))
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxWidth()) {
                                PlayerTransportButtons(
                                    style = style,
                                    isPlaying = true,
                                    canSkipPrevious = true,
                                    canSkipNext = true,
                                    onPrevious = {},
                                    onPlayPause = {},
                                    onNext = {},
                                    colors = transportColors,
                                )
                            }
                        }
                    }
                }
            }

            StudioSheet.SEEKBAR -> OptionSheet("Seek bar") {
                SeekBarStyle.entries.forEach { style ->
                    OptionRow(selected = style == seekBarStyle, onClick = { onSeekBarStyle(style) }, caption = style.displayName) {
                        Box(Modifier.fillMaxWidth().height(28.dp)) {
                            SeekBarTrack(
                                style = style,
                                fraction = 0.62f,
                                activeColor = Color.White,
                                inactiveColor = Color.White.copy(alpha = 0.24f),
                                animateWave = true,
                            )
                        }
                    }
                }
            }

            StudioSheet.END_TIME -> {
                var shown by rememberEnumPreference(PlayerEndTimestampKey, PlayerEndTimestamp.END_TIME)
                OptionSheet("End timestamp") {
                    OptionRow(selected = shown == PlayerEndTimestamp.TIME_LEFT, onClick = { shown = PlayerEndTimestamp.TIME_LEFT }) {
                        TwoLines("Time left", "showing the time left for this song")
                    }
                    OptionRow(selected = shown == PlayerEndTimestamp.END_TIME, onClick = { shown = PlayerEndTimestamp.END_TIME }) {
                        TwoLines("End time", "showing the end / total duration for this song")
                    }
                }
            }

            StudioSheet.MENU_SLOT -> {
                val slot = studio.sheetSlot
                val current = menuLayout.actionAt(slot)
                val used = (menuLayout.slots + menuLayout.side).toSet()
                OptionSheet(if (slot == PlayerMenuLayout.SideSlot) "The button by the chevron" else "Menu button ${slot + 1}") {
                    PlayerMenuAction.entries.forEach { action ->
                        OptionRow(
                            selected = action == current,
                            onClick = { menuLayout.assign(slot, action) },
                            tag = if (action != current && action in used) "SWAPS" else null,
                            compact = true,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(action.icon), null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(action.label, color = Color.White, style = studioText(StudioType.Body))
                            }
                        }
                    }
                }
            }

            StudioSheet.COVER_OPTIONS -> OptionSheet(
                title = "Options for this cover",
                subtitle = null,
            ) {
                val options = coverStyle.options
                if (options.isEmpty()) {
                    Text("This cover has nothing more to set", color = Color.White.copy(alpha = 0.6f), style = studioText(StudioType.Body, FontWeight.Normal), modifier = Modifier.padding(vertical = 8.dp))
                }
                options.forEach { option ->
                    when (option) {
                        is CoverOption.Switch -> SwitchRow(option)
                        is CoverOption.Text -> TextRow(option.title, option.description, option.key, option.placeholder)
                    }
                }
            }

            StudioSheet.DISC_LABEL -> {
                var content by rememberEnumPreference(DiscLabelContentKey, DiscLabelContent.CUSTOM)
                val connection = LocalPlayerConnection.current
                val track = connection?.uiState?.trackInfo?.collectAsState()?.value
                OptionSheet("The disc label") {
                    OptionRow(selected = content == DiscLabelContent.CUSTOM, onClick = { content = DiscLabelContent.CUSTOM }) {
                        Column {
                            TwoLines("Custom name", "whatever you type")
                            if (content == DiscLabelContent.CUSTOM) {
                                Spacer(Modifier.height(10.dp))
                                LabelField(DiscNameKey, "Type the label")
                            }
                        }
                    }
                    OptionRow(selected = content == DiscLabelContent.MUSIC_NAME, onClick = { content = DiscLabelContent.MUSIC_NAME }) {
                        TwoLines("Music name", track?.title.orEmpty())
                    }
                    OptionRow(selected = content == DiscLabelContent.ARTIST_NAME, onClick = { content = DiscLabelContent.ARTIST_NAME }) {
                        TwoLines("Artist", track?.artist.orEmpty())
                    }
                    OptionRow(selected = content == DiscLabelContent.ALBUM_NAME, onClick = { content = DiscLabelContent.ALBUM_NAME }) {
                        TwoLines("Album", track?.album.orEmpty())
                    }
                    OptionRow(selected = content == DiscLabelContent.MUSIC_AND_ARTIST, onClick = { content = DiscLabelContent.MUSIC_AND_ARTIST }) {
                        TwoLines("Music name and artist", listOfNotNull(track?.title, track?.artist).filter { it.isNotBlank() }.joinToString(" · "))
                    }
                    OptionRow(selected = content == DiscLabelContent.NONE, onClick = { content = DiscLabelContent.NONE }) {
                        TwoLines("Nothing", "a plain label")
                    }
                }
            }

            StudioSheet.POSTER -> PosterOptions()
            StudioSheet.IPOD -> IpodOptions()
            StudioSheet.BEDSIDE -> BedsideOptions()

        }
        }
    }
}

/**
 * The sheets of the styles that aren't the flagship (the Poster's), which open over the style
 * itself with none of the flagship's editor around them.
 */
@Composable
fun StyleSheets(studio: PlayerStudioState) {
    val sheet = studio.sheet
    if (sheet != StudioSheet.POSTER && sheet != StudioSheet.IPOD && sheet != StudioSheet.BEDSIDE) return
    MenuSheetSurface(onDismiss = { studio.sheet = null }, wrapHeight = true) { _ ->
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides studioText(StudioType.Body),
        ) {
            when (sheet) {
                StudioSheet.IPOD -> IpodOptions()
                StudioSheet.BEDSIDE -> BedsideOptions()
                else -> PosterOptions()
            }
        }
    }
}

private val PosterVisualizerOption = CoverOption.Switch(
    title = "Visualizer",
    description = "bars round the cover, moving with the music",
    icon = R.drawable.graphic_eq,
    key = com.mochyy.mochymusic.constants.PosterVisualizerKey,
    default = false,
)

@Composable
private fun ColumnScope.PosterOptions() {
    OptionSheet(title = "The poster") {
        SwitchRow(PosterVisualizerOption)
    }
}

/** "What to change with": the title, what it's for, the options, and Done */
@Composable
internal fun ColumnScope.OptionSheet(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val close = LocalMenuSheetClose.current
    Column(modifier = Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
        Spacer(Modifier.height(10.dp))
        Text("What to change with", color = Color.White, style = studioText(StudioType.Title, FontWeight.Bold))
        Spacer(Modifier.height(4.dp))
        Text(subtitle ?: title, color = Color.White.copy(alpha = 0.55f), style = studioText(StudioType.Subtitle, FontWeight.Medium))
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.15f))
                .clickable { close() },
            contentAlignment = Alignment.Center,
        ) {
            Text("Done", color = Color.White, style = studioText(StudioType.Button))
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** one choice: its preview, and SELECTED on the one in use */
@Composable
internal fun OptionRow(
    selected: Boolean,
    onClick: () -> Unit,
    caption: String? = null,
    tag: String? = null,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column {
        if (caption != null) {
            Text(caption, color = Color.White.copy(alpha = 0.55f), style = studioText(StudioType.Caption, FontWeight.Medium), modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 48.dp else 60.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(if (selected) lerp(MenuRowSurface, Color.White, 0.09f) else MenuRowSurface)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) { content() }
            val label = if (selected) "SELECTED" else tag
            if (label != null) {
                Spacer(Modifier.width(12.dp))
                Text(label, color = Color.White.copy(alpha = if (selected) 0.45f else 0.3f), style = studioText(StudioType.Label, FontWeight.Bold))
            }
        }
    }
}

@Composable
internal fun TwoLines(title: String, body: String) {
    Column {
        Text(title, color = Color.White, style = studioText(StudioType.Body))
        if (body.isNotBlank()) Text(body, color = Color.White.copy(alpha = 0.65f), style = studioText(StudioType.Caption, FontWeight.Normal), maxLines = 2)
    }
}

@Composable
internal fun SwitchRow(option: CoverOption.Switch) {
    val parent = option.parent
    val parentOn = if (parent != null) rememberPreference(parent.key, defaultValue = parent.default).value else true
    var stored by rememberPreference(option.key, defaultValue = option.default)
    val checked = if (option.inverted) !stored else stored
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MenuRowSurface)
            .clickable(enabled = parentOn) { stored = !stored }
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(option.icon), null, tint = Color.White.copy(alpha = if (parentOn) 1f else 0.4f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f)) { TwoLines(option.title, option.description) }
        AppSwitch(checked = checked, onCheckedChange = { stored = if (option.inverted) !it else it }, enabled = parentOn)
    }
}

@Composable
private fun TextRow(title: String, description: String, key: androidx.datastore.preferences.core.Preferences.Key<String>, placeholder: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        TwoLines(title, description)
        Spacer(Modifier.height(10.dp))
        LabelField(key, placeholder)
    }
}

@Composable
private fun LabelField(key: androidx.datastore.preferences.core.Preferences.Key<String>, placeholder: String) {
    var stored by rememberPreference(key, defaultValue = "")
    // Typed into a value of its own and saved on every change. Bound to the preference straight,
    // the field got the old text back between keystrokes (the save comes back asynchronously),
    // its cursor jumped to the start and the letters went in backwards.
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(stored, androidx.compose.ui.text.TextRange(stored.length))) }
    var focused by remember { mutableStateOf(false) }
    // the saved label arriving (or changed elsewhere) while it isn't being typed in
    androidx.compose.runtime.LaunchedEffect(stored, focused) {
        if (!focused && stored != field.text) {
            field = androidx.compose.ui.text.input.TextFieldValue(stored, androidx.compose.ui.text.TextRange(stored.length))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (field.text.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.4f), style = studioText(StudioType.Body))
        BasicTextField(
            value = field,
            onValueChange = { next ->
                val text = next.text.take(40)
                field = if (text == next.text) next else next.copy(text = text, selection = androidx.compose.ui.text.TextRange(text.length))
                if (text != stored) stored = text
            },
            singleLine = true,
            textStyle = studioText(StudioType.Body).copy(color = Color.White),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
    }
}
