package com.svartifoss.snfell.watch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView

/**
 * How [OutlineTextView] resolves text that doesn't fit its available width.
 *
 * [SMART] is the historical do-everything behavior (shrink, then wrap, then scroll as a last
 * resort). The other three make that choice explicit/exclusive for users who find the automatic
 * mix unpredictable: [MARQUEE] always scrolls a single line, [WRAP] always wraps up to the
 * configured line limit (ellipsizing beyond that), [SHRINK] always shrinks word-safely down to
 * the floor size (ellipsizing beyond that) - neither of the latter two ever animate.
 */
enum class TextSizingMode { SMART, MARQUEE, WRAP, SHRINK }

/**
 * TextView that can be displayed either as a filled text or as an outline.
 */
class OutlineTextView : AppCompatTextView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    /**
     * Outline width in pixels,
     * equivalent to 1dp by default
     */
    var outlineWidth = context.resources.displayMetrics.density * 1
        set(value) {
            field = value
            invalidate()
        }

    /**
     * When *false*, normal text will be displayed.
     * When *true*, only outline of the text will be displayed.
     */
    var displayTextOutline: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var smartSizingEnabled = false
    private var smartMaxSizePx = 0f
    private var smartMinSizePx = 0f
    private var sizingMode = TextSizingMode.SMART

    // Captured once, the first time smart sizing runs - the XML-configured cap (e.g. maxLines=2)
    // that wrapping should respect before giving up and switching to a scrolling single line.
    private var wrapMaxLines = 1
    private var marqueeActive = false

    /**
     * Shrinks the text size (down to [minSizeSp]) only as much as needed so that no single word
     * ever has to be broken mid-word to fit the available width - unlike the framework's
     * `autoSizeText`, which only optimizes for the text block as a whole and will happily force a
     * line break in the middle of a word once its size floor is hit.
     *
     * [mode] picks which of that shrink/wrap/scroll behavior actually applies - see
     * [TextSizingMode]. Defaults to [TextSizingMode.SMART] for callers that don't offer a choice.
     */
    fun enableSmartWordSizing(maxSizeSp: Float, minSizeSp: Float, mode: TextSizingMode = TextSizingMode.SMART) {
        smartSizingEnabled = true
        smartMaxSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, maxSizeSp, resources.displayMetrics)
        smartMinSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, minSizeSp, resources.displayMetrics)
        wrapMaxLines = maxLines
        sizingMode = mode
        requestSmartResize()
    }

    /** Changes the sizing mode of an already-configured view (e.g. reacting to a settings
     *  change) without needing to re-supply the min/max sizes. */
    fun setSizingMode(mode: TextSizingMode) {
        if (sizingMode == mode) return
        sizingMode = mode
        requestSmartResize()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        requestSmartResize()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        requestSmartResize()
    }

    private fun requestSmartResize() {
        if (!smartSizingEnabled || width == 0) {
            return
        }
        post { applySmartTextSize() }
    }

    private fun applySmartTextSize() {
        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        if (availableWidth <= 0) {
            return
        }

        val words = text.toString().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            return
        }

        when (sizingMode) {
            TextSizingMode.WRAP -> applyWrapMode()
            TextSizingMode.MARQUEE -> applyMarqueeMode(words, availableWidth)
            TextSizingMode.SHRINK -> applyShrinkOrWrapMode(words, availableWidth, fallbackToMarquee = false)
            TextSizingMode.SMART -> applyShrinkOrWrapMode(words, availableWidth, fallbackToMarquee = true)
        }
    }

    /** Fixed size, wraps up to [wrapMaxLines] and ellipsizes beyond that - never shrinks or
     *  scrolls. This is just the view's plain XML-declared behavior (maxLines + ellipsize), so
     *  all this does is make sure a previous mode's marquee/shrink state doesn't linger. */
    private fun applyWrapMode() {
        disableMarquee()
        setTextSize(TypedValue.COMPLEX_UNIT_PX, smartMaxSizePx)
    }

    /** Always a single line at [smartMaxSizePx]; scrolls (marquee) only if that line doesn't fit,
     *  otherwise sits still - never shrinks or wraps. */
    private fun applyMarqueeMode(words: List<String>, availableWidth: Float) {
        val measurePaint = Paint(paint)
        measurePaint.textSize = smartMaxSizePx
        val singleLineWidth = measurePaint.measureText(words.joinToString(" "))

        if (singleLineWidth <= availableWidth) {
            disableMarquee()
            setTextSize(TypedValue.COMPLEX_UNIT_PX, smartMaxSizePx)
        } else {
            enableMarquee(smartMaxSizePx)
        }
    }

    /**
     * Word-safe shrink down to [smartMinSizePx] (see [enableSmartWordSizing]). When
     * [fallbackToMarquee] is true and the text still doesn't fit [wrapMaxLines] even at the floor
     * size, falls back to a scrolling single line (this is [TextSizingMode.SMART]'s behavior).
     * When false, it just stays at the floor size and ellipsizes instead (bounded, non-animating -
     * [TextSizingMode.SHRINK]'s behavior).
     */
    private fun applyShrinkOrWrapMode(words: List<String>, availableWidth: Float, fallbackToMarquee: Boolean) {
        val measurePaint = Paint(paint)
        val stepPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)

        var sizePx = smartMaxSizePx
        var fits = false

        while (sizePx > smartMinSizePx) {
            measurePaint.textSize = sizePx

            if (fitsWithinLineLimit(words, measurePaint, availableWidth)) {
                fits = true
                break
            }

            sizePx -= stepPx
        }

        if (!fits) {
            measurePaint.textSize = smartMinSizePx
            fits = fitsWithinLineLimit(words, measurePaint, availableWidth)
            sizePx = smartMinSizePx
        }

        if (fits || !fallbackToMarquee) {
            disableMarquee()
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
        } else {
            enableMarquee(smartMinSizePx)
        }
    }

    /**
     * Whether [words] fit at [paint]'s current text size without needing more than [wrapMaxLines]
     * lines or breaking a word mid-way. Deliberately NOT also checking the view's own height: for
     * a `wrap_content` height view, that height is itself a *consequence* of the current
     * text/size (computed on a previous layout pass, often before this resize even runs), not a
     * real independent constraint - using it as a ceiling caused titles that comfortably fit in
     * (say) 2 lines to be wrongly rejected and fall back to marquee instead.
     */
    private fun fitsWithinLineLimit(words: List<String>, paint: Paint, maxWidth: Float): Boolean {
        val widestWord = words.maxOf { paint.measureText(it) }
        if (widestWord > maxWidth) {
            return false
        }

        return estimateWrappedLineCount(words, paint, maxWidth) <= wrapMaxLines
    }

    private fun enableMarquee(sizePx: Float) {
        if (!marqueeActive) {
            marqueeActive = true
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
    }

    private fun disableMarquee() {
        if (marqueeActive) {
            marqueeActive = false
            isSingleLine = false
            maxLines = wrapMaxLines
            ellipsize = TextUtils.TruncateAt.END
            isSelected = false
        }
    }

    // Marquee only animates while the view is considered focused; this view normally never
    // receives real focus (the four-way touch layout below claims it instead).
    override fun isFocused(): Boolean = (marqueeActive && !marqueePaused) || super.isFocused()

    private var marqueePaused = false

    /**
     * Freezes a scrolling title in place (e.g. for ambient mode, where a continuously animating
     * marquee would be both a burn-in risk and a pointless battery drain) without forgetting that
     * it's in marquee mode - [setMarqueePaused] (false) picks the animation back up afterwards.
     */
    fun setMarqueePaused(paused: Boolean) {
        if (marqueeActive) {
            marqueePaused = paused
            isSelected = !paused
        }
    }

    private fun estimateWrappedLineCount(words: List<String>, paint: Paint, maxWidth: Float): Int {
        var lines = 1
        var currentLineWidth = 0f
        val spaceWidth = paint.measureText(" ")

        for (word in words) {
            val wordWidth = paint.measureText(word)
            val neededWidth = if (currentLineWidth == 0f) wordWidth else currentLineWidth + spaceWidth + wordWidth

            if (neededWidth > maxWidth && currentLineWidth > 0f) {
                lines++
                currentLineWidth = wordWidth
            } else {
                currentLineWidth = neededWidth
            }
        }

        return lines
    }

    override fun onDraw(canvas: Canvas) {
        if (displayTextOutline) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = outlineWidth
        } else {
            paint.style = Paint.Style.FILL
        }

        super.onDraw(canvas)
    }
}
