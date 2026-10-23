package org.techarea.andaime.ime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShiftTapDetectorTest {
    private val detector = ShiftTapDetector(maxTapMillis = 400)

    @Test
    fun `quick press and release is a tap`() {
        detector.onShiftDown(1_000, repeat = 0)
        assertTrue(detector.onShiftUp(1_150))
    }

    @Test
    fun `typing a capital while holding shift is not a tap`() {
        detector.onShiftDown(1_000, repeat = 0)
        detector.onOtherKeyDown()
        assertFalse(detector.onShiftUp(1_100))
    }

    @Test
    fun `holding shift too long is not a tap, even with auto-repeat`() {
        detector.onShiftDown(1_000, repeat = 0)
        detector.onShiftDown(1_500, repeat = 1)
        assertFalse(detector.onShiftUp(1_600))
    }

    @Test
    fun `a release without a matching press is ignored`() {
        assertFalse(detector.onShiftUp(1_000))
        detector.onShiftDown(2_000, repeat = 0)
        assertTrue(detector.onShiftUp(2_100))
        assertFalse(detector.onShiftUp(2_200))
    }
}
