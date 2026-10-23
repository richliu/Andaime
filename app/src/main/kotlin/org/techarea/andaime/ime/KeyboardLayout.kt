package org.techarea.andaime.ime

import android.content.Context

enum class KeyboardMode { DOCKED, SPLIT, FLOATING }

/** What to show while a Bluetooth or USB keyboard is connected. */
enum class HardwareKeyboardMode {
    /** Only the candidate bar and option buttons; tap 鍵盤 to expand the touch keys. */
    TOOLBAR,
    /** The full touch keyboard, as without a hardware keyboard. */
    FULL,
}

/** Which label is big on a key: the QWERTY letter or the Dayi radical. */
enum class LabelStyle { LETTER, RADICAL }

/**
 * Keyboard preferences: the active table plus size and placement. Widths and positions are fractions so
 * they survive rotation and different tablets.
 */
data class KeyboardLayout(
    /** Id of the imported table in use; null or a deleted id falls back to the first installed one. */
    val tableId: String? = null,
    val mode: KeyboardMode = KeyboardMode.DOCKED,
    val labelStyle: LabelStyle = LabelStyle.LETTER,
    val hardwareKeyboardMode: HardwareKeyboardMode = HardwareKeyboardMode.TOOLBAR,
    /** Tapping Shift alone on a hardware keyboard toggles 中/英. */
    val shiftTogglesLanguage: Boolean = true,
    /** Ctrl+Space on a hardware keyboard toggles 中/英, as on Windows. */
    val ctrlSpaceTogglesLanguage: Boolean = true,
    /** Start in English whenever the user switches to this keyboard. */
    val startInEnglish: Boolean = false,
    /**
     * Compact (60%) keyboards put Esc on the ` key and send ` without Fn.
     * Dayi codes almost never contain `, so mid-composition it cancels instead.
     */
    val graveCancels: Boolean = true,
    /** `*` stands for any one key in a code, as in PIME. */
    val wildcard: Boolean = true,
    /** 打繁出簡: committed Chinese is converted to Simplified (OpenCC t2s), as PIME's 輸出簡體. */
    val outputSimplified: Boolean = false,
    val keyHeightDp: Float = 52f,
    /** Docked keyboard width as a fraction of the screen, centred. */
    val dockedWidth: Float = 1f,
    /** Empty space between the split halves, as a fraction of the screen. */
    val splitGap: Float = 0.3f,
    val floatingWidth: Float = 0.6f,
    /** Floating position within the free space: 0 = left/top edge, 1 = right/bottom edge. */
    val floatingX: Float = 0.5f,
    val floatingY: Float = 1f,
) {
    companion object {
        val keyHeightRange = 36f..72f
        val dockedWidthRange = 0.5f..1f
        val splitGapRange = 0.1f..0.5f
        val floatingWidthRange = 0.35f..0.9f
    }
}

class KeyboardLayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("keyboard_layout", Context.MODE_PRIVATE)

    fun load(): KeyboardLayout {
        val d = KeyboardLayout()
        return KeyboardLayout(
            tableId = prefs.getString("tableId", d.tableId),
            mode = prefs.getString("mode", null)
                ?.let { name -> KeyboardMode.entries.firstOrNull { it.name == name } } ?: d.mode,
            labelStyle = prefs.getString("labelStyle", null)
                ?.let { name -> LabelStyle.entries.firstOrNull { it.name == name } } ?: d.labelStyle,
            hardwareKeyboardMode = prefs.getString("hardwareKeyboardMode", null)
                ?.let { name -> HardwareKeyboardMode.entries.firstOrNull { it.name == name } } ?: d.hardwareKeyboardMode,
            shiftTogglesLanguage = prefs.getBoolean("shiftTogglesLanguage", d.shiftTogglesLanguage),
            ctrlSpaceTogglesLanguage = prefs.getBoolean("ctrlSpaceTogglesLanguage", d.ctrlSpaceTogglesLanguage),
            startInEnglish = prefs.getBoolean("startInEnglish", d.startInEnglish),
            graveCancels = prefs.getBoolean("graveCancels", d.graveCancels),
            wildcard = prefs.getBoolean("wildcard", d.wildcard),
            outputSimplified = prefs.getBoolean("outputSimplified", d.outputSimplified),
            keyHeightDp = prefs.getFloat("keyHeightDp", d.keyHeightDp),
            dockedWidth = prefs.getFloat("dockedWidth", d.dockedWidth),
            splitGap = prefs.getFloat("splitGap", d.splitGap),
            floatingWidth = prefs.getFloat("floatingWidth", d.floatingWidth),
            floatingX = prefs.getFloat("floatingX", d.floatingX),
            floatingY = prefs.getFloat("floatingY", d.floatingY),
        )
    }

    fun debugHardwareKeyboard(): Boolean = prefs.getBoolean("debugHardwareKeyboard", false)

    fun save(layout: KeyboardLayout) {
        prefs.edit()
            .putString("tableId", layout.tableId)
            .putString("mode", layout.mode.name)
            .putString("labelStyle", layout.labelStyle.name)
            .putString("hardwareKeyboardMode", layout.hardwareKeyboardMode.name)
            .putBoolean("shiftTogglesLanguage", layout.shiftTogglesLanguage)
            .putBoolean("ctrlSpaceTogglesLanguage", layout.ctrlSpaceTogglesLanguage)
            .putBoolean("startInEnglish", layout.startInEnglish)
            .putBoolean("graveCancels", layout.graveCancels)
            .putBoolean("wildcard", layout.wildcard)
            .putBoolean("outputSimplified", layout.outputSimplified)
            .putFloat("keyHeightDp", layout.keyHeightDp)
            .putFloat("dockedWidth", layout.dockedWidth)
            .putFloat("splitGap", layout.splitGap)
            .putFloat("floatingWidth", layout.floatingWidth)
            .putFloat("floatingX", layout.floatingX)
            .putFloat("floatingY", layout.floatingY)
            .apply()
    }
}
