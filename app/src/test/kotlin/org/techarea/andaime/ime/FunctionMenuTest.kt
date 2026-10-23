package org.techarea.andaime.ime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FunctionMenuTest {
    private var wildcard = true
    private var simplified = false
    private val menu = FunctionMenu(
        root = { menuRoot(simplified, { wildcard }, { false }, { true }) },
        emoji = { emojiMenu { it != "😀" } },
    )

    @Test
    fun `other keys pass through while closed`() {
        assertEquals(MenuResult.Ignored, menu.onKey('a'))
        assertEquals(MenuResult.Ignored, menu.onSpace())
        assertFalse(menu.isActive)
    }

    @Test
    fun `backtick m opens the menu and digits pick`() {
        menu.onKey('`')
        assertEquals("`", menu.composition)
        menu.onKey('m')
        assertEquals("選單", menu.composition)
        assertEquals("功能設定", menu.pageLabels.first())
        menu.onKey('4')
        assertEquals("選單 › 特殊符號", menu.composition)
        menu.onKey('4')
        assertEquals(MenuResult.Commit("↑"), menu.onKey('2'))
        assertFalse(menu.isActive)
    }

    @Test
    fun `three backticks open the menu too`() {
        repeat(3) { menu.onKey('`') }
        assertEquals("選單", menu.composition)
    }

    @Test
    fun `space types a lone backtick`() {
        menu.onKey('`')
        menu.onKey('x')
        assertEquals(MenuResult.Commit("`"), menu.onSpace())
    }

    @Test
    fun `backspace goes back a level then closes`() {
        menu.onKey('`')
        menu.onKey('m')
        menu.onKey('3')
        menu.onBackspace()
        assertEquals("選單", menu.composition)
        menu.onBackspace()
        assertFalse(menu.isActive)
    }

    @Test
    fun `switch labels show the current state and run the action`() {
        wildcard = false
        menu.onKey('`')
        menu.onKey('m')
        menu.onKey('3')
        assertTrue(menu.pageLabels.first().startsWith("☐"))
        assertEquals(MenuResult.Run(MenuAction.TOGGLE_WILDCARD), menu.onKey('1'))
        assertFalse(menu.isActive)
    }

    @Test
    fun `second row switches between simplified and traditional output`() {
        menu.onKey('`')
        menu.onKey('m')
        assertEquals("輸出簡體", menu.pageLabels[1])
        assertEquals(MenuResult.Run(MenuAction.TOGGLE_SIMPLIFIED), menu.onKey('2'))
        simplified = true
        menu.onKey('`')
        menu.onKey('m')
        assertEquals("輸出繁體", menu.pageLabels[1])
    }

    @Test
    fun `backtick u takes a code point in hex`() {
        menu.onKey('`')
        menu.onKey('u')
        "4e2d".forEach { menu.onKey(it) }
        assertEquals("`U4E2D", menu.composition)
        assertEquals(listOf("中"), menu.pageLabels)
        assertEquals(MenuResult.Commit("中"), menu.onSpace())
    }

    @Test
    fun `unicode takes supplementary planes and refuses surrogates`() {
        menu.onKey('`')
        menu.onKey('u')
        "20bb7".forEach { menu.onKey(it) }
        assertEquals(MenuResult.Commit("𠮷"), menu.onEnter())
        menu.onKey('`')
        menu.onKey('u')
        "d800".forEach { menu.onKey(it) }
        assertEquals(MenuResult.Consumed, menu.onSpace())
    }

    @Test
    fun `space pages through a long list and wraps`() {
        menu.onKey('`')
        menu.onKey('m')
        menu.onKey('5')
        val pages = menu.pageCount
        assertTrue(pages > 1)
        repeat(pages) { menu.onSpace() }
        assertEquals(0, menu.page)
    }

    @Test
    fun `backtick e opens emoji, skipping ones the font lacks`() {
        menu.onKey('`')
        menu.onKey('e')
        assertEquals("表情", menu.composition)
        assertEquals("笑臉", menu.pageLabels.first())
        menu.onKey('1')
        assertEquals("😃", menu.pageLabels.first())
        assertEquals(MenuResult.Commit("😃"), menu.onKey('1'))
    }

    @Test
    fun `kaomoji are the last emoji group`() {
        menu.onKey('`')
        menu.onKey('e')
        assertEquals("顏文字", menu.pageLabels.last())
        menu.onKey(('0' + menu.pageLabels.size % 10))
        menu.onKey('7')
        assertEquals("¯\\_(ツ)_/¯", menu.pageLabels[1])
    }
}

class RecentEmojiTest {
    @Test
    fun `newest first without repeats, capped`() {
        assertEquals(listOf("😀", "🐶"), addRecent(listOf("🐶", "😀"), "😀"))
        assertEquals(3, addRecent(listOf("a", "b", "c"), "d", max = 3).size)
        assertEquals("d", addRecent(listOf("a", "b", "c"), "d", max = 3).first())
    }
}
