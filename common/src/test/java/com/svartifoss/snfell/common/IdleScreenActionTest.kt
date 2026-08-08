package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleScreenActionTest {
    @Test
    fun `every stored value decodes to itself`() {
        IdleScreenAction.entries.forEach { action ->
            assertEquals(action, IdleScreenAction.forButton(action.preferenceValue))
        }
    }

    /** An unusable button would leave the idle screen as the dead end it used to be. */
    @Test
    fun `unknown button values keep a working button`() {
        assertEquals(IdleScreenAction.RESUME, IdleScreenAction.forButton(null))
        assertEquals(IdleScreenAction.RESUME, IdleScreenAction.forButton("teleport"))
    }

    /** Guessing an auto-open destination would hijack the screen on every launch. */
    @Test
    fun `unknown auto-open values stay silent`() {
        assertEquals(IdleScreenAction.NONE, IdleScreenAction.forAutoOpen(null))
        assertEquals(IdleScreenAction.NONE, IdleScreenAction.forAutoOpen("teleport"))
    }

    @Test
    fun `auto-open cannot start playback on its own`() {
        assertFalse(IdleScreenAction.RESUME in IdleScreenAction.AUTO_OPEN_CHOICES)
        assertTrue(IdleScreenAction.NONE in IdleScreenAction.AUTO_OPEN_CHOICES)
    }
}
