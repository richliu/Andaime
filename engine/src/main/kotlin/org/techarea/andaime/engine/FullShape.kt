package org.techarea.andaime.engine

/**
 * 全形 output, PIME's mapping: printable ASCII moves to the fullwidth block,
 * except a few keys that give the Chinese punctuation people mean by them.
 */
object FullShape {
    fun convert(c: Char): Char = when (c) {
        ' ' -> '　'
        '"', '\'' -> '、'
        '.', '>' -> '。'
        '<' -> '，'
        '_' -> '－'
        in '!'..'~' -> c + 0xFEE0
        else -> c
    }

    fun convert(text: String): String = buildString(text.length) { for (c in text) append(convert(c)) }
}
