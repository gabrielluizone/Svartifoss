package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.R as commonR
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Live miniature of the watch's now-playing screen, driving the "Watch face" customization tab.
 * Renders a sample track over procedurally generated album art, honoring the same preference
 * keys the watch reads (face, screen theme, album art style, dim, accent/color modes, mini
 * buttons appearance/offset, track time) - so every control on the screen below updates this
 * preview exactly the way the real watch will update after sync.
 *
 * The watch is modeled as a 192dp round screen; everything is drawn in "watch dp" scaled to
 * this view's size, mirroring the geometry of the real implementations (the classic View face
 * and the Compose ExpressiveFace in `wear/`). Purely decorative - not interactive.
 */
class WatchPreviewView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        /** WatchTheme.ACCENT_DEFAULT on the wear side - the static "neutral" accent. */
        const val ACCENT_NEUTRAL = 0xFF87A89F.toInt()

        /** Fixed "palette-extracted" accent of the generated sample art below. */
        const val SAMPLE_ALBUM_ACCENT = 0xFFD98E76.toInt()

        const val WATCH_DP = 192f
        const val SAMPLE_PROGRESS = 0.35f

        const val COOKIE_LOBES = 12
        const val COOKIE_SOFTNESS = 1.5f
        const val COOKIE_MODULATION = 0.08f
        const val RING_MODULATION = 0.045f
        const val RING_GAP_DEGREES = 7f
    }

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val grayscaleFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    private val iconDst = RectF()

    private val fontRegular: Typeface? = ResourcesCompat.getFont(context, R.font.google_sans_regular)
    private val fontBold: Typeface? = ResourcesCompat.getFont(context, R.font.google_sans_bold)

    private var sampleArt: Bitmap? = null
    private var sampleArtBlurred: Bitmap? = null

    // --- Live now-playing data (see setNowPlaying()/setPlayback()) ---
    private var nowPlayingSource: Bitmap? = null
    private var nowPlayingTitle: String? = null
    private var nowPlayingArtist: String? = null
    private var liveArt: Bitmap? = null
    private var liveArtBlurred: Bitmap? = null
    private var liveAccent: Int? = null
    private var livePlaying: Boolean? = null
    private var livePositionMs: Long = -1
    private var liveDurationMs: Long = -1

    /** White-tinted icons of the mini buttons the user has actually configured (playing
     *  config), in slot order - empty when none, matching the watch which shows the row only
     *  for configured slots. Set by the fragment via [setMiniButtons]. */
    private var miniButtonIcons: List<Bitmap> = emptyList()

    // --- Preference snapshot (see refresh()) ---
    private var face = "classic"
    private var expressiveSeekMode = "central"
    private var screenTheme = "default"
    private var artStyle = "cover"
    private var dimArt = true
    private var dimStrength = 80
    private var dynamicAccent = true
    private var artistMode = "album"
    private var artistCustom = ""
    private var artistDesaturated = false
    private var progressMode = "album"
    private var progressCustom = ""
    private var progressDesaturated = false
    private var trackTimeMode = "always"
    private var titleTextMode = "smart"
    private var alwaysShowTime = false

    /** Set while the current frame drew a scrolling title - schedules the next frame. */
    private var marqueeActive = false
    private var buttonsOffsetDp = 42
    private var buttonsCurveStyle = "flat"
    private var buttonsBgStyle = "glass"
    private var buttonsColorMode = "neutral"
    private var buttonsCustom = ""
    private var buttonsDesaturated = false

    init {
        refresh()
    }

    /** Re-reads all watched preferences and redraws. Call on any preference change. */
    fun refresh() {
        face = prefs.getString("wear_screen_face", "classic") ?: "classic"
        expressiveSeekMode = prefs.getString("wear_expressive_seek_mode", "central") ?: "central"
        screenTheme = prefs.getString("wear_screen_theme", "default") ?: "default"
        artStyle = prefs.getString("album_art_style", "cover") ?: "cover"
        dimArt = prefs.getBoolean("dim_album_art", true)
        dimStrength = readInt("album_art_dim_strength", 80).coerceIn(0, 100)
        dynamicAccent = prefs.getBoolean("wear_dynamic_accent", true)
        artistMode = prefs.getString("wear_artist_color_mode", "album") ?: "album"
        artistCustom = prefs.getString("wear_artist_custom_color", "") ?: ""
        artistDesaturated = prefs.getBoolean("wear_artist_desaturated", false)
        progressMode = prefs.getString("wear_progress_color_mode", "album") ?: "album"
        progressCustom = prefs.getString("wear_progress_custom_color", "") ?: ""
        progressDesaturated = prefs.getBoolean("wear_progress_desaturated", false)
        trackTimeMode = prefs.getString("wear_track_time_mode", "always") ?: "always"
        titleTextMode = prefs.getString("wear_title_text_mode", "smart") ?: "smart"
        alwaysShowTime = prefs.getBoolean("always_show_time", false)
        buttonsOffsetDp = readInt("screen_buttons_bottom_offset", 42).coerceIn(0, 120)
        buttonsCurveStyle = prefs.getString("screen_buttons_curve_style", "flat") ?: "flat"
        buttonsBgStyle = prefs.getString("screen_buttons_bg_style", "glass") ?: "glass"
        buttonsColorMode = prefs.getString("screen_buttons_color_mode", "neutral") ?: "neutral"
        buttonsCustom = prefs.getString("screen_buttons_custom_color", "") ?: ""
        buttonsDesaturated = prefs.getBoolean("screen_buttons_desaturated", false)
        invalidate()
    }

    /**
     * Feeds the preview the phone's actual current track (album art, title, artist from the
     * active media session) so it mirrors what the watch is really showing right now. Any null
     * piece falls back to the built-in sample. Passing the same art instance again is cheap -
     * scaling and palette extraction only rerun when the bitmap changes.
     */
    fun setNowPlaying(art: Bitmap?, title: String?, artist: String?) {
        nowPlayingTitle = title?.takeIf { it.isNotBlank() }
        nowPlayingArtist = artist?.takeIf { it.isNotBlank() }
        if (art !== nowPlayingSource) {
            nowPlayingSource = art
            rebuildLiveArt()
            // Prefer the already center-cropped/small liveArt when the view has been measured;
            // falls back to the raw art otherwise (e.g. the very first call, before layout).
            extractLiveAccent(liveArt ?: art)
        }
        invalidate()
    }

    /**
     * Live playback state driving the dynamic parts of the preview - the progress ring, the
     * track time line and the play/pause presentation. The owning fragment ticks this every
     * 500ms while music plays (same cadence as the watch's position ticker), so the ring and
     * time advance in real time. A non-positive [durationMs] falls back to the fixed sample
     * progress; [playing] null resets to the sample "playing" state.
     */
    fun setPlayback(playing: Boolean?, positionMs: Long, durationMs: Long) {
        livePlaying = playing
        livePositionMs = positionMs
        liveDurationMs = durationMs
        invalidate()
    }

    /**
     * The mini buttons the user actually configured (their real action icons, already tinted
     * white), so the preview's bottom row matches the watch - which shows nothing when no slot
     * is configured, rather than the previous three phantom pills.
     */
    fun setMiniButtons(icons: List<Bitmap>) {
        miniButtonIcons = icons
        invalidate()
    }

    private fun isPlayingShown(): Boolean = livePlaying ?: true

    private fun progressFraction(): Float = if (liveDurationMs > 0) {
        (livePositionMs.toFloat() / liveDurationMs).coerceIn(0f, 1f)
    } else {
        SAMPLE_PROGRESS
    }

    private fun timeText(): String = if (liveDurationMs > 0) {
        "${formatTime(livePositionMs.coerceAtLeast(0))} / ${formatTime(liveDurationMs)}"
    } else {
        "1:07 / 3:12"
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    /** Mirrors the watch's WEAR_TRACK_TIME_MODE handling against the shown playing state. */
    private fun trackTimeVisible(): Boolean = when (trackTimeMode) {
        "never" -> false
        "playing" -> isPlayingShown()
        "paused" -> !isPlayingShown()
        else -> true
    }

    private fun rebuildLiveArt() {
        liveArt = null
        liveArtBlurred = null
        val source = nowPlayingSource ?: return
        val size = min(width, height)
        if (size <= 0) {
            return // onSizeChanged() rebuilds once measured
        }
        liveArt = centerCrop(source, size)
        liveArtBlurred = blurApproximation(liveArt!!)
    }

    /** Center-crops [source] into a size×size square, like the watch's centerCrop ImageView. */
    private fun centerCrop(source: Bitmap, size: Int): Bitmap {
        val scale = size.toFloat() / min(source.width, source.height)
        val w = (source.width * scale).toInt().coerceAtLeast(size)
        val h = (source.height * scale).toInt().coerceAtLeast(size)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        val cropped = Bitmap.createBitmap(scaled, (w - size) / 2, (h - size) / 2, size, size)
        if (cropped !== scaled && scaled !== source) {
            scaled.recycle()
        }
        return cropped
    }

    /** Same swatch priority the watch uses to pick its dynamic accent from the album art.
     *  Synchronous rather than [Palette.generate]'s async callback: this preview thumbnail's
     *  art is already small (see [rebuildLiveArt]'s centerCrop, and Palette's own internal
     *  downsampling), so the extraction is fast enough not to jank - and doing it inline avoids
     *  a visible flash of the default/static accent before the real one lands a frame later. */
    private fun extractLiveAccent(art: Bitmap?) {
        liveAccent = art?.let { bitmap ->
            val p = Palette.from(bitmap).generate()
            listOf(
                    p.vibrantSwatch,
                    p.mutedSwatch,
                    p.lightVibrantSwatch,
                    p.darkVibrantSwatch,
                    p.lightMutedSwatch,
                    p.darkMutedSwatch,
                    p.dominantSwatch
            ).firstNotNullOfOrNull { swatch -> swatch?.rgb }
        }
    }

    /** Numeric prefs may be persisted as Int or as String (EditTextPreference) - accept both. */
    private fun readInt(key: String, default: Int): Int = try {
        prefs.getInt(key, default)
    } catch (ignored: ClassCastException) {
        prefs.getString(key, null)?.toIntOrNull() ?: default
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h)
        if (size <= 0) return
        sampleArt = buildSampleArt(size)
        sampleArtBlurred = blurApproximation(sampleArt!!)
        rebuildLiveArt()
    }

    /** Procedural "album cover": a warm two-tone gradient with soft color blobs, tuned so
     *  [SAMPLE_ALBUM_ACCENT] reads as its natural palette accent. */
    private fun buildSampleArt(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
                0xFF6E3B33.toInt(), 0xFF241B2F.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null
        p.color = 0x66B86450
        c.drawCircle(size * 0.68f, size * 0.30f, size * 0.34f, p)
        p.color = 0x558A4A5E.toInt()
        c.drawCircle(size * 0.25f, size * 0.75f, size * 0.40f, p)
        p.color = 0x33E8B08A
        c.drawCircle(size * 0.45f, size * 0.5f, size * 0.22f, p)
        return bmp
    }

    /** Same halving-downscale blur approximation the watch uses pre-API-31. */
    private fun blurApproximation(source: Bitmap): Bitmap {
        var current = source
        while (current.width > 16) {
            val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== source) current.recycle()
            current = next
        }
        val result = Bitmap.createScaledBitmap(current, source.width, source.height, true)
        if (current !== source) current.recycle()
        return result
    }

    // --- Color resolution (mirrors the wear-side logic) ---

    private fun parseHexOrNull(hex: String): Int? =
            if (hex.isBlank()) null else try { Color.parseColor(hex) } catch (ignored: Exception) { null }

    private fun albumAccent(): Int =
            if (dynamicAccent) (liveAccent ?: SAMPLE_ALBUM_ACCENT) else ACCENT_NEUTRAL

    private fun displayTitle(): String =
            nowPlayingTitle ?: context.getString(R.string.preview_sample_title)

    private fun displayArtist(): String =
            nowPlayingArtist ?: context.getString(R.string.preview_sample_artist)

    /** Truncates real track/artist names to the watch circle - uses [textPaint]'s current
     *  size/typeface, so set those first. */
    private fun ellipsize(text: String, maxWidth: Float): String =
            TextUtils.ellipsize(text, TextPaint(textPaint), maxWidth, TextUtils.TruncateAt.END).toString()

    // --- Title rendering (mirrors the watch's WEAR_TITLE_TEXT_MODE behaviors) ---

    /**
     * Scrolling title, like the watch's marquee: the text loops leftward inside a clip window,
     * drawn twice for a seamless wrap. Sets [marqueeActive] so onDraw schedules the next
     * animation frame - this is the only continuously animating piece of the preview (the mini
     * player's marquee titles already set that precedent).
     */
    private fun drawMarqueeText(canvas: Canvas, text: String, cx: Float, baseline: Float, availWidth: Float) {
        val textWidth = textPaint.measureText(text)
        val gap = availWidth * 0.4f
        val period = textWidth + gap
        val speedPxPerSecond = availWidth / 4f
        val phase = (SystemClock.uptimeMillis() % 3_600_000L) / 1000f * speedPxPerSecond % period

        val left = cx - availWidth / 2f
        canvas.save()
        canvas.clipRect(left, baseline - textPaint.textSize * 1.2f,
                left + availWidth, baseline + textPaint.textSize * 0.45f)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(text, left - phase, baseline, textPaint)
        canvas.drawText(text, left - phase + period, baseline, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.restore()

        marqueeActive = true
    }

    /** Largest size in [maxSize]..[floorSize] at which [text] fits [availWidth], or null. */
    private fun findFittingTextSize(text: String, availWidth: Float, maxSize: Float, floorSize: Float): Float? {
        var size = maxSize
        while (size >= floorSize) {
            textPaint.textSize = size
            if (textPaint.measureText(text) <= availWidth) {
                return size
            }
            size -= 0.5f
        }
        return null
    }

    /** Greedy word-boundary split into two lines (raw, not ellipsized), at the current text
     *  size. Line 1 packs as many leading words as fit [availWidth]; the rest goes to line 2. */
    private fun splitTwoLines(text: String, availWidth: Float): Pair<String, String> {
        val words = text.split(" ")
        var firstLine = ""
        var splitIndex = 0
        for (i in words.indices) {
            val candidate = if (firstLine.isEmpty()) words[i] else "$firstLine ${words[i]}"
            if (textPaint.measureText(candidate) <= availWidth || firstLine.isEmpty()) {
                firstLine = candidate
                splitIndex = i + 1
            } else {
                break
            }
        }
        return firstLine to words.drop(splitIndex).joinToString(" ")
    }

    /** Largest size in [maxSize]..[floorSize] at which [text] wraps to two lines that BOTH fit
     *  [availWidth] fully (no ellipsis) - the watch's "shrink until it wraps cleanly" behavior.
     *  Null if even at [floorSize] a line still overflows (caller then scrolls). */
    private fun findFittingWrapSize(text: String, availWidth: Float, maxSize: Float, floorSize: Float): Float? {
        var size = maxSize
        while (size >= floorSize) {
            textPaint.textSize = size
            val (line1, line2) = splitTwoLines(text, availWidth)
            if (line2.isNotEmpty() &&
                    textPaint.measureText(line1) <= availWidth &&
                    textPaint.measureText(line2) <= availWidth) {
                return size
            }
            size -= 0.5f
        }
        return null
    }

    /** A resolved title layout: the chosen font [size], the line(s) to draw ([line2] null =
     *  single line), and whether it scrolls. Leaves [textPaint]'s text size at [size]. */
    private class TitlePlan(val size: Float, val line1: String, val line2: String?, val marquee: Boolean)

    /**
     * Resolves how the classic title renders per the synced text mode - "smart" (shrink, then
     * wrap, then scroll), "marquee", "wrap" or "shrink" - mirroring the watch's OutlineTextView
     * sizing, without drawing (so the caller can lay out the whole text block first).
     */
    private fun planClassicTitle(availWidth: Float, maxSize: Float, floorSize: Float, wrapFloor: Float): TitlePlan {
        val text = displayTitle()
        textPaint.typeface = fontBold

        fun wrapPlan(size: Float): TitlePlan {
            textPaint.textSize = size
            val (line1, rawLine2) = splitTwoLines(text, availWidth)
            return if (rawLine2.isEmpty()) TitlePlan(size, line1, null, false)
            else TitlePlan(size, line1, ellipsize(rawLine2, availWidth), false)
        }

        return when (titleTextMode) {
            "marquee" -> TitlePlan(maxSize, text, null, marquee = true)
            "wrap" -> {
                textPaint.textSize = maxSize
                if (textPaint.measureText(text) <= availWidth) TitlePlan(maxSize, text, null, false)
                else wrapPlan(findFittingWrapSize(text, availWidth, maxSize, wrapFloor) ?: wrapFloor)
            }
            "shrink" -> {
                val fitted = findFittingTextSize(text, availWidth, maxSize, floorSize) ?: floorSize
                textPaint.textSize = fitted
                TitlePlan(fitted, ellipsize(text, availWidth), null, false)
            }
            else -> {
                // Smart: fit on one line (shrinking to the floor) first; else wrap to two lines
                // at the largest size that fits both; else scroll.
                val fitted = findFittingTextSize(text, availWidth, maxSize, floorSize)
                when {
                    fitted != null -> TitlePlan(fitted, text, null, false)
                    else -> findFittingWrapSize(text, availWidth, maxSize, wrapFloor)
                            ?.let { wrapPlan(it) }
                            ?: TitlePlan(maxSize, text, null, marquee = true)
                }
            }
        }
    }

    private fun resolveTint(mode: String, custom: String, desaturated: Boolean): Int = when (mode) {
        "album" -> if (desaturated) {
            ColorUtils.blendARGB(albumAccent(), Color.GRAY, 0.45f)
        } else {
            albumAccent()
        }
        "custom" -> parseHexOrNull(custom) ?: albumAccent()
        else -> ACCENT_NEUTRAL
    }

    /** WatchTheme.accentForText: lift lightness so accents read on the dark screen. */
    private fun accentForText(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceAtLeast(0.62f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun tonal(accent: Int, lightness: Float, minSat: Float, maxSat: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        hsl[1] = hsl[1].coerceIn(minSat, maxSat)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    // --- Drawing ---

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0) return
        marqueeActive = false

        val cx = width / 2f
        val cy = height / 2f
        // Leave a small margin around the device for its drop shadow; all geometry scales to
        // the (slightly inset) device radius so proportions stay watch-accurate.
        val shadowMargin = size * 0.035f
        val radius = size / 2f - shadowMargin
        val s = (radius * 2f) / WATCH_DP
        fun dp(v: Float) = v * s

        // Soft drop shadow beneath the device (shows on the light theme, stays subtle on the
        // near-black dark theme, like any elevated surface). The opaque device circle below is
        // drawn on top, so only the offset fringe reads as a shadow.
        val shadowOffset = dp(5f)
        val shadowRadius = radius + dp(7f)
        fillPaint.shader = RadialGradient(cx, cy + shadowOffset, shadowRadius,
                intArrayOf(0x33000000, 0x33000000, 0x00000000),
                floatArrayOf(0f, radius / shadowRadius, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy + shadowOffset, shadowRadius, fillPaint)
        fillPaint.shader = null

        val clip = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)

        // Screen background: black + album art per style.
        fillPaint.shader = null
        fillPaint.color = Color.BLACK
        canvas.drawCircle(cx, cy, radius, fillPaint)

        val art = when {
            artStyle == "hidden" -> null
            artStyle == "blur" || artStyle == "blur_bw" -> liveArtBlurred ?: sampleArtBlurred
            else -> liveArt ?: sampleArt
        }
        if (art != null) {
            bitmapPaint.colorFilter =
                    if (artStyle == "bw" || artStyle == "blur_bw") grayscaleFilter else null
            canvas.drawBitmap(art, cx - art.width / 2f, cy - art.height / 2f, bitmapPaint)
        }

        // Dim scrim: exactly the watch's album_art_scrim - a FULL-height gradient, transparent
        // at the very top down to strength% black at the very bottom (cinema deepens it). Over
        // the full height (not just the bottom half) it darkens the whole lower screen, which is
        // why the previous half-height version read as far too weak.
        if (dimArt) {
            val strength = (dimStrength + if (screenTheme == "cinema") 20 else 0).coerceAtMost(100)
            val alpha = strength * 255 / 100
            fillPaint.shader = LinearGradient(0f, cy - radius, 0f, cy + radius,
                    Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0), Shader.TileMode.CLAMP)
            canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, fillPaint)
            fillPaint.shader = null
        }

        if (face == "expressive") {
            drawExpressive(canvas, cx, cy, radius, ::dp)
        } else {
            drawClassic(canvas, cx, cy, radius, ::dp)
        }

        canvas.restore()

        // A scrolling title needs the next frame; everything else only redraws on data changes.
        if (marqueeActive && isShown) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawIcon(canvas: Canvas, resId: Int, centerX: Float, centerY: Float, sizePx: Float, tint: Int, alpha: Int = 255) {
        val drawable = AppCompatResources.getDrawable(context, resId) ?: return
        drawable.setTint(tint)
        drawable.alpha = alpha
        val half = (sizePx / 2f).toInt()
        drawable.setBounds((centerX - half).toInt(), (centerY - half).toInt(),
                (centerX + half).toInt(), (centerY + half).toInt())
        drawable.draw(canvas)
    }

    /**
     * Bezel-hugging progress ring - matches CircularProgressSeekBar: a 6dp band whose outer edge
     * touches the screen edge (inset = strokeWidth/2), track in glass_surface_border with a BUTT
     * cap and the played arc rounded, both starting at 12 o'clock. Used by the classic face and,
     * when the expressive face's seek mode is "edge", by the expressive face too.
     */
    private fun drawEdgeSeekRing(canvas: Canvas, cx: Float, cy: Float, radius: Float, dp: (Float) -> Float) {
        val progressColor = resolveTint(progressMode, progressCustom, progressDesaturated)
        val strokeW = dp(6f)
        val ringInset = strokeW / 2f
        val ringRect = RectF(cx - radius + ringInset, cy - radius + ringInset,
                cx + radius - ringInset, cy + radius - ringInset)
        strokePaint.strokeWidth = strokeW
        strokePaint.strokeCap = Paint.Cap.BUTT
        strokePaint.color = 0x33FFFFFF
        canvas.drawArc(ringRect, 0f, 360f, false, strokePaint)
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = progressColor
        canvas.drawArc(ringRect, -90f, progressFraction() * 360f, false, strokePaint)
    }

    private fun drawClassic(canvas: Canvas, cx: Float, cy: Float, radius: Float, dp: (Float) -> Float) {
        // The seek ring uses the raw resolved accent (like the watch's seekBar.progressColor);
        // only the artist text gets the accentForText lightness lift.
        val artistColor = accentForText(resolveTint(artistMode, artistCustom, artistDesaturated))

        drawEdgeSeekRing(canvas, cx, cy, radius, dp)

        // Quadrant hint icons per screen theme (minimal hides them, compact shrinks, cinema
        // fades). 4dp from the edge, matching music_screen_icon_offset.
        val iconAlpha = if (screenTheme == "cinema") 89 else 255
        val iconSize = dp(if (screenTheme == "compact") 18f else 24f)
        val edge = dp(4f)
        // Clock: near the top like the watch's ambient_clock (15sp, ~5dp below the very top),
        // shown when Always-show-time is on (which also hides the top quadrant icon).
        if (screenTheme != "minimal") {
            if (alwaysShowTime) {
                drawSmallClock(canvas, cx, cy - radius + dp(23f), dp)
            } else {
                drawIcon(canvas, commonR.drawable.action_volume_up, cx, cy - radius + edge + iconSize / 2f, iconSize, Color.WHITE, iconAlpha)
            }
            drawIcon(canvas, commonR.drawable.action_volume_down, cx, cy + radius - edge - iconSize / 2f, iconSize, Color.WHITE, iconAlpha)
            drawIcon(canvas, commonR.drawable.action_skip_prev, cx - radius + edge + iconSize / 2f, cy, iconSize, Color.WHITE, iconAlpha)
            drawIcon(canvas, commonR.drawable.action_skip_next, cx + radius - edge - iconSize / 2f, cy, iconSize, Color.WHITE, iconAlpha)
        } else if (alwaysShowTime) {
            drawSmallClock(canvas, cx, cy - radius + dp(23f), dp)
        }

        drawClassicTextBlock(canvas, cx, cy, radius * 1.6f, artistColor, dp)

        drawMiniButtons(canvas, cx, cy, radius, dp)
    }

    /**
     * Draws the [artist, title, time] block exactly like the watch's centered LinearLayout:
     * tight stacking (title directly under artist, time 4dp below the title), the whole block
     * vertically centered on the screen, font padding excluded, at the watch's sp sizes
     * (16 / 40-46 / 13). The title itself follows the synced text mode (see [planClassicTitle]).
     */
    private fun drawClassicTextBlock(canvas: Canvas, cx: Float, cy: Float, textWidth: Float, artistColor: Int, dp: (Float) -> Float) {
        val plan = planClassicTitle(textWidth, dp(46f), dp(25f), dp(15f))

        // 14dp (not the raw 16sp) so the artist reads at the same on-screen size as a real
        // watch, which is physically larger than this preview's 192dp geometry model.
        val artistSize = dp(14f)
        textPaint.typeface = fontBold
        textPaint.textSize = artistSize
        val artistFm = textPaint.fontMetrics
        val artistLineH = artistFm.descent - artistFm.ascent

        textPaint.textSize = plan.size
        val titleFm = textPaint.fontMetrics
        val titleLineH = titleFm.descent - titleFm.ascent
        val titleH = (if (plan.line2 != null) 2 else 1) * titleLineH

        val timeVisible = trackTimeVisible()
        textPaint.typeface = fontRegular
        textPaint.textSize = dp(13f)
        val timeFm = textPaint.fontMetrics
        val timeLineH = timeFm.descent - timeFm.ascent
        val timeGap = dp(4f)

        val totalH = artistLineH + titleH + (if (timeVisible) timeGap + timeLineH else 0f)
        var y = cy - totalH / 2f

        // Artist (or the "Playback Stopped" status in white while paused).
        textPaint.typeface = fontBold
        textPaint.textSize = artistSize
        if (isPlayingShown()) {
            textPaint.color = artistColor
            canvas.drawText(ellipsize(displayArtist(), textWidth), cx, y - artistFm.ascent, textPaint)
        } else {
            textPaint.color = Color.WHITE
            canvas.drawText(context.getString(R.string.preview_playback_stopped), cx, y - artistFm.ascent, textPaint)
        }
        y += artistLineH

        // Title (1-2 lines, or scrolling in marquee mode).
        textPaint.color = Color.WHITE
        textPaint.textSize = plan.size
        if (plan.marquee) {
            drawMarqueeText(canvas, plan.line1, cx, y - titleFm.ascent, textWidth)
            y += titleLineH
        } else {
            canvas.drawText(plan.line1, cx, y - titleFm.ascent, textPaint)
            y += titleLineH
            plan.line2?.let {
                canvas.drawText(it, cx, y - titleFm.ascent, textPaint)
                y += titleLineH
            }
        }

        // Track time, 4dp below the title.
        if (timeVisible) {
            y += timeGap
            textPaint.typeface = fontRegular
            textPaint.textSize = dp(13f)
            textPaint.color = Color.WHITE
            canvas.drawText(timeText(), cx, y - timeFm.ascent, textPaint)
        }
    }

    private fun drawSmallClock(canvas: Canvas, x: Float, y: Float, dp: (Float) -> Float) {
        textPaint.typeface = fontRegular
        textPaint.color = 0xC8FFFFFF.toInt()
        textPaint.textSize = dp(13f)
        // Real wall-clock time, like the watch (which follows the system 12/24h setting and
        // never appends AM/PM). Minute precision is enough - the preview redraws often enough
        // while playing, and a stale minute at rest is harmless.
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        val time = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                .format(java.util.Date())
        canvas.drawText(time, x, y, textPaint)
    }

    /** The configurable mini-buttons row - only the slots the user actually configured (their
     *  real action icons), re-centered by count, at the user's offset/background/color/curve.
     *  No-op when nothing is configured, exactly like the watch. Returns true if it drew a row. */
    private fun drawMiniButtons(canvas: Canvas, cx: Float, cy: Float, radius: Float, dp: (Float) -> Float): Boolean {
        val icons = miniButtonIcons
        if (icons.isEmpty()) {
            return false
        }

        // 46x38dp pills match screen_button_width/height; icon fits inside the 9dp padding.
        val pillW = dp(46f)
        val pillH = dp(38f)
        val gap = dp(if (icons.size == 2) 8f else 5f)
        val iconSize = dp(20f)
        val rowBottom = cy + radius - dp(buttonsOffsetDp.toFloat())
        val totalWidth = icons.size * pillW + (icons.size - 1) * gap

        val tintColor = when (buttonsColorMode) {
            "album" -> if (buttonsDesaturated) {
                ColorUtils.blendARGB(albumAccent(), Color.GRAY, 0.45f)
            } else {
                albumAccent()
            }
            "custom" -> parseHexOrNull(buttonsCustom)
            else -> null
        }
        val pillColor = when {
            buttonsBgStyle == "transparent" -> Color.TRANSPARENT
            tintColor != null -> ColorUtils.setAlphaComponent(tintColor, if (buttonsBgStyle == "solid") 230 else 96)
            buttonsBgStyle == "solid" -> Color.argb(230, 22, 24, 26)
            else -> 0x28FFFFFF
        }

        val curveFraction = when (buttonsCurveStyle) {
            "arc" -> 0f
            "curved_soft" -> 0.5f
            "curved" -> 1f
            else -> null
        }

        for ((i, icon) in icons.withIndex()) {
            val pillCx = cx - totalWidth / 2f + pillW / 2f + i * (pillW + gap)
            var pillCy = rowBottom - pillH / 2f
            var rotation = 0f

            if (curveFraction != null) {
                val dx = pillCx - cx
                val clamped = dx.coerceIn(-radius + 1f, radius - 1f)
                pillCy -= radius - sqrt(radius * radius - clamped * clamped)
                rotation = curveFraction *
                        -Math.toDegrees(asin((clamped / radius).toDouble())).toFloat()
            }

            canvas.save()
            canvas.rotate(rotation, pillCx, pillCy)
            if (pillColor != Color.TRANSPARENT) {
                fillPaint.color = pillColor
                canvas.drawRoundRect(pillCx - pillW / 2f, pillCy - pillH / 2f,
                        pillCx + pillW / 2f, pillCy + pillH / 2f, pillH, pillH, fillPaint)
            }
            iconDst.set(pillCx - iconSize / 2f, pillCy - iconSize / 2f,
                    pillCx + iconSize / 2f, pillCy + iconSize / 2f)
            canvas.drawBitmap(icon, null, iconDst, bitmapPaint)
            canvas.restore()
        }
        return true
    }

    // --- Expressive face (mirrors wear's ExpressiveFace geometry) ---

    private fun cookieProfile(angleRad: Float, modulation: Float): Float {
        val wave = tanh(COOKIE_SOFTNESS * cos(COOKIE_LOBES * angleRad)) / tanh(COOKIE_SOFTNESS)
        return 1f + modulation * wave
    }

    private fun contourPath(cx: Float, cy: Float, radius: Float, modulation: Float, fromDeg: Float, toDeg: Float): Path {
        val path = Path()
        var degrees = fromDeg
        var first = true
        while (degrees <= toDeg) {
            val angleRad = Math.toRadians((degrees - 90f).toDouble()).toFloat()
            val r = radius * cookieProfile(angleRad, modulation)
            val x = cx + r * cos(angleRad)
            val y = cy + r * sin(angleRad)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            degrees += 2f
        }
        return path
    }

    private fun drawExpressive(canvas: Canvas, cx: Float, cy: Float, radius: Float, dp: (Float) -> Float) {
        val accent = albumAccent()

        // Accent tint + black scrim + radial vignette over the art.
        fillPaint.color = ColorUtils.setAlphaComponent(tonal(accent, 0.30f, 0.30f, 0.80f), 115)
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.color = Color.argb(77, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.shader = RadialGradient(cx, cy, radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(224, 0, 0, 0)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.shader = null

        // With the "edge" seek mode the classic bezel ring rides on top of the expressive face's
        // own central ring, so the miniature mirrors what the watch actually shows.
        if (expressiveSeekMode == "edge") {
            drawEdgeSeekRing(canvas, cx, cy, radius, dp)
        }

        val sideContainer = tonal(accent, 0.74f, 0.40f, 0.85f)
        val centerContainer = tonal(accent, 0.87f, 0.30f, 0.70f)
        val onContainer = tonal(accent, 0.16f, 0.25f, 0.60f)
        val artistColor = accentForText(resolveTint(artistMode, artistCustom, artistDesaturated))

        drawSmallClock(canvas, cx, cy - radius + dp(24f), dp)

        // Title/artist near the top. Overflowing titles always scroll here - the watch's
        // expressive face uses an unconditional marquee, independent of the title text mode.
        // Sizes mirror the watch's expressive face (16sp title / 13sp artist).
        textPaint.typeface = fontBold
        textPaint.color = Color.WHITE
        textPaint.textSize = dp(16f)
        val expressiveTitle = displayTitle()
        if (textPaint.measureText(expressiveTitle) <= radius * 1.45f) {
            canvas.drawText(expressiveTitle, cx, cy - radius + dp(40f), textPaint)
        } else {
            drawMarqueeText(canvas, expressiveTitle, cx, cy - radius + dp(40f), radius * 1.45f)
        }
        // Artist, or the "Playback Stopped" status in white while paused (the watch's expressive
        // face mirrors the same textArtist line the classic face swaps).
        textPaint.typeface = fontRegular
        textPaint.textSize = dp(12f)
        if (isPlayingShown()) {
            textPaint.color = artistColor
            canvas.drawText(ellipsize(displayArtist(), radius * 1.45f), cx, cy - radius + dp(56f), textPaint)
        } else {
            textPaint.color = Color.WHITE
            canvas.drawText(context.getString(R.string.preview_playback_stopped), cx, cy - radius + dp(56f), textPaint)
        }

        // Side round buttons.
        val sideDiameter = dp(WATCH_DP * 0.235f)
        val ringBox = dp(WATCH_DP * 0.34f)
        val gap = dp(WATCH_DP * 0.02f)
        val sideOffset = ringBox / 2f + gap + sideDiameter / 2f
        fillPaint.color = sideContainer
        canvas.drawCircle(cx - sideOffset, cy, sideDiameter / 2f, fillPaint)
        canvas.drawCircle(cx + sideOffset, cy, sideDiameter / 2f, fillPaint)
        drawIcon(canvas, commonR.drawable.action_skip_prev, cx - sideOffset, cy, sideDiameter * 0.42f, onContainer)
        drawIcon(canvas, commonR.drawable.action_skip_next, cx + sideOffset, cy, sideDiameter * 0.42f, onContainer)

        // Cookie button + contour-following ring. Paused flattens both into plain circles and
        // swaps the icon, matching the watch face's morph.
        val playing = isPlayingShown()
        val cookieModulation = if (playing) COOKIE_MODULATION else 0f
        val ringModulation = if (playing) RING_MODULATION else 0f
        val cookieRadius = dp(WATCH_DP * 0.215f) / 2f / (1f + COOKIE_MODULATION)
        fillPaint.color = centerContainer
        canvas.drawPath(contourPath(cx, cy, cookieRadius, cookieModulation, 0f, 360f).apply { close() }, fillPaint)
        drawIcon(canvas,
                if (playing) commonR.drawable.action_pause else commonR.drawable.action_play,
                cx, cy, dp(WATCH_DP * 0.215f) * 0.48f, onContainer)

        val stroke = dp(3.5f)
        val ringRadius = (ringBox / 2f - stroke) / (1f + RING_MODULATION)
        val sweep = progressFraction() * 360f
        strokePaint.strokeWidth = stroke
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = 0x4DFFFFFF
        if (sweep + RING_GAP_DEGREES < 360f - RING_GAP_DEGREES) {
            canvas.drawPath(contourPath(cx, cy, ringRadius, ringModulation, sweep + RING_GAP_DEGREES, 360f - RING_GAP_DEGREES), strokePaint)
        }
        if (sweep > RING_GAP_DEGREES) {
            strokePaint.color = Color.WHITE
            canvas.drawPath(contourPath(cx, cy, ringRadius, ringModulation, 0f, sweep - RING_GAP_DEGREES / 2f), strokePaint)
        }
        val thumbRad = Math.toRadians((sweep - 90f).toDouble()).toFloat()
        val thumbDist = ringRadius * cookieProfile(thumbRad, ringModulation)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(cx + thumbDist * cos(thumbRad), cy + thumbDist * sin(thumbRad), dp(3f), fillPaint)

        if (trackTimeVisible()) {
            textPaint.typeface = fontRegular
            textPaint.color = 0xB3FFFFFF.toInt()
            textPaint.textSize = dp(10f)
            canvas.drawText(timeText(), cx, cy + dp(WATCH_DP * 0.17f + 16f), textPaint)
        }

        // The watch's expressive face shows its default queue/volume/overflow trio ONLY when
        // no mini buttons are configured; configured mini buttons take over that area instead.
        if (!drawMiniButtons(canvas, cx, cy, radius, dp)) {
            val pillW = dp(WATCH_DP * 0.235f)
            val pillH = dp(WATCH_DP * 0.155f)
            fun glassPill(centerX: Float, centerY: Float, iconRes: Int?) {
                fillPaint.color = 0x29FFFFFF
                canvas.drawRoundRect(centerX - pillW / 2f, centerY - pillH / 2f,
                        centerX + pillW / 2f, centerY + pillH / 2f, pillH, pillH, fillPaint)
                if (iconRes != null) {
                    drawIcon(canvas, iconRes, centerX, centerY, dp(13f), Color.WHITE)
                } else {
                    fillPaint.color = Color.WHITE
                    for (i in -1..1) {
                        canvas.drawCircle(centerX, centerY + i * dp(4.5f), dp(1.4f), fillPaint)
                    }
                }
            }
            val sideX = dp(WATCH_DP * 0.275f)
            glassPill(cx - sideX, cy + radius - dp(WATCH_DP * 0.152f) - pillH / 2f, R.drawable.ic_playlist_play)
            glassPill(cx, cy + radius - dp(WATCH_DP * 0.032f) - pillH / 2f, commonR.drawable.action_volume_up)
            glassPill(cx + sideX, cy + radius - dp(WATCH_DP * 0.152f) - pillH / 2f, null)
        }
    }
}
