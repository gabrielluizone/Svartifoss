package com.svartifoss.snfell.watch.view.shortcut

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.SwatchInfo
import com.svartifoss.snfell.common.selectPrimaryAccent

/**
 * Everything the shortcut screen takes from a cover, worked out once: the backdrop as a bitmap
 * (the cover softened and toned, see [ShortcutGlowMath]) with the pixels it was made from, and the
 * cover's palette, from which its colour is chosen under the user's accent source.
 *
 * One pass for both, off the main thread, and kept by [obtain] for as long as the cover is: the
 * screen opened again on the same shortcut reuses it rather than blurring and extracting again,
 * and nothing about it is ever recomputed while the screen draws.
 */
internal class ShortcutGlow private constructor(
        /** The backdrop, drawn stretched over the whole screen. Colour only - the masks that close
         *  it to black are drawn by the screen. */
        val bitmap: Bitmap,
        /** The [ShortcutGlowMath.SAMPLE_PX]-square grid [bitmap] was made from, for measuring what
         *  colour the backdrop is under a line of text. */
        val pixels: IntArray,
        private val vibrant: SwatchInfo?,
        private val swatches: List<SwatchInfo>
) {
    /**
     * The cover's colour as the screen uses it: chosen the way the player chooses an album's
     * ([selectPrimaryAccent]), then null when it is black, white or grey
     * ([ShortcutPalette.usableAccent]). Cheap - the extraction already happened.
     */
    fun accent(source: AlbumAccentSource): Int? =
            ShortcutPalette.usableAccent(selectPrimaryAccent(vibrant, swatches, source))

    companion object {
        /**
         * The side of the bitmap handed to Compose. The blur happens at [ShortcutGlowMath.SAMPLE_PX]
         * and is stretched once to this, so the screen's own scaling (about 1.8x on a 466 px watch)
         * starts from a smooth picture instead of stretching a 64 px one seven times over.
         */
        private const val OUTPUT_PX = 256

        /** Covers whose glow is kept - a handful, since each holds a 256 px bitmap. */
        private const val CACHE_SIZE = 4

        private val cache = object : LinkedHashMap<Bitmap, ShortcutGlow>(CACHE_SIZE, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Bitmap, ShortcutGlow>?) =
                    size > CACHE_SIZE
        }

        /** [cover]'s glow if it was already worked out - for the first frame, without waiting. */
        fun cached(cover: Bitmap): ShortcutGlow? = synchronized(cache) {
            cache[cover]?.takeUnless { cover.isRecycled }
        }

        /** [cover]'s glow, worked out now if it has not been. Blocking: call it off the main thread.
         *  Keyed by the bitmap itself: the watch keeps one decoded bitmap per cover while its list
         *  is unchanged, so the same cover is the same object. */
        fun obtain(cover: Bitmap): ShortcutGlow? {
            cached(cover)?.let { return it }
            val glow = from(cover) ?: return null
            synchronized(cache) { cache[cover] = glow }
            return glow
        }

        /** [cover]'s glow, or null when it has no pixels to read (recycled, empty, or a bitmap the
         *  platform will not hand its pixels over for) - the screen then stays black. */
        private fun from(cover: Bitmap): ShortcutGlow? = try {
            if (cover.isRecycled || cover.width <= 0 || cover.height <= 0) {
                null
            } else {
                val side = ShortcutGlowMath.SAMPLE_PX
                val thumbnail = thumbnail(cover, side)
                val pixels = IntArray(side * side)
                thumbnail.getPixels(pixels, 0, side, 0, 0, side, side)
                // The colour comes from the cover as it is, before the blur and the tone: those
                // are the backdrop's treatment, not the cover's colour.
                val palette = Palette.from(thumbnail).generate()
                val glow = ShortcutGlowMath.tone(
                        ShortcutGlowMath.blur(pixels, side, side, ShortcutGlowMath.SIGMA_PX))
                val small = Bitmap.createBitmap(glow, side, side, Bitmap.Config.ARGB_8888)
                ShortcutGlow(
                        bitmap = Bitmap.createScaledBitmap(small, OUTPUT_PX, OUTPUT_PX, true),
                        pixels = glow,
                        vibrant = palette.vibrantSwatch?.let { SwatchInfo(it.rgb, it.population) },
                        swatches = palette.swatches.map { SwatchInfo(it.rgb, it.population) })
            }
        } catch (_: RuntimeException) {
            null
        }

        /**
         * [cover] cropped to a centred square and halved until one more halving would go under
         * [side], then scaled to exactly [side]. Halving rather than one big step: a bilinear
         * reduction of more than 2x skips pixels instead of averaging them, and the glow would
         * pick up the aliasing the blur is there to remove.
         */
        private fun thumbnail(cover: Bitmap, side: Int): Bitmap {
            val crop = minOf(cover.width, cover.height)
            var current = if (cover.width == cover.height) {
                cover
            } else {
                Bitmap.createBitmap(
                        cover, (cover.width - crop) / 2, (cover.height - crop) / 2, crop, crop)
            }
            while (current.width >= side * 2) {
                current = Bitmap.createScaledBitmap(
                        current, current.width / 2, current.height / 2, true)
            }
            return Bitmap.createScaledBitmap(current, side, side, true)
        }
    }
}
