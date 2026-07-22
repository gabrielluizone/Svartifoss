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
import android.widget.OverScroller
import android.widget.ScrollView
import com.svartifoss.snfell.R
import kotlin.math.abs
import kotlin.math.min

/** Position contract shared by the View scroll host and its fixed overlay indicator. */
internal fun quickActionScrollFraction(scrollOffset: Int, scrollRange: Int): Float =
        if (scrollRange <= 0) 0f
        else (scrollOffset.toFloat() / scrollRange).coerceIn(0f, 1f)

/**
 * Scroll host for Quick Actions. The content remains View-based so media-app drawables and all
 * existing callbacks stay intact.
 *
 * This deliberately does NOT imitate ScalingLazyColumn's per-item scale/alpha response the way an
 * earlier revision did: pills shrinking and fading at the edges under plain ScrollView physics
 * read as a knockoff of the Queue screen rather than as this panel's own surface. Items keep
 * their real size and a native vertical fading edge handles the round-bezel clipping instead.
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
        // No per-item scaling and no fading edge: items keep their real size/opacity and simply
        // slide past the round bezel, which clips them naturally. Generous top/bottom content
        // padding (see the layout) lets the first and last pills settle clear of the bezel.
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
        publishScrollPosition(showIndicator = false)
    }

    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        if (top != oldTop) publishScrollPosition(showIndicator = true)
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
