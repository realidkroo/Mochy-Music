// discord's message formatting turned into styled text: bold, italic, underline, strike, code, headings, quotes, lists, links, mentions, timestamps and custom emoji

package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** styled text plus the custom emoji it shows, by their placeholder id */
class DiscordText(val text: AnnotatedString, val emoji: Map<String, String>)

private val LinkColor = Color(0xFF8AB4FF)
private val MentionColor = Color(0xFFC9CDFB)
private val MentionBackground = Color(0x335865F2)
private val CodeBackground = Color.White.copy(alpha = 0.12f)
private val QuoteBar = Color.White.copy(alpha = 0.35f)
private val Dim = Color.White.copy(alpha = 0.55f)
private val CodeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = CodeBackground)
private val LinkStyles = TextLinkStyles(SpanStyle(color = LinkColor, textDecoration = TextDecoration.Underline))

// a backslash before any of these shows it as is
private const val Escapable = "*_~`|>#-\\[]()<:"
// the paired marks, longest first so ** isn't read as two *
private val Pairs = listOf("**", "__", "~~", "||", "*", "_")
private val Emoji = Regex("^<a?:(\\w+):(\\d+)>")
private val Mention = Regex("^<(@!?|@&|#)(\\d+)>")
private val Timestamp = Regex("^<t:(-?\\d+)(?::([tTdDfFR]))?>")
private val Heading = Regex("^(#{1,3}) (.+)")
private val Numbered = Regex("^(\\d+)\\. (.+)")

object DiscordMarkdown {
    /** the whole message styled; cheap but not free, so callers run it off the main thread */
    fun parse(source: String): DiscordText {
        val emoji = HashMap<String, String>()
        val text = buildAnnotatedString {
            val lines = source.replace("\r\n", "\n").split('\n')
            var index = 0
            var first = true
            while (index < lines.size) {
                val line = lines[index]
                if (!first) append('\n')
                first = false
                if (line.trimStart().startsWith("```")) {
                    // a fenced block runs to the next fence, its language name on the first line dropped
                    val body = StringBuilder()
                    val opening = line.trimStart().removePrefix("```")
                    if (opening.contains("```")) {
                        body.append(opening.substringBefore("```"))
                    } else {
                        index++
                        while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                            if (body.isNotEmpty()) body.append('\n')
                            body.append(lines[index])
                            index++
                        }
                    }
                    withStyle(CodeStyle) { append(body.toString()) }
                    index++
                    continue
                }
                blockLine(line, emoji)
                index++
            }
        }
        return DiscordText(text, emoji)
    }

    /** the words alone, for a card's short summary */
    fun plain(source: String): String = parse(source).text.text.replace(Regex("\n{2,}"), "\n").trim()
}

// one line: a heading, small text, a quote or a list item get their look, then the inline marks inside
private fun AnnotatedString.Builder.blockLine(line: String, emoji: MutableMap<String, String>) {
    val heading = Heading.find(line)
    when {
        heading != null -> {
            val size = when (heading.groupValues[1].length) {
                1 -> 1.45.em
                2 -> 1.25.em
                else -> 1.1.em
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size, color = Color.White)) { inline(heading.groupValues[2], emoji) }
        }
        line.startsWith("-# ") -> withStyle(SpanStyle(fontSize = 0.85.em, color = Dim)) { inline(line.removePrefix("-# "), emoji) }
        line.startsWith("> ") || line == ">" -> {
            withStyle(SpanStyle(color = QuoteBar)) { append("┃ ") }
            inline(line.removePrefix(">").removePrefix(" "), emoji)
        }
        line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") } -> {
            val indent = line.length - line.trimStart().length
            append("  ".repeat(indent / 2 + 1))
            append("• ")
            inline(line.trimStart().drop(2), emoji)
        }
        Numbered.find(line.trimStart()) != null -> {
            val match = Numbered.find(line.trimStart())!!
            append("  ${match.groupValues[1]}. ")
            inline(match.groupValues[2], emoji)
        }
        else -> inline(line, emoji)
    }
}

// the marks inside a line, found left to right; a mark with no partner is just text
private fun AnnotatedString.Builder.inline(text: String, emoji: MutableMap<String, String>) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length && text[i + 1] in Escapable) {
            plain.append(text[i + 1])
            i += 2
            continue
        }
        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                withStyle(CodeStyle) { append(text.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }
        val pair = Pairs.firstOrNull { text.startsWith(it, i) }
        if (pair != null) {
            val end = closing(text, i, pair)
            if (end != null) {
                flush()
                withStyle(styleOf(pair)) { inline(text.substring(i + pair.length, end), emoji) }
                i = end + pair.length
                continue
            }
        }
        if (c == '[') {
            val label = text.indexOf("](", i)
            val close = if (label > i) text.indexOf(')', label + 2) else -1
            val url = if (close > label && label > i) text.substring(label + 2, close).trim('<', '>') else ""
            if (url.startsWith("https://") || url.startsWith("http://")) {
                flush()
                withLink(LinkAnnotation.Url(url, LinkStyles)) { inline(text.substring(i + 1, label), emoji) }
                i = close + 1
                continue
            }
        }
        if (text.startsWith("https://", i) || text.startsWith("http://", i)) {
            var end = i
            while (end < text.length && !text[end].isWhitespace() && text[end] != '<' && text[end] != '>') end++
            // a link at the end of a sentence keeps its full stop or bracket outside
            while (end > i && text[end - 1] in ".,!?;:)") end--
            flush()
            val url = text.substring(i, end)
            withLink(LinkAnnotation.Url(url, LinkStyles)) { append(url) }
            i = end
            continue
        }
        if (c == '<') {
            val taken = special(text.substring(i), emoji, ::flush)
            if (taken > 0) {
                i += taken
                continue
            }
        }
        plain.append(c)
        i++
    }
    flush()
}

// a custom emoji, a mention or a timestamp at the start of [rest]; how many characters it took, 0 when it's none of them
private fun AnnotatedString.Builder.special(rest: String, emoji: MutableMap<String, String>, flush: () -> Unit): Int {
    Emoji.find(rest)?.let { match ->
        flush()
        val id = match.groupValues[2]
        val animated = match.value.startsWith("<a:")
        emoji[id] = "https://cdn.discordapp.com/emojis/$id.${if (animated) "gif" else "webp"}?size=64"
        appendInlineContent(id, ":${match.groupValues[1]}:")
        return match.value.length
    }
    Mention.find(rest)?.let { match ->
        flush()
        val label = when (match.groupValues[1]) {
            "#" -> "#channel"
            "@&" -> "@role"
            else -> "@member"
        }
        withStyle(SpanStyle(color = MentionColor, background = MentionBackground)) { append(label) }
        return match.value.length
    }
    Timestamp.find(rest)?.let { match ->
        flush()
        append(timestamp(match.groupValues[1].toLong(), match.groupValues[2]))
        return match.value.length
    }
    return 0
}

// where a pair's partner is, or null when there isn't one; _ only counts at word edges, so snake_case stays as it is
private fun closing(text: String, start: Int, pair: String): Int? {
    val from = start + pair.length
    if (from >= text.length || text[from].isWhitespace()) return null
    if (pair == "_" && start > 0 && text[start - 1].isLetterOrDigit()) return null
    var end = text.indexOf(pair, from)
    while (end > from) {
        // a single * or _ mustn't stop on half of a double one
        val doubled = pair.length == 1 && end + 1 < text.length && text[end + 1] == pair[0]
        val wordEdge = pair != "_" || end + 1 >= text.length || !text[end + 1].isLetterOrDigit()
        if (!doubled && wordEdge && !text[end - 1].isWhitespace()) return end
        end = text.indexOf(pair, end + if (doubled) 2 else 1)
    }
    return null
}

private fun styleOf(pair: String): SpanStyle = when (pair) {
    "**" -> SpanStyle(fontWeight = FontWeight.Bold)
    "__" -> SpanStyle(textDecoration = TextDecoration.Underline)
    "~~" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    // a spoiler shows on a dim block instead of hiding, there's no tap to reveal here
    "||" -> SpanStyle(background = Color.White.copy(alpha = 0.18f))
    else -> SpanStyle(fontStyle = FontStyle.Italic)
}

// discord's <t:...> in the phone's own date and time style; R, "relative", reads as the plain date here
private fun timestamp(seconds: Long, style: String): String {
    val date = Date(seconds * 1000)
    return when (style) {
        "t" -> DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
        "T" -> DateFormat.getTimeInstance(DateFormat.MEDIUM).format(date)
        "d" -> DateFormat.getDateInstance(DateFormat.SHORT).format(date)
        "D" -> DateFormat.getDateInstance(DateFormat.LONG).format(date)
        "F" -> DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(date)
        else -> DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(date)
    }
}

/** a discord message shown with its formatting; it starts as the plain words and picks up its styles a frame later, worked out off the main thread */
@Composable
fun DiscordFormattedText(source: String, style: TextStyle, modifier: Modifier = Modifier) {
    val parsed by produceState(initialValue = DiscordText(AnnotatedString(source), emptyMap()), source) {
        value = withContext(Dispatchers.Default) { DiscordMarkdown.parse(source) }
    }
    val inline = remember(parsed) {
        parsed.emoji.mapValues { (_, url) ->
            InlineTextContent(Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter)) {
                AsyncImage(model = url, contentDescription = it)
            }
        }
    }
    Text(parsed.text, style = style, inlineContent = inline, modifier = modifier)
}
