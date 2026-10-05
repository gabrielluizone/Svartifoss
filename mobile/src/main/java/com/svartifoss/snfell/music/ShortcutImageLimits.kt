package com.svartifoss.snfell.music

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Bounds compressed downloads as well as decoded pixels, including very wide images. */
internal object ShortcutImageLimits {
    const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    const val MAX_METADATA_BYTES = 256 * 1024
    private const val MAX_DECODED_SIDE = 960

    fun read(input: InputStream, limit: Int): ByteArray {
        require(limit >= 0)
        val buffer = ByteArray(8192)
        return ByteArrayOutputStream().use { output ->
            while (true) {
                // Read at most one byte beyond the limit, including streams without a length.
                val count = input.read(buffer, 0, minOf(buffer.size - 1, limit - output.size()) + 1)
                if (count == -1) break
                if (count > limit - output.size()) throw IOException("Shortcut artwork is too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    fun sampleSize(width: Int, height: Int): Int? {
        if (width <= 0 || height <= 0) return null
        var sample = 1
        val longest = maxOf(width, height).toLong()
        while ((longest + sample - 1) / sample > MAX_DECODED_SIDE) sample *= 2
        return sample
    }
}
