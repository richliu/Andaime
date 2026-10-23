package org.techarea.andaime.engine

/** What the IME shell should do after the engine has seen a key. */
sealed interface KeyResult {
    /** Not ours: let the editor or system handle the key as usual. */
    data object Ignored : KeyResult

    /** Swallowed; the composition or candidates may have changed. */
    data object Consumed : KeyResult

    data class Commit(val text: String) : KeyResult
}

/** How keys turn into codes and selections; the table data itself lives in a [Dictionary]. */
data class DecodeRules(
    val maxCodeLength: Int,
    /** Keys that pick a candidate on the current page. */
    val selKeys: String,
    /** Space picks the first candidate and [selKeys] start at the second. */
    val spaceSelectsFirst: Boolean,
    /** OXIM style: a selection key that can extend the code is typed as a radical instead. */
    val selKeysOnlyWhenNoMatch: Boolean,
    /** OXIM style: refuse keys that cannot lead to any code. PIME just appends them. */
    val strictPrefix: Boolean,
    /** Commit straight away when a full-length code has exactly one candidate. */
    val autoCommitFullUnique: Boolean,
    /** Space with no candidates clears the composition. */
    val spaceReset: Boolean,
    val pageSize: Int,
    /** Stands for exactly one key in a code, e.g. `a*c`; null turns wildcard lookup off. */
    val wildcard: Char? = null,
) {
    companion object {
        /** Behaviour described by the table's own `%selkey` and `%setting` block (OXIM / gcin). */
        fun fromCin(table: CinTable): DecodeRules {
            val shift = table.flag("SelectKeyShift")
            return DecodeRules(
                maxCodeLength = table.maxCodeLength,
                selKeys = table.selKeys,
                spaceSelectsFirst = shift,
                selKeysOnlyWhenNoMatch = true,
                strictPrefix = true,
                autoCommitFullUnique = table.flag("AutoFullUp"),
                spaceReset = table.flag("SpaceReset"),
                pageSize = table.selKeys.length + if (shift) 1 else 0,
                wildcard = if (table.flag("WildEnable")) WILDCARD else null,
            )
        }

        /**
         * PIME's chedayi module, used for every Dayi table regardless of the
         * table's own `%selkey`: space plus ' [ ] - \ pick six candidates per
         * page, those keys always select while candidates are shown, nothing
         * auto-commits, codes stop at [maxCodeLength], and `*` is a wildcard.
         */
        fun pimeDayi(maxCodeLength: Int) = DecodeRules(
            maxCodeLength = maxCodeLength,
            selKeys = "'[]-\\",
            spaceSelectsFirst = true,
            selKeysOnlyWhenNoMatch = false,
            strictPrefix = false,
            autoCommitFullUnique = false,
            spaceReset = false,
            pageSize = 6,
            wildcard = WILDCARD,
        )

        /** Shift+8, as in PIME. */
        const val WILDCARD = '*'
    }
}

/**
 * 大易符號 table, as in PIME: `=` followed by one key, e.g. `=,` -> ， 、 ‘ ’ “ ”.
 * Shared by every Dayi table and checked before the table's own `=` codes.
 */
class SymbolTable(private val entries: Map<String, List<String>>) {
    operator fun get(key: String): List<String>? = entries[key]

    val size: Int get() = entries.size

    /** Writes the table in the format [read] accepts, with a header explaining it for hand editing. */
    fun write(out: java.io.Writer) {
        out.write("Andaime 大易符號表：先按 = 再按下面每一行開頭的鍵，就會列出那一行的符號。\n")
        out.write("格式：每行「鍵<Tab>符號 符號 符號…」，符號之間用空白分隔；沒有 Tab 的行（像這兩行說明）會被略過。\n")
        for ((key, symbols) in entries) {
            out.write(key)
            out.write("\t")
            out.write(symbols.joinToString(" "))
            out.write("\n")
        }
    }

    companion object {
        const val PREFIX = '='

        /**
         * One entry per line: `key<TAB>sym sym sym`. No comment syntax, because
         * `#` is itself a key.
         */
        fun read(reader: java.io.Reader): SymbolTable = reader.useLines { lines ->
            SymbolTable(
                lines.filter { '\t' in it }.associate { line ->
                    val tab = line.indexOf('\t')
                    line.substring(0, tab) to line.substring(tab + 1).split(' ').filter { it.isNotEmpty() }
                },
            )
        }
    }
}

/** Composition state machine for a table input method. */
class TableEngine(
    private val dictionary: Dictionary,
    private val rules: DecodeRules,
    private val symbols: SymbolTable? = null,
) {

    constructor(table: CinTable) : this(table, DecodeRules.fromCin(table))

    var composition: String = ""
        private set

    /** Every candidate for the current composition; see [pageCandidates] for the visible page. */
    var candidates: List<String> = emptyList()
        private set

    var page: Int = 0
        private set

    val pageSize: Int get() = rules.pageSize

    val pageCount: Int get() = (candidates.size + rules.pageSize - 1) / rules.pageSize

    val pageCandidates: List<String>
        get() = candidates.drop(page * rules.pageSize).take(rules.pageSize)

    /** Labels for the keys that pick each slot on a page, e.g. "空" then ' [ ] - \. */
    val selectionLabels: List<String>
        get() = (if (rules.spaceSelectsFirst) listOf("空") else emptyList()) + rules.selKeys.map { it.toString() }

    val isComposing: Boolean get() = composition.isNotEmpty()

    /** After `=`: the next key is looked up in [symbols], not the table. */
    var isSymbolMode: Boolean = false
        private set

    /** Radicals for the current composition, e.g. "1e" -> "言一"; symbol mode shows "＝" and the key. */
    val compositionRadicals: String
        get() = if (isSymbolMode) {
            "＝" + composition.drop(1)
        } else {
            composition.map { if (it == rules.wildcard) "＊" else dictionary.keyNames[it] ?: it.toString() }.joinToString("")
        }

    fun onKey(key: Char): KeyResult {
        if (isSymbolMode) return onSymbolKey(key)
        if (!isComposing && key == SymbolTable.PREFIX && symbols != null) {
            isSymbolMode = true
            composition = key.toString()
            return KeyResult.Consumed
        }

        val extended = composition + key

        if (isComposing && candidates.isNotEmpty()) {
            val slot = rules.selKeys.indexOf(key)
            if (slot >= 0 && !(rules.selKeysOnlyWhenNoMatch && dictionary.isPrefix(extended))) {
                return selectOnPage(slot + if (rules.spaceSelectsFirst) 1 else 0)
            }
        }

        val wild = isWildcard(extended)
        val known = !wild && dictionary.isPrefix(extended)
        // After a wildcard no prefix check is possible, so any radical key is taken
        val accepted = known || key == rules.wildcard ||
            ((wild || !rules.strictPrefix) && key in dictionary.keyNames)
        if (!accepted) return if (isComposing) KeyResult.Consumed else KeyResult.Ignored
        if (extended.length > rules.maxCodeLength) return KeyResult.Consumed

        composition = extended
        candidates = lookup(extended)
        page = 0

        if (rules.autoCommitFullUnique && !wild && extended.length == rules.maxCodeLength && candidates.size == 1) {
            return select(0)
        }
        return KeyResult.Consumed
    }

    /** `=` then one key: that key's symbols; once they show, selection keys pick as usual. */
    private fun onSymbolKey(key: Char): KeyResult {
        if (candidates.isNotEmpty()) {
            val slot = rules.selKeys.indexOf(key)
            return if (slot >= 0) selectOnPage(slot + if (rules.spaceSelectsFirst) 1 else 0) else KeyResult.Consumed
        }
        if (composition.length > 1) return KeyResult.Consumed
        val found = symbols?.get(key.toString()) ?: return KeyResult.Consumed
        composition += key
        candidates = found
        page = 0
        return KeyResult.Consumed
    }

    fun onSpace(): KeyResult {
        if (!isComposing) return KeyResult.Ignored
        if (candidates.isNotEmpty()) return selectOnPage(0)
        // A lone "=" falls back to the table's own "=" code, as in PIME
        if (isSymbolMode && composition.length == 1) {
            candidates = dictionary.lookup(composition)
            return KeyResult.Consumed
        }
        if (rules.spaceReset) reset()
        return KeyResult.Consumed
    }

    fun onBackspace(): KeyResult {
        if (!isComposing) return KeyResult.Ignored
        composition = composition.dropLast(1)
        candidates = if (isComposing && !isSymbolMode) lookup(composition) else emptyList()
        if (!isComposing) isSymbolMode = false
        page = 0
        return KeyResult.Consumed
    }

    private fun isWildcard(code: String) = rules.wildcard != null && rules.wildcard in code

    private fun lookup(code: String): List<String> = if (isWildcard(code)) wildcardLookup(code) else dictionary.lookup(code)

    /**
     * PIME's wildcard search: each wildcard matches exactly one key, codes are
     * visited in sorted order, and common characters come before rare ones
     * (CJK extensions, compatibility ideographs, private use), at most
     * [MAX_WILDCARD_CANDIDATES] in all.
     */
    private fun wildcardLookup(pattern: String): List<String> {
        val wild = rules.wildcard
        val found = LinkedHashSet<String>()
        dictionary.codes
            .filter { code -> code.length == pattern.length && pattern.indices.all { pattern[it] == wild || pattern[it] == code[it] } }
            .sorted()
            .forEach { found += dictionary.lookup(it) }
        val (rare, common) = found.partition(::isRare)
        return (common + rare).take(MAX_WILDCARD_CANDIDATES)
    }

    fun nextPage(): KeyResult = turnPage(+1)

    fun previousPage(): KeyResult = turnPage(-1)

    private fun turnPage(delta: Int): KeyResult {
        if (!isComposing) return KeyResult.Ignored
        page = (page + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        return KeyResult.Consumed
    }

    /** Picks slot [index] of the current page. */
    fun selectOnPage(index: Int): KeyResult {
        if (index !in 0 until rules.pageSize) return KeyResult.Consumed
        return select(page * rules.pageSize + index)
    }

    /** Picks [index] in the full candidate list (e.g. a tap on the candidate bar). */
    fun select(index: Int): KeyResult {
        val text = candidates.getOrNull(index) ?: return KeyResult.Consumed
        reset()
        return KeyResult.Commit(text)
    }

    fun reset() {
        composition = ""
        candidates = emptyList()
        page = 0
        isSymbolMode = false
    }
}

private const val MAX_WILDCARD_CANDIDATES = 100

/** Characters PIME lists after the rest in wildcard results; a phrase counts by its first character. */
private fun isRare(text: String): Boolean {
    val c = text.codePointAt(0)
    return c in 0x3400..0x4DBF || c >= 0x20000 || c in 0xE000..0xF8FF || c in 0xF900..0xFAFF
}
