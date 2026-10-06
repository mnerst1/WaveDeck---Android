package dev.wavedeck

import android.content.Context
import androidx.room.Room
import dev.wavedeck.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseTest {
    private lateinit var db: MusicDatabase
    private val t =
        Track(
            1,
            "content://media/external/audio/media/1",
            "One",
            "Artist",
            "Album",
            1,
            10000,
            "Music/",
            "",
            500,
            1,
            "audio/wav",
        )

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    MusicDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun incrementalScanPreservesUserDataAndPrunesMissing() = runTest {
        val dao = db.music()
        dao.upsert(listOf(t.copy(favorite = true, plays = 3, lyrics = "Words"), t.copy(id = 2)))
        dao.mergeScan(listOf(t.copy(title = "Moved")))
        val result = dao.all().single()
        assertEquals("Moved", result.title)
        assertTrue(result.favorite)
        assertEquals(3, result.plays)
        assertEquals("Words", result.lyrics)
    }

    @Test
    fun playlistCrudAndDuplicateAdd() = runTest {
        val dao = db.music()
        val id = dao.create(Playlist(name = "Morning"))
        dao.addTracks(id, listOf(1, 2, 1))
        assertEquals(listOf(1L, 2L), dao.entries(id).map { it.trackId })
        dao.rename(id, "Evening")
        assertEquals("Evening", dao.playlists().first().single().name)
        dao.remove(id, 1)
        assertEquals(1, dao.entries(id).size)
        dao.deletePlaylist(id)
        assertTrue(dao.entries(id).isEmpty())
    }

    @Test
    fun queueAndResumeSavedAtomically() = runTest {
        val dao = db.music()
        dao.saveQueue(listOf(2, 1), Resume(index = 1, position = 1500, shuffle = true, repeat = 2))
        assertEquals(listOf(2L, 1L), dao.queue().map { it.trackId })
        assertEquals(1500L, dao.resume()!!.position)
        assertTrue(dao.resume()!!.shuffle)
    }

    @Test
    fun settingsPersistWithDataStore() = runTest {
        val store = SettingsStore(RuntimeEnvironment.getApplication())
        store.string("layout", "vinyl")
        store.flag("performance", true)
        store.number("radius", 12f)
        val s = store.flow.first()
        assertEquals("vinyl", s.layout)
        assertTrue(s.performance)
        assertEquals(12f, s.radius, 0f)
    }

    @Test
    fun migrationPreservesExistingLibrary() = runTest {
        val context = RuntimeEnvironment.getApplication() as Context
        val name = "migration-verification.db"
        context.deleteDatabase(name)
        val schema =
            org.json
                .JSONObject(
                    java.io.File("schemas/dev.wavedeck.data.MusicDatabase/1.json").readText()
                )
                .getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(
                    entity
                        .getString("createSql")
                        .replace("\u0024{TABLE_NAME}", entity.getString("tableName"))
                )
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(
                    indices
                        .getJSONObject(j)
                        .getString("createSql")
                        .replace("\u0024{TABLE_NAME}", entity.getString("tableName"))
                )
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL(
                "INSERT INTO tracks VALUES (1,'content://media/1','Persisted','Artist','Album',1,1000,'Music/','',500,0,'audio/wav',1,3,100,'Words',0)"
            )
            old.version = 1
        }
        val upgraded =
            Room.databaseBuilder(context, MusicDatabase::class.java, name)
                .addMigrations(MusicDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
        try {
            val result = upgraded.music().track(1)!!
            assertEquals("Persisted", result.title)
            assertTrue(result.favorite)
            assertEquals(3, result.plays)
            assertEquals("", result.fileName)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun exactlyTwoDesignSystemsAndLegacyPreferencesMigrate() = runTest {
        val store = SettingsStore(RuntimeEnvironment.getApplication())
        listOf("original", "expressive", "minimal", "compact", "soft", "material").forEach {
            previous ->
            store.string("mode", previous)
            assertEquals("material", store.flow.first().mode)
        }
        store.string("mode", "glass")
        assertEquals("glass", store.flow.first().mode)
    }
}
