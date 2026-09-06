package com.svartifoss.snfell.watch.communication

import com.svartifoss.snfell.proto.MusicState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSnapshotOrderTest {
    private fun snapshot(sequence: Long, pending: Boolean = false): MusicState =
            MusicState.newBuilder().setPlaying(true).setSeq(sequence)
                    .setAlbumArtPending(pending).build()

    @Test
    fun `completed artwork wins over its interim snapshot in either buffer order`() {
        val interim = snapshot(10, pending = true)
        val settled = snapshot(10)
        assertTrue(shouldReplaceMusicSnapshot(settled, interim))
        assertFalse(shouldReplaceMusicSnapshot(interim, settled))
    }

    @Test
    fun `newer track supersedes completed artwork from an older track`() {
        val oldArt = snapshot(10)
        val newTrack = snapshot(11, pending = true)
        assertTrue(shouldReplaceMusicSnapshot(newTrack, oldArt))
        assertFalse(shouldReplaceMusicSnapshot(oldArt, newTrack))
    }

    @Test
    fun `artwork finishing after a newer message cannot publish`() {
        assertTrue(isCurrentMusicSnapshot(sequence = 10, lastAppliedSequence = 10))
        // A fast message advances the state while the asset fetch is suspended.
        assertFalse(isCurrentMusicSnapshot(sequence = 10, lastAppliedSequence = 11))
        assertTrue(isCurrentMusicSnapshot(sequence = 11, lastAppliedSequence = 11))
    }

    @Test
    fun `unversioned older phone builds remain compatible`() {
        assertTrue(isCurrentMusicSnapshot(sequence = 0, lastAppliedSequence = 11))
    }

    @Test
    fun `late interim callback cannot cancel completed artwork decoding`() {
        val order = MusicAssetOrder()
        assertTrue(order.accept(snapshot(10, pending = true)))
        assertTrue(order.accept(snapshot(10)))
        assertFalse(order.accept(snapshot(10, pending = true)))
        assertFalse(order.accept(snapshot(9)))
        assertTrue(order.accept(snapshot(11, pending = true)))
        assertTrue(order.accept(snapshot(11)))
    }

    @Test
    fun `reconnect can reload same completed revision after a cancelled load`() {
        val order = MusicAssetOrder()
        assertTrue(order.accept(snapshot(10)))
        assertTrue(order.accept(snapshot(10)))
    }
}
