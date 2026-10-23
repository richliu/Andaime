package org.techarea.andaime.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class PimeDayiRulesTest {
    private val table = CinTable.parse(
        """
        %keyname begin
        A 人
        B 馬
        1 言
        6 門
        %keyname end
        %chardef begin
        A 甲
        A 乙
        A 丙
        A 丁
        A 戊
        A 己
        A 庚
        A 辛
        A6 人六
        AB 人馬
        ABB1 四碼
        %chardef end
        """.trimIndent().lineSequence(),
    )

    private val engine = TableEngine(table, DecodeRules.pimeDayi(maxCodeLength = 4))

    @Test
    fun `upper-case codes and keynames are normalised`() {
        assertEquals("人", table.keyNames['a'])
        assertEquals(8, table.lookup("a").size)
    }

    @Test
    fun `space then quote bracket keys pick the six slots of a page`() {
        engine.onKey('a')
        assertEquals(listOf("甲", "乙", "丙", "丁", "戊", "己"), engine.pageCandidates)
        assertEquals(KeyResult.Commit("丙"), engine.onKey('['))
        engine.onKey('a')
        assertEquals(KeyResult.Commit("己"), engine.onKey('\\'))
        engine.onKey('a')
        assertEquals(KeyResult.Commit("甲"), engine.onSpace())
    }

    @Test
    fun `selection keys address the current page`() {
        engine.onKey('a')
        assertEquals(2, engine.pageCount)
        engine.nextPage()
        assertEquals(listOf("庚", "辛"), engine.pageCandidates)
        assertEquals(KeyResult.Commit("辛"), engine.onKey('\''))
    }

    @Test
    fun `digits are radicals even when they could not select`() {
        engine.onKey('a')
        assertEquals(KeyResult.Consumed, engine.onKey('6'))
        assertEquals("a6", engine.composition)
    }

    @Test
    fun `full code does not auto-commit and longer input is dropped`() {
        "abb1".forEach { engine.onKey(it) }
        assertEquals(listOf("四碼"), engine.candidates)
        assertEquals(KeyResult.Consumed, engine.onKey('a'))
        assertEquals("abb1", engine.composition)
    }

    @Test
    fun `radicals with no matching code are still shown, like PIME`() {
        engine.onKey('1')
        assertEquals("1", engine.composition)
        assertEquals(emptyList(), engine.candidates)
        assertEquals("言", engine.compositionRadicals)
        assertEquals(KeyResult.Consumed, engine.onSpace())
        assertEquals("1", engine.composition)
    }

    @Test
    fun `selection labels`() {
        assertEquals(listOf("空", "'", "[", "]", "-", "\\"), engine.selectionLabels)
    }
}
