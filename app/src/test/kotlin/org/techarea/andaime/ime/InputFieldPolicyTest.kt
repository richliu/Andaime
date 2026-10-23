package org.techarea.andaime.ime

import android.text.InputType.TYPE_CLASS_DATETIME
import android.text.InputType.TYPE_CLASS_NUMBER
import android.text.InputType.TYPE_CLASS_PHONE
import android.text.InputType.TYPE_CLASS_TEXT
import android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
import android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
import android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
import android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
import android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
import android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME
import android.text.InputType.TYPE_TEXT_VARIATION_URI
import android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
import android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
import android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputFieldPolicyTest {
    @Test
    fun `numbers, phones and dates are English`() {
        assertTrue(prefersEnglish(TYPE_CLASS_NUMBER))
        assertTrue(prefersEnglish(TYPE_CLASS_NUMBER or TYPE_NUMBER_FLAG_DECIMAL))
        assertTrue(prefersEnglish(TYPE_CLASS_PHONE))
        assertTrue(prefersEnglish(TYPE_CLASS_DATETIME))
    }

    @Test
    fun `urls, e-mail and passwords are English`() {
        for (variation in listOf(
            TYPE_TEXT_VARIATION_URI, TYPE_TEXT_VARIATION_EMAIL_ADDRESS, TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            TYPE_TEXT_VARIATION_PASSWORD, TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) {
            assertTrue(prefersEnglish(TYPE_CLASS_TEXT or variation), "variation $variation")
        }
    }

    @Test
    fun `plain text, names and messages keep the user's choice`() {
        assertFalse(prefersEnglish(TYPE_CLASS_TEXT))
        assertFalse(prefersEnglish(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PERSON_NAME))
        assertFalse(prefersEnglish(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE or TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertFalse(prefersEnglish(0))
    }
}
