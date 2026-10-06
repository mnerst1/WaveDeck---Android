package dev.wavedeck.data

import android.content.Context
import dev.wavedeck.R
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Demo {
    suspend fun create(context: Context, dao: MusicDao) =
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "tidal-light.wav")
            val rate = 44100
            val seconds = 16
            val count = rate * seconds
            val bytes = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
            bytes
                .put("RIFF".toByteArray())
                .putInt(36 + count * 2)
                .put("WAVEfmt ".toByteArray())
                .putInt(16)
                .putShort(1)
                .putShort(1)
            bytes
                .putInt(rate)
                .putInt(rate * 2)
                .putShort(2)
                .putShort(16)
                .put("data".toByteArray())
                .putInt(count * 2)
            for (i in 0 until count) {
                val t = i.toDouble() / rate
                val envelope = minOf(1.0, t / 1.5, (seconds - t) / 2).coerceAtLeast(0.0)
                val signal =
                    (sin(2 * PI * 220 * t) +
                        .45 * sin(2 * PI * 330 * t) +
                        .25 * sin(2 * PI * 440 * t)) * .16 * envelope
                bytes.putShort((signal * Short.MAX_VALUE).toInt().toShort())
            }
            FileOutputStream(file).use { it.write(bytes.array()) }
            dao.upsert(
                listOf(
                    Track(
                        -1,
                        android.net.Uri.fromFile(file).toString(),
                        context.getString(R.string.demo_title),
                        context.getString(R.string.demo_artist),
                        context.getString(R.string.demo_album),
                        -1,
                        seconds * 1000L,
                        "WaveDeck",
                        "",
                        file.length(),
                        System.currentTimeMillis() / 1000,
                        "audio/wav",
                        lyrics = context.getString(R.string.demo_lyrics),
                    )
                )
            )
        }
}
