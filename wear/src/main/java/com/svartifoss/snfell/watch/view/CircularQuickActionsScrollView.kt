package com.svartifoss.snfell.watch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.ScrollView
import com.svartifoss.snfell.R
import kotlin.math.abs
import kotlin.math.min

/** Scale/opacity applied to one Quick Actions item at its current viewport position. */
internal data class QuickActionItemTransform(val scale: Float, val alpha: Float)

/**
 * View equivalent of Wear Compose's `calculateScaleAndAlpha` using the default
 * `ScalingLazyColumnDefaults.scalingParams()` values from the queue:
 *
 * - edgeScale 0.7, edgeAlpha 0.5
 * - element-height range 0.2..0.6
 * - transition-area range 0.35..0.55
 * - CubicBezierEasing(0.3, 0, 0.7, 1)
 *
 * Keeping this as a pure function makes the visual contract testable without an Android device.
 */
internal fun quickActionItemTransform(
        viewportStart: Float,
        viewportEnd: Float,
        itemStart: Float,
        itemEnd: Float
): QuickActionItemTransform {
    val viewportSize = viewportEnd - viewportStart
    if (viewportSize <= 0f || itemEnd <= itemStart) {
        return QuickActionItemTransform(scale = 1f, alpha = 1f)
    }

    // This is the same closest-edge metric used by ScalingLazyColumn. It deliberately reaches
    // beyond the actual visible height for an item near the centre, keeping centre items at 1x.
    val closestEdgeDistance = min(viewportEnd - itemStart, itemEnd - viewportStart)
    val viewportFraction = closestEdgeDistance / viewportSize
    val elementFraction = (itemEnd - itemStart) / viewportSize
    val elementProgress = inverseLerp(0.2f, 0.6f, elementFraction)
    val transitionArea = lerp(0.35f, 0.55f, elementProgress)

    if (viewportFraction >= transitionArea) {
        return QuickActionItemTransform(scale = 1f, alpha = 1f)
    }

    val transitionProgress = (1f - viewportFraction / transitionArea).coerceIn(0f, 1f)
    val eased = scalingCubicBezier(transitionProgress)
    return QuickActionItemTransform(
            scale = lerp(1f, 0.7f, eased),
            alpha = lerp(1f, 0.5f, eased)
    )
}

/** Position contract shared by the View scroll host and its fixed overlay indicator. */
internal fun quickActionScrollFraction(scrollOffset: Int, scrollRange: Int): Float =
        if (scrollRange <= 0) 0f
        else (scrollOffset.toFloat() / scrollRange).coerceIn(0f, 1f)

private fun inverseLerp(start: Float, end: Float, value: Float): Float =
        ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

/** Evaluates CubicBezier(0.3, 0, 0.7, 1) as an easing (solve X, then return Y). */
private fun scalingCubicBezier(x: Float): Float {
    val target = x.coerceIn(0f, 1f)
    var t = target
    repeat(6) {
        val currentX = cubicCoordinate(t, 0.3f, 0.7f)
        val derivative = cubicDerivative(t, 0.3f, 0.7f)
        if (abs(derivative) > 0.0001f) {
            t = (t - (currentX - target) / derivative).coerceIn(0f, 1f)
        }
    }
    return cubicCoordinate(t, 0f, 1f).coerceIn(0f, 1f)
}

private fun cubicCoordinate(t: Float, control1: Float, control2: Float): Float {
    val inverse = 1f - t
    return 3f * inverse * inverse * t * control1 +
            3f * inverse * t * t * control2 + t * t * t
}

private fun cubicDerivative(t: Float, control1: Float, control2: Float): Float {
    val inverse = 1f - t
    return 3f * inverse * inverse * control1 +
            6f * inverse * t * (control2 - control1) +
            3f * t * t * (1f - control2)
}

/**
 * Scroll host for Quick Actions. The content remains View-based so media-app drawables and all
 * existing callbacks stay intact, but its per-item geometry now follows the same scaling curve as
 * QueueScreen's ScalingLazyColumn.
 *
 * The curved indicator is intentionally *not* drawn here. ScrollView's descendant canvas is in a
 * scrolling coordinate space on some Wear framework versions, which previously alternated
 * between a travelling track and a visually frozen thumb. [QuickActionsScrollIndicatorView] is a
 * fixed sibling overlay, matching QueueScreen's `Box { list; indicator }` structure.
 */
class CircularQuickActionsScrollView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = android.R.attr.scrollViewStyle
) : ScrollView(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val hitRect = Rect()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val rotaryScroller = OverScroller(context)
    private var rotaryTargetY = 0
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureMoved = false
    private var dismissRequestListener: (() -> Unit)? = null
    private var indicator: QuickActionsScrollIndicatorView? = null

    init {
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isSmoothScrollingEnabled = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Resolve after XML inflation, without making MainActivity own indicator plumbing.
        post {
            indicator = rootView.findViewById(R.id.quick_actions_scroll_indicator)
            publishScrollPosition(showIndicator = false)
        }
    }

    override fun onDetachedFromWindow() {
        rotaryScroller.abortAnimation()
        indicator?.hideImmediately()
        indicator = null
        super.onDetachedFromWindow()
    }

    /**
     * The panel fills the screen, so its backdrop can never receive a tap through this
     * ScrollView. Report taps that do not land on an actual action, plus a rightward fallback
     * gesture for devices whose parent SwipeDismissFrameLayout loses the gesture to scrolling.
     */
    fun setOnDismissRequestListener(listener: (() -> Unit)?) {
        dismissRequestListener = listener
    }

    /**
     * Rotary scrolling with one continuous target. ScrollView.smoothScrollBy switches later
     * events to immediate jumps when crown events arrive less than 250ms apart; that was the
     * visible stop/start motion absent from QueueScreen's RotaryScrollable behavior.
     */
    fun smoothScrollByRotary(deltaY: Int) {
        val range = scrollRange()
        if (range <= 0 || deltaY == 0) return

        val base = if (rotaryScroller.isFinished) scrollY else rotaryTargetY
        rotaryTargetY = (base + deltaY).coerceIn(0, range)
        rotaryScroller.abortAnimation()
        val distance = rotaryTargetY - scrollY
        if (distance == 0) return
        rotaryScroller.startScroll(0, scrollY, 0, distance, ROTARY_SCROLL_DURATION_MS)
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        super.computeScroll()
        if (rotaryScroller.computeScrollOffset()) {
            scrollTo(0, rotaryScroller.currY)
            postInvalidateOnAnimation()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rotaryScroller.abortAnimation()
                rotaryTargetY = scrollY
                gestureStartX = event.rawX
                gestureStartY = event.rawY
                gestureMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - gestureStartX) > touchSlop ||
                        abs(event.rawY - gestureStartY) > touchSlop) {
                    gestureMoved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = event.rawX - gestureStartX
                val deltaY = event.rawY - gestureStartY
                val rightwardDismiss = deltaX >= maxOf(width * 0.22f, 44f * density) &&
                        deltaX > abs(deltaY) * 1.25f
                val blankTap = !gestureMoved &&
                        !hasClickableDescendantAt(this, event.rawX.toInt(), event.rawY.toInt())
                if (rightwardDismiss || blankTap) {
                    // Let the pressed child clear its state before the overlay disappears.
                    val handled = super.dispatchTouchEvent(event)
                    post { dismissRequestListener?.invoke() }
                    return handled || dismissRequestListener != null
                }
            }
            MotionEvent.ACTION_CANCEL -> gestureMoved = false
        }
        return super.dispatchTouchEvent(event)
    }

    private fun hasClickableDescendantAt(
            parent: ViewGroup,
            screenX: Int,
            screenY: Int
    ): Boolean {
        for (index in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(index)
            if (child.visibility != View.VISIBLE || child.alpha <= 0f) continue
            if (child is ViewGroup && hasClickableDescendantAt(child, screenX, screenY)) {
                return true
            }
            if (child.isClickable && child.isEnabled &&
                    child.getGlobalVisibleRect(hitRect) && hitRect.contains(screenX, screenY)) {
                return true
            }
        }
        return false
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (rotaryScroller.isFinished) rotaryTargetY = scrollY
        updateRoundChildTransforms()
        publishScrollPosition(showIndicator = false)
    }

    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        updateRoundChildTransforms()
        if (top != oldTop) publishScrollPosition(showIndicator = true)
    }

    /** Applies the same default scale/alpha response as QueueScreen's ScalingLazyColumn. */
    private fun updateRoundChildTransforms() {
        val content = getChildAt(0) as? LinearLayout ?: return
        val viewportStart = paddingTop.toFloat()
        val viewportEnd = (height - paddingBottom).toFloat()
        if (viewportEnd <= viewportStart) return
        val contentTop = content.top - scrollY.toFloat()

        var index = 0
        while (index < content.childCount) {
            val child = content.getChildAt(index)
            if (child.visibility == View.GONE) {
                index++
                continue
            }

            // QueueScreen treats title + artist as one header item. Apply one transform to the
            // adjacent metadata pair as well, avoiding the two lines bending independently.
            val next = if (index + 1 < content.childCount) {
                content.getChildAt(index + 1)
            } else {
                null
            }
            val isMetadataPair = child.id == R.id.quick_action_panel_title &&
                    next?.id == R.id.quick_action_panel_artist && next.visibility != View.GONE
            if (isMetadataPair) {
                val itemStart = contentTop + child.top
                val itemEnd = contentTop + next!!.bottom
                applyRoundTransform(child, itemStart, itemEnd, viewportStart, viewportEnd)
                applyRoundTransform(next, itemStart, itemEnd, viewportStart, viewportEnd)
                index += 2
                continue
            }

            if (child.id == R.id.quick_action_extra_list && child is LinearLayout) {
                // Dynamic actions share a container; each row is a ScalingLazyColumn item.
                resetTransform(child)
                for (rowIndex in 0 until child.childCount) {
                    val row = child.getChildAt(rowIndex)
                    val itemStart = contentTop + child.top + row.top
                    applyRoundTransform(
                            row,
                            itemStart,
                            itemStart + row.height,
                            viewportStart,
                            viewportEnd
                    )
                }
            } else {
                val itemStart = contentTop + child.top
                applyRoundTransform(
                        child,
                        itemStart,
                        itemStart + child.height,
                        viewportStart,
                        viewportEnd
                )
            }
            index++
        }
    }

    private fun applyRoundTransform(
            child: View,
            itemStart: Float,
            itemEnd: Float,
            viewportStart: Float,
            viewportEnd: Float
    ) {
        val transform = quickActionItemTransform(
                viewportStart, viewportEnd, itemStart, itemEnd)
        child.pivotX = child.width / 2f
        child.pivotY = child.height / 2f
        child.scaleX = transform.scale
        child.scaleY = transform.scale
        child.alpha = transform.alpha
    }

    private fun resetTransform(child: View) {
        child.scaleX = 1f
        child.scaleY = 1f
        child.alpha = 1f
    }

    private fun scrollRange(): Int {
        val contentHeight = getChildAt(0)?.height ?: 0
        val viewportHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        return (contentHeight - viewportHeight).coerceAtLeast(0)
    }

    private fun publishScrollPosition(showIndicator: Boolean) {
        val range = scrollRange()
        indicator?.update(
                fraction = quickActionScrollFraction(scrollY, range),
                scrollable = range > 0,
                show = showIndicator
        )
    }

    private companion object {
        const val ROTARY_SCROLL_DURATION_MS = 160
    }
}

/**
 * Fixed curved indicator layered above [CircularQuickActionsScrollView], structurally identical
 * to QueueScreen's Canvas overlay. Only the thumb position changes; the complete track remains
 * attached to the physical bezel while content scrolls underneath it.
 */
class QuickActionsScrollIndicatorView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val indicatorBounds = RectF()
    private var scrollFraction = 0f
    private var scrollable = false
    private val fadeOut = Runnable {
        animate().cancel()
        animate().alpha(0f).setDuration(INDICATOR_FADE_MS).start()
    }

    init {
        alpha = 0f
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    internal fun update(fraction: Float, scrollable: Boolean, show: Boolean) {
        this.scrollFraction = fraction.coerceIn(0f, 1f)
        this.scrollable = scrollable
        if (!scrollable) {
            hideImmediately()
            return
        }
        invalidate()
        if (show) {
            removeCallbacks(fadeOut)
            animate().cancel()
            alpha = 1f
            postDelayed(fadeOut, INDICATOR_HOLD_MS)
        }
    }

    internal fun hideImmediately() {
        removeCallbacks(fadeOut)
        animate().cancel()
        alpha = 0f
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(fadeOut)
        animate().cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!scrollable) return

        val strokeInset = trackPaint.strokeWidth / 2f + 2f * density
        val side = min(width, height).toFloat() - strokeInset * 2f
        indicatorBounds.set(
                (width - side) / 2f,
                (height - side) / 2f,
                (width + side) / 2f,
                (height + side) / 2f
        )

        trackPaint.alpha = (0.12f * 255f).toInt()
        canvas.drawArc(indicatorBounds, -ARC_SPAN / 2f, ARC_SPAN, false, trackPaint)

        val thumbStart = -ARC_SPAN / 2f + (ARC_SPAN - THUMB_SWEEP) * scrollFraction
        trackPaint.alpha = (0.80f * 255f).toInt()
        canvas.drawArc(indicatorBounds, thumbStart, THUMB_SWEEP, false, trackPaint)
    }

    private companion object {
        const val ARC_SPAN = 22f
        const val THUMB_SWEEP = 5.5f
        const val INDICATOR_HOLD_MS = 1_200L
        const val INDICATOR_FADE_MS = 300L
    }
}
