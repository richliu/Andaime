package org.techarea.andaime.engine

import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserDictionaryTest {
    private val table = CinTable.parse(
        sequenceOf("%chardef begin", "p\t身", "p\t耳", "ab\t字", "abc\t學", "%chardef end"),
    )

    @Test
    fun `user list replaces the shipped list for that code only`() {
        val user = UserDictionary()
        val dict = LayeredDictionary(table, user)
        user.set("p", listOf("耳", "身", "聽"))
        assertEquals(listOf("耳", "身", "聽"), dict.lookup("p"))
        assertEquals(listOf("字"), dict.lookup("ab"))
        user.reset("p")
        assertEquals(listOf("身", "耳"), dict.lookup("p"))
    }

    @Test
    fun `new codes become typeable and emptied codes disappear`() {
        val user = UserDictionary()
        val dict = LayeredDictionary(table, user)
        user.set("zz", listOf("新"))
        assertTrue(dict.isPrefix("z"))
        user.set("ab", emptyList())
        assertEquals(emptyList(), dict.lookup("ab"))
        // Longer shipped codes behind an emptied one stay reachable
        assertTrue(dict.isPrefix("ab"))
        assertFalse(dict.isPrefix("q"))
    }

    @Test
    fun `reverse lookup follows the edits`() {
        val user = UserDictionary()
        val dict = LayeredDictionary(table, user)
        assertEquals(listOf("p"), dict.codesOf("耳"))
        user.set("p", listOf("身"))
        user.set("e", listOf("耳"))
        assertEquals(listOf("e"), dict.codesOf("耳"))
    }

    @Test
    fun `round-trips through its text file`() {
        val user = UserDictionary()
        user.set("p", listOf("耳", "身"))
        user.set("ab", emptyList())
        val text = StringWriter().also(user::write).toString()
        val back = UserDictionary.read(text.reader())
        assertEquals(user.entries, back.entries)
    }
}

class UserDictionaryFileTest {
    @Test
    fun `hand-edited file - comments, stray lines and removed codes`() {
        val text = """
            # comment
            這行沒有 Tab，略過
            P	耳 身 聽
            ab	
            abc	學 ${"　"}
        """.trimIndent()
        val user = UserDictionary.read(text.reader())
        assertEquals(setOf("p", "ab", "abc"), user.entries.keys)
        assertEquals(listOf("耳", "身", "聽"), user["p"])
        assertEquals(emptyList(), user["ab"])
    }

    @Test
    fun `import replaces every existing edit`() {
        val table = CinTable.parse(sequenceOf("%chardef begin", "p\t身", "p\t耳", "q\t石", "%chardef end"))
        val user = UserDictionary()
        val dict = LayeredDictionary(table, user)
        user.set("q", listOf("右"))
        user.replaceAll(UserDictionary(mapOf("p" to listOf("耳", "身"))))
        assertEquals(listOf("耳", "身"), dict.lookup("p"))
        assertEquals(listOf("石"), dict.lookup("q"))
        user.clear()
        assertEquals(listOf("身", "耳"), dict.lookup("p"))
    }

    @Test
    fun `export header lines are skipped when read back`() {
        val user = UserDictionary(mapOf("p" to listOf("耳"), "ab" to emptyList()))
        val text = StringWriter().also(user::write).toString()
        assertTrue(text.startsWith("# Andaime 詞庫修改"))
        assertEquals(user.entries, UserDictionary.read(text.reader()).entries)
    }
}
