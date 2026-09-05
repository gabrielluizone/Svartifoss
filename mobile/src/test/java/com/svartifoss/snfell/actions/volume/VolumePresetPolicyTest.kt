package com.svartifoss.snfell.actions.volume

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumePresetPolicyTest {
    @Test
    fun absoluteVolumeClampsPercentAndHandlesMissingRange() {
        assertEquals(0, absoluteVolumeForPercent(0, 50))
        assertEquals(0, absoluteVolumeForPercent(15, -10))
        assertEquals(15, absoluteVolumeForPercent(15, 200))
        assertEquals(7, absoluteVolumeForPercent(15, 50))
    }
}
