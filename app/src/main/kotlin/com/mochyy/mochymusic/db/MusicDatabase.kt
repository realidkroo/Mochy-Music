package com.mochyy.mochymusic.db

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.mochyy.mochymusic.db.daos.SpeedDialDao
import com.mochyy.mochymusic.db.entities.AlbumArtistMap
import com.mochyy.mochymusic.db.entities.AlbumEntity
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.Event
import com.mochyy.mochymusic.db.entities.FormatEntity
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.db.entities.PlayCountEntity
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.db.entities.ImportSession
import com.mochyy.mochymusic.db.entities.ImportSessionSong
import com.mochyy.mochymusic.db.entities.ImportedPlaylist
import com.mochyy.mochymusic.db.entities.ImportedPlaylistTrack
import com.mochyy.mochymusic.db.entities.ImportedSong
import com.mochyy.mochymusic.db.entities.PlaylistEvent
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.db.entities.PlaylistSongMapPreview
import com.mochyy.mochymusic.db.entities.RecognitionHistory
import com.mochyy.mochymusic.db.entities.RelatedSongMap
import com.mochyy.mochymusic.db.entities.SearchHistory
import com.mochyy.mochymusic.db.entities.SetVideoIdEntity
import com.mochyy.mochymusic.db.entities.SongAlbumMap
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.db.entities.SongEntity
import com.mochyy.mochymusic.db.entities.SpeedDialItem
import com.mochyy.mochymusic.db.entities.SortedSongAlbumMap
import com.mochyy.mochymusic.db.entities.SortedSongArtistMap
import com.mochyy.mochymusic.extensions.toSQLiteQuery
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date

class MusicDatabase(
    private val delegate: InternalDatabase,
) : DatabaseDao by delegate.dao {
    val speedDialDao: SpeedDialDao
        get() = delegate.speedDialDao

    /**
     * Pins [item], evicting the oldest pin first if that would push the count past
     * [com.mochyy.mochymusic.db.daos.SpeedDialDao.Companion.MaxPinned].
     *
     * The one place pins are created. Every menu that offers "Pin to Speed dial" should call this
     * instead of `speedDialDao.insert` directly — the cap only holds if there's a single entry
     * point enforcing it, and scattering the limit check across each menu's onClick would drift
     * the moment one of them was written (or edited) without it.
     */
    suspend fun pinToSpeedDial(item: com.mochyy.mochymusic.db.entities.SpeedDialItem) {
        if (speedDialDao.count() >= SpeedDialDao.MaxPinned) {
            speedDialDao.oldest()?.let { speedDialDao.delete(it.id) }
        }
        speedDialDao.insert(item)
    }

    val openHelper: SupportSQLiteOpenHelper
        get() = delegate.openHelper

    fun query(block: MusicDatabase.() -> Unit) =
        with(delegate) {
            queryExecutor.execute {
                block(this@MusicDatabase)
            }
        }

    fun transaction(block: MusicDatabase.() -> Unit) =
        with(delegate) {
            transactionExecutor.execute {
                runInTransaction {
                    block(this@MusicDatabase)
                }
            }
        }

    suspend fun withTransaction(block: suspend MusicDatabase.() -> Unit) =
        with(delegate) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runInTransaction {
                    kotlinx.coroutines.runBlocking {
                        block(this@MusicDatabase)
                    }
                }
            }
        }

    fun close() = delegate.close()
}

// bump this with every schema change and add its migration to AllMigrations, the import checks old exports against it too
const val MusicDatabaseVersion = 42

@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        SongArtistMap::class,
        SongAlbumMap::class,
        AlbumArtistMap::class,
        PlaylistSongMap::class,
        SearchHistory::class,
        FormatEntity::class,
        LyricsEntity::class,
        Event::class,
        RelatedSongMap::class,
        SetVideoIdEntity::class,
        PlayCountEntity::class,
        RecognitionHistory::class,
        SpeedDialItem::class,
        PlaylistEvent::class,
        ImportedSong::class,
        ImportSession::class,
        ImportSessionSong::class,
        ImportedPlaylist::class,
        ImportedPlaylistTrack::class,
    ],
    views = [
        SortedSongArtistMap::class,
        SortedSongAlbumMap::class,
        PlaylistSongMapPreview::class,
    ],
    version = MusicDatabaseVersion,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InternalDatabase : RoomDatabase() {
    abstract val dao: DatabaseDao
    abstract val speedDialDao: SpeedDialDao

    companion object {
        const val DB_NAME = "mochy.db"

        fun newInstance(context: Context): MusicDatabase =
            MusicDatabase(
                delegate =
                Room
                    .databaseBuilder(context, InternalDatabase::class.java, DB_NAME)
                    .addMigrations(*AllMigrations)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .setTransactionExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
                    .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                db.query("PRAGMA busy_timeout = 60000").close()
                                db.query("PRAGMA cache_size = -16000").close()
                                db.query("PRAGMA wal_autocheckpoint = 1000").close()
                                db.query("PRAGMA synchronous = NORMAL").close()
                            } catch (e: Exception) {
                                Timber.tag("MusicDatabase").e(e, "Failed to set PRAGMA settings")
                            }
                        }
                    })
                    .build(),
            )
    }
}

/**
 * Every hand-written migration, for every place the database is built (here and the Hilt module).
 * The app opens it through the Hilt module; when that copy of the list fell behind this one, an
 * upgrade found no path to the new version and `fallbackToDestructiveMigration` emptied the whole
 * library. Add new migrations here and nowhere else.
 */
val AllMigrations: Array<Migration>
    get() = arrayOf(
        MIGRATION_1_2,
        MIGRATION_21_24,
        MIGRATION_22_24,
        MIGRATION_24_25,
        MIGRATION_35_36,
        MIGRATION_36_37,
        MIGRATION_37_38,
        MIGRATION_38_39,
        MIGRATION_39_40,
        MIGRATION_40_41,
        MIGRATION_41_42,
    )

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            data class OldSongEntity(
                val id: String,
                val title: String,
                val duration: Int = -1,
                val thumbnailUrl: String? = null,
                val albumId: String? = null,
                val albumName: String? = null,
                val liked: Boolean = false,
                val totalPlayTime: Long = 0,
                val downloadState: Int = 0,
                val createDate: LocalDateTime = LocalDateTime.now(),
                val modifyDate: LocalDateTime = LocalDateTime.now(),
            )

            val converters = Converters()
            val artistMap = mutableMapOf<Int, String>()
            val artists = mutableListOf<ArtistEntity>()
            db.query("SELECT * FROM artist".toSQLiteQuery()).use { cursor ->
                while (cursor.moveToNext()) {
                    val oldId = cursor.getInt(0)
                    val newId = ArtistEntity.generateArtistId()
                    artistMap[oldId] = newId
                    artists.add(
                        ArtistEntity(
                            id = newId,
                            name = cursor.getString(1),
                        ),
                    )
                }
            }

            val playlistMap = mutableMapOf<Int, String>()
            val playlists = mutableListOf<PlaylistEntity>()
            db.query("SELECT * FROM playlist".toSQLiteQuery()).use { cursor ->
                while (cursor.moveToNext()) {
                    val oldId = cursor.getInt(0)
                    val newId = PlaylistEntity.generatePlaylistId()
                    playlistMap[oldId] = newId
                    playlists.add(
                        PlaylistEntity(
                            id = newId,
                            name = cursor.getString(1),
                        ),
                    )
                }
            }
            val playlistSongMaps = mutableListOf<PlaylistSongMap>()
            db.query("SELECT * FROM playlist_song".toSQLiteQuery()).use { cursor ->
                while (cursor.moveToNext()) {
                    playlistSongMaps.add(
                        PlaylistSongMap(
                            playlistId = playlistMap[cursor.getInt(1)]!!,
                            songId = cursor.getString(2),
                            position = cursor.getInt(3),
                        ),
                    )
                }
            }
            playlistSongMaps.sortBy { it.position }
            val playlistSongCount = mutableMapOf<String, Int>()
            playlistSongMaps.map { map ->
                if (map.playlistId !in playlistSongCount) playlistSongCount[map.playlistId] = 0
                map.copy(position = playlistSongCount[map.playlistId]!!).also {
                    playlistSongCount[map.playlistId] = playlistSongCount[map.playlistId]!! + 1
                }
            }
            val songs = mutableListOf<OldSongEntity>()
            val songArtistMaps = mutableListOf<SongArtistMap>()
            db.query("SELECT * FROM song".toSQLiteQuery()).use { cursor ->
                while (cursor.moveToNext()) {
                    val songId = cursor.getString(0)
                    songs.add(
                        OldSongEntity(
                            id = songId,
                            title = cursor.getString(1),
                            duration = cursor.getInt(3),
                            liked = cursor.getInt(4) == 1,
                            createDate = Instant.ofEpochMilli(Date(cursor.getLong(8)).time)
                                .atZone(ZoneOffset.UTC).toLocalDateTime(),
                            modifyDate = Instant.ofEpochMilli(Date(cursor.getLong(9)).time)
                                .atZone(ZoneOffset.UTC).toLocalDateTime(),
                        ),
                    )
                    songArtistMaps.add(
                        SongArtistMap(
                            songId = songId,
                            artistId = artistMap[cursor.getInt(2)]!!,
                            position = 0,
                        ),
                    )
                }
            }
            db.execSQL("DROP TABLE IF EXISTS song")
            db.execSQL("DROP TABLE IF EXISTS artist")
            db.execSQL("DROP TABLE IF EXISTS playlist")
            db.execSQL("DROP TABLE IF EXISTS playlist_song")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `duration` INTEGER NOT NULL, `thumbnailUrl` TEXT, `albumId` TEXT, `albumName` TEXT, `liked` INTEGER NOT NULL, `totalPlayTime` INTEGER NOT NULL, `isTrash` INTEGER NOT NULL, `download_state` INTEGER NOT NULL, `create_date` INTEGER NOT NULL, `modify_date` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `artist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `thumbnailUrl` TEXT, `bannerUrl` TEXT, `description` TEXT, `createDate` INTEGER NOT NULL, `lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `album` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `year` INTEGER, `thumbnailUrl` TEXT, `songCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `createDate` INTEGER NOT NULL, `lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `author` TEXT, `authorId` TEXT, `year` INTEGER, `thumbnailUrl` TEXT, `createDate` INTEGER NOT NULL, `lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_artist_map` (`songId` TEXT NOT NULL, `artistId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`songId`, `artistId`), FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`artistId`) REFERENCES `artist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_artist_map_songId` ON `song_artist_map` (`songId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_artist_map_artistId` ON `song_artist_map` (`artistId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_album_map` (`songId` TEXT NOT NULL, `albumId` TEXT NOT NULL, `index` INTEGER, PRIMARY KEY(`songId`, `albumId`), FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`albumId`) REFERENCES `album`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_album_map_songId` ON `song_album_map` (`songId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_album_map_albumId` ON `song_album_map` (`albumId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_artist_map` (`albumId` TEXT NOT NULL, `artistId` TEXT NOT NULL, `order` INTEGER NOT NULL, PRIMARY KEY(`albumId`, `artistId`), FOREIGN KEY(`albumId`) REFERENCES `album`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`artistId`) REFERENCES `artist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_artist_map_albumId` ON `album_artist_map` (`albumId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_artist_map_artistId` ON `album_artist_map` (`artistId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist_song_map` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` TEXT NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_map_playlistId` ON `playlist_song_map` (`playlistId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_map_songId` ON `playlist_song_map` (`songId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `download` (`id` INTEGER NOT NULL, `songId` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `query` TEXT NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_query` ON `search_history` (`query`)")
            db.execSQL("CREATE VIEW `sorted_song_artist_map` AS SELECT * FROM song_artist_map ORDER BY position")
            db.execSQL(
                "CREATE VIEW `playlist_song_map_preview` AS SELECT * FROM playlist_song_map WHERE position <= 3 ORDER BY position",
            )
            artists.forEach { artist ->
                db.insert(
                    "artist",
                    SQLiteDatabase.CONFLICT_ABORT,
                    contentValuesOf(
                        "id" to artist.id,
                        "name" to artist.name,
                        "createDate" to converters.dateToTimestamp(artist.lastUpdateTime),
                        "lastUpdateTime" to converters.dateToTimestamp(artist.lastUpdateTime),
                    ),
                )
            }
            songs.forEach { song ->
                db.insert(
                    "song",
                    SQLiteDatabase.CONFLICT_ABORT,
                    contentValuesOf(
                        "id" to song.id,
                        "title" to song.title,
                        "duration" to song.duration,
                        "liked" to song.liked,
                        "totalPlayTime" to song.totalPlayTime,
                        "isTrash" to false,
                        "download_state" to song.downloadState,
                        "create_date" to converters.dateToTimestamp(song.createDate),
                        "modify_date" to converters.dateToTimestamp(song.modifyDate),
                    ),
                )
            }
            songArtistMaps.forEach { songArtistMap ->
                db.insert(
                    "song_artist_map",
                    SQLiteDatabase.CONFLICT_ABORT,
                    contentValuesOf(
                        "songId" to songArtistMap.songId,
                        "artistId" to songArtistMap.artistId,
                        "position" to songArtistMap.position,
                    ),
                )
            }
            playlists.forEach { playlist ->
                db.insert(
                    "playlist",
                    SQLiteDatabase.CONFLICT_ABORT,
                    contentValuesOf(
                        "id" to playlist.id,
                        "name" to playlist.name,
                        "createDate" to converters.dateToTimestamp(LocalDateTime.now()),
                        "lastUpdateTime" to converters.dateToTimestamp(LocalDateTime.now()),
                    ),
                )
            }
            playlistSongMaps.forEach { playlistSongMap ->
                db.insert(
                    "playlist_song_map",
                    SQLiteDatabase.CONFLICT_ABORT,
                    contentValuesOf(
                        "playlistId" to playlistSongMap.playlistId,
                        "songId" to playlistSongMap.songId,
                        "position" to playlistSongMap.position,
                    ),
                )
            }
        }
    }

val MIGRATION_21_24 =
    object : Migration(21, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {

            try {
                db.execSQL("ALTER TABLE song ADD COLUMN libraryAddToken TEXT DEFAULT ''")
            } catch (e: Exception) {
                Timber.tag("Migration").w("Column libraryAddToken may already exist")
            }
            try {
                db.execSQL("ALTER TABLE song ADD COLUMN libraryRemoveToken TEXT DEFAULT ''")
            } catch (e: Exception) {
                Timber.tag("Migration").w("Column libraryRemoveToken may already exist")
            }
            try {
                db.execSQL("ALTER TABLE song ADD COLUMN romanizeLyrics INTEGER NOT NULL DEFAULT 1")
            } catch (e: Exception) {
                Timber.tag("Migration").w("Column romanizeLyrics may already exist")
            }
            try {
                db.execSQL("ALTER TABLE song ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                Timber.tag("Migration").w("Column isDownloaded may already exist")
            }

            var hasIsUploaded = false
            db.query("PRAGMA table_info('song')").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    val colName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    if (colName == "isUploaded") {
                        hasIsUploaded = true
                        break
                    }
                }
            }

            if (!hasIsUploaded) {
                db.execSQL("ALTER TABLE `song` ADD COLUMN `isUploaded` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

val MIGRATION_22_24 =
    object : Migration(22, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {

            var hasIsUploaded = false
            db.query("PRAGMA table_info('song')").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    val colName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    if (colName == "isUploaded") {
                        hasIsUploaded = true
                        break
                    }
                }
            }

            if (!hasIsUploaded) {
                db.execSQL("ALTER TABLE `song` ADD COLUMN `isUploaded` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

@DeleteColumn.Entries(
    DeleteColumn(tableName = "song", columnName = "isTrash"),
    DeleteColumn(tableName = "playlist", columnName = "author"),
    DeleteColumn(tableName = "playlist", columnName = "authorId"),
    DeleteColumn(tableName = "playlist", columnName = "year"),
    DeleteColumn(tableName = "playlist", columnName = "thumbnailUrl"),
    DeleteColumn(tableName = "playlist", columnName = "createDate"),
    DeleteColumn(tableName = "playlist", columnName = "lastUpdateTime"),
)
@RenameColumn.Entries(
    RenameColumn(
        tableName = "song",
        fromColumnName = "download_state",
        toColumnName = "downloadState"
    ),
    RenameColumn(tableName = "song", fromColumnName = "create_date", toColumnName = "createDate"),
    RenameColumn(tableName = "song", fromColumnName = "modify_date", toColumnName = "modifyDate"),
)
class Migration5To6 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.query("SELECT id FROM playlist WHERE id NOT LIKE 'LP%'").use { cursor ->
            while (cursor.moveToNext()) {
                db.execSQL(
                    "UPDATE playlist SET browseId = '${cursor.getString(0)}' WHERE id = '${cursor.getString(0)}'"
                )
            }
        }
    }
}

class Migration6To7 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.query("SELECT id, createDate FROM song").use { cursor ->
            while (cursor.moveToNext()) {
                db.execSQL(
                    "UPDATE song SET inLibrary = ${cursor.getLong(1)} WHERE id = '${cursor.getString(0)}'"
                )
            }
        }
    }
}

@DeleteColumn.Entries(
    DeleteColumn(tableName = "song", columnName = "createDate"),
    DeleteColumn(tableName = "song", columnName = "modifyDate"),
)
class Migration7To8 : AutoMigrationSpec

@DeleteTable.Entries(
    DeleteTable(tableName = "download"),
)
class Migration9To10 : AutoMigrationSpec

@DeleteColumn.Entries(
    DeleteColumn(tableName = "song", columnName = "downloadState"),
    DeleteColumn(tableName = "artist", columnName = "bannerUrl"),
    DeleteColumn(tableName = "artist", columnName = "description"),
    DeleteColumn(tableName = "artist", columnName = "createDate"),
)
class Migration10To11 : AutoMigrationSpec

@DeleteColumn.Entries(
    DeleteColumn(tableName = "album", columnName = "createDate"),
)
class Migration11To12 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE album SET bookmarkedAt = lastUpdateTime")
        db.query("SELECT DISTINCT albumId, albumName FROM song").use { cursor ->
            while (cursor.moveToNext()) {
                val albumId = cursor.getString(0)
                val albumName = cursor.getString(1)
                db.insert(
                    table = "album",
                    conflictAlgorithm = SQLiteDatabase.CONFLICT_IGNORE,
                    values =
                    contentValuesOf(
                        "id" to albumId,
                        "title" to albumName,
                        "songCount" to 0,
                        "duration" to 0,
                        "lastUpdateTime" to 0,
                    ),
                )
            }
        }
        db.query("CREATE INDEX IF NOT EXISTS `index_song_albumId` ON `song` (`albumId`)")
    }
}

class Migration12To13 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
    }
}

class Migration13To14 : AutoMigrationSpec {
    @SuppressLint("Range")
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE playlist SET createdAt = '${Converters().dateToTimestamp(LocalDateTime.now())}'")
        db.execSQL(
            "UPDATE playlist SET lastUpdateTime = '${Converters().dateToTimestamp(LocalDateTime.now())}'"
        )
    }
}

@DeleteColumn.Entries(
    DeleteColumn(tableName = "song", columnName = "isLocal"),
    DeleteColumn(tableName = "song", columnName = "localPath"),
    DeleteColumn(tableName = "artist", columnName = "isLocal"),
    DeleteColumn(tableName = "playlist", columnName = "isLocal"),
)
class Migration16To17 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE playlist SET bookmarkedAt = lastUpdateTime")
        db.execSQL("UPDATE playlist SET isEditable = 1 WHERE browseId IS NOT NULL")
    }
}

class Migration18To19 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE song SET explicit = 0 WHERE explicit IS NULL")
    }
}

class Migration19To20 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE song SET explicit = 0 WHERE explicit IS NULL")
    }
}

@DeleteColumn.Entries(
    DeleteColumn(
        tableName = "song",
        columnName = "artistName"
    )
)
class Migration20To21 : AutoMigrationSpec

class Migration21To22 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE song ADD COLUMN libraryAddToken TEXT DEFAULT ''")
        } catch (e: Exception) {
            Timber.tag("Migration21To22").w(e, "Column may already exist")
        }
        try {
            db.execSQL("ALTER TABLE song ADD COLUMN libraryRemoveToken TEXT DEFAULT ''")
        } catch (e: Exception) {
            Timber.tag("Migration21To22").w(e, "Column may already exist")
        }
        try {
            db.execSQL("ALTER TABLE song ADD COLUMN romanizeLyrics INTEGER NOT NULL DEFAULT 1")
        } catch (e: Exception) {
            Timber.tag("Migration21To22").w(e, "Column may already exist")
        }
        try {
            db.execSQL("ALTER TABLE song ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")
        } catch (e: Exception) {
            Timber.tag("Migration21To22").w(e, "Column may already exist")
        }
    }
}

class Migration22To23 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {

    }
}

class Migration23To24: AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        var hasIsUploaded = false
        db.query("PRAGMA table_info('song')").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                val colName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                if (colName == "isUploaded") {
                    hasIsUploaded = true
                    break
                }
            }
        }

        if (!hasIsUploaded) {
            db.execSQL("ALTER TABLE `song` ADD COLUMN `isUploaded` INTEGER NOT NULL DEFAULT 0")
        }
    }
}

val MIGRATION_24_25 =
    object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {

            var columnExists = false
            db.query("PRAGMA table_info(format)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "perceptualLoudnessDb") {
                        columnExists = true
                        break
                    }
                }
            }

            if (!columnExists) {

                db.execSQL("ALTER TABLE format ADD COLUMN perceptualLoudnessDb REAL DEFAULT NULL")
            }
        }
    }

class Migration29To30 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {

        var hasIsVideo = false
        db.query("PRAGMA table_info('song')").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                val colName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                if (colName == "isVideo") {
                    hasIsVideo = true
                    break
                }
            }
        }
        if (!hasIsVideo) {
            db.execSQL("ALTER TABLE song ADD COLUMN isVideo INTEGER NOT NULL DEFAULT 0")
        }

        var hasProvider = false
        db.query("PRAGMA table_info('lyrics')").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                val colName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                if (colName == "provider") {
                    hasProvider = true
                    break
                }
            }
        }
        if (!hasProvider) {
            db.execSQL("ALTER TABLE lyrics ADD COLUMN provider TEXT NOT NULL DEFAULT 'Unknown'")
        }
    }
}

val MIGRATION_35_36 =
    object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_event_playlistId` ON `playlist_event` (`playlistId`)")
        }
    }

val MIGRATION_36_37 =
    object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {

            var columnExists = false
            db.query("PRAGMA table_info(song)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "localUri") {
                        columnExists = true
                        break
                    }
                }
            }
            if (!columnExists) {
                db.execSQL("ALTER TABLE song ADD COLUMN localUri TEXT DEFAULT NULL")
            }
        }
    }

val MIGRATION_37_38 =
    object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Guarded like the migrations before it: a device that already has the column (a build
            // from another branch) must not fail the upgrade and fall into the destructive reset.
            var columnExists = false
            db.query("PRAGMA table_info(playlist)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "description") {
                        columnExists = true
                        break
                    }
                }
            }
            if (!columnExists) {
                db.execSQL("ALTER TABLE playlist ADD COLUMN description TEXT DEFAULT NULL")
            }
        }
    }

val MIGRATION_38_39 =
    object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // IF NOT EXISTS, like the playlist_event migration: a device that already has the
            // table must not fail the upgrade into the destructive reset.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `imported_song` (`songId` TEXT NOT NULL, `source` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`, `source`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_imported_song_source` ON `imported_song` (`source`)")
        }
    }

val MIGRATION_39_40 =
    object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // IF NOT EXISTS for the same reason as 38 -> 39: a device that already has the tables
            // must not fail the upgrade and fall into the destructive reset.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `import_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `source` TEXT NOT NULL, `startedAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `import_session_song` (`sessionId` INTEGER NOT NULL, `songId` TEXT NOT NULL, `addedToLibrary` INTEGER NOT NULL, `liked` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `songId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_session_song_songId` ON `import_session_song` (`songId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `imported_playlist` (`playlistId` TEXT NOT NULL, `source` TEXT NOT NULL, `sessionId` INTEGER, `importedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_imported_playlist_source` ON `imported_playlist` (`source`)")

            // Imports made before sessions existed become one session per service, so "Delete
            // Spotify imports" still reaches them. What each one changed wasn't recorded, so the
            // best guess is used: the import put its songs in the library, and a liked one was
            // liked by it. That is also how Liked songs already groups them.
            db.execSQL(
                "INSERT INTO `import_session` (`source`, `startedAt`) " +
                    "SELECT `source`, MIN(`importedAt`) FROM `imported_song` " +
                    "WHERE `source` NOT IN (SELECT `source` FROM `import_session`) GROUP BY `source`",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `import_session_song` (`sessionId`, `songId`, `addedToLibrary`, `liked`) " +
                    "SELECT `import_session`.`id`, `imported_song`.`songId`, " +
                    "CASE WHEN `song`.`inLibrary` IS NOT NULL THEN 1 ELSE 0 END, " +
                    "CASE WHEN `song`.`liked` THEN 1 ELSE 0 END " +
                    "FROM `imported_song` " +
                    "JOIN `import_session` ON `import_session`.`source` = `imported_song`.`source` " +
                    "JOIN `song` ON `song`.`id` = `imported_song`.`songId`",
            )
            // A playlist counts as imported when every song in it came from that one service and
            // its cover is the service's own picture (or none). Playlists made in the app get a
            // generated cover saved on the phone, so they never match.
            db.execSQL(
                "INSERT OR IGNORE INTO `imported_playlist` (`playlistId`, `source`, `sessionId`, `importedAt`) " +
                    "SELECT `m`.`playlistId`, `i`.`source`, `s`.`id`, `s`.`startedAt` " +
                    "FROM `playlist_song_map` AS `m` " +
                    "JOIN `imported_song` AS `i` ON `i`.`songId` = `m`.`songId` " +
                    "JOIN `playlist` AS `p` ON `p`.`id` = `m`.`playlistId` " +
                    "JOIN `import_session` AS `s` ON `s`.`source` = `i`.`source` " +
                    "WHERE `p`.`isEditable` AND (`p`.`thumbnailUrl` IS NULL OR `p`.`thumbnailUrl` LIKE 'http%') " +
                    "GROUP BY `m`.`playlistId`, `i`.`source` " +
                    "HAVING COUNT(DISTINCT `m`.`songId`) = " +
                    "(SELECT COUNT(DISTINCT `songId`) FROM `playlist_song_map` WHERE `playlistId` = `m`.`playlistId`)",
            )
        }
    }

val MIGRATION_40_41 =
    object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Where each imported playlist lives on its service, for Re-sync. Columns are checked
            // first, the way the tables above use IF NOT EXISTS: adding one that's already there
            // fails the upgrade, and a failed upgrade falls into the destructive reset.
            val columns = db.query("PRAGMA table_info(`imported_playlist`)").use { cursor ->
                val name = cursor.getColumnIndex("name")
                buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
            }
            if ("remoteId" !in columns) db.execSQL("ALTER TABLE `imported_playlist` ADD COLUMN `remoteId` TEXT")
            if ("remoteUrl" !in columns) db.execSQL("ALTER TABLE `imported_playlist` ADD COLUMN `remoteUrl` TEXT")
            if ("lastSyncedAt" !in columns) db.execSQL("ALTER TABLE `imported_playlist` ADD COLUMN `lastSyncedAt` INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `imported_playlist_track` (`playlistId` TEXT NOT NULL, `trackKey` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `trackKey`))",
            )
        }
    }

val MIGRATION_41_42 =
    object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // checked first like 40 -> 41, adding a column that's already there fails into the destructive reset
            val columns = db.query("PRAGMA table_info(`song`)").use { cursor ->
                val name = cursor.getColumnIndex("name")
                buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
            }
            if ("addedByLike" !in columns) db.execSQL("ALTER TABLE `song` ADD COLUMN `addedByLike` INTEGER NOT NULL DEFAULT 0")
            // best guess for songs already in: a like put it in when both dates match, or youtube sync brought the like, and imports or files never count
            db.execSQL(
                "UPDATE `song` SET `addedByLike` = 1 " +
                    "WHERE `inLibrary` IS NOT NULL AND `liked` AND NOT `isLocal` " +
                    "AND `id` NOT IN (SELECT `songId` FROM `imported_song` WHERE `source` != 'youtube_music') " +
                    "AND (`likedDate` IS NULL OR ABS(`likedDate` - `inLibrary`) < 5000 " +
                    "OR `id` IN (SELECT `songId` FROM `imported_song` WHERE `source` = 'youtube_music'))",
            )
        }
    }
