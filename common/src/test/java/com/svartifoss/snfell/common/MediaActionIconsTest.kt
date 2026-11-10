package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaActionIconsTest {

    private val like = ByteArray(4_096) { (it * 7).toByte() }

    /** What lets the phone keep no record of what the watch holds. */
    @Test
    fun `the same bytes always make the same key`() {
        assertEquals(MediaActionIcons.keyFor(like), MediaActionIcons.keyFor(like.copyOf()))
    }

    @Test
    fun `any change in the bytes makes a new key`() {
        val unlike = like.copyOf().also { it[2_000] = (it[2_000] + 1).toByte() }

        assertNotEquals(MediaActionIcons.keyFor(like), MediaActionIcons.keyFor(unlike))
    }

    @Test
    fun `a key is sixteen lowercase hex digits`() {
        val key = MediaActionIcons.keyFor(like)

        assertTrue(key, Regex("[0-9a-f]{16}").matches(key))
    }

    /** SHA-256 of the empty input begins e3b0c442 98fc1c14 - pins the encoding, not only its shape. */
    @Test
    fun `the key is the start of the SHA-256 digest`() {
        assertEquals("e3b0c44298fc1c14", MediaActionIcons.keyFor(ByteArray(0)))
    }

    @Test
    fun `the asset is named after the key`() {
        assertEquals("ActionIcon_e3b0c44298fc1c14", MediaActionIcons.assetName("e3b0c44298fc1c14"))
    }
}
