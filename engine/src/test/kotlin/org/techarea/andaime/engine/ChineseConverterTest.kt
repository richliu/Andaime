package org.techarea.andaime.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ChineseConverterTest {
    private val converter = ChineseConverter(
        phrases = mapOf("乾淨" to "干净", "乾隆" to "乾隆"),
        characters = mapOf("乾" to "干", "淨" to "净", "後" to "后", "𨋢" to "𫐌"),
    )

    @Test
    fun `phrases beat single characters`() {
        assertEquals("干净", converter.convert("乾淨"))
        assertEquals("乾隆", converter.convert("乾隆"))
        assertEquals("干", converter.convert("乾"))
    }

    @Test
    fun `unknown text and supplementary characters pass through`() {
        assertEquals("后来ABC，", converter.convert("後来ABC，"))
        assertEquals("𫐌", converter.convert("𨋢"))
    }

    @Test
    fun `reads the bundled OpenCC tables`() {
        val dir = File(System.getProperty("andaime.opencc"))
        val real = ChineseConverter(
            ChineseConverter.readDictionary(File(dir, "TSPhrases.txt").reader()),
            ChineseConverter.readDictionary(File(dir, "TSCharacters.txt").reader()),
        )
        assertEquals("大易输入法，头发后面", real.convert("大易輸入法，頭髮後面"))
        assertEquals("乾隆", real.convert("乾隆"))
    }
}
