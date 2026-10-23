package org.techarea.andaime.engine

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals

class CinDecodeTest {
    private val cin = "%cname 測試\n%chardef begin\na\t人\n%chardef end\n"

    private fun check(bytes: ByteArray) {
        val table = CinTable.parse(bytes)
        assertEquals("測試", table.cname)
        assertEquals(listOf("人"), table.lookup("a"))
    }

    @Test
    fun utf8() = check(cin.toByteArray(Charsets.UTF_8))

    @Test
    fun `utf8 with BOM`() = check(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + cin.toByteArray(Charsets.UTF_8))

    @Test
    fun `utf16 little endian with BOM`() = check(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + cin.toByteArray(Charsets.UTF_16LE))

    @Test
    fun big5() = check(cin.toByteArray(Charset.forName("Big5")))
}

class CinWriteTest {
    @Test
    fun `written table parses back identically`() {
        val dir = System.getProperty("andaime.tables") ?: error("andaime.tables not set")
        val original = java.io.File(dir, "dayi3.cin").readBytes().let(CinTable::parse)
        val text = java.io.StringWriter().also(original::writeCin).toString()
        val copy = CinTable.parse(text.lineSequence())
        assertEquals(original.cname, copy.cname)
        assertEquals(original.selKeys, copy.selKeys)
        assertEquals(original.keyNames, copy.keyNames)
        assertEquals(original.codes, copy.codes)
        for (code in original.codes) assertEquals(original.lookup(code), copy.lookup(code), code)
        assertEquals(original.flag("SelectKeyShift"), copy.flag("SelectKeyShift"))
    }
}
