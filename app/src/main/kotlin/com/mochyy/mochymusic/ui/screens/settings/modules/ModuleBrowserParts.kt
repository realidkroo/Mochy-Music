// the module browser's pieces: the header with the account chip, the search, the kind chips, a module's card and its loading stand-in

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.browser.FeedModule
import com.mochyy.mochymusic.modules.browser.FeedPerson
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.BrowserFilter

internal val BrowserCard = Color(0xFF2A2A2A)
private val BrowserChipOn = Color(0xFF5A5A5A)
private val BrowserBone = Color(0xFF474747)
private val BrowserPill = Color(0xFF6B6B6B)
private val BrowserSoft = Color.White.copy(alpha = 0.55f)
private val BrowserOnline = Color(0xFF7CFF5A)
private val BrowserCardShape = RoundedCornerShape(36.dp)

/** what a module's button says: it can be added, a newer one is out, it's already in, or it's paid and not theirs yet */
enum class BrowserAction { Add, Update, Added, Locked }

/** the back button and, once someone's signed in, their avatar with a dot that's green while they're in mochy's bar */
@Composable
internal fun BrowserHeader(person: FeedPerson?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(BrowserCard).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.arrow_back_ios), stringResource(R.string.back_content_desc), tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
        if (person != null && person.name.isNotBlank()) AccountChip(person)
    }
}

@Composable
private fun AccountChip(person: FeedPerson) {
    Row(
        modifier = Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(BrowserCard).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val description = stringResource(R.string.module_browser_account, person.name)
        if (person.avatar != null) {
            AsyncImage(model = person.avatar, contentDescription = description, modifier = Modifier.size(30.dp).clip(CircleShape))
        } else {
            Box(Modifier.size(30.dp).clip(CircleShape).background(BrowserBone), contentAlignment = Alignment.Center) {
                Text(person.name.take(1).uppercase(), color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.35f)))
        Box(Modifier.padding(end = 4.dp).size(12.dp).clip(CircleShape).background(if (person.member) BrowserOnline else BrowserSoft))
    }
}

@Composable
internal fun BrowserTitle() {
    Text(
        stringResource(R.string.module_browser_title),
        color = Color.White,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.2).sp,
    )
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.module_browser_subtitle), color = BrowserSoft, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
}

@Composable
internal fun BrowserSearch(query: String, onQuery: (String) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(BrowserCard).padding(horizontal = 22.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text(stringResource(R.string.module_browser_search), color = BrowserSoft, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            textStyle = TextStyle(color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun BrowserChips(filter: BrowserFilter, onFilter: (BrowserFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BrowserFilter.entries.forEach { option ->
            val on = option == filter
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (on) BrowserChipOn else BrowserCard)
                    .clickable { onFilter(option) }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(filterLabel(option)), color = if (on) Color.White else BrowserSoft, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

private fun filterLabel(filter: BrowserFilter): Int = when (filter) {
    BrowserFilter.All -> R.string.module_browser_all
    BrowserFilter.Looks -> R.string.module_browser_looks
    BrowserFilter.Players -> R.string.module_browser_players
    BrowserFilter.Sources -> R.string.module_browser_sources
    BrowserFilter.Paid -> R.string.module_browser_paid
}

@Composable
internal fun BrowserSectionTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        letterSpacing = (-0.8).sp,
        modifier = Modifier.padding(top = 26.dp, bottom = 14.dp),
    )
}

/** one module: its icon, name, who made it and what it does; the card opens its post, the pill adds it */
@Composable
internal fun BrowserModuleCard(module: FeedModule, action: BrowserAction, fetching: Boolean, onOpen: () -> Unit, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(BrowserCardShape).background(BrowserCard).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModuleLogo(module.moduleId ?: module.id, module.name, size = 66, art = module.icon, corner = 0.3f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(module.name, color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val by = module.author?.let { author ->
                module.version?.let { stringResource(R.string.module_browser_by_version, author, it) } ?: stringResource(R.string.module_browser_by, author)
            }
            by?.let { Text(it, color = BrowserSoft, fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (module.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(module.description, color = BrowserSoft, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        ActionPill(action, fetching, onAction)
    }
}

@Composable
private fun ActionPill(action: BrowserAction, fetching: Boolean, onClick: () -> Unit) {
    val label = when (action) {
        BrowserAction.Add -> R.string.module_browser_add
        BrowserAction.Update -> R.string.module_browser_update
        BrowserAction.Added -> R.string.module_browser_added
        BrowserAction.Locked -> R.string.module_browser_locked
    }
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (action == BrowserAction.Add || action == BrowserAction.Update) Color.White else BrowserPill)
            .clickable(enabled = !fetching && action != BrowserAction.Added, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (fetching) {
            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            val ink = if (action == BrowserAction.Add || action == BrowserAction.Update) Color.Black else Color.White.copy(alpha = 0.8f)
            Text(stringResource(label), color = ink, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/** the grey stand-in for a card while the list is on its way */
@Composable
internal fun BrowserBoneCard() {
    Row(Modifier.fillMaxWidth().clip(BrowserCardShape).background(BrowserCard).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(20.dp)).background(BrowserBone))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.width(120.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(BrowserBone))
            Box(Modifier.width(80.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(BrowserBone))
            Box(Modifier.width(50.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(BrowserBone))
        }
        Box(Modifier.width(70.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(BrowserPill))
    }
}

/** a message card in place of the list: signed out, not in the server yet, offline, and so on, with what to do about it */
@Composable
internal fun BrowserNote(title: String?, body: String, primary: String? = null, onPrimary: () -> Unit = {}, secondary: String? = null, onSecondary: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp).clip(RoundedCornerShape(30.dp)).background(BrowserCard).padding(22.dp)) {
        title?.let {
            Text(it, color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
        }
        Text(body, color = BrowserSoft, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp)
        primary?.let {
            Spacer(Modifier.height(16.dp))
            PillButton(text = it, filled = true, onClick = onPrimary)
        }
        secondary?.let {
            Spacer(Modifier.height(8.dp))
            PillButton(text = it, onClick = onSecondary)
        }
    }
}
