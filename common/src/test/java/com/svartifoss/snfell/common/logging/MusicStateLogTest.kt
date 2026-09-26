package com.svartifoss.snfell.common.logging

import com.google.protobuf.ByteString
import com.svartifoss.snfell.proto.MediaAction
import com.svartifoss.snfell.proto.MusicState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicStateLogTest {

    @Test
    fun `a state is summarised on one line with what the logs are read for`() {
        val state = MusicState.newBuilder()
                .setTitle("Song")
                .setArtist("Band")
                .setPlaying(true)
                .setPositionMs(1_000L)
                .setDurationMs(200_000L)
                .setVolume(0.5f)
                .setSeq(42L)
                .setAlbumArtPending(true)
                .build()

        assertEquals("'Song' by 'Band' playing at 1000/200000ms vol=0.5 seq=42 art-pending",
                state.logSummary())
    }

    /** The reason this exists: the protobuf's own toString spelled every icon byte out as text. */
    @Test
    fun `action icons are counted, never printed`() {
        val icon = ByteString.copyFrom(ByteArray(4_096) { it.toByte() })
        val state = MusicState.newBuilder()
                .setPlaying(false)
                .addMediaActions(MediaAction.newBuilder().setId("a").setLabel("Like").setIconPng(icon))
                .addMediaActions(MediaAction.newBuilder().setId("b").setLabel("Shuffle").setIconPng(icon))
                .build()

        val summary = state.logSummary()
        assertTrue(summary, summary.endsWith(" actions=2"))
        assertFalse(summary, summary.contains("Like"))
        assertTrue("summary is ${summary.length} chars", summary.length < 120)
    }

    @Test
    fun `an error and a missing state say so`() {
        val error = MusicState.newBuilder().setPlaying(false).setError(true).setTitle("No phone").build()

        assertTrue(error.logSummary().contains(" ERROR"))
        assertEquals("none", (null as MusicState?).logSummary())
    }
}
