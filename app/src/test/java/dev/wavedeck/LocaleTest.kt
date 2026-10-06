package dev.wavedeck

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LocaleTest {
    private fun keys(folder: String): Set<String> {
        val xml =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(File("src/main/res/$folder/strings.xml"))
        val nodes = xml.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
            .toSet()
    }

    @Test
    fun allThreeLocalesHaveCompleteKeyCoverage() {
        val en = keys("values")
        assertTrue(en.size > 150)
        assertEquals(en, keys("values-ru"))
        assertEquals(en, keys("values-kk"))
        assertFalse(File("src/main/res/values-kz").exists())
    }

    @Test
    fun placeholdersMatch() {
        val pattern = Regex("%\\d+\\$[dsf]")
        fun values(folder: String): Map<String, List<String>> {
            val xml =
                DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(File("src/main/res/$folder/strings.xml"))
            val nodes = xml.getElementsByTagName("string")
            return (0 until nodes.length).associate {
                val n = nodes.item(it)
                n.attributes.getNamedItem("name").nodeValue to
                    pattern.findAll(n.textContent).map { it.value }.toList()
            }
        }
        assertEquals(values("values"), values("values-ru"))
        assertEquals(values("values"), values("values-kk"))
    }
}
