package com.svartifoss.snfell.common

import com.svartifoss.snfell.proto.PinchCalibrationMessage
import org.junit.Assert.*
import org.junit.Test

class PinchCalibrationTransferTest {
    @Test fun `native is the default and there is no invented calibration`() {
        assertEquals("native", MiscPreferences.WEAR_HAND_GESTURE_MODE.defaultValue)
        assertEquals("", MiscPreferences.WEAR_PINCH_CALIBRATION.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_PINCH_SENSITIVITY.defaultValue)
        assertEquals(800, MiscPreferences.WEAR_PINCH_MAX_GAP.defaultValue)
        assertEquals(1000, MiscPreferences.WEAR_PINCH_COOLDOWN.defaultValue)
    }
    @Test fun `profile and request round trip unchanged for acknowledgement`() {
        val transfer = PinchCalibrationTransfer("test-123", PinchCalibration(0.03, 0.9, 0.3))
        assertEquals(transfer, PinchCalibrationTransfer.decode(transfer.encode()))
    }

    @Test fun `reject malformed oversized incomplete and invalid profiles`() {
        for (bytes in listOf(ByteArray(0), byteArrayOf(-1), ByteArray(513),
                PinchCalibrationMessage.newBuilder().setRequestId("id").buildPartial().toByteArray(),
                PinchCalibrationMessage.newBuilder().setRequestId("").setProfile("1;0.03;0.9;0.3")
                        .build().toByteArray(),
                PinchCalibrationMessage.newBuilder().setRequestId("id").setProfile("1;NaN;0.9;0.3")
                        .build().toByteArray())) {
            assertNull(PinchCalibrationTransfer.decode(bytes))
        }
    }

    @Test fun `experimental controls and profile are exported global input preferences`() {
        val definitions = listOf(MiscPreferences.WEAR_HAND_GESTURE_MODE,
                MiscPreferences.WEAR_PINCH_SENSITIVITY, MiscPreferences.WEAR_PINCH_MAX_GAP,
                MiscPreferences.WEAR_PINCH_COOLDOWN, MiscPreferences.WEAR_PINCH_CALIBRATION)
        definitions.forEach {
            assertTrue(MiscPreferences.EXPORTABLE.contains(it))
            assertFalse(FaceScopedPreferences.SCOPED_KEYS.contains(it.key))
        }
    }
}
