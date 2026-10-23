package org.techarea.andaime.engine

import java.io.Reader
import java.io.Writer

/**
 * The user's edits to one table. Each touched code stores its whole
 * candidate list, which replaces the shipped list; an empty list hides the
 * code. Untouched codes fall through to the table.
 *
 * File format, one code per line; `#` lines are comments and lines without
 * a tab are ignored, so hand-edited files can't hide codes by accident:
 * ```
 * code<TAB>字 字 字
 * code<TAB>          (nothing after the tab: the code is removed)
 * ```
 */
class UserDictionary(overrides: Map<String, List<String>> = emptyMap()) {
    private val overrides = LinkedHashMap(overrides)
    private var prefixes: Set<String> = computePrefixes()

    val entries: Map<String, List<String>> get() = overrides

    operator fun get(code: String): List<String>? = overrides[code]

    fun set(code: String, candidates: List<String>) {
        overrides[code] = candidates.filter { it.isNotBlank() }.distinct()
        prefixes = computePrefixes()
    }

    /** Drops the user's version so the shipped list applies again. */
    fun reset(code: String) {
        overrides.remove(code)
        prefixes = computePrefixes()
    }

    /** Takes [other]'s edits in place of all of this one's (an import). */
    fun replaceAll(other: UserDictionary) {
        overrides.clear()
        overrides.putAll(other.overrides)
        prefixes = computePrefixes()
    }

    fun clear() = replaceAll(UserDictionary())

    fun isPrefix(code: String): Boolean = code in prefixes

    private fun computePrefixes(): Set<String> = buildSet {
        for ((code, values) in overrides) {
            if (values.isEmpty()) continue
            for (end in 1..code.length) add(code.substring(0, end))
        }
    }

    fun write(writer: Writer) {
        writer.write("# Andaime 詞庫修改：每行「字碼<Tab>字 字 字…」，取代碼表裡這個字碼的整串候選字，順序就是選字順序。\n")
        writer.write("# Tab 後面什麼都不寫，代表刪除這個字碼。以 # 開頭或沒有 Tab 的行會被略過。\n")
        for ((code, values) in overrides) {
            writer.write(code)
            writer.write("\t")
            writer.write(values.joinToString(" "))
            writer.write("\n")
        }
    }

    companion object {
        fun read(reader: Reader): UserDictionary = reader.useLines { lines ->
            val map = LinkedHashMap<String, List<String>>()
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                if (tab < 0) continue
                val code = line.substring(0, tab).trim().lowercase()
                if (code.isEmpty()) continue
                val values = line.substring(tab + 1).split(' ').filter { it.isNotBlank() }
                map[code] = values
            }
            UserDictionary(map)
        }
    }
}

/** A shipped table with the user's edits on top. */
class LayeredDictionary(
    val base: Dictionary,
    val user: UserDictionary,
) : Dictionary {
    override val keyNames: Map<Char, String> get() = base.keyNames

    override fun lookup(code: String): List<String> = user[code] ?: base.lookup(code)

    // A code the user emptied still counts as a prefix if longer shipped codes start with it
    override fun isPrefix(code: String): Boolean = user.isPrefix(code) || base.isPrefix(code)

    // Emptied codes stay in the set; looking them up just gives nothing
    override val codes: Collection<String> get() = base.codes + user.entries.keys

    override fun codesOf(text: String): List<String> {
        val fromBase = base.codesOf(text).filter { code -> user[code]?.contains(text) ?: true }
        val fromUser = user.entries.filter { (_, values) -> text in values }.keys
        return (fromBase + fromUser).distinct()
    }
}
