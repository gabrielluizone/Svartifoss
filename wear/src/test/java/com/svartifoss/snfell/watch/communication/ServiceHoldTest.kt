package com.svartifoss.snfell.watch.communication

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceHoldTest {
    @Test
    fun `open ui keeps the service fully active`() {
        assertEquals(ServiceHold.ACTIVE, resolveServiceHold(
                uiOpen = true, musicPlaying = false, hasTrack = false))
    }

    @Test
    fun `playing music keeps the service fully active with the ui closed`() {
        assertEquals(ServiceHold.ACTIVE, resolveServiceHold(
                uiOpen = false, musicPlaying = true, hasTrack = true))
    }

    /**
     * The regression this whole split exists for: screen off (so the UI has unbound) on a paused
     * track used to resolve to plain idle, which tore down the foreground notification - and with
     * it the watch-face chip and the proxy MediaSession - 30 seconds after the pause.
     */
    @Test
    fun `paused track with the ui closed holds the service instead of going idle`() {
        assertEquals(ServiceHold.PAUSED_TRACK, resolveServiceHold(
                uiOpen = false, musicPlaying = false, hasTrack = true))
    }

    @Test
    fun `no track at all is idle`() {
        assertEquals(ServiceHold.IDLE, resolveServiceHold(
                uiOpen = false, musicPlaying = false, hasTrack = false))
    }

    /** An open screen outranks idle: the user is looking at it. */
    @Test
    fun `open ui on the idle screen still counts as active`() {
        assertEquals(ServiceHold.ACTIVE, resolveServiceHold(
                uiOpen = true, musicPlaying = false, hasTrack = false))
    }
}
