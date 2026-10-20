package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportRefusalTest {

    @Test
    fun `a skip is refused only when nothing moved and the session never claimed it`() {
        assertTrue(TransportRefusal.skipRefused(advertised = false, trackChanged = false, restartedTrack = false))
        assertFalse(TransportRefusal.skipRefused(advertised = true, trackChanged = false, restartedTrack = false))
        assertFalse(TransportRefusal.skipRefused(advertised = false, trackChanged = true, restartedTrack = false))
        assertFalse(TransportRefusal.skipRefused(advertised = false, trackChanged = false, restartedTrack = true))
    }

    @Test
    fun `previous going back to the start of the track is an answer`() {
        assertTrue(TransportRefusal.restartedTrack(positionBeforeMs = 95_000L, positionAfterMs = 400L))
        assertFalse(TransportRefusal.restartedTrack(positionBeforeMs = 95_000L, positionAfterMs = 97_000L))
        assertFalse(TransportRefusal.restartedTrack(positionBeforeMs = null, positionAfterMs = 400L))
    }

    @Test
    fun `a seek that left playback where it would have been is refused`() {
        assertTrue(TransportRefusal.seekRefused(false, targetMs = 60_000L,
                expectedWithoutSeekMs = 30_000L, actualMs = 30_500L))
    }

    @Test
    fun `a seek that landed, a claimed one, or one to where playback already was is not`() {
        assertFalse(TransportRefusal.seekRefused(false, 60_000L, 30_000L, 60_200L))
        assertFalse(TransportRefusal.seekRefused(true, 60_000L, 30_000L, 30_500L))
        assertFalse(TransportRefusal.seekRefused(false, 31_000L, 30_000L, 30_500L))
        assertFalse(TransportRefusal.seekRefused(false, 60_000L, null, 30_500L))
        assertFalse(TransportRefusal.seekRefused(false, 60_000L, 30_000L, null))
    }

    @Test
    fun `codes are stable and unknown ones are ignored`() {
        assertEquals(1, TransportRefusal.SKIP_NEXT.code)
        assertEquals(2, TransportRefusal.SKIP_PREVIOUS.code)
        assertEquals(3, TransportRefusal.SEEK.code)
        TransportRefusal.values().forEach { assertEquals(it, TransportRefusal.fromCode(it.code)) }
        assertNull(TransportRefusal.fromCode(0))
        assertNull(TransportRefusal.fromCode(9))
    }
}
