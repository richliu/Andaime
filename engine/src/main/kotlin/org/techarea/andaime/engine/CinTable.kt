package org.techarea.andaime.engine

import java.io.Reader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Code -> candidates lookup shared by the shipped tables and the user-edited layer. */
interface Dictionary {
    /** Radical shown for each code key, e.g. '1' -> "言". */
    val keyNames: Map<Char, String>

    fun lookup(code: String): List<String>

    /** True if [code] is a full code or the start of one. */
    fun isPrefix(code: String): Boolean

    /** Every code that produces [text]; used for 字根反查 and the dictionary editor. */
    fun codesOf(text: String): List<String>

    /** Every code with candidates, for wildcard (`*`) matching. */
    val codes: Collection<String>
}

/**
 * A parsed .cin code table (the format used by PIME, gcin and OXIM).
 *
 * Only the sections a table IME needs are kept: names, selection keys,
 * key names (the radicals), `%setting` flags and `%chardef`. Codes are
 * lower-cased: PIME's dayi4.cin and thdayi.cin use upper-case letters.
 */
class CinTable(
    val cname: String,
    val ename: String,
    val selKeys: String,
    override val keyNames: Map<Char, String>,
    private val settings: Map<String, String>,
    private val chardef: Map<String, List<String>>,
) : Dictionary {
    val maxCodeLength: Int = chardef.keys.maxOfOrNull { it.length } ?: 0

    /** Every proper and full prefix of every code, so "can this still become a code?" is O(1). */
    private val prefixes: Set<String> = buildSet {
        for (code in chardef.keys) {
            for (end in 1..code.length) add(code.substring(0, end))
        }
    }

    val size: Int get() = chardef.size

    /** Every code in table order, for listing in the editor. */
    override val codes: Set<String> get() = chardef.keys

    private val reverse: Map<String, List<String>> by lazy {
        buildMap<String, MutableList<String>> {
            for ((code, values) in chardef) for (v in values) getOrPut(v) { mutableListOf() } += code
        }
    }

    override fun lookup(code: String): List<String> = chardef[code].orEmpty()

    override fun isPrefix(code: String): Boolean = code in prefixes

    override fun codesOf(text: String): List<String> = reverse[text].orEmpty()

    /** `%setting` values are "Yes"/"No"; anything missing counts as off. */
    fun flag(name: String): Boolean = settings[name].equals("yes", ignoreCase = true)

    /** Writes this table back out as a UTF-8 .cin that [parse] reads unchanged. */
    fun writeCin(out: java.io.Writer) {
        if (cname.isNotEmpty()) out.write("%cname $cname\n")
        if (ename.isNotEmpty()) out.write("%ename $ename\n")
        if (selKeys.isNotEmpty()) out.write("%selkey $selKeys\n")
        if (settings.isNotEmpty()) {
            out.write("%setting begin\n")
            for ((key, value) in settings) out.write("$key \"$value\"\n")
            out.write("%setting end\n")
        }
        out.write("%keyname begin\n")
        for ((key, name) in keyNames) out.write("$key $name\n")
        out.write("%keyname end\n%chardef begin\n")
        for ((code, values) in chardef) for (value in values) out.write("$code\t$value\n")
        out.write("%chardef end\n")
    }

    companion object {
        fun parse(reader: Reader): CinTable = reader.useLines { parse(it) }

        /** A user-supplied file in whatever encoding it came in; see [decode]. */
        fun parse(bytes: ByteArray): CinTable = parse(decode(bytes).lineSequence())

        /**
         * .cin files are UTF-8 today (PIME, gcin), sometimes with a BOM; older
         * Taiwanese tools wrote Big5, and some Windows tools UTF-16.
         */
        fun decode(bytes: ByteArray): String {
            fun startsWith(vararg prefix: Int) =
                bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }
            return when {
                startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
                startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
                startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
                else -> try {
                    Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString()
                } catch (e: CharacterCodingException) {
                    String(bytes, Charset.forName("Big5"))
                }
            }
        }

        fun parse(lines: Sequence<String>): CinTable {
            var cname = ""
            var ename = ""
            var selKeys = ""
            val keyNames = LinkedHashMap<Char, String>()
            val settings = LinkedHashMap<String, String>()
            val chardef = LinkedHashMap<String, MutableList<String>>()
            var section: String? = null

            for (raw in lines) {
                val line = raw.trimEnd('\r')
                if (line.isBlank() || line.startsWith("#")) continue

                if (line.startsWith("%")) {
                    val (directive, value) = splitPair(line.substring(1))
                    when {
                        value == "begin" -> { section = directive; continue }
                        value == "end" -> { section = null; continue }
                        section == null -> when (directive) {
                            "cname" -> cname = value
                            "ename" -> ename = value
                            "selkey" -> selKeys = value
                        }
                    }
                    if (section == null) continue
                }

                val (key, value) = splitPair(line.trimStart(' ', '\t'))
                when (section) {
                    "keyname" -> if (key.length == 1) keyNames[key[0].lowercaseChar()] = value
                    "setting" -> settings[key] = value.trim('"')
                    "chardef" -> {
                        if (key.isEmpty() || value.isEmpty()) continue
                        val list = chardef.getOrPut(key.lowercase()) { mutableListOf() }
                        // Tables carry duplicate rows (e.g. 11x 謏 twice); keep the first position
                        if (value !in list) list += value
                    }
                }
            }

            return CinTable(cname, ename, selKeys, keyNames, settings, chardef)
        }

        /**
         * Splits "key<whitespace>value"; the value keeps inner spaces.
         * Only ASCII space/tab are separators: Dayi maps `=` to U+3000, which trim() would eat.
         */
        private fun splitPair(line: String): Pair<String, String> {
            val i = line.indexOfFirst { it == ' ' || it == '\t' }
            if (i < 0) return line to ""
            return line.substring(0, i) to line.substring(i + 1).trim(' ', '\t')
        }
    }
}
