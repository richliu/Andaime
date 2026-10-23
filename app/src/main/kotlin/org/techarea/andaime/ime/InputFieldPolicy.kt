package org.techarea.andaime.ime

import android.text.InputType

/**
 * Fields whose content is never Chinese: numbers, phone numbers, dates,
 * URLs, e-mail addresses and passwords. The keyboard starts these in English
 * so codes like "p" or "/db." aren't swallowed by Dayi composition.
 */
fun prefersEnglish(inputType: Int): Boolean {
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
        InputType.TYPE_CLASS_TEXT -> when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            -> true
            else -> false
        }
        else -> false
    }
}
