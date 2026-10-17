package com.svartifoss.snfell.common

import com.svartifoss.snfell.proto.MusicState
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicStateDisplayTest {
    @Test
    fun actualArtistWinsOverSourceAppName() {
        val state = MusicState.newBuilder()
                .setArtist("Thirteen Senses")
                .setSourceAppName("Symfonium")
                .setPlaying(true)
                .build()

        assertEquals("Thirteen Senses", state.displayArtist())
    }

    @Test
    fun sourceAppNameFillsMissingArtist() {
        val state = MusicState.newBuilder()
                .setSourceAppName("YouTube Music")
                .setPlaying(true)
                .build()

        assertEquals("YouTube Music", state.displayArtist())
    }

    @Test
    fun missingArtistAndSourceRemainEmpty() {
        assertEquals("", MusicState.getDefaultInstance().displayArtist())
    }
}
