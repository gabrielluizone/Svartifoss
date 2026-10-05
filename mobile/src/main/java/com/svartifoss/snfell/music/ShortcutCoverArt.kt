package com.svartifoss.snfell.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.svartifoss.snfell.R
import kotlin.math.roundToInt

/**
 * Draws the covers a streaming destination gets when no picture of it was fetched: a gradient with
 * a white mark in the middle, the way the services themselves draw a liked-songs collection.
 *
 * Two kinds. A [StreamingCollection] gets its own design (a thumbs-up for YouTube Music's Liked
 * Music, a heart for Spotify's Liked Songs). Any other link gets its service's colour with the
 * service's mark on it ([service]) - so a playlist whose cover was never looked up still says which
 * player it opens in, instead of standing in a tile of one fixed colour.
 *
 * The glyphs are Material Symbols (see `icons/`), not the services' artwork: nothing here copies an
 * image anyone published. Covers are cached in memory - they cost a few milliseconds to draw, but
 * list rows ask for them on every bind.
 */
object ShortcutCoverArt {

    /** Enough for the shortcut screen's cover on any watch, and small enough for a list row's PNG. */
    const val SIZE_PX = 256

    /** The share of the cover a white glyph spans - large enough to survive a 40dp circular row. */
    private const val GLYPH_FRACTION = .44f

    /**
     * A player's notification glyph arrives with a margin of its own inside its square (see
     * `MediaNotificationActions`), so it is given more of the cover to come out the same size.
     */
    private const val APP_GLYPH_FRACTION = .58f

    /** A launcher icon carries its own background shape, so it stands a little larger. */
    private const val LAUNCHER_ICON_FRACTION = .5f

    private val cache = object : android.util.LruCache<String, Bitmap>(12) {}

    fun collection(context: Context, collection: StreamingCollection): Bitmap {
        val key = "collection:" + collection.name
        cache.get(key)?.let { return it }
        val glyph = AppCompatResources.getDrawable(context, glyphFor(collection))!!
        val fraction = if (collection == StreamingCollection.SPOTIFY_LIKED) .40f else GLYPH_FRACTION
        return draw(collection.cover, glyph, tintWhite = true, fraction).also { cache.put(key, it) }
    }

    /**
     * [service]'s cover with [mark] - the app's mark - in the middle. For a service the palette
     * does not know, the colour comes from the mark itself; with neither, there is nothing to draw.
     *
     * [markKey] identifies the mark for caching (a package name and which of its marks it is), so a
     * glyph learned later replaces the launcher icon instead of being masked by the cached cover.
     */
    fun service(
            service: StreamingService,
            mark: ShortcutAppMark,
            markKey: String
    ): Bitmap? {
        val key = "service:${service.name}:$markKey:${mark.tintable}"
        cache.get(key)?.let { return it }
        val gradient = ServiceCoverPalette.gradientFor(service)
                ?: gradientFromIcon(mark)
                ?: return null
        val fraction = if (mark.tintable) APP_GLYPH_FRACTION else LAUNCHER_ICON_FRACTION
        return draw(gradient, mark.drawable, tintWhite = mark.tintable, fraction)
                .also { cache.put(key, it) }
    }

    private fun glyphFor(collection: StreamingCollection): Int = when (collection) {
        StreamingCollection.YOUTUBE_MUSIC_LIKED -> R.drawable.cover_glyph_thumb_up
        StreamingCollection.SPOTIFY_LIKED,
        StreamingCollection.SOUNDCLOUD_LIKES -> R.drawable.cover_glyph_favorite
        StreamingCollection.DEEZER_FLOW -> R.drawable.cover_glyph_graphic_eq
    }

    private fun draw(
            gradient: CoverGradient,
            glyph: Drawable,
            tintWhite: Boolean,
            fraction: Float
    ): Bitmap {
        val size = SIZE_PX.toFloat()
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (endX, endY) = when (gradient.direction) {
            CoverGradient.Direction.VERTICAL -> 0f to size
            CoverGradient.Direction.DIAGONAL -> size to size
        }
        canvas.drawRect(0f, 0f, size, size, Paint().apply {
            shader = LinearGradient(0f, 0f, endX, endY, gradient.start, gradient.end,
                    Shader.TileMode.CLAMP)
        })

        // Rasterised at its final size first rather than drawn through setBounds, so a vector and
        // a cached glyph PNG go through one path, and a mutable copy can be tinted without
        // touching the drawable a list row is also showing.
        val side = (size * fraction).roundToInt().coerceAtLeast(1)
        val glyphBitmap = glyph.toBitmap(side, side, Bitmap.Config.ARGB_8888)
        val offset = (size - side) / 2f
        canvas.drawBitmap(glyphBitmap, offset, offset, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (tintWhite) {
                colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE,
                        PorterDuff.Mode.SRC_IN)
            }
        })
        return bitmap
    }

    /**
     * A gradient in the colour of an app's own launcher icon, for a link to an app the palette does
     * not know: its most vivid colour, deepened towards the far corner the way the table's pairs
     * are. Null for a template glyph (it is white) and for an icon with no colour worth using.
     */
    private fun gradientFromIcon(mark: ShortcutAppMark): CoverGradient? {
        if (mark.tintable) return null
        val icon = mark.drawable.toBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val palette = Palette.from(icon).generate()
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: return null
        val hsl = swatch.hsl.copyOf()
        if (hsl[1] < .15f) return null
        val start = FloatArray(3).also {
            it[0] = hsl[0]; it[1] = hsl[1].coerceIn(.35f, .85f); it[2] = hsl[2].coerceIn(.45f, .60f)
        }
        val end = start.copyOf().also { it[2] = (start[2] - .30f).coerceAtLeast(.14f) }
        return CoverGradient(ColorUtils.HSLToColor(start), ColorUtils.HSLToColor(end),
                CoverGradient.Direction.DIAGONAL)
    }
}
