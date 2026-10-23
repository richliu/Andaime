package org.techarea.andaime.engine

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableEngineTest {
    private val cin = """
        %cname 測試大易
        %selkey '[]-\6789
        %setting begin
                AutoFullUp "Yes"
                SelectKeyShift "Yes"
                SpaceReset "Yes"
        %setting end
        %keyname begin
        1 言
        e 一
        o 口
        = ${"\u3000"}
        %keyname end
        %chardef begin
        1	言
        1	丨
        1	訁
        1e	誼
        1eo	話
        1eo	語
        1e1	評
        11	評
        16	誧
        11x	謏
        11x	謏
        %chardef end
    """.trimIndent()

    private lateinit var table: CinTable
    private lateinit var engine: TableEngine

    @BeforeTest
    fun setUp() {
        table = CinTable.parse(cin.lineSequence())
        engine = TableEngine(table)
    }

    @Test
    fun `parses header, settings and keynames`() {
        assertEquals("測試大易", table.cname)
        assertEquals("'[]-\\6789", table.selKeys)
        assertEquals("言", table.keyNames['1'])
        assertEquals("　", table.keyNames['='])
        assertTrue(table.flag("SelectKeyShift"))
        assertEquals(3, table.maxCodeLength)
    }

    @Test
    fun `duplicate chardef rows collapse to one candidate`() {
        assertEquals(listOf("謏"), table.lookup("11x"))
    }

    @Test
    fun `typing builds composition and candidates`() {
        assertEquals(KeyResult.Consumed, engine.onKey('1'))
        assertEquals(listOf("言", "丨", "訁"), engine.candidates)
        assertEquals(KeyResult.Consumed, engine.onKey('e'))
        assertEquals("1e", engine.composition)
        assertEquals("言一", engine.compositionRadicals)
        assertEquals(listOf("誼"), engine.candidates)
    }

    @Test
    fun `space commits the first candidate`() {
        engine.onKey('1')
        assertEquals(KeyResult.Commit("言"), engine.onSpace())
        assertEquals("", engine.composition)
    }

    @Test
    fun `selkey picks the second candidate onwards when it cannot extend the code`() {
        engine.onKey('1')
        assertEquals(KeyResult.Commit("丨"), engine.onKey('\''))
        engine.onKey('1')
        assertEquals(KeyResult.Commit("訁"), engine.onKey('['))
    }

    @Test
    fun `a selkey that extends the code is treated as a radical`() {
        // '6' is a selection key, but "16" is a code, so it continues the composition
        engine.onKey('1')
        assertEquals(KeyResult.Consumed, engine.onKey('6'))
        assertEquals("16", engine.composition)
        assertEquals(listOf("誧"), engine.candidates)
    }

    @Test
    fun `full code with a single candidate commits automatically`() {
        engine.onKey('1')
        engine.onKey('1')
        assertEquals(KeyResult.Commit("謏"), engine.onKey('x'))
        assertEquals("", engine.composition)
    }

    @Test
    fun `full code with several candidates waits for selection`() {
        engine.onKey('1'); engine.onKey('e')
        assertEquals(KeyResult.Consumed, engine.onKey('o'))
        assertEquals(listOf("話", "語"), engine.candidates)
        assertEquals(KeyResult.Commit("語"), engine.onKey('\''))
    }

    @Test
    fun `keys that cannot start a code are ignored, and swallowed mid-composition`() {
        assertEquals(KeyResult.Ignored, engine.onKey('q'))
        engine.onKey('1')
        assertEquals(KeyResult.Consumed, engine.onKey('q'))
        assertEquals("1", engine.composition)
    }

    @Test
    fun `backspace edits the composition and is ignored when empty`() {
        engine.onKey('1'); engine.onKey('e')
        assertEquals(KeyResult.Consumed, engine.onBackspace())
        assertEquals(listOf("言", "丨", "訁"), engine.candidates)
        engine.onBackspace()
        assertEquals(KeyResult.Ignored, engine.onBackspace())
    }

    @Test
    fun `space with no candidates resets when SpaceReset is on`() {
        val t = CinTable.parse(sequenceOf("%setting begin", "SpaceReset \"Yes\"", "%setting end",
            "%chardef begin", "ab\t字", "%chardef end"))
        val e = TableEngine(t)
        e.onKey('a')
        assertEquals(KeyResult.Consumed, e.onSpace())
        assertEquals("", e.composition)
    }
}
