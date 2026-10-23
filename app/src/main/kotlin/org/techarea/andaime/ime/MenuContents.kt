package org.techarea.andaime.ime

/**
 * What the `M menu offers. The layout follows PIME's menu, but the lists are
 * Andaime's own, mostly whole Unicode runs (①–⑳, ㄅ–ㄩ, あ–ゖ...).
 */
fun menuRoot(
    simplified: Boolean,
    wildcard: () -> Boolean,
    fullShape: () -> Boolean,
    emojiAvailable: (String) -> Boolean,
): List<MenuItem> = listOf(
    MenuItem.Action("功能設定", MenuAction.SETTINGS),
    // Names what choosing it does, like PIME
    MenuItem.Action(if (simplified) "輸出繁體" else "輸出簡體", MenuAction.TOGGLE_SIMPLIFIED),
    MenuItem.Submenu("功能開關") {
        listOf(
            MenuItem.Action(check(wildcard()) + " 萬用字元查詢（＊）", MenuAction.TOGGLE_WILDCARD),
            MenuItem.Action(check(fullShape()) + " 全形輸入（Shift+空白）", MenuAction.TOGGLE_FULL_SHAPE),
        )
    },
    submenu("特殊符號", symbolGroups),
    MenuItem.Submenu("注音符號") { outputs(range(0x3105, 0x3129) + "˙ˊˇˋ") },
    submenu("外語文字", letterGroups),
    MenuItem.Submenu("表情符號（`E）") { emojiMenu(emojiAvailable) },
    MenuItem.Action("萬國碼輸入（`U）", MenuAction.UNICODE),
)

private fun check(on: Boolean) = if (on) "☑" else "☐"

private val symbolGroups = listOf(
    "標點" to "，、。．・：；？！…‥—–～﹏「」『』（）〔〕【】《》〈〉‘’“”〝〞‵′″",
    "括號" to "（）［］｛｝〔〕【】《》〈〉「」『』〖〗﹙﹚﹛﹜﹝﹞",
    "數學" to "＋－×÷＝≠≒≡≦≧＜＞±√∞∫∮∑∏∠⊥∵∴∩∪⊂⊃∈∉％‰°",
    "箭頭" to "←↑→↓↖↗↘↙↔↕⇐⇒⇔⇑⇓➔",
    "單位與貨幣" to "℃℉㎎㎏㎜㎝㎞㎡㏄㎖㎗㎘㏊￥＄￠￡€",
    "圖形" to "○●◎◇◆□■△▲▽▼☆★♀♂♠♥♦♣☐☑✓✗※§¶♪♫☀☁☂☎",
    "圈號數字" to range(0x2460, 0x2473),
    "括號數字" to range(0x2474, 0x2487),
    "點號數字" to range(0x2488, 0x249B),
    "中文序號" to range(0x3220, 0x3229),
    "羅馬數字" to range(0x2160, 0x216B) + range(0x2170, 0x2179),
    "框線" to "─│┌┐└┘├┤┬┴┼═║╔╗╚╝╠╣╦╩╬▁▂▃▄▅▆▇█",
)

private val letterGroups = listOf(
    "希臘字母" to range(0x0391, 0x03A1) + range(0x03A3, 0x03A9) + range(0x03B1, 0x03C1) + range(0x03C3, 0x03C9),
    "平假名" to range(0x3041, 0x3096),
    "片假名" to range(0x30A1, 0x30FA) + "ー",
    "俄文字母" to range(0x0410, 0x044F),
    "拉丁字母" to "àáâãäåæçèéêëìíîïñòóôõöøùúûüýÿßÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÑÒÓÔÕÖØÙÚÛÜÝ",
)

private fun submenu(label: String, groups: List<Pair<String, String>>) =
    MenuItem.Submenu(label) { groups.map { (name, chars) -> MenuItem.Submenu(name) { outputs(chars) } } }

private fun outputs(chars: String): List<MenuItem> =
    chars.codePoints().toArray().map { MenuItem.Output(String(Character.toChars(it))) }

/** Every character from [first] to [last], inclusive. */
private fun range(first: Int, last: Int): String = buildString { for (c in first..last) appendCodePoint(c) }
