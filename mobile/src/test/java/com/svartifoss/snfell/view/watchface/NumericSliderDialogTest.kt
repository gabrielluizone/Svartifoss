package com.svartifoss.snfell.view.watchface

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compact editors share one slider dialog. A slider alone makes exact values needlessly hard
 * to reach, especially for typography's 1..1000 weight range, so every consumer must retain the
 * editable number field as well as the drag affordance.
 */
class NumericSliderDialogTest {

    @Test
    fun `all numeric slider dialogs accept and validate a typed value`() {
        val layout = repoFile("mobile/src/main/res/layout/dialog_typography_slider.xml").readText()
        val fragment = repoFile(
                "mobile/src/main/java/com/svartifoss/snfell/view/watchface/" +
                        "WatchFacePrefsFragment.kt").readText()

        assertTrue("The displayed value must be an editable numeric field",
                layout.contains("TextInputEditText"))
        assertTrue("Tracking accepts negative values, so the field must allow a signed number",
                layout.contains("android:inputType=\"numberSigned\""))
        assertTrue("The range labels remain beside the manual field",
                layout.contains("@+id/typography_slider_min") &&
                        layout.contains("@+id/typography_slider_max"))

        // showNumericSlider serves Text, Colors, Always-on and Mini buttons; the Background
        // editor has no Preference object, so it has its small wrapper. Both must wire the field
        // back into their slider and keep OK unavailable while an incomplete/out-of-range number
        // is present.
        assertEquals(2, fragment.count("valueInput.doAfterTextChanged"))
        assertEquals(2, fragment.count("slider.value != selected.toFloat()"))
        assertEquals(2, fragment.count("isEnabled = valid"))
        assertEquals(2, fragment.count("setting_numeric_range_error, range.first, range.last"))
        assertEquals(2, fragment.count(
                "tintNumericSliderDialog(dialog, slider, valueLayout, valueInput)"))
        assertTrue("The numeric field outline must follow the runtime accent",
                fragment.contains("valueLayout.boxStrokeColor = palette.accent"))
        assertTrue("No slider state may retain the theme's static green",
                fragment.contains("slider.trackInactiveTintList = divider") &&
                        fragment.contains("slider.tickActiveTintList = accent") &&
                        fragment.contains("slider.tickInactiveTintList = divider"))
    }

    private fun repoFile(path: String): File = File(path).takeIf { it.exists() }
            ?: File("../$path").takeIf { it.exists() }
            ?: error("Missing repository file: $path")

    private fun String.count(needle: String): Int =
            windowed(needle.length, partialWindows = false).count { it == needle }
}
