package com.svartifoss.snfell.view.watchface.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import com.svartifoss.snfell.view.watchface.PreviewButtonIcons
import com.svartifoss.snfell.view.watchface.WatchPreviewView
import com.svartifoss.snfell.view.watchface.theme.WatchThemeProfile
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Draws watch themes, and single choices within the active theme, as pictures - with the same code
 * that draws the Watch tab's preview.
 *
 * One [WatchPreviewView] that is never attached to a window, measured once and drawn into bitmaps:
 * the arrangement `SubmitCommunityThemeActivity.renderModerationPreview` already uses. A single
 * renderer rather than one live preview per tile, because each preview extracts the cover's
 * palette and blurs it for itself; here that is done once and every picture shares it, and a strip
 * or a grid then scrolls as plain images.
 *
 * Two kinds of picture:
 *
 *  - a **look** ([request]): a whole theme from a profile, on the cover that is playing, with the
 *    sample title. Kept in an in-memory cache, since the theme strip shows the same ones for as
 *    long as the tab is open;
 *  - a **choice** ([requestChoice]): the active theme with one value changed, on the screen that
 *    value belongs to - the candidate mechanism the editors already use to paint a picked value on
 *    the docked preview before it is stored, so a picture and the result of picking it come from
 *    the same code. Handed to the caller only; a picker keeps its pictures while it is open.
 *
 * Rendering stays on the main thread - a View is not safe to draw anywhere else - one picture per
 * posted message, so a long strip or grid never holds a frame. Nothing is written to disk or
 * uploaded, and the moderation upload path is not involved.
 *
 * The renderer runs in the preview's moderation mode, which fixes the clock and the animation
 * phase. A cached picture showing the time it was drawn would disagree with the clock beside it a
 * minute later, and a title caught mid-scroll would look broken rather than animated.
 */
internal class WatchLookThumbnailer(context: Context) {

    /** Answers one [request] once its picture exists. */
    fun interface Listener {
        fun onThumbnail(key: String, thumbnail: Bitmap)
    }

    private class Job(val draw: () -> Bitmap?, val onDrawn: (Bitmap) -> Unit)

    /** One size for every consumer; an ImageView scales it down, so the renderer is laid out once. */
    val sizePx: Int = (THUMBNAIL_DP * context.resources.displayMetrics.density).roundToInt()

    private val renderer = WatchPreviewView(context).apply {
        setModerationPreviewMode(true)
        val spec = View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY)
        measure(spec, spec)
        layout(0, 0, sizePx, sizePx)
    }

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Pictures that are out of date but still shown until their replacement is drawn. Without
     * them a new song would blank every tile at once and fill the strip back in one by one.
     */
    private val stale = HashMap<String, Bitmap>()

    private val handler = Handler(Looper.getMainLooper())
    private val pending = LinkedHashMap<String, Job>()
    private var draining = false
    private var released = false

    /** True while a list of these pictures is moving under a finger or a fling - see [hold]. */
    private var held = false

    /** Whoever shows the looks; told about each one as it is drawn. */
    var listener: Listener? = null

    /** What is playing now, whose cover every picture is drawn on. */
    private var artwork: Bitmap? = null

    private var deviceRound: Boolean? = null
    private var deviceWidthPx = 0
    private var deviceHeightPx = 0
    private var deviceDensity = 1f

    /** The picture to show for [key]: the current one, or the out-of-date one until it is replaced. */
    fun picture(key: String): Bitmap? = cache.get(key) ?: stale[key]

    /** Whether [key]'s picture is up to date; if not, [request] it. */
    fun isFresh(key: String): Boolean = cache.get(key) != null

    /**
     * Queues the picture of the look [key] unless a current one exists or one is already queued.
     * [profile] is read only when its turn comes, so the theme is drawn as it is then, not as it
     * was when it was asked for.
     */
    fun request(key: String, profile: () -> WatchThemeProfile?) {
        if (released || cache.get(key) != null || key in pending) return
        pending[key] = Job(
                draw = { profile()?.let(::renderLook) },
                onDrawn = { picture ->
                    stale.remove(key)
                    cache.put(key, picture)
                    listener?.onThumbnail(key, picture)
                })
        scheduleNext()
    }

    /**
     * Queues a picture of the active theme with [key] set to [value], for a picker. [companions]
     * are the other values picking it would write, drawn with it. [onDrawn] gets the picture;
     * nothing is cached here, so a picker that closes takes its pictures with it.
     */
    fun requestChoice(
            key: String,
            value: String,
            companions: Map<String, Any?> = emptyMap(),
            onDrawn: (Bitmap) -> Unit
    ) {
        if (released) return
        val jobKey = choiceKey(key, value)
        if (jobKey in pending) return
        pending[jobKey] = Job(draw = { renderChoice(key, value, companions) }, onDrawn = onDrawn)
        scheduleNext()
    }

    /**
     * Holds the queue while a list of pictures is moving, and lets it go when the list rests.
     *
     * A picture is drawn on the main thread and takes longer than a frame, so drawing while a grid
     * is flung cost a frame per picture: measured on a Pixel 3 over the 71 album art styles, one
     * frame in eight missed its deadline. Cells that scroll past are cancelled anyway, so what is
     * drawn once the list rests is only what is on screen.
     */
    fun hold(holding: Boolean) {
        if (held == holding) return
        held = holding
        if (!holding) scheduleNext()
    }

    /** Forgets a queued choice that scrolled away before its turn came. */
    fun cancelChoice(key: String, value: String) {
        pending.remove(choiceKey(key, value))
    }

    /** Forgets every queued choice; the picker that asked for them has closed. */
    fun cancelChoices() {
        pending.keys.removeAll { it.startsWith(CHOICE_PREFIX) }
    }

    /** Marks [key]'s picture out of date; it stays on screen until the next [request] replaces it. */
    fun invalidate(key: String) {
        cache.remove(key)?.let { stale[key] = it }
    }

    fun invalidateAll() {
        cache.snapshot().forEach { (key, picture) -> stale[key] = picture }
        cache.evictAll()
    }

    /**
     * The cover now playing, or null for the bundled sample. A different picture makes every
     * look stale, because each one shows that theme on this cover.
     */
    fun setArtwork(art: Bitmap?): Boolean {
        if (art === artwork) return false
        artwork = art
        renderer.setNowPlaying(art, null, null)
        invalidateAll()
        return true
    }

    private var buttonIcons: PreviewButtonIcons? = null
    private var sourceGlyph: Bitmap? = null
    private var sourceGlyphTintable = true

    /**
     * The user's own mini buttons (and quadrant and quick-panel icons), from the same action config
     * the docked preview reads. Without them every picture showed a watch with no mini buttons -
     * a layout grid in which the text sat lower than it will on the wrist, and a mini-button
     * picker that could only draw sample icons. Returns whether the pictures went out of date.
     */
    fun setButtonIcons(icons: PreviewButtonIcons): Boolean {
        if (icons == buttonIcons) return false
        buttonIcons = icons
        renderer.setButtonIcons(icons)
        invalidateAll()
        return true
    }

    /** The playing app's mark, as the docked preview has it. Returns whether pictures went stale. */
    fun setSourceGlyph(glyph: Bitmap?, tintable: Boolean): Boolean {
        // Compared by content: the Watch tab converts the app's drawable afresh on every media
        // change, and redrawing every picture for the same mark would cost a frame per tile.
        val same = glyph === sourceGlyph ||
                (glyph != null && sourceGlyph?.let { glyph.sameAs(it) } == true)
        if (same && tintable == sourceGlyphTintable) return false
        sourceGlyph = glyph
        sourceGlyphTintable = tintable
        renderer.setSourceGlyph(glyph, tintable)
        invalidateAll()
        return true
    }

    /** The connected watch's shape, so a square watch is not shown its themes on a round one. */
    fun setDeviceProfile(round: Boolean?, widthPx: Int, heightPx: Int, density: Float): Boolean {
        if (round == deviceRound && widthPx == deviceWidthPx && heightPx == deviceHeightPx &&
                density == deviceDensity) {
            return false
        }
        deviceRound = round
        deviceWidthPx = widthPx
        deviceHeightPx = heightPx
        deviceDensity = density
        renderer.setDeviceProfile(round, widthPx, heightPx, density)
        invalidateAll()
        return true
    }

    /** Stops drawing and forgets every picture; the owner is going away. */
    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        pending.clear()
        cache.evictAll()
        stale.clear()
        listener = null
    }

    private fun scheduleNext() {
        if (draining || held || pending.isEmpty()) return
        draining = true
        handler.post(::drawNext)
    }

    private fun drawNext() {
        draining = false
        if (released || held) return
        val next = pending.entries.firstOrNull() ?: return
        pending.remove(next.key)
        next.value.draw()?.let(next.value.onDrawn)
        scheduleNext()
    }

    private fun renderLook(profile: WatchThemeProfile): Bitmap? = drawSafely {
        renderer.setThemeProfile(profile, useLocalArtwork = true)
    }

    /**
     * The active theme read from the live preferences, with [value] as the candidate for [key] -
     * which also moves the picture to the screen that key belongs to. Built-in or saved, the active
     * theme is what the picker is editing, and a layout choice keeps that theme's values on the new
     * layout, exactly as picking it does. The candidate is cleared afterwards so none is left on
     * the shared renderer for the next picture.
     */
    private fun renderChoice(
            key: String,
            value: String,
            companions: Map<String, Any?>
    ): Bitmap? = try {
        drawSafely {
            renderer.clearThemeProfile()
            renderer.showPreference(key, value, companions)
        }
    } finally {
        renderer.refresh()
    }

    private fun drawSafely(prepare: () -> Unit): Bitmap? = try {
        prepare()
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
            renderer.draw(Canvas(bitmap))
        }
    } catch (error: RuntimeException) {
        // A look the preview cannot draw keeps an empty tile rather than taking the tab down.
        Timber.w(error, "Could not draw a watch theme picture")
        null
    } catch (error: OutOfMemoryError) {
        Timber.w(error, "No memory left for a watch theme picture")
        cache.evictAll()
        stale.clear()
        null
    }

    private fun choiceKey(key: String, value: String) = "$CHOICE_PREFIX$key=$value"

    private companion object {
        /** A look is shown at 78dp on Home; a picker's grid cell needs a little more. */
        const val THUMBNAIL_DP = 112f

        /** Room for every built-in layout and a full library of saved themes at a phone's density. */
        const val CACHE_BYTES = 24 * 1024 * 1024

        const val CHOICE_PREFIX = "choice:"
    }
}
