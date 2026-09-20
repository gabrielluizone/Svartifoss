package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPanelSourceTest {

    @Test
    fun `the configured buttons are used unless the panel follows the playing app`() {
        assertTrue(QuickPanelSource.usesConfiguredButtons(QuickPanelSource.MANUAL))
        assertFalse(QuickPanelSource.usesConfiguredButtons(QuickPanelSource.SESSION))
    }

    /**
     * A value can arrive from a backup or a newer build. Treating a stray string as "the playing
     * app's buttons" would hide slots that are in fact live, so it falls back to the default.
     */
    @Test
    fun `an unset or unrecognised value keeps the slots in play`() {
        assertTrue(QuickPanelSource.usesConfiguredButtons(null))
        assertTrue(QuickPanelSource.usesConfiguredButtons(""))
        assertTrue(QuickPanelSource.usesConfiguredButtons("something-newer"))
    }

    /** The two constants must be the literal values the preference and the watch already use. */
    @Test
    fun `the constants are the stored preference values`() {
        assertEquals("manual", QuickPanelSource.MANUAL)
        assertEquals("session", QuickPanelSource.SESSION)
        assertEquals(QuickPanelSource.MANUAL, MiscPreferences.WEAR_QUICK_PANEL_SOURCE.defaultValue)
    }

    @Test
    fun `the retired long slot stays out of the three assignable ones`() {
        assertFalse(QuickPanelButtons.SLOT_LONG in QuickPanelButtons.ALL_SLOTS)
        assertEquals(3, QuickPanelButtons.ALL_SLOTS.size)
    }
}
