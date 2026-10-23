package org.techarea.andaime.ime

/**
 * Recognises a quick tap of Shift on its own (press and release with no
 * other key in between), the usual 中/英 toggle on desktop Chinese IMEs.
 * Holding Shift to type capitals or symbols never counts.
 */
class ShiftTapDetector(private val maxTapMillis: Long = 400) {
    private var downAt: Long? = null

    fun onShiftDown(timeMillis: Long, repeat: Int) {
        if (repeat == 0) downAt = timeMillis
    }

    /** Any other key while Shift is down turns it into a modifier, not a tap. */
    fun onOtherKeyDown() {
        downAt = null
    }

    /** True when this release completes a tap. */
    fun onShiftUp(timeMillis: Long): Boolean {
        val start = downAt ?: return false
        downAt = null
        return timeMillis - start <= maxTapMillis
    }
}
