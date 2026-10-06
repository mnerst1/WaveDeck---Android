package dev.wavedeck.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArtworkStore(private val context: Context) {
    private val cache =
        object : LruCache<Long, ByteArray>(8 * 1024 * 1024) {
            override fun sizeOf(key: Long, value: ByteArray) = value.size
        }

    suspend fun model(track: Track): Any? =
        withContext(Dispatchers.IO) {
            if (track.id < 0)
                return@withContext "android.resource://dev.wavedeck/drawable/demo_cover"
            cache.get(track.albumId)?.let {
                return@withContext it
            }
            val uri = Uri.parse(track.artwork)
            if (
                runCatching { context.contentResolver.openInputStream(uri)?.use { true } ?: false }
                    .getOrDefault(false)
            )
                return@withContext uri
            runCatching {
                val reader = MediaMetadataRetriever()
                try {
                    reader.setDataSource(context, Uri.parse(track.uri))
                    reader.embeddedPicture
                        ?.takeIf { it.size <= 4 * 1024 * 1024 }
                        ?.also { cache.put(track.albumId, it) }
                } finally {
                    reader.release()
                }
            }
                .getOrNull()
        }
}
