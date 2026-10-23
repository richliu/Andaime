package org.techarea.andaime.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WildcardTest {
    private val table = CinTable.parse(
        """
        %keyname begin
        a 人
        b 牛
        c 金
        %keyname end
        %chardef begin
        ab 甲
        ac 乙
        ab 丙
        cb 丁
        abc 戊
        bb 㐀
        aa 乙
        %chardef end
        """.trimIndent().lineSequence(),
    )

    private fun engine(rules: DecodeRules = DecodeRules.pimeDayi(3)) = TableEngine(table, rules)

    @Test
    fun `wildcard matches one key and keeps code length`() {
        val e = engine()
        e.onKey('a')
        e.onKey('*')
        // aa, ab, ac in code order, duplicates dropped; abc is too long
        assertEquals(listOf("乙", "甲", "丙"), e.candidates)
        assertEquals("人＊", e.compositionRadicals)
    }

    @Test
    fun `rare characters come last`() {
        val e = engine()
        e.onKey('*')
        e.onKey('b')
        assertEquals(listOf("甲", "丙", "丁", "㐀"), e.candidates)
    }

    @Test
    fun `keys after a wildcard are accepted even with strict prefixes`() {
        val rules = DecodeRules.fromCin(table).copy(wildcard = '*')
        val e = engine(rules)
        e.onKey('*')
        e.onKey('b')
        e.onKey('c')
        assertEquals("*bc", e.composition)
        assertEquals(listOf("戊"), e.candidates)
    }

    @Test
    fun `backspace recomputes wildcard candidates`() {
        val e = engine()
        e.onKey('*')
        e.onKey('b')
        e.onKey('c')
        e.onBackspace()
        assertEquals(4, e.candidates.size)
        assertIs<KeyResult.Commit>(e.onSpace())
    }

    @Test
    fun `disabled wildcard types nothing`() {
        val e = engine(DecodeRules.pimeDayi(3).copy(wildcard = null))
        assertEquals(KeyResult.Ignored, e.onKey('*'))
    }

    @Test
    fun `user edits take part in wildcard lookup`() {
        val user = UserDictionary(mapOf("ab" to listOf("己"), "cc" to listOf("庚")))
        val e = TableEngine(LayeredDictionary(table, user), DecodeRules.pimeDayi(3))
        e.onKey('*')
        e.onKey('b')
        assertEquals(listOf("己", "丁", "㐀"), e.candidates)
        e.onBackspace()
        e.onKey('c')
        assertEquals(listOf("乙", "庚"), e.candidates)
    }

    @Test
    fun `full shape follows PIME`() {
        assertEquals("ＡＢｃ１２！", FullShape.convert("ABc12!"))
        assertEquals("　、、。。，－～", FullShape.convert(" \"'.><_~"))
        assertEquals("中", FullShape.convert("中"))
    }
}
