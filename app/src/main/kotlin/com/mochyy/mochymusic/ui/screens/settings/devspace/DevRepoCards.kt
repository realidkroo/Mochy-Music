// the repo half of dev spaces: the playground numbers and the open and resolved issues with their filters

package com.mochyy.mochymusic.ui.screens.settings.devspace

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.OdometerNumber
import com.mochyy.mochymusic.ui.component.pressBounce
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.mochyy.mochymusic.ui.component.RemoteFocus

private val BugText = Color(0xFFFF8A80)
private val BugFill = Color(0xFF4A2424)
private const val IssueRowsShown = 6

private enum class IssueFilter(val label: String) {
    All("All"), Bug("Bug"), Feature("Feature"), Other("Other");

    fun matches(issue: DevIssue) = when (this) {
        All -> true
        Bug -> issue.isBug
        Feature -> issue.isFeature
        Other -> !issue.isBug && !issue.isFeature
    }
}

@Composable
internal fun DevPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DevCardCorner))
            .background(DevCard)
            .padding(DevCardPad)
            .animateContentSize(tween(260, easing = FastOutSlowInEasing)),
        content = content,
    )
}

/** the developer playground card: what the repo did since this build, a tap opens the playground page */
@Composable
internal fun PlaygroundCard(stats: DevSpaceStats?, settled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    RepoFiguresCard(
        title = "Developer playground",
        figures = listOf(
            stats?.newIssues to "new reported issues",
            stats?.newResolved to "new resolved issue",
            stats?.commits to "commits",
            stats?.releases to "releases",
        ),
        settled = settled,
        showChevron = true,
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .clip(RoundedCornerShape(DevCardCorner))
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
    )
}

/** a card of big numbers side by side, the figures shrink a step when one runs long so nothing wraps */
@Composable
internal fun RepoFiguresCard(
    title: String,
    figures: List<Pair<Int?, String>>,
    settled: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showChevron: Boolean = false,
) {
    DevPanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = devText(17f, tracking = -0.6f))
            if (showChevron) {
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (subtitle != null) {
            Text(subtitle, style = devText(13f, FontWeight.Medium, Color(0xFFE5E5EA), -0.1f))
        }
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val divider = 1.dp
            val cell = (maxWidth - divider * (figures.size - 1)) / figures.size
            val digits = figures.maxOf { (it.first ?: 0).toString().length }.coerceAtLeast(1)
            val figureDp = ((cell.value - 14f) / (digits * 0.62f)).coerceIn(22f, 44f)
            Row(verticalAlignment = Alignment.Top) {
                figures.forEachIndexed { i, (value, label) ->
                    if (i > 0) {
                        Box(
                            Modifier
                                .padding(top = (figureDp * 0.2f).dp)
                                .width(divider)
                                .height((figureDp * 0.7f).dp)
                                .background(Color.White.copy(alpha = 0.7f)),
                        )
                    }
                    Column(Modifier.width(cell).padding(start = if (i == 0) 0.dp else 12.dp, end = 4.dp)) {
                        OdometerNumber(
                            value = value?.toString() ?: if (settled) "–" else "0",
                            fontSize = devFigureSp(figureDp),
                            letterSpacing = (-1.5).sp,
                        )
                        Text(label, style = devText(9.5f, FontWeight.Medium, Color(0xFFD1D1D6), 0f), maxLines = 2)
                    }
                }
            }
        }
    }
}

/** the open issues with filters and, under them, the resolved ones */
@Composable
internal fun RepoIssuesSection(
    stats: DevSpaceStats?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(IssueFilter.All) }
    var showAllOpen by remember { mutableStateOf(false) }
    var showAllResolved by remember { mutableStateOf(false) }
    val open = stats?.issues.orEmpty().filter { it.open }
    val resolved = stats?.issues.orEmpty().filter { !it.open }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DevPanel {
            Text("Repo issues", style = devText(17f, tracking = -0.6f))
            Spacer(Modifier.height(10.dp))
            FilterChips(
                selected = filter,
                countOf = { f -> open.count { f.matches(it) } },
                onSelect = { filter = it; showAllOpen = false; showAllResolved = false },
            )
            Spacer(Modifier.height(12.dp))
            val shown = open.filter { filter.matches(it) }
            when {
                stats == null && loading -> repeat(3) { SkeletonRow() }
                stats == null -> RetryNote(error ?: "Couldn't reach GitHub", onRetry)
                shown.isEmpty() -> Note(if (open.isEmpty()) "No open issues. Nice." else "No ${filter.label.lowercase()} issues open.")
                else -> IssueList(shown, showAllOpen, context) { showAllOpen = true }
            }
            if (stats != null && error != null) {
                Spacer(Modifier.height(10.dp))
                RetryNote("$error. Showing the last numbers.", onRetry)
            }
        }

        val shownResolved = resolved.filter { filter.matches(it) }
        if (stats != null && shownResolved.isNotEmpty()) {
            DevPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Resolved", style = devText(17f, tracking = -0.6f), modifier = Modifier.weight(1f))
                    Text("${shownResolved.size}", style = devText(15f, FontWeight.SemiBold, DevMuted))
                }
                Spacer(Modifier.height(12.dp))
                IssueList(shownResolved, showAllResolved, context) { showAllResolved = true }
            }
        }
    }
}

@Composable
private fun FilterChips(selected: IssueFilter, countOf: (IssueFilter) -> Int, onSelect: (IssueFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        IssueFilter.entries.forEach { f ->
            val on = f == selected
            val n = countOf(f)
            Text(
                text = if (n > 0) "${f.label} $n" else f.label,
                style = devText(13f, FontWeight.SemiBold, if (on) Color.Black else Color.White, -0.2f),
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) Color.White else DevPill)
                    .clickable { onSelect(f) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun IssueList(issues: List<DevIssue>, showAll: Boolean, context: Context, onShowAll: () -> Unit) {
    val visible = if (showAll) issues else issues.take(IssueRowsShown)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { IssueRow(it) { context.openDevUrl(it.url) } }
        if (!showAll && issues.size > IssueRowsShown) {
            Text(
                "Show all ${issues.size}",
                style = devText(13f, FontWeight.SemiBold, Color.White, -0.2f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(DevPill)
                    .clickable(onClick = onShowAll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun IssueRow(issue: DevIssue, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DevRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(issue.title, style = devText(15f, FontWeight.SemiBold, tracking = -0.2f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text("#${issue.number} · ${ago(issue.createdAt)}", style = devText(12f, FontWeight.Medium, DevMuted, 0f), maxLines = 1)
        }
        val tag = if (issue.isBug) "bug" else issue.labels.firstOrNull()
        if (tag != null) {
            Spacer(Modifier.width(10.dp))
            val bug = issue.isBug
            Text(
                tag,
                style = devText(11f, FontWeight.SemiBold, if (bug) BugText else Color(0xFFD1D1D6), 0f),
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (bug) BugFill else DevPill)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SkeletonRow() {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { alpha = pulse }
            .clip(RoundedCornerShape(18.dp))
            .background(DevRow),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = devText(14f, FontWeight.Medium, DevMuted, -0.2f), modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun RetryNote(text: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = devText(13f, FontWeight.Medium, DevMuted, -0.2f), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(
            "Try again",
            style = devText(13f, FontWeight.SemiBold, Color.Black, -0.2f),
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

private fun ago(at: Instant): String {
    val days = ChronoUnit.DAYS.between(at, Instant.now())
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 30L -> "${days}d ago"
        days < 365L -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}
