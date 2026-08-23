package com.svartifoss.snfell.watch.view.queue

import com.svartifoss.snfell.watch.view.queue.QueueScrollPolicy.Move
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueScrollPolicyTest {

    // ---- resolve ----------------------------------------------------------

    /** The brief's own example: looking at row 3, playing row 5. */
    @Test
    fun `a nearby row is animated to`() {
        assertEquals(Move.ANIMATE, QueueScrollPolicy.resolve(centerIndex = 3, targetIndex = 5))
    }

    /** The other half of it: looking at row 3, playing row 80. */
    @Test
    fun `a distant row is jumped to`() {
        assertEquals(Move.JUMP, QueueScrollPolicy.resolve(centerIndex = 3, targetIndex = 80))
    }

    @Test
    fun `distance is symmetric - scrolling back behaves like scrolling forward`() {
        assertEquals(Move.ANIMATE, QueueScrollPolicy.resolve(centerIndex = 20, targetIndex = 18))
        assertEquals(Move.JUMP, QueueScrollPolicy.resolve(centerIndex = 80, targetIndex = 3))
    }

    @Test
    fun `the threshold itself still animates`() {
        val far = QueueScrollPolicy.ANIMATE_WITHIN_ROWS
        assertEquals(Move.ANIMATE, QueueScrollPolicy.resolve(0, far))
        assertEquals(Move.JUMP, QueueScrollPolicy.resolve(0, far + 1))
    }

    /**
     * The phone republishes the queue on every track change, so this is asked far more often than
     * the list actually needs to move. Re-running an animation over a list already in place would
     * be a visible twitch for no reason.
     */
    @Test
    fun `already centred does nothing`() {
        assertEquals(Move.NONE, QueueScrollPolicy.resolve(centerIndex = 12, targetIndex = 12))
    }

    /** Scrolling to the top because the song could not be located is a confident wrong answer. */
    @Test
    fun `an unknown row does nothing rather than going to the top`() {
        assertEquals(Move.NONE, QueueScrollPolicy.resolve(centerIndex = 40, targetIndex = -1))
    }

    // ---- activeRowIndex ---------------------------------------------------

    @Test
    fun `the flagged row wins`() {
        assertEquals(2, QueueScrollPolicy.activeRowIndex(
                playing = listOf(false, false, true, false),
                titles = listOf("A", "B", "C", "D"),
                nowPlayingTitle = "C"))
    }

    /**
     * The flag is what the highlight is drawn from, so preferring the title would let the screen
     * scroll to one row and light up another.
     */
    @Test
    fun `the flag beats a title that matches a different row`() {
        assertEquals(3, QueueScrollPolicy.activeRowIndex(
                playing = listOf(false, false, false, true),
                titles = listOf("Echo", "Echo", "Echo", "Echo"),
                nowPlayingTitle = "Echo"))
    }

    /** Plenty of players never publish activeQueueItemId; without this the queue would simply not
     *  find the song on those. */
    @Test
    fun `with no flag anywhere the title finds the row`() {
        assertEquals(1, QueueScrollPolicy.activeRowIndex(
                playing = listOf(false, false, false),
                titles = listOf("First", "Second", "Third"),
                nowPlayingTitle = "Second"))
    }

    @Test
    fun `the title fallback ignores case and surrounding space`() {
        assertEquals(2, QueueScrollPolicy.activeRowIndex(
                playing = listOf(false, false, false),
                titles = listOf("First", "Second", "  Third "),
                nowPlayingTitle = "third"))
    }

    @Test
    fun `no flag and no usable title is unknown`() {
        assertEquals(-1, QueueScrollPolicy.activeRowIndex(
                playing = listOf(false, false),
                titles = listOf("A", "B"),
                nowPlayingTitle = null))
        assertEquals(-1, QueueScrollPolicy.activeRowIndex(
                playing = listOf(false, false),
                titles = listOf("A", "B"),
                nowPlayingTitle = "   "))
    }

    /** The playing track can sit past the loaded prefix of a long queue - see QueuePaging. */
    @Test
    fun `a title that is not in the loaded page is unknown`() {
        assertEquals(-1, QueueScrollPolicy.activeRowIndex(
                playing = listOf(false, false),
                titles = listOf("A", "B"),
                nowPlayingTitle = "Track 300"))
    }

    @Test
    fun `an empty queue is unknown`() {
        assertEquals(-1, QueueScrollPolicy.activeRowIndex(
                playing = emptyList(), titles = emptyList(), nowPlayingTitle = "Anything"))
    }
}
