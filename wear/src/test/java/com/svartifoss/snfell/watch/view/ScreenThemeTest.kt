package com.svartifoss.snfell.watch.view

import com.svartifoss.snfell.watch.view.face.ScreenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenThemeTest {
    @Test
    fun allPickerValuesMapAndUnknownFallsBack() {
        ScreenTheme.entries.forEach { theme ->
            assertEquals(theme, ScreenTheme.fromPreference(theme.preferenceValue))
        }
        assertEquals(ScreenTheme.DEFAULT, ScreenTheme.fromPreference("unknown"))
    }

    @Test
    fun everyThemeExceptHiddenKeepsControlsVisible() {
        ScreenTheme.entries
                .filter { it != ScreenTheme.HIDDEN }
                .forEach { theme ->
                    assertTrue("${theme.name} made controls invisible", theme.tokens.iconAlpha > 0f)
                }
    }

    @Test
    fun hiddenThemeDrawsNoControlIconsButKeepsTouchTargets() {
        // "Hidden" is the one style that intentionally paints control icons fully transparent.
        // Their touch targets stay sized (iconScale > 0) so tapping still works and a11y is intact.
        assertEquals(0f, ScreenTheme.HIDDEN.tokens.iconAlpha, 0f)
        assertTrue(ScreenTheme.HIDDEN.tokens.iconScale > 0f)
    }
}
