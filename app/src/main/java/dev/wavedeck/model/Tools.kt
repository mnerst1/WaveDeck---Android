package dev.wavedeck.model

import dev.wavedeck.data.Track
import java.text.Collator
import java.util.Locale

data class LyricLine(val at: Long, val text: String)

object Lrc {
    private val stamp = Regex("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(text: String): List<LyricLine> {
        val global =
            Regex("\\[offset:([+-]?\\d+)]").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return text
            .lines()
            .flatMap { line ->
                val matches = stamp.findAll(line).toList()
                val words = line.replace(stamp, "").trim()
                matches.map { m ->
                    val fraction = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    LyricLine(
                        (m.groupValues[1].toLong() * 60000 +
                                m.groupValues[2].toLong() * 1000 +
                                fraction +
                                global)
                            .coerceAtLeast(0),
                        words,
                    )
                }
            }
            .sortedBy { it.at }
    }

    fun active(lines: List<LyricLine>, position: Long, offset: Long): Int = lines.indexOfLast {
        it.at <= position - offset
    }
}

object LibraryRules {
    fun filter(
        tracks: List<Track>,
        query: String,
        sort: String,
        locale: Locale = Locale.getDefault(),
    ): List<Track> {
        val terms = query.trim().lowercase(locale).split(Regex("\\s+")).filter { it.isNotBlank() }
        val found = tracks.filter { t ->
            terms.all {
                "${t.title} ${t.artist} ${t.album} ${t.folder}".lowercase(locale).contains(it)
            }
        }
        val collator = Collator.getInstance(locale)
        return when (sort) {
            "artist" -> found.sortedWith { a, b -> collator.compare(a.artist, b.artist) }
            "duration" -> found.sortedByDescending { it.duration }
            "recent" -> found.sortedByDescending { it.modified }
            else -> found.sortedWith { a, b -> collator.compare(a.title, b.title) }
        }
    }

    fun duplicates(tracks: List<Track>) =
        tracks
            .groupBy { Triple(it.title.lowercase(), it.artist.lowercase(), it.size) }
            .values
            .filter { it.size > 1 }
            .flatten()
}

object QueueRules {
    fun move(ids: List<Long>, from: Int, to: Int): List<Long> =
        ids.toMutableList().apply { if (from in indices && to in indices) add(to, removeAt(from)) }

    fun next(index: Int, size: Int, repeat: Int): Int? =
        when {
            size == 0 -> null
            repeat == 1 -> index
            index + 1 < size -> index + 1
            repeat == 2 -> 0
            else -> null
        }
}

object M3u {
    fun export(tracks: List<Track>) =
        "#EXTM3U\n" +
            tracks.joinToString("\n") {
                "#EXTINF:${it.duration/1000},${it.artist.replace('\n',' ')} - ${it.title.replace('\n',' ')}\n${if(it.fileName.isNotBlank()) it.folder.trimEnd('/')+"/"+it.fileName else it.uri}"
            } +
            "\n"

    fun resolve(text: String, tracks: List<Track>): List<Long> =
        text
            .removePrefix("\uFEFF")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .mapNotNull { line ->
                tracks
                    .firstOrNull {
                        it.uri == line ||
                            (it.fileName.isNotBlank() &&
                                (it.folder.trimEnd('/') + "/" + it.fileName).equals(line, true))
                    }
                    ?.id
                    ?: tracks
                        .filter {
                            (it.fileName.ifBlank { it.title })
                                .substringBeforeLast('.')
                                .equals(line.substringAfterLast('/').substringBeforeLast('.'), true)
                        }
                        .singleOrNull()
                        ?.id
            }
            .distinct()
}
