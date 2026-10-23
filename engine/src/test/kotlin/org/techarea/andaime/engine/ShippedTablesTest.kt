package org.techarea.andaime.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sanity checks against the real PIME tables in third_party/pime. */
class ShippedTablesTest {
    private fun load(name: String): CinTable {
        val dir = System.getProperty("andaime.tables") ?: error("andaime.tables not set")
        return File(dir, name).reader(Charsets.UTF_8).let(CinTable::parse)
    }

    @Test
    fun `dayi3 loads whole`() {
        val table = load("dayi3.cin")
        assertEquals("大易三碼", table.cname)
        assertEquals(3, table.maxCodeLength)
        assertEquals(47, table.keyNames.size)
        assertTrue(table.size > 11_000, "only ${table.size} distinct codes")
    }

    @Test
    fun `dayi3 common characters with its own cin rules`() {
        val engine = TableEngine(load("dayi3.cin"))
        // "./" is 的 in Dayi: 點 + 竹
        engine.onKey('.'); engine.onKey('/')
        assertEquals("的", engine.candidates.first())
        assertEquals(KeyResult.Commit("的"), engine.onSpace())
    }

    @Test
    fun `dayi4 loads with lower-cased codes`() {
        val table = load("dayi4.cin")
        assertEquals(4, table.maxCodeLength)
        assertEquals("人", table.keyNames['a'])
        assertTrue(table.size > 16_000, "only ${table.size} codes")
        assertTrue(table.lookup("/db.").contains("的"), "/db. should give 的")
        assertEquals("耳", table.lookup("p").first())
    }
}
