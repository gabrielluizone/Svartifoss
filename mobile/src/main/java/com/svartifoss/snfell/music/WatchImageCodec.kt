package com.svartifoss.snfell.music

import android.graphics.Bitmap
import android.os.Build
import java.io.ByteArrayOutputStream

/**
 * How a photograph is encoded for its trip to the watch: lossy WebP.
 *
 * Covers used to travel as JPEG at quality 85. Measured on 2026-11-11 on real covers at the 466 px
 * a TicWatch Pro 5 asks for, WebP at [QUALITY] reproduces the picture as faithfully as that JPEG
 * did in about a third fewer bytes: 84 covers came out at 31.9 KB on average against 48.2 (PSNR
 * 34.7 dB against 34.6), and a second sample of 70 from the phone's own cache at 35.6 KB against
 * 51.1 (35.4 dB against 35.3). The 96 px and 320 px queue thumbnails come out 40% smaller at the
 * same or a slightly better PSNR. Pictures are nine tenths of what this app sends over Bluetooth,
 * so the format is the largest single factor in that traffic after not sending a picture twice
 * ([WatchCoverLedger]).
 *
 * Nothing on the watch has to change, and no older watch build is left behind: every picture is
 * decoded there by `BitmapFactory` (through `SampledBitmaps`), which reads the format from the
 * bytes and has read lossy WebP since long before the oldest Wear OS this app installs on.
 *
 * One place rather than a quality constant at each call site, because the Data Layer only spares a
 * transfer when two encodings of one picture are byte-identical - which they stop being the moment
 * two callers disagree about format or quality.
 */
object WatchImageCodec {

    /** See the class comment for the measurement behind this number. Below 100, so always lossy. */
    const val QUALITY = 80

    /**
     * For the one picture drawn full-bleed and unblurred behind a whole face, which was a JPEG at
     * quality 88 for that reason. On the 70 cached covers WebP at 85 matches it (36.7 dB against
     * 36.6, SSIM 0.958 against 0.958) in about a quarter fewer bytes (42.9 KB against 57.8).
     */
    const val BACKDROP_QUALITY = 85

    /**
     * `WEBP_LOSSY` exists from API 30. The constant it replaced is lossy at any quality below 100
     * on every version this app supports, and is what older phones are given.
     */
    @Suppress("DEPRECATION")
    private val format: Bitmap.CompressFormat
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    /** [bitmap] as lossy WebP, or null when the platform encoder refuses it. */
    fun encode(bitmap: Bitmap, quality: Int = QUALITY): ByteArray? =
            ByteArrayOutputStream().use { stream ->
                if (bitmap.compress(format, quality.coerceIn(0, 99), stream)) {
                    stream.toByteArray()
                } else {
                    null
                }
            }
}
