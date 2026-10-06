package dev.wavedeck.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val folder: String,
    val genre: String,
    val size: Long,
    val modified: Long,
    val mime: String,
    val favorite: Boolean = false,
    val plays: Int = 0,
    val lastPlayed: Long = 0,
    val lyrics: String = "",
    val lyricsOffset: Long = 0,
    @ColumnInfo(defaultValue = "''") val fileName: String = "",
) {
    val artwork: String
        get() = "content://media/external/audio/albumart/$albumId"
}

@Entity(tableName = "playlists")
data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys =
        [
            ForeignKey(
                entity = Playlist::class,
                parentColumns = ["id"],
                childColumns = ["playlistId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("playlistId")],
)
data class PlaylistEntry(val playlistId: Long, val trackId: Long, val position: Int)

@Entity(tableName = "history", indices = [Index("trackId")])
data class History(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val at: Long,
)

@Entity(tableName = "queue") data class QueueEntry(@PrimaryKey val position: Int, val trackId: Long)

@Entity(tableName = "resume")
data class Resume(
    @PrimaryKey val id: Int = 0,
    val index: Int = 0,
    val position: Long = 0,
    val repeat: Int = 0,
    val shuffle: Boolean = false,
    val speed: Float = 1f,
)

@Dao
abstract class MusicDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    abstract fun tracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    abstract suspend fun all(): List<Track>

    @Query("SELECT * FROM tracks WHERE id=:id") abstract suspend fun track(id: Long): Track?

    @Upsert abstract suspend fun upsert(tracks: List<Track>)

    @Query("DELETE FROM tracks WHERE id NOT IN (:ids)")
    abstract suspend fun deleteMissing(ids: List<Long>)

    @Query("UPDATE tracks SET favorite=NOT favorite WHERE id=:id")
    abstract suspend fun favorite(id: Long)

    @Query("UPDATE tracks SET plays=plays+1,lastPlayed=:at WHERE id=:id")
    abstract suspend fun count(id: Long, at: Long)

    @Insert abstract suspend fun history(entry: History)

    @Query("SELECT * FROM history ORDER BY at DESC LIMIT 500")
    abstract fun history(): Flow<List<History>>

    @Query(
        "DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY at DESC LIMIT 5000)"
    )
    abstract suspend fun trimHistory()

    @Query("UPDATE tracks SET lyrics=:text,lyricsOffset=:offset WHERE id=:id")
    abstract suspend fun lyrics(id: Long, text: String, offset: Long)

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    abstract fun playlists(): Flow<List<Playlist>>

    @Insert abstract suspend fun create(playlist: Playlist): Long

    @Query("UPDATE playlists SET name=:name WHERE id=:id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id=:id") abstract suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_entries ORDER BY position")
    abstract fun entries(): Flow<List<PlaylistEntry>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId=:id ORDER BY position")
    abstract suspend fun entries(id: Long): List<PlaylistEntry>

    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun add(entry: PlaylistEntry)

    @Query("DELETE FROM playlist_entries WHERE playlistId=:id AND trackId=:track")
    abstract suspend fun remove(id: Long, track: Long)

    @Transaction
    open suspend fun addTracks(id: Long, tracks: List<Long>) {
        var p = (entries(id).maxOfOrNull { it.position } ?: -1) + 1
        tracks.distinct().forEach { add(PlaylistEntry(id, it, p++)) }
    }

    @Query("SELECT * FROM queue ORDER BY position") abstract suspend fun queue(): List<QueueEntry>

    @Query("SELECT * FROM resume WHERE id=0") abstract suspend fun resume(): Resume?

    @Query("DELETE FROM queue") abstract suspend fun clearQueue()

    @Insert abstract suspend fun insertQueue(entries: List<QueueEntry>)

    @Upsert abstract suspend fun saveResume(resume: Resume)

    @Transaction
    open suspend fun saveQueue(ids: List<Long>, resume: Resume) {
        clearQueue()
        insertQueue(ids.mapIndexed { i, id -> QueueEntry(i, id) })
        saveResume(resume)
    }

    @Transaction
    open suspend fun recordPlay(id: Long) {
        val at = System.currentTimeMillis()
        count(id, at)
        history(History(trackId = id, at = at))
        trimHistory()
    }

    @Transaction
    open suspend fun mergeScan(scanned: List<Track>) {
        val old = all().associateBy { it.id }
        upsert(
            scanned.map { n ->
                old[n.id]?.let {
                    n.copy(
                        favorite = it.favorite,
                        plays = it.plays,
                        lastPlayed = it.lastPlayed,
                        lyrics = it.lyrics,
                        lyricsOffset = it.lyricsOffset,
                    )
                } ?: n
            }
        )
        deleteMissing(scanned.map { it.id } + old.keys.filter { it < 0 })
    }
}

@Database(
    entities =
        [
            Track::class,
            Playlist::class,
            PlaylistEntry::class,
            History::class,
            QueueEntry::class,
            Resume::class,
        ],
    version = 2,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun music(): MusicDao

    companion object {
        val MIGRATION_1_2 =
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tracks ADD COLUMN fileName TEXT NOT NULL DEFAULT ''")
                }
            }
    }
}
