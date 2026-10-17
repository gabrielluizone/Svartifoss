package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconStyleTest {

    @Test
    fun `preference values round trip`() {
        AppIconStyle.entries.forEach { style ->
            assertEquals(style, AppIconStyle.fromPreference(style.preferenceValue))
        }
    }

    @Test
    fun `missing and unknown values use gradient`() {
        assertEquals(AppIconStyle.GRADIENT, AppIconStyle.fromPreference(null))
        assertEquals(AppIconStyle.GRADIENT, AppIconStyle.fromPreference("neon"))
    }
}
