package com.svartifoss.snfell.music

import com.google.protobuf.ByteString
import com.svartifoss.snfell.proto.MediaAction
import com.svartifoss.snfell.proto.MusicState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicStateDedupeTest {

    private val playing: MusicState = MusicState.newBuilder()
            .setTitle("Suicide")
            .setArtist("Decalius")
            .setSourceAppName("YouTube Music")
            .setPlaying(true)
            .setVolume(0.56f)
            .setDurationMs(225_721L)
            .setPositionMs(60_000L)
            .setPositionUpdateTime(1_000_000L)
            .setPlaybackSpeed(1f)
            .addMediaActions(action(iconSeed = 1))
            .build()

    private fun action(iconSeed: Int): MediaAction = MediaAction.newBuilder()
            .setId("like")
            .setLabel("Like")
            .setSemantic("like")
            .setIconPng(ByteString.copyFrom(ByteArray(64) { (it * iconSeed).toByte() }))
            .build()

    /** What the dedupe exists for: a player reporting its position once a second, stamped anew. */
    @Test
    fun `a position that moved with the clock is nothing new`() {
        val oneSecondLater = playing.toBuilder()
                .setPositionMs(61_000L)
                .setPositionUpdateTime(1_001_000L)
                .setTime(2)
                .setSeq(9L)
                .setPositionAgeMs(30L)
                .build()

        assertNull(MusicStateDedupe.firstDifference(playing, oneSecondLater))
    }

    /** The track-boundary burst in the investigation: the new track briefly reports the old
     *  track's position, so each state lands far from where the previous one predicted. */
    @Test
    fun `a jump past the threshold names the expected and the actual position`() {
        val jumped = playing.toBuilder().setPositionMs(0L).setPositionUpdateTime(1_001_000L).build()

        assertEquals("position expected 61000 ms, got 0 ms",
                MusicStateDedupe.firstDifference(playing, jumped))
    }

    @Test
    fun `jitter inside the threshold is not a seek`() {
        val jittered = playing.toBuilder().setPositionMs(62_400L).setPositionUpdateTime(1_001_000L).build()

        assertNull(MusicStateDedupe.firstDifference(playing, jittered))
    }

    @Test
    fun `a paused state is expected to stand still`() {
        val paused = playing.toBuilder().setPlaying(false).build()
        val later = paused.toBuilder().setPositionUpdateTime(1_010_000L).build()
        val moved = later.toBuilder().setPositionMs(62_000L).build()

        assertNull(MusicStateDedupe.firstDifference(paused, later))
        assertEquals("position expected 60000 ms, got 62000 ms",
                MusicStateDedupe.firstDifference(paused, moved))
    }

    @Test
    fun `a state with nothing before it is new`() {
        assertEquals("no previous state", MusicStateDedupe.firstDifference(null, playing))
    }

    @Test
    fun `a changed field is named with both values`() {
        assertEquals("title 'Suicide' -> 'in aeternum'", MusicStateDedupe.firstDifference(
                playing, playing.toBuilder().setTitle("in aeternum").build()))
        assertEquals("volume 0.56 -> 0.5", MusicStateDedupe.firstDifference(
                playing, playing.toBuilder().setVolume(0.5f).build()))
        assertEquals("live false -> true", MusicStateDedupe.firstDifference(
                playing, playing.toBuilder().setLive(true).build()))
        assertEquals("audio block 0 -> 2", MusicStateDedupe.firstDifference(
                playing, playing.toBuilder().setAudioBlock(2).build()))
    }

    /** A player at rest may report its speed as -0 one time and 0 the next. */
    @Test
    fun `a speed of minus zero is the speed zero`() {
        val stopped = playing.toBuilder().setPlaybackSpeed(0f).build()

        assertNull(MusicStateDedupe.firstDifference(stopped, stopped.toBuilder().setPlaybackSpeed(-0f).build()))
    }

    /** One of the investigation's hypotheses: icons rasterized to different bytes on every
     *  notification update would defeat the dedupe while looking the same on the wrist. */
    @Test
    fun `an icon rendered to different bytes is named with its size and digest`() {
        val rerendered = playing.toBuilder().setMediaActions(0, action(iconSeed = 3)).build()

        val difference = MusicStateDedupe.firstDifference(playing, rerendered)

        assertTrue("$difference", difference!!.startsWith("action #0 icon 64 B "))
        assertTrue("$difference", difference.contains(" -> 64 B "))
    }

    /** With the icon sent by key, a like toggled in the player is a new key and nothing else. */
    @Test
    fun `an icon sent by key is compared by its key`() {
        val keyed = playing.toBuilder()
                .setMediaActions(0, action(iconSeed = 1).toBuilder().clearIconPng().setIconKey("aaaa"))
                .build()
        val toggled = keyed.toBuilder()
                .setMediaActions(0, keyed.getMediaActions(0).toBuilder().setIconKey("bbbb"))
                .build()

        assertNull(MusicStateDedupe.firstDifference(keyed, keyed.toBuilder().build()))
        assertEquals("action #0 icon key aaaa -> bbbb", MusicStateDedupe.firstDifference(keyed, toggled))
    }

    @Test
    fun `an action that comes or goes is counted`() {
        val more = playing.toBuilder().addMediaActions(action(iconSeed = 2)).build()

        assertEquals("actions 1 -> 2", MusicStateDedupe.firstDifference(playing, more))
    }

    @Test
    fun `fields that change on every build are not compared`() {
        val restamped = playing.toBuilder()
                .setTime(99)
                .setSeq(1234L)
                .setPositionAgeMs(800L)
                .build()

        assertNull(MusicStateDedupe.firstDifference(playing, restamped))
    }
}
