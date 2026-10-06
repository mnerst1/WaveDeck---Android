package dev.wavedeck.ui

import java.io.Reader

/** Reject oversized document input instead of allocating an unbounded string. */
internal fun readLimited(reader: Reader, maximum: Int): String {
    val result = StringBuilder()
    val buffer = CharArray(4096)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) return result.toString()
        require(result.length + count <= maximum) { "Document exceeds import limit" }
        result.append(buffer, 0, count)
    }
}
