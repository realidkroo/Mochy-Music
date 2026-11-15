// CarHomeTab.kt

package com.mochyy.mochymusic.car.screens

import androidx.car.app.model.CarIcon
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.media3.common.util.UnstableApi
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.car.CarApp
import com.mochyy.mochymusic.car.CarShelf
import com.mochyy.mochymusic.car.CarSong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// the phone's home on the car: quick picks to play straight away, then the categories to open
@UnstableApi
internal class CarHomeTab(app: CarApp) : CarDataTab<CarHomeTab.Data>(app, "home", R.string.car_tab_home, R.drawable.home) {
    class Data(
        val picks: List<CarSong>,
        val pickCovers: List<CarIcon>,
        val shelves: List<CarShelf>,
        val shelfCovers: List<CarIcon>,
    )

    override suspend fun fetch(): Data = coroutineScope {
        val picks = async { app.catalog.quickPicks().take(app.gridLimit) }
        // youtube may be out of reach in a tunnel, the quick picks still come from the phone itself
        val shelves = async { runCatching { app.catalog.shelves() }.getOrDefault(emptyList()).take(app.gridLimit) }
        val songs = picks.await()
        val rows = shelves.await()
        Data(songs, app.images.covers(songs.map { it.thumbnail }), rows, app.images.covers(rows.map { it.thumbnail }))
    }

    override fun empty() = Data(emptyList(), emptyList(), emptyList(), emptyList())

    override fun build(data: Data): Template {
        val template = SectionedItemTemplate.Builder()
        if (data.picks.isNotEmpty()) {
            val tiles = data.picks.mapIndexed { index, song -> songTile(song, data.pickCovers[index]) { app.play(app.catalog.radio(song)) } }
            template.addSection(coverGrid().setTitle(app.string(R.string.car_quick_picks)).setItems(tiles).build())
        }
        if (data.shelves.isNotEmpty()) {
            val tiles = data.shelves.mapIndexed { index, shelf ->
                collectionTile(shelf.title, null, data.shelfCovers[index]) { app.push(CarShelfScreen(app, shelf)) }
            }
            template.addSection(coverGrid().setTitle(app.string(R.string.car_categories)).setItems(tiles).build())
        }
        if (data.picks.isEmpty() && data.shelves.isEmpty()) {
            template.addSection(RowSection.Builder().setNoItemsMessage(app.string(R.string.car_home_empty)).build())
        }
        return template.addAction(app.playerButton()).build()
    }

    override fun loadingTemplate(): Template = SectionedItemTemplate.Builder().setLoading(true).build()
}
