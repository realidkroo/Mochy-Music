// CarRows.kt

package com.mochyy.mochymusic.car.screens

import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.Row
import androidx.media3.common.util.UnstableApi
import com.mochyy.mochymusic.car.CarApp
import com.mochyy.mochymusic.car.CarCollection
import com.mochyy.mochymusic.car.CarSong

// the rows and tiles every car list is made of, so a song looks the same wherever it shows up

// the car refuses a row without a title, and a song saved without one still deserves a row
private fun String.orDash(): String = ifBlank { "–" }

internal fun songRow(song: CarSong, cover: CarIcon, onClick: () -> Unit): Row =
    Row.Builder()
        .setTitle(song.title.orDash())
        .apply { if (song.subtitle.isNotBlank()) addText(song.subtitle) }
        .setImage(cover, Row.IMAGE_TYPE_LARGE)
        .setOnClickListener(onClick)
        .build()

internal fun collectionRow(collection: CarCollection, cover: CarIcon, onClick: () -> Unit): Row =
    Row.Builder()
        .setTitle(collection.title.orDash())
        .apply { collection.subtitle?.takeIf { it.isNotBlank() }?.let { addText(it) } }
        .setImage(cover, Row.IMAGE_TYPE_LARGE)
        .setBrowsable(true)
        .setOnClickListener(onClick)
        .build()

internal fun songTile(song: CarSong, cover: CarIcon, onClick: () -> Unit): GridItem =
    GridItem.Builder()
        .setTitle(song.title.orDash())
        .apply { if (song.subtitle.isNotBlank()) setText(song.subtitle) }
        .setImage(cover, GridItem.IMAGE_TYPE_LARGE)
        .setOnClickListener(onClick)
        .build()

internal fun collectionTile(title: String, subtitle: String?, cover: CarIcon, onClick: () -> Unit): GridItem =
    GridItem.Builder()
        .setTitle(title.orDash())
        .apply { subtitle?.takeIf { it.isNotBlank() }?.let { setText(it) } }
        .setImage(cover, GridItem.IMAGE_TYPE_LARGE)
        .setOnClickListener(onClick)
        .build()

// the round button every browse screen keeps, so the player is always one tap away
@UnstableApi
internal fun CarApp.playerButton(): Action =
    Action.Builder(Action.MEDIA_PLAYBACK).setOnClickListener { showNowPlaying() }.build()
