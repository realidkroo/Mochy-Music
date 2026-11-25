// reads a lyrics block's "style": "custom": the words, where the sung line rests, the three poses, how lines move and come in, the sweep and its shaders, every number kept in range

package com.mochyy.mochymusic.modules.skin

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal object SkinLyricsParser {

    fun style(o: JsonObject, library: Map<String, SkinKeyframes>, shader: (JsonElement?) -> SkinShaderSource?): SkinLyricStyle {
        val color = skinColor(o.string("color")) ?: SkinColor.InkSoft
        return SkinLyricStyle(
            size = (o.float("size") ?: 26f).coerceIn(10f, 72f).sp,
            weight = skinWeight(o.string("weight")) ?: FontWeight.Bold,
            color = color,
            sung = skinColor(o.string("sung") ?: o.string("active")) ?: SkinColor.Ink,
            align = skinAlign(o.string("align")),
            gap = (o.float("gap") ?: 14f).coerceIn(0f, 80f).dp,
            anchor = (o.float("anchor") ?: 0.3f).coerceIn(0f, 1f),
            lines = (o.float("lines")?.toInt() ?: 3).coerceIn(1, 4),
            // without poses of its own the sung line stands out the way mochy's lyrics do
            current = (o["current"] as? JsonObject)?.let(SkinAnimationParser::pose).orEmpty(),
            past = (o["past"] as? JsonObject)?.let(SkinAnimationParser::pose) ?: DefaultPast,
            next = (o["next"] as? JsonObject)?.let(SkinAnimationParser::pose) ?: DefaultNext,
            transition = SkinAnimationParser.transition(o["transition"]) ?: DefaultTransition,
            // "enter" and "scale" are every block's own, so a line's entrance and the words' size go by their own names
            enter = (o["lineEnter"] as? JsonObject)?.let { SkinAnimationParser.timeline(it, library, SkinRepeat.Once, DefaultEnterMs) }?.copy(clock = SkinClock.Time),
            sweep = o.bool("sweep") ?: true,
            fill = shader(o["fill"]),
            effect = shader(o["effect"]),
            // the player around them is already fitted to the screen, so the words only take a scale of their own
            scale = SkinSize.of(o, scaleName = "textScale").copy(designWidth = null),
            translation = o.bool("translation") ?: false,
            // keyframes every word plays the moment it starts being sung, on top of the sweep
            wordEnter = (o["wordEnter"] as? JsonObject)?.let { SkinAnimationParser.timeline(it, library, SkinRepeat.Once, DefaultWordMs) }?.copy(clock = SkinClock.Time),
        )
    }

    private val DefaultPast = mapOf(PoseProp.Opacity to 0.35f, PoseProp.Scale to 0.94f)
    private val DefaultNext = mapOf(PoseProp.Opacity to 0.55f, PoseProp.Scale to 0.94f)
    private val DefaultTransition = SkinTransition(450, FastOutSlowInEasing, spring = true)
    private const val DefaultEnterMs = 500
    // a word is short, so its pop is too
    private const val DefaultWordMs = 320
}
