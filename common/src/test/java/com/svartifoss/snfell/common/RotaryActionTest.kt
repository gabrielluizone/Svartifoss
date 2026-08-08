package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RotaryActionTest {
    @Test
    fun `explicit values decode to themselves`() {
        assertEquals(RotaryAction.VOLUME, RotaryAction.resolve("volume", legacyRotarySeek = false))
        assertEquals(RotaryAction.SEEK, RotaryAction.resolve("seek", legacyRotarySeek = false))
        assertEquals(RotaryAction.OFF, RotaryAction.resolve("off", legacyRotarySeek = false))
    }

    /** An install that predates the three-way key must keep the behaviour it already had. */
    @Test
    fun `unset falls back to the legacy boolean`() {
        assertEquals(RotaryAction.SEEK, RotaryAction.resolve(null, legacyRotarySeek = true))
        assertEquals(RotaryAction.VOLUME, RotaryAction.resolve(null, legacyRotarySeek = false))
        assertEquals(RotaryAction.SEEK, RotaryAction.resolve("", legacyRotarySeek = true))
    }

    /**
     * Once chosen, the new key wins outright - otherwise a user who picked "volume" on a device
     * whose legacy boolean was true would silently keep seeking.
     */
    @Test
    fun `explicit choice overrides a conflicting legacy boolean`() {
        assertEquals(RotaryAction.VOLUME, RotaryAction.resolve("volume", legacyRotarySeek = true))
        assertEquals(RotaryAction.OFF, RotaryAction.resolve("off", legacyRotarySeek = true))
    }

    /** A value from a newer build (or a corrupted one) must not disable rotary input silently. */
    @Test
    fun `unknown values fall back rather than resolving to off`() {
        assertEquals(RotaryAction.VOLUME, RotaryAction.resolve("scroll", legacyRotarySeek = false))
        assertEquals(RotaryAction.SEEK, RotaryAction.resolve("scroll", legacyRotarySeek = true))
    }
}
