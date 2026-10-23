package org.techarea.andaime.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SymbolModeTest {
    private val symbols = SymbolTable.read(
        "，\n,\t， ‘ ’ “ ”\n.\t。 、 ．\n[\t「 『 （ 〔 【\n?\t？ α β\n#\t＃ △\n".reader(),
    )
    private val table = CinTable.parse(
        sequenceOf(
            "%keyname begin", "a 人", "= 符", "%keyname end",
            "%chardef begin", "a\t甲", "=\t。", "=\t，", "=,\t舊", "%chardef end",
        ),
    )
    private val engine = TableEngine(table, DecodeRules.pimeDayi(4), symbols)

    @Test
    fun `reader skips lines without a tab and keeps hash as a key`() {
        assertEquals(5, symbols.size)
        assertEquals(listOf("＃", "△"), symbols["#"])
    }

    @Test
    fun `equals then a key shows that key's symbols`() {
        assertEquals(KeyResult.Consumed, engine.onKey('='))
        assertTrue(engine.isSymbolMode)
        assertEquals("＝", engine.compositionRadicals)
        engine.onKey(',')
        assertEquals("＝,", engine.compositionRadicals)
        // The shared symbol table wins over the table's own "=," code
        assertEquals(listOf("，", "‘", "’", "“", "”"), engine.candidates)
        assertEquals(KeyResult.Commit("，"), engine.onSpace())
        assertFalse(engine.isSymbolMode)
    }

    @Test
    fun `selection keys pick symbols, including keys that are symbol codes themselves`() {
        engine.onKey('='); engine.onKey('[')
        assertEquals(KeyResult.Commit("『"), engine.onKey('\''))
        engine.onKey('='); engine.onKey('[')
        assertEquals(KeyResult.Commit("（"), engine.onKey('['))
    }

    @Test
    fun `shifted keys work as symbol codes`() {
        engine.onKey('='); engine.onKey('?')
        assertEquals(KeyResult.Commit("α"), engine.onKey('\''))
    }

    @Test
    fun `lone equals then space falls back to the table's own equals code`() {
        engine.onKey('=')
        assertEquals(KeyResult.Consumed, engine.onSpace())
        assertEquals(listOf("。", "，"), engine.candidates)
        assertEquals(KeyResult.Commit("，"), engine.onKey('\''))
    }

    @Test
    fun `unknown keys are swallowed and backspace leaves symbol mode`() {
        engine.onKey('=')
        assertEquals(KeyResult.Consumed, engine.onKey('a'))
        assertEquals("=", engine.composition)
        engine.onKey('.')
        engine.onBackspace()
        assertEquals("=", engine.composition)
        assertTrue(engine.isSymbolMode)
        engine.onBackspace()
        assertFalse(engine.isSymbolMode)
        // Back to normal decoding
        engine.onKey('a')
        assertEquals(listOf("甲"), engine.candidates)
    }

    @Test
    fun `equals mid-composition is not a symbol prefix`() {
        engine.onKey('a')
        engine.onKey('=')
        assertFalse(engine.isSymbolMode)
    }

    @Test
    fun `without a symbol table equals decodes through the table`() {
        val plain = TableEngine(table, DecodeRules.pimeDayi(4))
        plain.onKey('=')
        assertFalse(plain.isSymbolMode)
        assertEquals(listOf("。", "，"), plain.candidates)
    }

    @Test
    fun `shipped symbol table`() {
        val path = System.getProperty("andaime.symbols") ?: error("andaime.symbols not set")
        val shipped = File(path).reader(Charsets.UTF_8).let(SymbolTable::read)
        assertEquals(42, shipped.size)
        // The first symbol is the full-width form of the key, as Dayi users expect
        assertEquals("。", shipped["."]?.first())
        assertEquals("，", shipped[","]?.first())
        assertEquals("「", shipped["["]?.first())
        assertEquals("？", shipped["?"]?.first())
        assertEquals(listOf("‵", "ˊ", "ˇ", "ˋ", "˙", "｀"), shipped["`"])
        for (d in '0'..'9') assertEquals(('０' + (d - '0')).toString(), shipped[d.toString()]?.first())
    }
}

class SymbolTableWriteTest {
    @Test
    fun `written table reads back identically, header lines ignored`() {
        val path = System.getProperty("andaime.symbols") ?: error("andaime.symbols not set")
        val original = File(path).reader(Charsets.UTF_8).let(SymbolTable::read)
        val text = java.io.StringWriter().also(original::write).toString()
        val copy = SymbolTable.read(text.reader())
        assertEquals(original.size, copy.size)
        for (line in File(path).readLines()) {
            val key = line.substringBefore('\t')
            assertEquals(original[key], copy[key], key)
        }
    }
}
