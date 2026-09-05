package com.svartifoss.snfell.common

import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoublePinchGestureTest {

    @Test
    fun `double pinch has its own single-action screen input`() {
        val buttonInfo = DoublePinchGesture.buttonInfo()

        assertFalse(buttonInfo.physicalButton)
        assertEquals(DoublePinchGesture.DOUBLE_PINCH, buttonInfo.buttonCode)
        assertEquals(GESTURE_SINGLE_TAP, buttonInfo.gesture)
        assertTrue(buttonInfo.buttonCode > CenterButton.TAP)
    }
}
