// interactive settings blocks for custom module settings pages: live search, category grid/list, safe app toggles/sliders/choices, validated settings links and module settings rows

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.FloatingLyricsKey
import com.mochyy.mochymusic.floating.rememberFloatingLyricsSwitch
import com.mochyy.mochymusic.modules.browser.ModuleFeed
import com.mochyy.mochymusic.modules.security.SafeAppSettings
import com.mochyy.mochymusic.modules.security.SafeModuleRoutes
import com.mochyy.mochymusic.modules.skin.MediaListLayout
import com.mochyy.mochymusic.modules.skin.PageBlock
import com.mochyy.mochymusic.modules.skin.SkinColor
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.SettingsHighlight
import com.mochyy.mochymusic.ui.component.onChrome
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.devices.DeviceIntegrationRoute
import com.mochyy.mochymusic.ui.screens.devices.rememberDevicesLink
import com.mochyy.mochymusic.ui.screens.devices.showDeviceIntegration
import com.mochyy.mochymusic.ui.screens.settings.LocalizedSettingsPages
import com.mochyy.mochymusic.ui.screens.settings.LocalizedSettingsTitles
import com.mochyy.mochymusic.ui.screens.settings.SettingsCategoriesCatalog
import com.mochyy.mochymusic.ui.screens.settings.SettingsCategoryDef
import com.mochyy.mochymusic.ui.screens.settings.findSettingsCategory
import com.mochyy.mochymusic.ui.screens.settings.searchSettings
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.roundToInt

/** shared frosted/solid surface container for settings blocks */
@Composable
internal fun SettingsBlockSurface(
    corner: Dp,
    glass: GlassState?,
    glassRadius: Float?,
    colors: List<SkinColor> = emptyList(),
    values: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val accent = MaterialTheme.colorScheme.primary
    val resolved = colors.map { it.onChrome(accent, values) }
    Box(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (glassRadius != null && glass != null) {
            val radiusPx = with(LocalDensity.current) { glassRadius.dp.toPx() }
            GlassPillBackground(
                state = glass,
                blurRadius = { radiusPx },
                tint = resolved.singleOrNull() ?: Color.Black.copy(alpha = 0.28f),
                tileMode = android.graphics.Shader.TileMode.CLAMP,
                bleed = true,
                fallbackBlur = true,
                modifier = Modifier.matchParentSize(),
            )
            Box(Modifier.matchParentSize().border(0.75.dp, Color.White.copy(alpha = 0.14f), shape))
        } else {
            val bg = resolved.ifEmpty { listOf(LabColors.Card) }
            Box(Modifier.matchParentSize().background(pageBackgroundBrush(bg)))
        }
        content()
    }
}

/** live search across all of Mochy's settings */
@Composable
internal fun PageSettingsSearchBlock(block: PageBlock.SettingsSearch, page: PageContext, glass: GlassState?) {
    val context = LocalContext.current
    val sheets = LocalZoomOutOverlayState.current
    val devices = rememberDevicesLink()
    var query by remember { mutableStateOf("") }
    val results = remember(query, context) { searchSettings(context, query).take(20) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsBlockSurface(
            corner = block.corner,
            glass = glass,
            glassRadius = block.glass,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Icon(painterResource(R.drawable.search_filled), null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            block.placeholder?.takeIf { it.isNotBlank() } ?: stringResource(R.string.settings_search_placeholder),
                            fontFamily = InterFontFamily, fontSize = 15.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 15.sp, color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.settings_clear_search),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp).clip(CircleShape).clickable { query = "" },
                    )
                }
            }
        }

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                Text(
                    stringResource(R.string.settings_nothing_matches, query),
                    fontFamily = InterFontFamily, fontSize = 13.sp, color = LabColors.Secondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            } else {
                results.forEach { entry ->
                    SettingsBlockSurface(
                        corner = block.corner,
                        glass = glass,
                        glassRadius = block.glass,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            SettingsHighlight.pending = entry.highlightKey
                            if (entry.route == DeviceIntegrationRoute) {
                                sheets.showDeviceIntegration(devices)
                            } else {
                                page.navController.navigate(entry.route)
                            }
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(R.drawable.settings), null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (entry.titleRes ?: LocalizedSettingsTitles[entry.title])?.let { stringResource(it) } ?: entry.title,
                                    fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    (entry.pageRes ?: LocalizedSettingsPages[entry.page])?.let { stringResource(it) } ?: entry.page,
                                    fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Mochy's settings categories in List, Grid or Row; always guarantees Modules stays reachable */
@Composable
internal fun PageSettingsCategoriesBlock(block: PageBlock.SettingsCategories, page: PageContext, glass: GlassState?) {
    val sheets = LocalZoomOutOverlayState.current
    val devices = rememberDevicesLink()
    val defs = remember(block.categories) {
        val base = if (block.categories.isEmpty()) {
            SettingsCategoriesCatalog.filter { it.id != "module_store" || ModuleFeed.FeedUrl.isNotBlank() }
        } else {
            block.categories.mapNotNull(::findSettingsCategory).distinctBy { it.id }
                .filter { it.id != "module_store" || ModuleFeed.FeedUrl.isNotBlank() }
        }
        // modules MUST always remain reachable so a broken look module can always be disabled
        val modules = SettingsCategoriesCatalog.first { it.id == "modules" }
        if (base.any { it.id == "modules" }) base else base + modules
    }
    val open: (SettingsCategoryDef) -> Unit = { cat ->
        if (cat.route == DeviceIntegrationRoute) {
            sheets.showDeviceIntegration(devices)
        } else {
            page.navController.navigate(cat.route)
        }
    }

    when (block.layout) {
        MediaListLayout.Grid -> {
            val cols = block.columns.coerceIn(2, 4)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                defs.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { cat ->
                            SettingsBlockSurface(
                                corner = block.corner,
                                glass = glass,
                                glassRadius = block.glass,
                                colors = block.colors,
                                values = page.entry.values,
                                modifier = Modifier.weight(1f),
                                onClick = { open(cat) },
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                ) {
                                    Box(
                                        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(painterResource(cat.iconRes), null, tint = Color.White, modifier = Modifier.size(19.dp))
                                    }
                                    Text(
                                        stringResource(cat.titleRes),
                                        fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        MediaListLayout.Row -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                defs.forEach { cat ->
                    SettingsBlockSurface(
                        corner = block.corner,
                        glass = glass,
                        glassRadius = block.glass,
                        colors = block.colors,
                        values = page.entry.values,
                        onClick = { open(cat) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Icon(painterResource(cat.iconRes), null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(cat.titleRes),
                                fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        MediaListLayout.List, MediaListLayout.Compact -> {
            val compact = block.layout == MediaListLayout.Compact
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
                defs.forEach { cat ->
                    SettingsBlockSurface(
                        corner = block.corner,
                        glass = glass,
                        glassRadius = block.glass,
                        colors = block.colors,
                        values = page.entry.values,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { open(cat) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 11.dp else 14.dp),
                        ) {
                            Box(
                                Modifier.size(if (compact) 34.dp else 40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(cat.iconRes), null, tint = Color.White, modifier = Modifier.size(if (compact) 17.dp else 20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(cat.titleRes),
                                fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 15.sp else 17.sp,
                                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Icon(painterResource(R.drawable.navigate_next), null, tint = LabColors.Secondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** renders one safe Mochy preference or an entire safe category interactively */
@Composable
internal fun PageAppSettingBlock(block: PageBlock.AppSetting, glass: GlassState?) {
    val specs = remember(block.settingKey, block.category) {
        when {
            block.settingKey != null -> listOfNotNull(SafeAppSettings.find(block.settingKey))
            block.category != null -> SafeAppSettings.byCategory(block.category)
            else -> emptyList()
        }
    }
    if (specs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        specs.forEach { spec ->
            when (spec) {
                is SafeAppSettings.Toggle -> SafeToggleRow(spec, block.corner, glass, block.glass)
                is SafeAppSettings.Slider -> SafeSliderRow(spec, block.corner, glass, block.glass)
                is SafeAppSettings.Choice -> SafeChoiceRow(spec, block.corner, glass, block.glass)
            }
        }
    }
}

@Composable
private fun SafeToggleRow(spec: SafeAppSettings.Toggle, corner: Dp, glass: GlassState?, glassRadius: Float?) {
    if (spec.key == FloatingLyricsKey) {
        val (floating, onFloating) = rememberFloatingLyricsSwitch()
        ToggleRowChrome(spec.title, spec.description, floating, onFloating, corner, glass, glassRadius)
        return
    }
    var raw by rememberPreference(spec.key, spec.default)
    val shown = if (spec.inverted) !raw else raw
    ToggleRowChrome(
        title = spec.title,
        description = spec.description,
        checked = shown,
        onCheckedChange = { next -> raw = if (spec.inverted) !next else next },
        corner = corner,
        glass = glass,
        glassRadius = glassRadius,
    )
}

@Composable
private fun ToggleRowChrome(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    corner: Dp,
    glass: GlassState?,
    glassRadius: Float?,
) {
    SettingsBlockSurface(
        corner = corner,
        glass = glass,
        glassRadius = glassRadius,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!checked) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(description, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SafeSliderRow(spec: SafeAppSettings.Slider, corner: Dp, glass: GlassState?, glassRadius: Float?) {
    var value by rememberPreference(spec.key, spec.default)
    val accent = MaterialTheme.colorScheme.primary
    val formatted = if ((spec.step ?: 1f) >= 1f) "${value.roundToInt()}${spec.unit}" else "${"%.1f".format(value)}${spec.unit}"
    SettingsBlockSurface(
        corner = corner,
        glass = glass,
        glassRadius = glassRadius,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(spec.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
                Text(formatted, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = accent)
            }
            if (spec.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(spec.description, fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, maxLines = 2)
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = value.coerceIn(spec.min, spec.max),
                onValueChange = { raw ->
                    val stepped = spec.step?.takeIf { it > 0f }?.let { step -> ((raw / step).roundToInt() * step) } ?: raw
                    value = stepped.coerceIn(spec.min, spec.max)
                },
                valueRange = spec.min..spec.max,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
            )
        }
    }
}

@Composable
private fun SafeChoiceRow(spec: SafeAppSettings.Choice, corner: Dp, glass: GlassState?, glassRadius: Float?) {
    var value by rememberPreference(spec.key, spec.default)
    val accent = MaterialTheme.colorScheme.primary
    SettingsBlockSurface(
        corner = corner,
        glass = glass,
        glassRadius = glassRadius,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(spec.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            if (spec.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(spec.description, fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, maxLines = 2)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                spec.options.forEach { (key, label) ->
                    val lit = value.equals(key, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (lit) accent else Color.White.copy(alpha = 0.1f))
                            .clickable { value = key }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            label,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (lit) Color.Black else Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** row linking to a validated safe settings destination */
@Composable
internal fun PageSettingsLinkBlock(block: PageBlock.SettingsLink, page: PageContext, glass: GlassState?) {
    val route = SafeModuleRoutes.sanitize(block.target)?.takeIf { SafeModuleRoutes.isSafeSettingsRoute(it) } ?: return
    val cat = remember(route) { findSettingsCategory(route) }
    val title = block.title?.takeIf { it.isNotBlank() }?.let { page.subject.fill(it, page.entry.values) }
        ?: cat?.let { stringResource(it.titleRes) }
        ?: route.replace('_', ' ').replaceFirstChar { it.uppercase() }
    val subtitle = block.subtitle?.takeIf { it.isNotBlank() }?.let { page.subject.fill(it, page.entry.values) }
    SettingsBlockSurface(
        corner = block.corner,
        glass = glass,
        glassRadius = block.glass,
        modifier = Modifier.fillMaxWidth(),
        onClick = { page.navController.navigate(route) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (block.icon != null) {
                    AsyncImage(
                        model = page.skin.resolve(block.icon), contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(painterResource(cat?.iconRes ?: R.drawable.settings), null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(painterResource(R.drawable.navigate_next), null, tint = LabColors.Secondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** embeds active modules' inSettings pages on a custom settings screen */
@Composable
internal fun PageModuleSettingsPagesBlock(block: PageBlock.ModuleSettingsPages, page: PageContext) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        ModuleSettingsPages(open = { page.navController.navigate(it) })
    }
}
