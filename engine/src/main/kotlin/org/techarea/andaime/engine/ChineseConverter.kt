package org.techarea.andaime.engine

import java.io.Reader

/**
 * 繁體 to 簡體, the way OpenCC's t2s does it: at each position the longest
 * phrase in [phrases] wins, otherwise the character is looked up in
 * [characters], otherwise it is kept. Only each entry's first (default)
 * choice is used.
 */
class ChineseConverter(
    private val phrases: Map<String, String>,
    private val characters: Map<String, String>,
) {
    private val longestPhrase = phrases.keys.maxOfOrNull { it.length } ?: 0

    fun convert(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            val phrase = (minOf(longestPhrase, text.length - i) downTo 2).firstNotNullOfOrNull { len ->
                phrases[text.substring(i, i + len)]?.let { it to len }
            }
            if (phrase != null) {
                append(phrase.first)
                i += phrase.second
                continue
            }
            val end = i + Character.charCount(text.codePointAt(i))
            val ch = text.substring(i, end)
            append(characters[ch] ?: ch)
            i = end
        }
    }

    companion object {
        /** An OpenCC dictionary: `key<TAB>value value…`, `#` comments. */
        fun readDictionary(reader: Reader): Map<String, String> = reader.useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") && '\t' in it }.associate { line ->
                val tab = line.indexOf('\t')
                line.substring(0, tab) to line.substring(tab + 1).substringBefore(' ').trim()
            }
        }
    }
}
