package com.svartifoss.snfell.music

import com.svartifoss.snfell.music.MusicTransmitPacer.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTransmitPacerTest {

    /** A track change must reach the wrist as fast as it always has. */
    @Test
    fun `the first state goes at once`() {
        assertEquals(Decision.Send, MusicTransmitPacer().decide(nowMs = 50_000L))
    }

    @Test
    fun `a state inside the spacing waits for its end`() {
        val pacer = MusicTransmitPacer()
        pacer.sent(nowMs = 10_000L)

        assertEquals(Decision.Wait(atMs = 11_000L, overBudget = false), pacer.decide(nowMs = 10_120L))
        assertEquals(Decision.Send, pacer.decide(nowMs = 11_000L))
    }

    /** The burst measured on 2026-10-28: six states inside half a second after a track change. */
    @Test
    fun `a burst of callbacks costs two transmissions`() {
        val pacer = MusicTransmitPacer()
        var sent = 0
        var pendingAt: Long? = null
        for (at in listOf(0L, 46L, 90L, 116L, 316L, 488L)) {
            when (val decision = pacer.decide(at)) {
                Decision.Send -> { pacer.sent(at); sent++ }
                is Decision.Wait -> pendingAt = decision.atMs
            }
        }
        // The caller re-reads the session when the wait ends and sends what it says by then.
        val trailing = pendingAt!!
        assertEquals(Decision.Send, pacer.decide(trailing))
        pacer.sent(trailing)
        sent++

        assertEquals(2, sent)
        assertEquals(1_000L, trailing)
    }

    @Test
    fun `an empty budget slows a sustained cause to the refill rate`() {
        val pacer = MusicTransmitPacer(minIntervalMs = 1_000L, budget = 3, refillMs = 4_000L)
        var now = 0L
        repeat(3) {
            assertEquals(Decision.Send, pacer.decide(now))
            pacer.sent(now)
            now += 1_000L
        }

        val held = pacer.decide(now)

        assertTrue("$held", held is Decision.Wait && held.overBudget)
        // Three spent at 0, 1 and 2 s; by 3 s 0.75 of a token has come back, so the next whole
        // one is due a second later.
        assertEquals(4_000L, (held as Decision.Wait).atMs)
        assertEquals(Decision.Send, pacer.decide(4_000L))
    }

    @Test
    fun `the budget refills while nothing is sent`() {
        val pacer = MusicTransmitPacer(minIntervalMs = 1_000L, budget = 2, refillMs = 4_000L)
        pacer.sent(0L)
        pacer.sent(1_000L)

        assertEquals(Decision.Send, pacer.decide(nowMs = 60_000L))
        pacer.sent(60_000L)
        assertEquals(Decision.Wait(atMs = 61_000L, overBudget = false), pacer.decide(nowMs = 60_500L))
    }

    /** Continuous playback: a track change every three minutes never touches the budget. */
    @Test
    fun `ordinary playback is never over budget`() {
        val pacer = MusicTransmitPacer()
        var now = 0L
        repeat(200) {
            listOf(0L, 1_000L).forEach { offset ->
                val decision = pacer.decide(now + offset)
                assertEquals(Decision.Send, decision)
                pacer.sent(now + offset)
            }
            now += 180_000L
        }
    }
}
