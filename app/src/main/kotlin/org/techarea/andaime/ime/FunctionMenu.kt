package org.techarea.andaime.ime

/** One row of the `M menu. */
sealed interface MenuItem {
    val label: String

    class Submenu(override val label: String, val items: () -> List<MenuItem>) : MenuItem

    /** Commits [text]; symbol lists are made of these. */
    class Output(val text: String) : MenuItem {
        override val label: String get() = text
    }

    /** Something the IME shell does, e.g. flip a switch; the menu closes first. */
    class Action(override val label: String, val action: MenuAction) : MenuItem
}

enum class MenuAction { SETTINGS, TOGGLE_SIMPLIFIED, TOGGLE_WILDCARD, TOGGLE_FULL_SHAPE, UNICODE }

sealed interface MenuResult {
    /** The menu used the key; redraw. */
    data object Consumed : MenuResult

    /** The menu isn't open; handle the key as usual. */
    data object Ignored : MenuResult

    data class Commit(val text: String) : MenuResult

    data class Run(val action: MenuAction) : MenuResult
}

/**
 * PIME's 多功能前導字元, for Chinese mode with nothing being composed:
 * `` ` `` then `m` (or three `` ` ``) opens the function menu, `` ` `` then
 * `e` the emoji menu, and `` ` `` then `u` takes a Unicode code point in hex.
 * Menu contents come from [root] and [emoji], rebuilt each time so switch
 * labels show the current state.
 *
 * In the menu 1–9 and 0 pick a row, Space turns the page, Backspace goes
 * back a level, and another `` ` `` (or the caller's Esc, via [cancel]) closes it.
 */
class FunctionMenu(private val root: () -> List<MenuItem>, private val emoji: () -> List<MenuItem>) {
    private enum class Mode { CLOSED, PREFIX, MENU, UNICODE }

    private var mode = Mode.CLOSED
    /** Backticks typed so far in [Mode.PREFIX]. */
    private var backticks = 0
    private var hex = ""
    /** Open levels, innermost last, with the title shown for each. */
    private val levels = ArrayDeque<Pair<String, List<MenuItem>>>()

    var page: Int = 0
        private set

    val isActive: Boolean get() = mode != Mode.CLOSED

    /** What the composition area shows, e.g. "`", "`U4E2D" or "選單 › 箭頭". */
    val composition: String
        get() = when (mode) {
            Mode.CLOSED -> ""
            Mode.PREFIX -> "`".repeat(backticks)
            Mode.UNICODE -> "`U$hex"
            Mode.MENU -> levels.joinToString(" › ") { it.first }
        }

    private val items: List<MenuItem>
        get() = when (mode) {
            Mode.MENU -> levels.last().second
            Mode.UNICODE -> unicodeText()?.let { listOf(MenuItem.Output(it)) }.orEmpty()
            else -> emptyList()
        }

    val pageCount: Int get() = (items.size + PAGE_SIZE - 1) / PAGE_SIZE

    val pageLabels: List<String> get() = items.drop(page * PAGE_SIZE).take(PAGE_SIZE).map { it.label }

    val selectionLabels: List<String> get() = if (mode == Mode.MENU) SEL_KEYS.map { it.toString() } else listOf("空")

    /** Feed every key here first while [isActive]; `` ` `` with nothing composed starts the prefix. */
    fun onKey(key: Char): MenuResult {
        // Compact keyboards send ` from their Esc key
        if (key == '`' && (mode == Mode.MENU || mode == Mode.UNICODE)) {
            cancel()
            return MenuResult.Consumed
        }
        when (mode) {
            Mode.CLOSED -> {
                if (key != '`') return MenuResult.Ignored
                mode = Mode.PREFIX
                backticks = 1
            }
            Mode.PREFIX -> when {
                key == '`' && ++backticks == 3 -> openMenu("選單", root())
                key == '`' -> Unit
                backticks > 1 -> Unit
                key.lowercaseChar() == 'm' -> openMenu("選單", root())
                key.lowercaseChar() == 'e' -> openMenu("表情", emoji())
                key.lowercaseChar() == 'u' -> {
                    mode = Mode.UNICODE
                    hex = ""
                }
                // Other keys do nothing, as in PIME; space types the ` itself
            }
            Mode.UNICODE -> if (key.isHexDigit() && hex.length < MAX_HEX) hex += key.uppercaseChar()
            Mode.MENU -> {
                val slot = SEL_KEYS.indexOf(key)
                if (slot >= 0) return select(slot)
            }
        }
        return MenuResult.Consumed
    }

    fun onSpace(): MenuResult = when (mode) {
        Mode.CLOSED -> MenuResult.Ignored
        Mode.PREFIX -> commit("`".repeat(backticks))
        Mode.UNICODE -> unicodeText()?.let(::commit) ?: MenuResult.Consumed
        Mode.MENU -> {
            page = if (page + 1 < pageCount) page + 1 else 0
            MenuResult.Consumed
        }
    }

    fun onEnter(): MenuResult = if (mode == Mode.MENU) MenuResult.Consumed else onSpace()

    fun onBackspace(): MenuResult {
        when (mode) {
            Mode.CLOSED -> return MenuResult.Ignored
            Mode.PREFIX -> if (--backticks == 0) cancel()
            Mode.UNICODE -> if (hex.isEmpty()) cancel() else hex = hex.dropLast(1)
            Mode.MENU -> {
                levels.removeLast()
                page = 0
                if (levels.isEmpty()) cancel()
            }
        }
        return MenuResult.Consumed
    }

    /** Picks row [indexOnPage] (a digit key or a tap on the candidate bar). */
    fun select(indexOnPage: Int): MenuResult {
        val item = items.getOrNull(page * PAGE_SIZE + indexOnPage) ?: return MenuResult.Consumed
        return when (item) {
            is MenuItem.Output -> commit(item.text)
            is MenuItem.Submenu -> {
                levels.addLast(item.label to item.items())
                page = 0
                MenuResult.Consumed
            }
            is MenuItem.Action -> if (item.action == MenuAction.UNICODE) {
                cancel()
                mode = Mode.UNICODE
                MenuResult.Consumed
            } else {
                cancel()
                MenuResult.Run(item.action)
            }
        }
    }

    fun nextPage(): MenuResult = turnPage(+1)

    fun previousPage(): MenuResult = turnPage(-1)

    private fun turnPage(delta: Int): MenuResult {
        if (!isActive) return MenuResult.Ignored
        page = (page + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        return MenuResult.Consumed
    }

    fun cancel() {
        mode = Mode.CLOSED
        backticks = 0
        hex = ""
        levels.clear()
        page = 0
    }

    private fun openMenu(title: String, items: List<MenuItem>) {
        mode = Mode.MENU
        levels.clear()
        levels.addLast(title to items)
        page = 0
    }

    private fun commit(text: String): MenuResult {
        cancel()
        return MenuResult.Commit(text)
    }

    /** The character for [hex], or null while it isn't a code point that can be typed. */
    private fun unicodeText(): String? {
        val cp = hex.toIntOrNull(16) ?: return null
        if (cp !in 0x20..0x10FFFF || cp in 0xD800..0xDFFF || Character.getType(cp) == Character.UNASSIGNED.toInt()) return null
        return String(Character.toChars(cp))
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    companion object {
        const val PAGE_SIZE = 10
        private const val SEL_KEYS = "1234567890"
        private const val MAX_HEX = 6
    }
}
