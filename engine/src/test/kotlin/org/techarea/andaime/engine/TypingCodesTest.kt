package org.techarea.andaime.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class TypingCodesTest {
    private val table = CinTable.parse(
        """
        %chardef begin
        abc	大
        ab	天
        ab	大
        x	大
        xy	夫
        %chardef end
        """.trimIndent().lineSequence(),
    )

    @Test
    fun `shortest code first, then earliest candidate`() {
        assertEquals(
            listOf(TypingCode("x", 0), TypingCode("ab", 1), TypingCode("abc", 0)),
            table.typingCodes("大"),
        )
        assertEquals(emptyList(), table.typingCodes("小"))
    }

    @Test
    fun `user edits move or remove codes`() {
        val user = UserDictionary(mapOf("ab" to listOf("大", "天"), "x" to emptyList()))
        assertEquals(
            listOf(TypingCode("ab", 0), TypingCode("abc", 0)),
            LayeredDictionary(table, user).typingCodes("大"),
        )
    }

    @Test
    fun `splits into whole characters without spaces`() {
        assertEquals(listOf("大", "𠮷", "a"), splitCharacters("大 𠮷\na"))
    }
}
