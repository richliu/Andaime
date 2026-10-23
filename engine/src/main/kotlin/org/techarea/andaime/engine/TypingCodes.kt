package org.techarea.andaime.engine

/** One way to type a text: its [code] and its place in that code's candidates (0 = first). */
data class TypingCode(val code: String, val position: Int)

/** Every code that types [text], easiest first: shortest code, then earliest candidate. */
fun Dictionary.typingCodes(text: String): List<TypingCode> =
    codesOf(text)
        .map { TypingCode(it, lookup(it).indexOf(text)) }
        .filter { it.position >= 0 }
        .sortedWith(compareBy({ it.code.length }, { it.position }, { it.code }))

/** [text] as single characters (whole code points, so 𠮷 stays one), without whitespace. */
fun splitCharacters(text: String): List<String> =
    text.codePoints().toArray()
        .filter { !Character.isWhitespace(it) }
        .map { String(Character.toChars(it)) }
