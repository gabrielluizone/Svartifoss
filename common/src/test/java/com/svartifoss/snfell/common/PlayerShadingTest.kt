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

    @Test fun `three intensity levels stay ordered`() {
        assertEquals(.45f, PlayerShadingIntensity.SOFT.multiplier)
        assertEquals(.80f, PlayerShadingIntensity.BALANCED.multiplier)
        assertEquals(1f, PlayerShadingIntensity.STRONG.multiplier)
    }

    @Test fun `legacy percentages resolve to the nearest named band`() {
        assertEquals(PlayerShadingIntensity.SOFT, PlayerShadingIntensity.fromLegacyPercent(35))
        assertEquals(PlayerShadingIntensity.BALANCED, PlayerShadingIntensity.fromLegacyPercent(80))
        assertEquals(PlayerShadingIntensity.STRONG, PlayerShadingIntensity.fromLegacyPercent(100))
    }
}
