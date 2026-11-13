package com.svartifoss.snfell.view.watchface.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import com.svartifoss.snfell.view.watchface.WatchPreviewView
import com.svartifoss.snfell.view.watchface.theme.WatchThemeProfile
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Draws watch themes as pictures, with the same code that draws the Watch tab's preview.
 *
 * One [WatchPreviewView] that is never attached to a window, measured once and drawn into bitmaps:
 * the arrangement `SubmitCommunityThemeActivity.renderModerationPreview` already uses. A single
 * renderer rather than one live preview per tile, because each preview extracts the cover's
 * palette and blurs it for itself; here that is done once and every thumbnail shares it, and the
 * strip then scrolls as plain images.
 *
 * Rendering stays on the main thread - a View is not safe to draw anywhere else - one thumbnail
 * per posted message, so a long strip never holds a frame. Thumbnails are kept only in memory: they
 * are never written to disk or uploaded, and the moderation upload path is not involved.
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

    private class Request(val key: String, val profile: () -> WatchThemeProfile?)

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
    private val pending = LinkedHashMap<String, Request>()
    private var draining = false
    private var released = false

    /** Whoever shows the thumbnails; told about each one as it is drawn. */
    var listener: Listener? = null

    /** What is playing now, whose cover every thumbnail is drawn on. */
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
     * Queues the picture for [key] unless a current one exists or one is already queued. [profile]
     * is read only when its turn comes, so the theme is drawn as it is then, not as it was when it
     * was asked for.
     */
    fun request(key: String, profile: () -> WatchThemeProfile?) {
        if (released || cache.get(key) != null || key in pending) return
        pending[key] = Request(key, profile)
        scheduleNext()
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
     * thumbnail stale, because each one shows that theme on this cover.
     */
    fun setArtwork(art: Bitmap?): Boolean {
        if (art === artwork) return false
        artwork = art
        renderer.setNowPlaying(art, null, null)
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
        if (draining || pending.isEmpty()) return
        draining = true
        handler.post(::drawNext)
    }

    private fun drawNext() {
        draining = false
        if (released) return
        val next = pending.entries.firstOrNull() ?: return
        pending.remove(next.key)
        val profile = next.value.profile()
        if (profile != null) {
            render(profile)?.let { thumbnail ->
                stale.remove(next.key)
                cache.put(next.key, thumbnail)
                listener?.onThumbnail(next.key, thumbnail)
            }
        }
        scheduleNext()
    }

    private fun render(profile: WatchThemeProfile): Bitmap? = try {
        renderer.setThemeProfile(profile, useLocalArtwork = true)
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
            renderer.draw(Canvas(bitmap))
        }
    } catch (error: RuntimeException) {
        // A theme the preview cannot draw keeps an empty tile rather than taking the tab down.
        Timber.w(error, "Could not draw the thumbnail of a watch theme")
        null
    } catch (error: OutOfMemoryError) {
        Timber.w(error, "No memory left for a watch theme thumbnail")
        cache.evictAll()
        stale.clear()
        null
    }

    private companion object {
        /** A thumbnail is shown at 78dp on Home; the picker grids of a later phase need a little more. */
        const val THUMBNAIL_DP = 112f

        /** Room for every built-in layout and a full library of saved themes at a phone's density. */
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}
