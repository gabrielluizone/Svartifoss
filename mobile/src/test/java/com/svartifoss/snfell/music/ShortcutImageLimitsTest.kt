package com.svartifoss.snfell.music

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ShortcutImageLimitsTest {
    @Test fun acceptsAnExactLimitAndEmptyStream() {
        assertArrayEquals(byteArrayOf(1, 2), ShortcutImageLimits.read(ByteArrayInputStream(byteArrayOf(1, 2)), 2))
        assertEquals(0, ShortcutImageLimits.read(ByteArrayInputStream(byteArrayOf()), 0).size)
    }

    @Test(expected = IOException::class)
    fun rejectsAStreamWithoutDependingOnContentLength() {
        ShortcutImageLimits.read(ByteArrayInputStream(ByteArray(101)), 100)
    }

    @Test fun boundsSquareAndPanoramicImagesBeforeDecoding() {
        listOf(4000 to 4000, 100000 to 1, 1 to 100000, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (w, h) ->
            val sample = ShortcutImageLimits.sampleSize(w, h)!!
            assertTrue((maxOf(w, h).toLong() + sample - 1) / sample <= 960)
        }
        assertEquals(1, ShortcutImageLimits.sampleSize(480, 480))
        assertNull(ShortcutImageLimits.sampleSize(-1, 100))
        assertNull(ShortcutImageLimits.sampleSize(100, 0))
    }
}
