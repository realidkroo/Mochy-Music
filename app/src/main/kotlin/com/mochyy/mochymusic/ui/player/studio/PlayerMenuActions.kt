package com.mochyy.mochymusic.ui.player.studio

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerMenuSlotsKey
import com.mochyy.mochymusic.constants.PlayerSideButtonKey
import com.mochyy.mochymusic.utils.rememberPreference

/**
 * Everything the player's round buttons can do: the button beside the chevron and the four in the
 * menu the chevron opens are each set to one of these in the editor ("Edit menu").
 */
enum class PlayerMenuAction(val label: String, @param:DrawableRes val icon: Int) {
    LIKE("Like", R.drawable.ic_untitled_heart_unfill),
    OTHER_MENU("Other menu", R.drawable.more_horiz),
    SLEEP_TIMER("Sleep timer", R.drawable.sleep_timer),
    SEE_VIDEO("See video", R.drawable.slow_motion_video),
    REPEAT("Repeat", R.drawable.repeat),
    SHUFFLE("Shuffle", R.drawable.shuffle),
    LYRICS("Lyrics", R.drawable.lyrics),
    QUEUE("Queue", R.drawable.queue_music),
    SPEED("Playback speed", R.drawable.speed),
    // the equalizer page, the player stepping down out of its way
    EQUALIZER("Equalizer", R.drawable.equalizer),
    OUTPUT("Device output", R.drawable.cast),
    // the player turned sideways around the song's youtube video
    FULL_VIDEO("Full video", R.drawable.fullscreen),

    // the ones below are worked by PlayerMenuExtras
    KEEP_AWAKE("Keep screen on", R.drawable.lock_open),
    TRANSLATE_LYRICS("Translate lyrics", R.drawable.translate),
    DOWNLOAD("Download", R.drawable.download),
    ADD_TO_PLAYLIST("Add to playlist", R.drawable.playlist_add);

    companion object {
        val DefaultSlots = listOf(OTHER_MENU, SLEEP_TIMER, SEE_VIDEO, REPEAT)
        val DefaultSide = LIKE

        fun parseSlots(stored: String): List<PlayerMenuAction> {
            val parsed = stored.split(',').mapNotNull { name -> entries.firstOrNull { it.name == name } }
            return if (parsed.size == DefaultSlots.size) parsed else DefaultSlots
        }

        fun parseSide(stored: String): PlayerMenuAction = entries.firstOrNull { it.name == stored } ?: DefaultSide
    }
}

/** the four chevron-menu actions and the side button, as set in the editor, with their setters */
class PlayerMenuLayout(
    val slots: List<PlayerMenuAction>,
    val side: PlayerMenuAction,
    private val writeSlots: (String) -> Unit,
    private val writeSide: (String) -> Unit,
) {
    /**
     * puts [action] at [slot] (0..3, or [SideSlot] for the side button). an action can only be in
     * one place: if it was already somewhere else, whatever was at [slot] moves there instead
     */
    fun assign(slot: Int, action: PlayerMenuAction) {
        val all = slots + side
        val previous = all[slot]
        if (previous == action) return
        val elsewhere = all.indexOf(action)
        val next = all.toMutableList()
        next[slot] = action
        if (elsewhere >= 0) next[elsewhere] = previous
        writeSlots(next.take(slots.size).joinToString(",") { it.name })
        writeSide(next.last().name)
    }

    fun actionAt(slot: Int): PlayerMenuAction = if (slot == SideSlot) side else slots[slot]

    companion object {
        const val SideSlot = 4
    }
}

@Composable
fun rememberPlayerMenuLayout(): PlayerMenuLayout {
    val (slotsStored, onSlots) = rememberPreference(PlayerMenuSlotsKey, defaultValue = "")
    val (sideStored, onSide) = rememberPreference(PlayerSideButtonKey, defaultValue = "")
    val slots = remember(slotsStored) { PlayerMenuAction.parseSlots(slotsStored) }
    val side = remember(sideStored) { PlayerMenuAction.parseSide(sideStored) }
    return remember(slots, side, onSlots, onSide) { PlayerMenuLayout(slots, side, onSlots, onSide) }
}
