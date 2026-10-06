package dev.wavedeck.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class Library(private val context: Context, val dao: MusicDao, private val scope: CoroutineScope) {
    val scanning = MutableStateFlow(false)
    val error = MutableStateFlow(false)
    private var scanJob: Job? = null
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refresh()
            }
        }

    init {
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(250)
            scanning.value = true
            error.value = false
            try {
                val tracks = withContext(Dispatchers.IO) { scan() }
                dao.mergeScan(tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error.value = true
            } finally {
                scanning.value = false
            }
        }
    }

    private fun scan(): List<Track> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns =
            mutableListOf(
                "_id",
                "title",
                "artist",
                "album",
                "album_id",
                "duration",
                "_size",
                "date_modified",
                "mime_type",
                "_display_name",
            )
        columns += if (Build.VERSION.SDK_INT >= 29) "relative_path" else "_data"
        if (Build.VERSION.SDK_INT >= 30) columns += "genre"
        val result = ArrayList<Track>()
        context.contentResolver
            .query(uri, columns.toTypedArray(), "duration > 0", null, null)
            ?.use { c ->
                fun text(k: String) =
                    c.getColumnIndex(k).let { if (it < 0 || c.isNull(it)) "" else c.getString(it) }
                fun num(k: String) = c.getColumnIndex(k).let { if (it < 0) 0L else c.getLong(it) }
                while (c.moveToNext()) {
                    if (Thread.currentThread().isInterrupted) break
                    val id = num("_id")
                    val path = text(if (Build.VERSION.SDK_INT >= 29) "relative_path" else "_data")
                    result +=
                        Track(
                            id,
                            ContentUris.withAppendedId(uri, id).toString(),
                            text("title"),
                            text("artist").takeUnless { it == "<unknown>" } ?: "",
                            text("album"),
                            num("album_id"),
                            num("duration"),
                            if (Build.VERSION.SDK_INT >= 29) path
                            else path.substringBeforeLast('/'),
                            text("genre"),
                            num("_size"),
                            num("date_modified"),
                            text("mime_type"),
                            fileName = text("_display_name"),
                        )
                }
            } ?: error("MediaStore unavailable")
        return result
    }
}
