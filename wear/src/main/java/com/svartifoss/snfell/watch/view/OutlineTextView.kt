package com.svartifoss.snfell.watch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.widget.AppCompatTextView
import com.svartifoss.snfell.common.MarqueeMotion
import kotlin.math.ceil

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

    /**
     * Stroke width for a *filled* line that also carries an outline, as a fraction of the text
     * size. Zero draws no outline.
     *
     * Distinct from [displayTextOutline], which replaces the fill; this draws a stroke underneath
     * one. The width is a fraction rather than a pixel value because this View shrinks its own
     * text (see [enableSmartWordSizing]) - a width resolved once against the designed size would
     * be wrong at every size the cascade actually lands on.
     */
    var strokeOutlineWidthFraction: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** The stroke's colour. Ignored while [strokeOutlineWidthFraction] is zero. */
    var strokeOutlineColor: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Pixel floor for the stroke, so the thinnest setting cannot alias into nothing. */
    var strokeOutlineMinWidthPx: Float = 0f

    /**
     * A filled box drawn behind each line of text, already carrying its own alpha. Zero draws none.
     *
     * Drawn here rather than as the View's `background`: this View is `match_parent` on every face
     * that uses it, so a View background would be a full-width band across the screen instead of a
     * plate behind the words. Compose paints the same thing through `TextStyle.background`, which
     * is also per line - see `TextBackdropStyle` for why the two are kept to what both can do.
     */
    var textBackdropColor: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var smartSizingEnabled = false
    private var smartMaxSizePx = 0f
    private var smartMinSizePx = 0f
    private var sizingMode = TextSizingMode.SMART

    /**
     * Whether the view's own height is a real ceiling on the chosen text size.
     *
     * Off by default, and [fitsWithinLineLimit] explains why: on a `wrap_content` line the height
     * is a *consequence* of the size chosen on a previous pass, so treating it as a constraint
     * rejects sizes that in fact fit. The Matejdro face is the case that reasoning does not cover -
     * its two lines are weighted bands of the block, so each has a fixed height decided before any
     * text is measured, and that height genuinely is independent. Without this the cascade would
     * happily pick its 112sp ceiling for a short title and let the band clip it.
     */
    var fitsWithinViewHeight = false
        set(value) {
            if (field == value) return
            field = value
            requestSmartResize()
        }

    /**
     * The height, in pixels, this line may take inside a block whose other lines it must not push
     * out of sight - or null to keep the view's own behaviour.
     *
     * Classic's title shares one centred block with the artist row and the track time, inside the
     * round screen's inscribed square. Its cascade picked the largest size that wraps the title
     * cleanly within its line limit and never asked whether two lines *that* size left room for
     * the rest: a short two-word title such as "Panic Station" settled on two 40sp lines, the block
     * overflowed its box, and the track time was cut off at the bottom of the screen on the
     * default settings. The caller knows what else is in the block, so it supplies the room; the
     * cascade then also steps down until the lines fit it, and the wrap modes stop at the number of
     * lines it can hold. Read when the size is chosen, so it follows the other lines appearing and
     * disappearing - call [refreshSizing] when they do.
     */
    var heightBudget: (() -> Int)? = null

    /** Chooses the size again, for a caller whose [heightBudget] has just changed. */
    fun refreshSizing() = requestSmartResize()

    // The cap wrapping respects before giving up and switching to a scrolling single line. Follows
    // the chosen text mode, so "Wrap to 3 lines" really wraps to three.
    private var wrapMaxLines = 1
    // The XML-configured cap (e.g. maxLines=2), captured before any mode can change `maxLines`.
    // Re-reading the view's own maxLines later would pick up the 1 an active marquee leaves behind
    // and shrink the wrap cap permanently.
    private var declaredMaxLines = -1
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
    fun enableSmartWordSizing(
            maxSizeSp: Float,
            minSizeSp: Float,
            mode: TextSizingMode? = null,
            wrapLines: Int? = null
    ) {
        val firstConfiguration = !smartSizingEnabled
        smartSizingEnabled = true
        smartMaxSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, maxSizeSp, resources.displayMetrics)
        smartMinSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, minSizeSp, resources.displayMetrics)
        if (declaredMaxLines < 0) declaredMaxLines = maxLines
        // Omitting [mode] and [wrapLines] *keeps* whatever the text mode already selected, and
        // that is load-bearing rather than tidy. This is re-called whenever typography changes to
        // re-supply only the sizes - forty lines after the mode was applied, in the same function -
        // so defaulting to SMART here reverted the user's choice immediately every time. The result
        // was that Classic's title behaviour setting did nothing at all: not just the wrap counts,
        // but marquee and shrink too.
        if (mode != null) sizingMode = mode
        applyWrapLines(
                wrapLines ?: if (firstConfiguration) declaredMaxLines else wrapMaxLines)
        requestSmartResize()
    }

    /**
     * Changes the sizing mode of an already-configured view (e.g. reacting to a settings change)
     * without needing to re-supply the min/max sizes.
     *
     * [wrapLines] is how many lines [TextSizingMode.WRAP] may use. It is a parameter rather than a
     * property of the mode because the user picks the number: "Cut off after one line", "Wrap to 3
     * lines" and "Wrap to 5 lines" are all WRAP, differing only here. Null keeps the XML cap, which
     * is what the shrink/smart cascade wraps to before it gives up.
     */
    fun setSizingMode(mode: TextSizingMode, wrapLines: Int? = null) {
        if (declaredMaxLines < 0) declaredMaxLines = maxLines
        val resolvedLines = wrapLines ?: declaredMaxLines
        if (sizingMode == mode && wrapMaxLines == resolvedLines) return
        sizingMode = mode
        applyWrapLines(resolvedLines)
        requestSmartResize()
    }

    /** A scrolling title is single-line by construction; disableMarquee restores the cap from
     *  [wrapMaxLines], so setting it here while the marquee is up would fight that restore. */
    private fun applyWrapLines(lines: Int) {
        wrapMaxLines = lines.coerceAtLeast(1)
        if (!marqueeActive) maxLines = wrapMaxLines
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        // A new title starts from rest, like any line that has just appeared. Also reached from
        // TextView's constructor, before this class's initialisers have run; restartMarquee only
        // touches primitives and the line cache is keyed by the text, so that is harmless.
        restartMarquee()
        requestSmartResize()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        restartMarquee()
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
        // The mode's line count, but no more lines than the block has room for: a fifth line
        // that pushes the artist or the time out of the screen is worse than an ellipsis.
        val measurePaint = Paint(paint)
        measurePaint.textSize = smartMaxSizePx
        val fitting = linesWithinBudget(measurePaint)
        maxLines = if (fitting == null) wrapMaxLines else minOf(wrapMaxLines, fitting)
    }

    /** How many lines at [paint]'s size fit [heightBudget], or null when there is no budget. */
    private fun linesWithinBudget(paint: Paint): Int? {
        val budget = heightBudget?.invoke() ?: return null
        if (budget <= 0) return null
        return (budget / lineHeightFor(paint)).toInt().coerceAtLeast(1)
    }

    /** One line's height at [paint]'s size, the way this view lays its lines out. */
    private fun lineHeightFor(paint: Paint): Float {
        val metrics = paint.fontMetrics
        return (metrics.descent - metrics.ascent) * lineSpacingMultiplier + lineSpacingExtra
    }

    /** Always a single line at [smartMaxSizePx]; scrolls (marquee) only if that line doesn't fit,
     *  otherwise sits still - never shrinks or wraps. */
    private fun applyMarqueeMode(words: List<String>, availableWidth: Float) {
        val measurePaint = Paint(paint)
        measurePaint.textSize = smartMaxSizePx
        val singleLineWidth = measurePaint.measureText(words.joinToString(" "))

        if (singleLineWidth <= availableWidth) {
            disableMarquee()
            maxLines = wrapMaxLines
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
            // Shrink stops at its floor even when that still does not fit, so the line count is
            // capped to the room as well - an ellipsis rather than a line pushed out of sight.
            measurePaint.textSize = sizePx
            val fitting = linesWithinBudget(measurePaint)
            maxLines = if (fitting == null) wrapMaxLines else minOf(wrapMaxLines, fitting)
        } else {
            enableMarquee(smartMinSizePx)
        }
    }

    /**
     * Whether [words] fit at [paint]'s current text size without needing more than [wrapMaxLines]
     * lines or breaking a word mid-way. Deliberately NOT also checking the view's own height by
     * default: for a `wrap_content` height view, that height is itself a *consequence* of the
     * current text/size (computed on a previous layout pass, often before this resize even runs),
     * not a real independent constraint - using it as a ceiling caused titles that comfortably fit
     * in (say) 2 lines to be wrongly rejected and fall back to marquee instead.
     *
     * [fitsWithinViewHeight] opts a caller back in, and only a caller for whom that reasoning does
     * not hold: a line laid out as a fixed band knows its height before any text is measured.
     */
    private fun fitsWithinLineLimit(words: List<String>, paint: Paint, maxWidth: Float): Boolean {
        val widestWord = words.maxOf { paint.measureText(it) }
        if (widestWord > maxWidth) {
            return false
        }

        val lines = estimateWrappedLineCount(words, paint, maxWidth)
        if (lines > wrapMaxLines) {
            return false
        }
        val budget = heightBudget?.invoke()
        if (budget != null && budget > 0 && lines * lineHeightFor(paint) > budget) {
            return false
        }
        if (!fitsWithinViewHeight) {
            return true
        }
        val availableHeight = height - paddingTop - paddingBottom
        // Before the first layout there is no band to measure against yet. Accepting the size
        // keeps this from reporting "nothing fits" and collapsing to the floor; onSizeChanged
        // re-runs the cascade as soon as the real height is known.
        if (availableHeight <= 0) {
            return true
        }
        val metrics = paint.fontMetrics
        return lines * (metrics.descent - metrics.ascent) <= availableHeight
    }

    /**
     * Turns the scroll on. The line is drawn by [drawMarquee], not by TextView's own marquee.
     *
     * TextView's marquee keeps its position to itself, so nothing outside it can tell a line that
     * is moving from one resting between passes - and its fading edge stayed on through the rest,
     * dissolving the clipped end of a title that was standing still. Drawing the line here, from
     * [MarqueeMotion], is what lets the dissolve follow the movement, and it is the same motion the
     * Compose faces and the phone's miniature run, so a title scrolls and fades alike on all three.
     *
     * TextView itself is left holding one ellipsized line, which is all it needs to measure the
     * view's height; that layout is never drawn while the line overflows.
     */
    private fun enableMarquee(sizePx: Float) {
        if (!marqueeActive) {
            marqueeActive = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            restartMarquee()
        }
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
    }

    private fun disableMarquee() {
        if (marqueeActive) {
            marqueeActive = false
            maxLines = wrapMaxLines
            ellipsize = TextUtils.TruncateAt.END
            marqueeLine = null
            invalidate()
        }
    }

    private var marqueePaused = false

    /**
     * Freezes a scrolling title at rest (e.g. for ambient mode, where a continuously animating
     * marquee would be both a burn-in risk and a pointless battery drain) without forgetting that
     * it's in marquee mode - [setMarqueePaused] (false) picks the animation back up afterwards,
     * from rest, as a title that has just appeared would.
     */
    fun setMarqueePaused(paused: Boolean) {
        if (marqueePaused == paused) return
        marqueePaused = paused
        restartMarquee()
    }

    /** When the current pass began, on [AnimationUtils]'s clock; negative until the next draw. */
    private var marqueeStartMs = -1L

    /** The whole text laid out on one line at its natural width - what the scroll moves. */
    private var marqueeLine: StaticLayout? = null
    private var marqueeLineKey: MarqueeLineKey? = null
    private val marqueeMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /** What [marqueeLine] was built from; anything else changing means building it again. */
    private data class MarqueeLineKey(
            val text: CharSequence,
            val textSize: Float,
            val typeface: Typeface?,
            val letterSpacing: Float,
            val includePad: Boolean,
            val rtl: Boolean)

    /** Back to rest: the next draw starts a new pass from the line's start. */
    private fun restartMarquee() {
        marqueeStartMs = -1L
        if (marqueeActive) invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartMarquee()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Returning to a screen shows the title from its start rather than wherever the clock
        // would have carried it while nobody could see it.
        restartMarquee()
    }

    private fun obtainMarqueeLine(): StaticLayout? {
        val content = text ?: return null
        if (content.isEmpty()) return null
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val key = MarqueeLineKey(content.toString(), paint.textSize, paint.typeface,
                paint.letterSpacing, includeFontPadding, rtl)
        marqueeLine?.let { if (key == marqueeLineKey) return it }
        val width = ceil(Layout.getDesiredWidth(content, paint)).toInt().coerceAtLeast(1)
        val built = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(includeFontPadding)
                .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
                .setTextDirection(
                        if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL
                        else TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setMaxLines(1)
                .build()
        marqueeLine = built
        marqueeLineKey = key
        return built
    }

    /**
     * Draws the scrolling line, or returns false to let TextView draw it - which is what happens
     * once the text fits after all (the view grew, or the text got shorter).
     */
    private fun drawMarquee(canvas: Canvas): Boolean {
        val line = obtainMarqueeLine() ?: return false
        val left = compoundPaddingLeft.toFloat()
        val right = (width - compoundPaddingRight).toFloat()
        val viewport = right - left
        val content = line.width.toFloat()
        if (!MarqueeMotion.overflows(content, viewport)) return false

        val density = resources.displayMetrics.density
        val loop = MarqueeMotion.loopPx(content, viewport)
        val pxPerSecond = MarqueeMotion.VELOCITY_DP_PER_SECOND * density
        val now = AnimationUtils.currentAnimationTimeMillis()
        val running = !marqueePaused && isShown
        if (!running) {
            marqueeStartMs = -1L
        } else if (marqueeStartMs < 0L) {
            marqueeStartMs = now
        }
        val elapsed = if (running) now - marqueeStartMs else 0L
        val offset = MarqueeMotion.offsetAt(elapsed, loop, pxPerSecond)
        val fadePx = MarqueeMotion.fadeWidthPx(MarqueeMotion.FADE_WIDTH_DP * density, viewport)
        val strength = MarqueeMotion.fadeStrength(offset, loop, fadePx)

        // Same vertical placement TextView gives its own single line.
        val boxHeight = height - extendedPaddingTop - extendedPaddingBottom
        val slack = (boxHeight - line.height).coerceAtLeast(0)
        val lineTop = extendedPaddingTop + when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> 0
            Gravity.BOTTOM -> slack
            else -> slack / 2
        }
        // The resting copy sits on the start edge, the next one a loop behind it.
        val rtl = line.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
        val firstX = if (rtl) right - content + offset else left - offset
        val secondX = if (rtl) firstX - loop else firstX + loop

        // The View's own scroll is TextView's business (it centres its own line with it), and
        // the canvas arrives translated by it; this line is placed in the View's own coordinates.
        canvas.save()
        canvas.translate(scrollX.toFloat(), scrollY.toFloat())
        val layer = if (strength > 0f) {
            canvas.saveLayer(left, 0f, right, height.toFloat(), null)
        } else {
            canvas.save()
        }
        canvas.clipRect(left, 0f, right, height.toFloat())
        drawMarqueeCopy(canvas, line, firstX, lineTop.toFloat(), right, left)
        if (offset > 0f) drawMarqueeCopy(canvas, line, secondX, lineTop.toFloat(), right, left)
        if (strength > 0f) {
            val edge = fadePx / viewport
            val outer = Color.argb(((1f - strength) * 255f).toInt(), 0, 0, 0)
            marqueeMaskPaint.shader = LinearGradient(
                    left, 0f, right, 0f,
                    intArrayOf(outer, Color.BLACK, Color.BLACK, outer),
                    floatArrayOf(0f, edge, 1f - edge, 1f),
                    Shader.TileMode.CLAMP)
            canvas.drawRect(left, 0f, right, height.toFloat(), marqueeMaskPaint)
            marqueeMaskPaint.shader = null
        }
        canvas.restoreToCount(layer)
        canvas.restore()

        if (running) {
            val wait = MarqueeMotion.millisUntilMoving(elapsed, loop, pxPerSecond)
            // Nothing changes while the line rests, so the next frame is not asked for until it
            // moves again.
            if (wait > 0L) postInvalidateDelayed(wait) else postInvalidateOnAnimation()
        }
        return true
    }

    /** One copy of the scrolling line at [x], with every pass [onDraw] gives the static text. */
    private fun drawMarqueeCopy(
            canvas: Canvas,
            line: StaticLayout,
            x: Float,
            top: Float,
            clipRight: Float,
            clipLeft: Float
    ) {
        if (x >= clipRight || x + line.width <= clipLeft) return
        canvas.save()
        canvas.translate(x, top)
        if (textBackdropColor != 0) {
            backdropPaint.color = textBackdropColor
            canvas.drawRect(line.getLineLeft(0), line.getLineTop(0).toFloat(),
                    line.getLineRight(0), line.getLineBottom(0).toFloat(), backdropPaint)
        }
        paint.color = currentTextColor
        paint.drawableState = drawableState
        drawTextPasses { line.draw(canvas) }
        canvas.restore()
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

    /**
     * Fills the box each laid-out line occupies.
     *
     * `getLineLeft`/`getLineRight` are the *text* extents rather than the View's, which is what
     * makes this hug the words on a centred line. Total padding is deliberately zero: the line box
     * already runs from the line's top to its bottom, which is taller than the glyphs and reads as
     * a highlight rather than as a tight outline of the letters.
     */
    private fun drawTextBackdrop(canvas: Canvas) {
        if (textBackdropColor == 0) return
        val textLayout = layout ?: return
        backdropPaint.color = textBackdropColor
        for (line in 0 until textLayout.lineCount) {
            canvas.drawRect(
                    totalPaddingLeft + textLayout.getLineLeft(line),
                    (totalPaddingTop + textLayout.getLineTop(line)).toFloat(),
                    totalPaddingLeft + textLayout.getLineRight(line),
                    (totalPaddingTop + textLayout.getLineBottom(line)).toFloat(),
                    backdropPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (marqueeActive && drawMarquee(canvas)) return
        drawTextBackdrop(canvas)
        drawTextPasses { super.onDraw(canvas) }
    }

    /**
     * Runs [draw] once per pass the text needs: a stroke alone for [displayTextOutline], a stroke
     * under a fill when [strokeOutlineWidthFraction] is set, otherwise just the fill. Shared by the
     * static text (TextView's own draw) and the scrolling line, so a marquee keeps its outline.
     */
    private inline fun drawTextPasses(draw: () -> Unit) {
        if (displayTextOutline) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = outlineWidth
            draw()
            return
        }

        paint.style = Paint.Style.FILL
        val strokeWidth = if (strokeOutlineWidthFraction > 0f) {
            (textSize * strokeOutlineWidthFraction).coerceAtLeast(strokeOutlineMinWidthPx)
        } else {
            0f
        }
        if (strokeWidth <= 0f) {
            draw()
            return
        }

        // Two passes, because no text API strokes and fills in one. The stroke's colour cannot be
        // set through `paint.color`: TextView re-applies the current text colour to its own paint
        // at the top of every onDraw, so the assignment would be overwritten before anything is
        // drawn. A SRC_IN colour filter is applied *after* that and wins, without touching the
        // text colour itself - which would invalidate() and re-enter this method.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.strokeJoin = Paint.Join.ROUND
        val previousFilter = paint.colorFilter
        paint.colorFilter = PorterDuffColorFilter(strokeOutlineColor, PorterDuff.Mode.SRC_IN)
        draw()

        paint.colorFilter = previousFilter
        paint.style = Paint.Style.FILL
        draw()
    }
}
