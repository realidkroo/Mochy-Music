// StatsScreen.kt
//
// Stats: everything the play history says about one month or one year. A ruler of months (each
// tick a day, as tall as that day's listening) picks the period; "Re'cap >" opens the Show by /
// year / month picker. Cards open into the detail behind them: a line of every day, the top ten,
// the genre split, the week and the clock. Nothing here is sent anywhere.

package com.mochyy.mochymusic.ui.screens.recap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.ProfilePicUriKey
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalBottomSheetPageState
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.ui.utils.stableSystemBars
import com.mochyy.mochymusic.utils.rememberPreference
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.util.Locale
import java.time.format.TextStyle as JavaTextStyle

private val StatsCard = Color(0xFF0F0F0F)
private val ChipBg = Color(0xFF2A2A2A)
private val PagePadding = 20.dp

private fun statsText(
    size: Float,
    weight: FontWeight = FontWeight.Bold,
    color: Color = StatsInk,
    tracking: Float = -0.02f * size,
) = TextStyle(fontFamily = InterFontFamily, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, color = color)

private fun monthShort(month: Int) = Month.of(month).getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())

private fun monthFull(month: Int) = Month.of(month).getDisplayName(JavaTextStyle.FULL, Locale.getDefault())

@Composable
fun StatsScreen(navController: NavController) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val sheets = LocalBottomSheetPageState.current
    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")
    val today = remember { LocalDate.now() }

    var kind by rememberSaveable { mutableStateOf(StatsRange.Kind.Month) }
    var year by rememberSaveable { mutableStateOf(today.year) }
    var month by rememberSaveable { mutableStateOf(today.monthValue) }
    val range = StatsRange(kind, year, if (kind == StatsRange.Kind.Month) month else 1)

    var firstDay by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(Unit) {
        firstDay = RecapRepository.firstPlayDate(database) ?: today
    }

    var stats by remember { mutableStateOf<ListeningStats?>(null) }
    LaunchedEffect(range) { stats = RecapRepository.load(database, range) }
    var genres by remember { mutableStateOf<GenreState>(GenreState.Loading) }
    LaunchedEffect(stats) {
        val s = stats ?: return@LaunchedEffect
        genres = GenreState.Loading
        genres = RecapGenres.shares(context, s.topArtists)?.let { GenreState.Ready(it) } ?: GenreState.Unavailable
    }

    val start = firstDay ?: today
    val periods =
        remember(kind, start) {
            if (kind == StatsRange.Kind.Month) {
                val first = YearMonth.from(start)
                val last = YearMonth.from(today)
                generateSequence(first) { it.plusMonths(1) }
                    .takeWhile { !it.isAfter(last) }
                    .map { StatsRange(StatsRange.Kind.Month, it.year, it.monthValue) }
                    .toList()
            } else {
                (start.year..today.year).map { StatsRange(StatsRange.Kind.Year, it) }
            }
        }

    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val allOpen = CardKeys.all { expanded[it] == true }

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val topInset = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex >
                0
            ) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / with(density) { 90.dp.toPx() }).coerceIn(0f, 1f)
            }
        }
    }

    fun openPicker() {
        val choice = PickerChoice(kind, year, month)
        var handle: PopupSheetHandle? = null
        // done sits in the button bar: at the end of the content it was cut off below the screen on most phones, so the sheet couldn't be closed with a pick
        handle =
            sheets.showWithHandle(
                buttonBar = {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF666666))
                                .clickable {
                                    kind = choice.kind
                                    year = choice.year
                                    month = choice.month
                                    handle?.dismiss()
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.rc_stats_done), style = statsText(20f))
                    }
                },
            ) {
                StatsPicker(choice = choice, firstDay = start, today = today)
            }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.fillMaxSize().glassRoot(glassState, isActive = { collapse > 0f })) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = topInset + 82.dp, bottom = 200.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "title") {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier =
                            Modifier
                                .padding(horizontal = PagePadding)
                                .graphicsLayer { alpha = 1f - collapse },
                    ) {
                        Text(stringResource(R.string.rc_stats_title), style = statsText(44f, tracking = -1.6f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.rc_stats_year_recap_link, year),
                            style = statsText(22f, color = StatsInkMuted, tracking = -0.6f),
                            modifier =
                                Modifier
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = ::openPicker),
                        )
                    }
                }
                item(key = "ruler") {
                    val available = remember(periods) { periods.toSet() }
                    StatsRuler(
                        selected = range,
                        available = available,
                        today = today,
                        onSelect = { p ->
                            year = p.year
                            if (p.kind == StatsRange.Kind.Month) month = p.month
                        },
                    )
                }
                item(key = "expandAll") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .padding(start = PagePadding, top = 4.dp, bottom = 14.dp)
                                .clip(CircleShape)
                                .background(ChipBg)
                                .clickable {
                                    val open = !allOpen
                                    CardKeys.forEach { expanded[it] = open }
                                }.padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                    ) {
                        Text(
                            stringResource(if (allOpen) R.string.rc_stats_collapse_all else R.string.rc_stats_expand_all),
                            style = statsText(13f, FontWeight.SemiBold, tracking = 0f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Chevron(open = allOpen, size = 18)
                    }
                }

                val s = stats
                if (s == null) {
                    item(key = "loading") { Spacer(Modifier.height(400.dp)) }
                } else if (s.isEmpty) {
                    item(key = "empty") {
                        StatsCardBox {
                            Text(
                                stringResource(
                                    R.string.rc_stats_empty_title,
                                    if (kind == StatsRange.Kind.Month) "${monthFull(s.range.month)} ${s.range.year}" else "${s.range.year}",
                                ),
                                style = statsText(17f),
                            )
                            Text(
                                stringResource(R.string.rc_stats_empty_hint),
                                style = statsText(13f, FontWeight.Medium, StatsInkMuted, 0f),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                } else {
                    item(key = "total") { TotalCard(s, expanded) }
                    item(key = "genre") { GenreCard(genres, expanded) }
                    s.topArtists.firstOrNull()?.let { top -> item(key = "artist") { ArtistCard(s, top, expanded) } }
                    s.topSongs.firstOrNull()?.let { top -> item(key = "song") { SongCard(s, top, expanded) } }
                    item(key = "often") {
                        Text(
                            stringResource(R.string.rc_stats_often),
                            style = statsText(16f),
                            modifier = Modifier.padding(start = PagePadding, end = PagePadding, top = 18.dp, bottom = 12.dp),
                        )
                    }
                    item(key = "weekday") { WeekdayCard(s, expanded) }
                    item(key = "hour") { HourCard(s, expanded) }
                    item(key = "privacy") {
                        Text(
                            stringResource(R.string.rc_stats_privacy),
                            style = statsText(11f, FontWeight.SemiBold, StatsInkMuted, 0f),
                            modifier = Modifier.padding(horizontal = PagePadding + 4.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        // glass bar: a sibling of the glassRoot box, never inside it
        val showGlass by remember { derivedStateOf { collapse > 0.01f } }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(topInset + 84.dp)
                    .graphicsLayer { alpha = collapse },
        ) {
            if (showGlass) {
                ProgressiveGlassBackground(
                    state = glassState,
                    maxBlurRadius = { 50f * collapse },
                    foundationColor = Color.Black.copy(alpha = 0.5f),
                    direction = BlurDirection.BottomToTop,
                    steps = 3,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val progressiveBar =
                com.mochyy.mochymusic.ui.component
                    .rememberProgressiveBlur()
            Box(
                Modifier.fillMaxSize().background(
                    if (progressiveBar) {
                        Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.7f), Color.Transparent))
                    } else {
                        androidx.compose.ui.graphics.SolidColor(
                            Color.Black.copy(alpha = com.mochyy.mochymusic.ui.component.FlatBarScrimAlpha),
                        )
                    },
                ),
            )
        }

        // top row: the logo, which turns into "Stats" once the title scrolls away (one element
        // morphing, like Home's), and the avatar
        val titleGone by remember { derivedStateOf { collapse > 0.5f } }
        val morph by animateFloatAsState(
            targetValue = if (titleGone) 1f else 0f,
            animationSpec = tween(600, easing = FastOutSlowInEasing),
            label = "statsTopBar",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = PagePadding, end = PagePadding, top = topInset + 32.dp),
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Icon(
                    painter = painterResource(R.drawable.ic_mochy_mark),
                    contentDescription = "Mochy",
                    tint = Color.White,
                    modifier =
                        Modifier
                            .size(width = 31.dp, height = 34.dp)
                            .graphicsLayer {
                                alpha = 1f - morph
                                val k = 1f - morph * 0.25f
                                scaleX = k
                                scaleY = k
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                renderEffect = BlurEffectCache.element(morph * 15f)
                            },
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier =
                        Modifier
                            .graphicsLayer {
                                alpha = morph
                                renderEffect = BlurEffectCache.element((1f - morph) * 15f)
                            }.clickable(enabled = morph > 0.5f, onClick = ::openPicker),
                ) {
                    Text(stringResource(R.string.rc_stats_title), style = statsText(28f, tracking = -1f))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.rc_stats_year_recap, year),
                        style = statsText(15f, color = StatsInkMuted, tracking = -0.3f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            AsyncImage(
                model = profilePicUri.takeIf { it.isNotBlank() }?.let { if (it.contains("://")) it else "file://$it" },
                contentDescription = stringResource(R.string.rc_stats_cd_profile),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.person),
                error = painterResource(R.drawable.person),
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A)),
            )
        }
    }
}

private val CardKeys = listOf("total", "genre", "artist", "song", "weekday", "hour")

internal sealed interface GenreState {
    data object Loading : GenreState

    data object Unavailable : GenreState

    data class Ready(
        val shares: List<GenreShare>,
    ) : GenreState
}

// ---------------------------------------------------------------------------------------------
// Cards

@Composable
private fun StatsCardBox(
    expandKey: String? = null,
    expanded: MutableMap<String, Boolean>? = null,
    detail: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val open = expandKey != null && expanded?.get(expandKey) == true
    Column(
        modifier =
            Modifier
                .padding(horizontal = PagePadding, vertical = 7.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(StatsCard)
                .then(if (expandKey != null && expanded != null) Modifier.clickable { expanded[expandKey] = !open } else Modifier)
                .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
    ) {
        Box {
            Column(modifier = Modifier.padding(end = if (detail != null) 30.dp else 0.dp)) { content() }
            if (detail != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(ChipBg),
                    contentAlignment = Alignment.Center,
                ) { Chevron(open = open, size = 18) }
            }
        }
        if (detail != null) {
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(tween(420, easing = Emphasized)) + fadeIn(tween(300, 120)),
                exit = shrinkVertically(tween(320, easing = Emphasized)) + fadeOut(tween(160)),
            ) {
                Column(modifier = Modifier.padding(top = 18.dp, end = 4.dp)) { detail() }
            }
        }
    }
}

@Composable
private fun Chevron(
    open: Boolean,
    size: Int,
) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(320, easing = Emphasized), label = "chevron")
    Icon(
        painter = painterResource(R.drawable.expand_more),
        contentDescription = stringResource(if (open) R.string.rc_stats_cd_collapse else R.string.rc_stats_cd_expand),
        tint = Color.White,
        modifier = Modifier.size(size.dp).graphicsLayer { rotationZ = turn },
    )
}

/** Plays the odometer each time [key] changes. */
@Composable
private fun rememberRoll(key: Any?): () -> Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(1400, easing = Emphasized)) }
    return { a.value }
}

@Composable
private fun TotalCard(
    s: ListeningStats,
    expanded: MutableMap<String, Boolean>,
) {
    val minutes = s.totalMs / 60_000L
    val roll = rememberRoll(s)
    val big = statsText(52f, tracking = -2.4f)
    val unit = statsText(22f, tracking = -0.5f)
    StatsCardBox(
        expandKey = "total",
        expanded = expanded,
        detail = {
            Text(
                if (s.range.kind ==
                    StatsRange.Kind.Month
                ) {
                    stringResource(R.string.rc_stats_every_day, monthFull(s.range.month))
                } else {
                    stringResource(R.string.rc_stats_every_month, s.range.year)
                },
                style = statsText(13f, FontWeight.SemiBold, StatsInkMuted, 0f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (s.range.kind == StatsRange.Kind.Month) {
                val days = s.bucketMs.size
                ListeningLine(
                    values = s.bucketMs,
                    labelAt = { i -> if (i == 0 || (i + 1) % 7 == 0) "${i + 1}" else null },
                    nameOf = { i -> "${monthShort(s.range.month)} ${i + 1}" },
                )
                if (days == 0) Unit
            } else {
                ListeningLine(
                    values = s.bucketMs,
                    labelAt = { i -> if (i % 3 == 0) monthShort(i + 1) else null },
                    nameOf = { i -> monthFull(i + 1) },
                )
            }
        },
    ) {
        Text(
            stringResource(
                if (s.range.kind ==
                    StatsRange.Kind.Month
                ) {
                    R.string.rc_stats_total_month
                } else {
                    R.string.rc_stats_total_year
                },
            ),
            style = statsText(15f),
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            OdometerText("${minutes / 60}", big, progress = roll)
            Text("h", style = unit, modifier = Modifier.padding(bottom = 8.dp))
            Text(":", style = big)
            OdometerText((minutes % 60).toString().padStart(2, '0'), big, progress = roll)
            Text("m", style = unit, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3A3A3A)))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Fact(pluralStringResource(R.plurals.rc_stats_fact_songs, s.songCount, s.songCount))
            Fact(pluralStringResource(R.plurals.rc_stats_fact_artists, s.artistCount, s.artistCount))
            Fact(pluralStringResource(R.plurals.rc_stats_fact_albums, s.albumCount, s.albumCount))
            Fact(pluralStringResource(R.plurals.rc_stats_fact_plays, s.plays, s.plays))
        }
    }
}

@Composable
private fun Fact(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFD9D9D9)))
        Spacer(Modifier.width(5.dp))
        Text(text, style = statsText(13f, FontWeight.SemiBold, StatsInkSecondary, 0f), maxLines = 1)
    }
}

@Composable
private fun GenreCard(
    state: GenreState,
    expanded: MutableMap<String, Boolean>,
) {
    val shares = (state as? GenreState.Ready)?.shares
    StatsCardBox(
        expandKey = if (shares != null) "genre" else null,
        expanded = expanded,
        detail =
            shares?.let {
                {
                    RankList(
                        rows =
                            it.mapIndexed { i, g ->
                                RankRow(
                                    title = g.name,
                                    detail = "${(g.fraction * 100).toInt()}% · ${shortDuration(g.playTimeMs)}",
                                    value = g.playTimeMs,
                                    swatch = GenreColors.getOrElse(i) { GenreOther },
                                    noImage = true,
                                )
                            },
                        circle = true,
                    )
                }
            },
    ) {
        Text(stringResource(R.string.rc_stats_dominant_genre), style = statsText(15f))
        when (state) {
            GenreState.Loading -> {
                Text(
                    stringResource(R.string.rc_stats_genre_loading),
                    style = statsText(14f, FontWeight.Medium, StatsInkMuted, 0f),
                    modifier = Modifier.padding(vertical = 30.dp),
                )
            }

            GenreState.Unavailable -> {
                Text(
                    stringResource(R.string.rc_stats_genre_unavailable),
                    style = statsText(14f, FontWeight.Medium, StatsInkMuted, 0f),
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            }

            is GenreState.Ready -> {
                val top = state.shares.first()
                Text(top.name, style = statsText(24f, tracking = -0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                    GenreDonut(state.shares)
                    Box(
                        Modifier
                            .padding(horizontal = 14.dp)
                            .width(34.dp)
                            .height(1.dp)
                            .background(StatsInkSecondary),
                    )
                    Column {
                        Text("${(top.fraction * 100).toInt()}%", style = statsText(22f))
                        Text(
                            top.name,
                            style = statsText(12f, FontWeight.SemiBold, StatsInkSecondary, 0f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // identity never by colour alone: the top five are named under the ring
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                    donutSlices(state.shares).take(5).forEachIndexed { i, (_, color) ->
                        val name = state.shares.getOrNull(i)?.name ?: return@forEachIndexed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                name,
                                style = statsText(11f, FontWeight.Medium, StatsInkMuted, 0f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(52.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistCard(
    s: ListeningStats,
    top: RecapArtist,
    expanded: MutableMap<String, Boolean>,
) {
    StatsCardBox(
        expandKey = "artist",
        expanded = expanded,
        detail = {
            RankList(
                rows =
                    s.topArtists.take(10).map { a ->
                        RankRow(
                            a.name,
                            pluralStringResource(R.plurals.rc_stats_artist_row, a.plays, shortDuration(a.playTimeMs), a.plays),
                            a.playTimeMs,
                            a.thumbnailUrl,
                        )
                    },
                circle = true,
            )
        },
    ) {
        Text(stringResource(R.string.rc_stats_artist_love), style = statsText(15f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            AsyncImage(
                model = top.thumbnailUrl?.resize(240, 240),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF2A2A2A)),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(top.name, style = statsText(24f, tracking = -0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.rc_stats_artist_summary, top.plays, shortDuration(top.playTimeMs), top.plays, top.name),
                    style = statsText(12f, FontWeight.SemiBold, StatsInkMuted, 0f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SongCard(
    s: ListeningStats,
    top: RecapSong,
    expanded: MutableMap<String, Boolean>,
) {
    StatsCardBox(
        expandKey = "song",
        expanded = expanded,
        detail = {
            RankList(
                rows =
                    s.topSongs.take(10).map { song ->
                        RankRow(
                            song.title,
                            pluralStringResource(
                                R.plurals.rc_stats_song_row,
                                song.plays,
                                song.artistName,
                                song.plays,
                                shortDuration(song.playTimeMs),
                            ),
                            song.plays.toLong(),
                            song.thumbnailUrl,
                        )
                    },
                circle = false,
            )
        },
    ) {
        Text(stringResource(R.string.rc_stats_song_number_one), style = statsText(15f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            AsyncImage(
                model = top.thumbnailUrl?.resize(240, 240),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(78.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A)),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.rc_stats_song_from, top.title, top.artistName),
                    style = statsText(20f, tracking = -0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.rc_stats_song_summary, top.plays, shortDuration(top.playTimeMs), top.plays),
                    style = statsText(12f, FontWeight.SemiBold, StatsInkMuted, 0f),
                )
            }
        }
    }
}

@Composable
private fun WeekdayCard(
    s: ListeningStats,
    expanded: MutableMap<String, Boolean>,
) {
    val best = s.weekdayMs.indices.maxByOrNull { s.weekdayMs[it] } ?: 0
    val day = DayOfWeek.of(best + 1)
    StatsCardBox(
        expandKey = "weekday",
        expanded = expanded,
        detail = {
            WeekdayBars(
                values = s.weekdayMs,
                labels = DayOfWeek.entries.map { it.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()) },
            )
        },
    ) {
        Text(
            stringResource(
                if (s.range.kind ==
                    StatsRange.Kind.Month
                ) {
                    R.string.rc_stats_weekday_month
                } else {
                    R.string.rc_stats_weekday_year
                },
            ),
            style = statsText(13f, FontWeight.SemiBold),
        )
        Text(
            day.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()),
            style = statsText(32f, tracking = -1.2f),
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            stringResource(R.string.rc_stats_totaling_hours, formatHoursDecimal(s.weekdayMs[best])),
            style = statsText(12f, FontWeight.SemiBold, StatsInkSecondary, 0f),
        )
    }
}

@Composable
private fun HourCard(
    s: ListeningStats,
    expanded: MutableMap<String, Boolean>,
) {
    val best = s.hourMs.indices.maxByOrNull { s.hourMs[it] } ?: 0
    val hourRes =
        when (best) {
            in 5..11 -> R.string.rc_stats_hour_morning
            in 12..16 -> R.string.rc_stats_hour_afternoon
            in 17..21 -> R.string.rc_stats_hour_evening
            else -> R.string.rc_stats_hour_night
        }
    StatsCardBox(
        expandKey = "hour",
        expanded = expanded,
        detail = {
            ListeningLine(
                values = s.hourMs,
                labelAt = { h -> if (h % 6 == 0) hourLabel(h) else null },
                nameOf = { h -> hourLabel(h) },
            )
        },
    ) {
        Text(stringResource(hourRes), style = statsText(13f, FontWeight.SemiBold))
        Text(hourLabel(best), style = statsText(32f, tracking = -1.2f), modifier = Modifier.padding(vertical = 2.dp))
        Text(
            stringResource(R.string.rc_stats_totaling_hours_time, formatHoursDecimal(s.hourMs[best])),
            style = statsText(12f, FontWeight.SemiBold, StatsInkSecondary, 0f),
        )
    }
}

private fun hourLabel(h: Int): String =
    java.time.LocalTime
        .of(h, 0)
        .format(
            java.time.format.DateTimeFormatter
                .ofLocalizedTime(java.time.format.FormatStyle.SHORT),
        )

// ---------------------------------------------------------------------------------------------
// The picker sheet

// what the picker has landed on, shared by its wheels and the done button pinned under them
@Stable
private class PickerChoice(
    kind: StatsRange.Kind,
    year: Int,
    month: Int,
) {
    var kind by mutableStateOf(kind)
    var year by mutableStateOf(year)
    var month by mutableStateOf(month)
}

@Composable
private fun ColumnScope.StatsPicker(
    choice: PickerChoice,
    firstDay: LocalDate,
    today: LocalDate,
) {
    var k by choice::kind
    var y by choice::year
    var m by choice::month
    val years = (firstDay.year..today.year).toList()
    val months =
        remember(y) {
            val from = if (y == firstDay.year) firstDay.monthValue else 1
            val to = if (y == today.year) today.monthValue else 12
            (from..to).toList()
        }
    if (m !in months) m = months.last()

    // two wheels and their titles are taller than a sheet gets on most phones, so the picker scrolls
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 8.dp),
    ) {
        Text(stringResource(R.string.rc_stats_show_by), style = statsText(36f, tracking = -1.4f))
        Spacer(Modifier.height(10.dp))
        listOf(
            StatsRange.Kind.Month to stringResource(R.string.rc_stats_kind_month),
            StatsRange.Kind.Year to stringResource(R.string.rc_stats_kind_year),
        ).forEach { (value, label) ->
            val on = k == value
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(vertical = 5.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Color(0xFF4A4A4A) else Color(0xFF2A2A2A))
                        .then(
                            if (on) {
                                Modifier.border(
                                    BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                                    RoundedCornerShape(14.dp),
                                )
                            } else {
                                Modifier
                            },
                        ).clickable { k = value }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFD9D9D9)), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF2A2A2A)))
                }
                Spacer(Modifier.width(14.dp))
                Text(label, style = statsText(18f), modifier = Modifier.weight(1f))
                if (on) Text(stringResource(R.string.rc_stats_selected), style = statsText(13f, FontWeight.SemiBold, StatsInkMuted, 0f))
            }
        }
        Box(
            Modifier
                .padding(vertical = 18.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF555555)),
        )
        Text(stringResource(R.string.rc_stats_what_year), style = statsText(36f, tracking = -1.4f))
        Wheel(items = years, selected = y, label = { "$it" }, onSelected = { y = it })
        AnimatedVisibility(visible = k == StatsRange.Kind.Month) {
            Column {
                Text(
                    stringResource(R.string.rc_stats_what_month),
                    style = statsText(36f, tracking = -1.4f),
                    modifier = Modifier.padding(top = 10.dp),
                )
                val thisMonthTag = stringResource(R.string.rc_stats_this_month_tag)
                Wheel(
                    items = months,
                    selected = m,
                    label = { mm ->
                        monthFull(mm) +
                            if (y == today.year && mm == today.monthValue) " $thisMonthTag" else ""
                    },
                    onSelected = { m = it },
                )
            }
        }
    }
}

/** A snapping wheel of [items]; the middle row, inside the outlined pill, is the choice. */
@Composable
private fun <T> Wheel(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    val rowH = 40.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(selected).coerceAtLeast(0))
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val density = LocalDensity.current
    val rowPx = with(density) { rowH.toPx() }
    val centre by remember(items) {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            (first + if (state.firstVisibleItemScrollOffset > rowPx / 2f) 1 else 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(centre, items) { items.getOrNull(centre)?.let { if (it != selected) onSelected(it) } }

    Box(modifier = Modifier.fillMaxWidth().height(rowH * 5).padding(vertical = 4.dp)) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(rowH)
                    .border(BorderStroke(1.dp, Color(0xFF8A8A8A)), CircleShape),
        )
        androidx.compose.foundation.lazy.LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowH * 2),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items) { i, item ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(rowH)
                            .graphicsLayer {
                                val info = state.layoutInfo
                                val visible = info.visibleItemsInfo.firstOrNull { it.index == i }
                                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val d =
                                    if (visible !=
                                        null
                                    ) {
                                        kotlin.math.abs(visible.offset + visible.size / 2f - mid) / (rowPx * 2.5f)
                                    } else {
                                        1f
                                    }
                                alpha = (1f - d).coerceIn(0.25f, 1f)
                                val k = 1f - 0.12f * d.coerceIn(0f, 1f)
                                scaleX = k
                                scaleY = k
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(item), style = statsText(15f, if (i == centre) FontWeight.Bold else FontWeight.SemiBold))
                }
            }
        }
    }
}
