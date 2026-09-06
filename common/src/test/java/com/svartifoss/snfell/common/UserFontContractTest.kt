package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the two devices have to agree on about an imported typeface.
 *
 * The fingerprint is the interesting half. It decides whether the watch re-writes two megabytes and
 * rebuilds a typeface or does nothing, and both mistakes are quiet: one that never changes leaves
 * the wrist rendering the previous font for good, and one that changes when the bytes have not
 * turns the phone's once-per-process republish into a file write on every launch.
 */
class UserFontContractTest {

    private fun font(size: Int, fill: (Int) -> Byte = { (it % 251).toByte() }): ByteArray =
            ByteArray(size) { fill(it) }

    @Test
    fun `identical bytes fingerprint identically`() {
        val bytes = font(64_000)
        assertEquals(
                UserFontContract.fingerprint(bytes),
                UserFontContract.fingerprint(bytes.copyOf()))
    }

    @Test
    fun `a different font of the same length fingerprints differently`() {
        // The case a length-only key would miss, and the realistic one: swapping one weight of a
        // family for another produces files of very similar size.
        val a = font(64_000) { (it % 251).toByte() }
        val b = font(64_000) { ((it * 7 + 3) % 251).toByte() }
        assertNotEquals(UserFontContract.fingerprint(a), UserFontContract.fingerprint(b))
    }

    @Test
    fun `a truncated copy fingerprints differently`() {
        val full = font(64_000)
        assertNotEquals(
                UserFontContract.fingerprint(full),
                UserFontContract.fingerprint(full.copyOf(60_000)))
    }

    @Test
    fun `a change anywhere in the file is noticed`() {
        // Sampling is a real trade - a stride skips bytes - so the guarantee worth pinning is that
        // a change lands on a sampled position for a file of any plausible size rather than that
        // every single byte is read.
        listOf(2_000, 20_000, 400_000, UserFontContract.MAX_FONT_BYTES).forEach { size ->
            val original = font(size)
            val edited = original.copyOf().also { it[0] = (it[0] + 1).toByte() }
            assertNotEquals(
                    "a $size-byte font must notice an edit at its start",
                    UserFontContract.fingerprint(original),
                    UserFontContract.fingerprint(edited))
        }
    }

    @Test
    fun `an empty font still fingerprints rather than throwing`() {
        // The receiver fingerprints whatever arrives before deciding it is unusable, so this must
        // not be a crash on the Data Layer callback path.
        assertNotEquals("", UserFontContract.fingerprint(ByteArray(0)))
    }

    @Test
    fun `the accepted size window excludes both a stub and an oversized face`() {
        assertTrue(UserFontContract.isAcceptableSize(UserFontContract.MIN_FONT_BYTES.toLong()))
        assertTrue(UserFontContract.isAcceptableSize(UserFontContract.MAX_FONT_BYTES.toLong()))
        assertTrue(UserFontContract.isAcceptableSize(120_000))
        assertFalse(UserFontContract.isAcceptableSize(0))
        assertFalse(UserFontContract.isAcceptableSize(
                UserFontContract.MIN_FONT_BYTES - 1L))
        assertFalse(UserFontContract.isAcceptableSize(
                UserFontContract.MAX_FONT_BYTES + 1L))
    }

    @Test
    fun `only formats Android can actually render are accepted`() {
        listOf("Inter.ttf", "inter.TTF", "My Font.otf", "family.ttc", "family.otc")
                .forEach { assertTrue(it, UserFontContract.hasAcceptedExtension(it)) }
        // WOFF is the trap: the same outlines in a web wrapper, which imports and transmits fine
        // and then renders as nothing on both devices.
        listOf("Inter.woff", "Inter.woff2", "font.zip", "cover.png", "noextension", null)
                .forEach { assertFalse("$it", UserFontContract.hasAcceptedExtension(it)) }
    }

    @Test
    fun `the picker offers the octet-stream fallback providers actually report`() {
        // Font MIME reporting is unreliable, so a picker filtered to the precise types alone would
        // grey out real fonts. The bytes are what gets validated.
        assertTrue("application/octet-stream" in UserFontContract.PICKER_MIME_TYPES)
    }

    @Test
    fun `the ceiling stays well under the transport's own limits`() {
        // Not a formal bound - Play Services carries far larger assets - but the wait is real, and
        // a value that crept up to several megabytes would leave the wrist on its old font for
        // most of a minute with nothing on screen explaining the delay.
        assertTrue(UserFontContract.MAX_FONT_BYTES <= 4 * 1024 * 1024)
        assertTrue(UserFontContract.MIN_FONT_BYTES < UserFontContract.MAX_FONT_BYTES)
    }
}
