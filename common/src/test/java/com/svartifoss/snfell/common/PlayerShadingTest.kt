package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerShadingTest {
    @Test fun `all persisted shading values round trip`() {
        PlayerShadingStyle.entries.forEach { style ->
            assertEquals(style, PlayerShadingStyle.fromPreference(style.preferenceValue))
        }
    }

    @Test fun `unknown shading value safely follows the face`() {
        assertEquals(PlayerShadingStyle.FOLLOW, PlayerShadingStyle.fromPreference("future_style"))
        assertEquals(PlayerShadingStyle.FOLLOW, PlayerShadingStyle.fromPreference(null))
    }

    @Test fun `numeric shading ceiling allows more than full strength`() {
        assertEquals(150, SHADING_MAX_PERCENT)
        assertEquals(1.5f, SHADING_MAX_MULTIPLIER)
    }

    @Test fun `legacy named levels migrate to their percentage`() {
        assertEquals(45, PlayerShadingIntensity.percentFor("soft"))
        assertEquals(80, PlayerShadingIntensity.percentFor("balanced"))
        assertEquals(100, PlayerShadingIntensity.percentFor("strong"))
        // Unknown/empty falls back to the balanced default.
        assertEquals(80, PlayerShadingIntensity.percentFor(null))
    }
}
