package com.mochyy.mochymusic.db.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class AlbumSongRow(
    @Embedded val album: AlbumEntity,
    @ColumnInfo(name = "mappedSongId") val mappedSongId: String?,
    @Relation(
        entity = ArtistEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy = Junction(
            value = AlbumArtistMap::class,
            parentColumn = "albumId",
            entityColumn = "artistId",
        ),
    )
    val artists: List<ArtistEntity>,
    @Relation(entity = SongEntity::class, parentColumn = "mappedSongId", entityColumn = "id")
    val song: Song?,
)
