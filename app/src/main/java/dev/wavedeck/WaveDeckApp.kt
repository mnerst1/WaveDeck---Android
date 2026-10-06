package dev.wavedeck

import android.app.Application
import androidx.room.Room
import dev.wavedeck.data.*
import kotlinx.coroutines.*

class WaveDeckApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy {
        Room.databaseBuilder(this, MusicDatabase::class.java, "wavedeck.db")
            .addMigrations(MusicDatabase.MIGRATION_1_2)
            .build()
    }
    val settings by lazy { SettingsStore(this) }
    val artwork by lazy { ArtworkStore(this) }
    val library by lazy { Library(this, database.music(), scope) }
}
