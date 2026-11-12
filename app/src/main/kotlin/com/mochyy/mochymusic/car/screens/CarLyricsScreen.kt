// CarLyricsScreen.kt

package com.mochyy.mochymusic.car.screens

import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.car.CarApp
import com.mochyy.mochymusic.car.CarLyricLines
import com.mochyy.mochymusic.constants.CarLyricsKey
import com.mochyy.mochymusic.playback.MusicService
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// the playing song's whole lyrics as a list: the line being sung gets a mark, and tapping a line jumps the song there
@UnstableApi
class CarLyricsScreen(private val app: CarApp) : Screen(app.carContext) {
    private var title: String? = null
    private var lyrics: CarLyricLines? = null
    private var looked = false

    // which lyric lines become rows, blank gaps left out
    private var rowLines: List<Int> = emptyList()
    private var singing = -1

    private val singingIcon: CarIcon by lazy {
        CarIcon.Builder(IconCompat.createWithResource(app.carContext, R.drawable.graphic_eq)).setTint(CarColor.PRIMARY).build()
    }

    init {
        lifecycleScope.launch {
            val service = app.playback.service.filterNotNull().first()
            val song = service.currentMediaMetadata.value
            title = song?.title
            lyrics = song?.let { app.lyrics.load(it) }
            rowLines = lyrics?.lines?.withIndex()?.filter { it.value.text.isNotBlank() }?.map { it.index }.orEmpty()
            looked = true
            invalidate()
            launch { follow(service) }
            // android auto counts every new list it's sent, so a new song gets a fresh screen in place of this one rather than a new list here
            service.currentMediaMetadata.first { it?.id != song?.id }
            if (screenManager.top === this@CarLyricsScreen) {
                screenManager.pop()
                screenManager.push(CarLyricsScreen(app))
            }
        }
    }

    // moves the mark along while the screen is up; only the mark changes, which the car takes as a refresh
    private suspend fun follow(service: MusicService) {
        val lines = lyrics?.takeIf { it.synced } ?: return
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val player = service.player
                val position = player.currentPosition
                val index = lines.lineAt(position).takeIf { it >= 0 && !lines.isBreak(it, position) } ?: -1
                val row = rowLines.indexOf(index)
                if (row != singing) {
                    singing = row
                    invalidate()
                }
                delay(if (player.isPlaying) PlayingTickMs else PausedTickMs)
            }
        }
    }

    override fun onGetTemplate(): Template {
        val header = Header.Builder()
            .setStartHeaderAction(Action.BACK)
            .setTitle(title ?: app.string(R.string.car_lyrics))
            .addEndHeaderAction(onPlayerToggle())
            .build()
        if (!looked) return ListTemplate.Builder().setHeader(header).setLoading(true).build()
        val lines = lyrics
        val list = ItemList.Builder().setNoItemsMessage(app.string(R.string.car_lyrics_none))
        if (lines != null) {
            rowLines.forEachIndexed { row, index -> list.addItem(lineRow(lines, index, row == singing)) }
        }
        return ListTemplate.Builder().setHeader(header).setSingleList(list.build()).build()
    }

    private fun lineRow(lines: CarLyricLines, index: Int, isSinging: Boolean): Row {
        val line = lines.lines[index]
        return Row.Builder()
            .setTitle(line.text)
            .apply { if (isSinging) setImage(singingIcon, Row.IMAGE_TYPE_ICON) }
            .apply {
                if (lines.synced) {
                    setOnClickListener { app.playback.service.value?.player?.seekTo((line.time - lines.offsetMs).coerceAtLeast(0L)) }
                }
            }
            .build()
    }

    // lyrics as the player's title and cover while driving, on unless turned off here
    private fun onPlayerToggle(): Action =
        Action.Builder()
            .setIcon(app.images.icon(R.drawable.lyrics))
            .setTitle(app.string(R.string.car_lyrics_on_player))
            .setOnClickListener {
                lifecycleScope.launch {
                    var nowOn = true
                    app.carContext.dataStore.edit { prefs ->
                        nowOn = !(prefs[CarLyricsKey] ?: true)
                        prefs[CarLyricsKey] = nowOn
                    }
                    app.toast(app.string(if (nowOn) R.string.car_lyrics_player_on else R.string.car_lyrics_player_off))
                }
            }
            .build()

    private companion object {
        const val PlayingTickMs = 250L
        const val PausedTickMs = 750L
    }
}
