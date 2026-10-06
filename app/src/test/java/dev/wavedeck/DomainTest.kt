package dev.wavedeck

import dev.wavedeck.data.*
import dev.wavedeck.model.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private fun track(
        id: Long,
        title: String = "Track",
        artist: String = "Artist",
        duration: Long = 1000,
    ) =
        Track(
            id,
            "content://media/external/audio/media/$id",
            title,
            artist,
            "Album",
            1,
            duration,
            "Music/",
            "",
            500,
            0,
            "audio/wav",
        )

    @Test
    fun multipleTimestampsAndFractionWidths() {
        val lines = Lrc.parse("[00:02.5][01:03.025]Hello\n[00:01.12]First\n[ar:Demo]")
        assertEquals(listOf(1120L, 2500L, 63025L), lines.map { it.at })
        assertEquals("Hello", lines[1].text)
    }

    @Test
    fun offsetsAreConsistentWithSeeking() {
        val lines = Lrc.parse("[offset:-500]\n[00:01.00]First\n[00:02.00]Second")
        assertEquals(500L, lines.first().at)
        assertEquals(-1, Lrc.active(lines, 400, 0))
        assertEquals(0, Lrc.active(lines, 1000, 500))
        assertEquals(1, Lrc.active(lines, 2000, 500))
    }

    @Test
    fun plainLyricsDoNotBecomeTimed() {
        assertTrue(Lrc.parse("Plain words\n[bad]format").isEmpty())
    }

    @Test
    fun queueReorderAndBounds() {
        assertEquals(listOf(2L, 3L, 1L), QueueRules.move(listOf(1, 2, 3), 0, 2))
        assertEquals(listOf(1L, 2L), QueueRules.move(listOf(1, 2), -1, 0))
    }

    @Test
    fun repeatSemantics() {
        assertNull(QueueRules.next(2, 3, 0))
        assertEquals(2, QueueRules.next(2, 3, 1))
        assertEquals(0, QueueRules.next(2, 3, 2))
        assertNull(QueueRules.next(0, 0, 2))
    }

    @Test
    fun multiTermSearchAcrossMetadata() {
        val tracks = listOf(track(1, "Northern lights", "Delta"), track(2, "Lights", "Other"))
        assertEquals(
            listOf(1L),
            LibraryRules.filter(tracks, "delta lights", "title", Locale.ENGLISH).map { it.id },
        )
    }

    @Test
    fun durationSort() {
        assertEquals(
            listOf(2L, 1L),
            LibraryRules.filter(
                    listOf(track(1, duration = 50), track(2, duration = 100)),
                    "",
                    "duration",
                )
                .map { it.id },
        )
    }

    @Test
    fun duplicateDetectionIsNondestructive() {
        val list = listOf(track(1), track(2), track(3, "Other"))
        assertEquals(listOf(1L, 2L), LibraryRules.duplicates(list).map { it.id })
        assertEquals(3, list.size)
    }

    @Test
    fun playlistRoundTripAndUnknownEntries() {
        val tracks = listOf(track(1), track(2))
        assertEquals(
            listOf(1L, 2L),
            M3u.resolve(M3u.export(tracks) + "content://media/external/audio/media/99", tracks),
        )
    }

    @Test
    fun ambiguousPlaylistNamesSkipped() {
        assertTrue(M3u.resolve("Track.wav", listOf(track(1), track(2))).isEmpty())
    }
}
