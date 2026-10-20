package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAudioBlockTest {

    private fun resolve(
            local: Boolean = true,
            dnd: Boolean = false,
            muted: Boolean = false,
            volume: Int = 7,
            held: Boolean = false
    ) = PhoneAudioBlock.resolve(local, dnd, muted, volume, held)

    @Test
    fun `nothing in the way is nothing to report`() {
        assertEquals(PhoneAudioBlock.NONE, resolve())
    }

    @Test
    fun `do not disturb wins over the muted stream it causes`() {
        assertEquals(PhoneAudioBlock.DO_NOT_DISTURB, resolve(dnd = true, muted = true, volume = 0))
    }

    @Test
    fun `a muted stream and a stream at zero are both muted`() {
        assertEquals(PhoneAudioBlock.MUTED, resolve(muted = true))
        assertEquals(PhoneAudioBlock.MUTED, resolve(volume = 0))
        assertEquals(PhoneAudioBlock.MUTED, resolve(volume = 0, held = true))
    }

    @Test
    fun `a held volume is reported only when nothing louder is wrong`() {
        assertEquals(PhoneAudioBlock.SAFE_VOLUME_HELD, resolve(held = true))
    }

    @Test
    fun `playback elsewhere is never blamed on the phone`() {
        assertEquals(PhoneAudioBlock.NONE, resolve(local = false, dnd = true, muted = true, volume = 0))
    }

    @Test
    fun `codes round-trip, and an unknown one reads as nothing`() {
        PhoneAudioBlock.values().forEach { assertEquals(it, PhoneAudioBlock.fromCode(it.code)) }
        assertEquals(PhoneAudioBlock.NONE, PhoneAudioBlock.fromCode(99))
        assertEquals(PhoneAudioBlock.NONE, PhoneAudioBlock.fromCode(-1))
    }

    @Test
    fun `codes are stable on the wire`() {
        assertEquals(0, PhoneAudioBlock.NONE.code)
        assertEquals(1, PhoneAudioBlock.DO_NOT_DISTURB.code)
        assertEquals(2, PhoneAudioBlock.MUTED.code)
        assertEquals(3, PhoneAudioBlock.SAFE_VOLUME_HELD.code)
    }

    @Test
    fun `the safety limit is a shortfall on headphones only`() {
        assertTrue(PhoneAudioBlock.heldBySafetyLimit(requested = 12, applied = 9, headphonesOutput = true))
        assertFalse(PhoneAudioBlock.heldBySafetyLimit(requested = 12, applied = 9, headphonesOutput = false))
        assertFalse(PhoneAudioBlock.heldBySafetyLimit(requested = 9, applied = 9, headphonesOutput = true))
        assertFalse(PhoneAudioBlock.heldBySafetyLimit(requested = 5, applied = 9, headphonesOutput = true))
    }
}
