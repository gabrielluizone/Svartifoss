package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Split face's own backdrop control - see [SplitPanelStyle].
 *
 * Worth a test for the reason every other `fromPref` here is: the value crosses from the phone as
 * text, so the only interesting behaviour is what happens to one this build does not recognise.
 */
class SplitPanelStyleTest {

    @Test
    fun `each style round-trips through its preference value`() {
        SplitPanelStyle.entries.forEach { style ->
            assertEquals(style, SplitPanelStyle.fromPref(style.preferenceValue))
        }
    }

    /**
     * Blurred artwork, not the flat panel.
     *
     * An unreadable value has to resolve to the face's normal appearance. Resolving it to the other
     * *named* option would make a corrupt or newer-than-this-build value look like a deliberate
     * choice of solid colour, which is indistinguishable from the setting having been changed.
     */
    @Test
    fun `an unknown value falls back to the default rather than to the other option`() {
        assertEquals(SplitPanelStyle.DEFAULT, SplitPanelStyle.fromPref("gradient"))
        assertEquals(SplitPanelStyle.DEFAULT, SplitPanelStyle.fromPref(null))
        assertEquals(SplitPanelStyle.DEFAULT, SplitPanelStyle.fromPref(""))
        assertEquals(SplitPanelStyle.BLUR, SplitPanelStyle.DEFAULT)
    }

    /** Preference values are the wire format; renaming one silently resets every saved theme that
     *  carried it, since an unrecognised value resolves to the default. */
    @Test
    fun `preference values are stable and distinct`() {
        val values = SplitPanelStyle.entries.map { it.preferenceValue }
        assertEquals(values.size, values.toSet().size)
        assertEquals(listOf("blur", "solid"), values)
    }

    /** Surrounding whitespace is survivable - a hand-edited backup should not silently reset the
     *  face. */
    @Test
    fun `a padded value still resolves`() {
        assertEquals(SplitPanelStyle.SOLID, SplitPanelStyle.fromPref("  solid "))
    }
}
