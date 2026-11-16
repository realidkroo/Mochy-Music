// the pieces app behaviour, playback and now playing build their rows from

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.theme.InterFontFamily

// a section's title (if it has one) and its grouped rows, left out whole when the search hides every row
@Composable
internal fun SettingsSection(title: String?, items: List<SettingsItem>) {
    if (items.isEmpty()) return
    if (title != null) {
        Text(
            text = title,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
        )
    }
    SettingsGroup(style = SettingsGroupStyle.Grouped, items = items)
    Spacer(Modifier.height(18.dp))
}

internal fun List<SettingsItem>.matching(query: String): List<SettingsItem> {
    if (query.isBlank()) return this
    return filter {
        it.highlightKey?.contains(query, ignoreCase = true) == true ||
            it.searchText?.contains(query, ignoreCase = true) == true ||
            it.descriptionText?.contains(query, ignoreCase = true) == true ||
            it.detail?.contains(query, ignoreCase = true) == true
    }
}

// a row title with its value on the right, for the rows with a slider under them
@Composable
internal fun ValueTitle(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title)
        Text(
            text = value,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.70f),
        )
    }
}

// one switch row: key is the english name search results point at, subOptionOf tucks it under a parent that's on
internal fun switchItem(
    key: String,
    title: String,
    description: String,
    icon: Painter,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: String? = null,
    subOptionOf: Boolean? = null,
    visible: Boolean = true,
) = SettingsItem(
    title = { Text(title) },
    highlightKey = key,
    searchText = title,
    descriptionText = description,
    detail = detail,
    icon = icon,
    iconShape = CircleShape,
    isVisible = visible && subOptionOf != false,
    isSubOption = subOptionOf != null,
    onClick = { onCheckedChange(!checked) },
    trailingContent = {
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = subOptionOf != false)
    },
)

// a row that opens another page, with the little arrow on the right
internal fun linkItem(
    key: String,
    title: String,
    description: String,
    icon: Painter,
    onClick: () -> Unit,
    visible: Boolean = true,
    isSubOption: Boolean = false,
) = SettingsItem(
    title = { Text(title) },
    highlightKey = key,
    searchText = title,
    descriptionText = description,
    icon = icon,
    iconShape = CircleShape,
    isVisible = visible,
    isSubOption = isSubOption,
    onClick = onClick,
    trailingContent = { NextArrow() },
)

@Composable
private fun NextArrow() {
    Icon(
        painter = painterResource(R.drawable.navigate_next),
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.size(20.dp),
    )
}
