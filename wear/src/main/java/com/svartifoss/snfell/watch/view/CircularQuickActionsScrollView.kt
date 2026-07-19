package com.svartifoss.snfell.watch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.svartifoss.snfell.R
import kotlin.math.abs

/**
 * Scroll host for Quick Actions that follows the same round-screen language as QueueScreen:
 * rows gently recede at the top/bottom bezel and the scroll position is drawn as a short arc on
 * the right edge. A stock ScrollView scrollbar is straight, permanently square-screen shaped,
 * and was especially conspicuous on small round watches.
 */
class CircularQuickActionsScrollView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = android.R.attr.scrollViewStyle
) : ScrollView(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val indicatorBounds = RectF()
    private var indicatorVisibleUntilMs = 0L

    init {
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        setWillNotDraw(false)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateRoundChildTransforms()
    }

    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        updateRoundChildTransforms()
        if (top != oldTop) {
            indicatorVisibleUntilMs = android.os.SystemClock.uptimeMillis() + INDICATOR_HOLD_MS
            postInvalidateOnAnimation()
        }
    }

    /**
     * ScalingLazyColumn makes rows nearest the bezel smaller/dimmer. Reproducing that treatment
     * on the existing view-based panel keeps all of its media-app actions and callbacks intact.
     */
    private fun updateRoundChildTransforms() {
        val content = getChildAt(0) as? LinearLayout ?: return
        val viewportCenter = scrollY + height / 2f
        val halfViewport = (height / 2f).coerceAtLeast(1f)
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child.visibility == View.GONE) continue
            if (child.id == R.id.quick_action_extra_list && child is LinearLayout) {
                // The configured rows share one container for dynamic inflation. Treat each as a
                // list item instead of shrinking the whole (potentially tall) group as one card.
                resetTransform(child)
                for (rowIndex in 0 until child.childCount) {
                    val row = child.getChildAt(rowIndex)
                    applyRoundTransform(
                            row,
                            child.top + row.top + row.height / 2f,
                            viewportCenter,
                            halfViewport
                    )
                }
            } else {
                applyRoundTransform(
                        child,
                        child.top + child.height / 2f,
                        viewportCenter,
                        halfViewport
                )
            }
        }
    }

    private fun applyRoundTransform(
            child: View,
            childCenter: Float,
            viewportCenter: Float,
            halfViewport: Float
    ) {
        val edgeFraction = (abs(childCenter - viewportCenter) / halfViewport).coerceIn(0f, 1f)
        val scale = 1f - 0.14f * edgeFraction * edgeFraction
        child.pivotX = child.width / 2f
        child.pivotY = child.height / 2f
        child.scaleX = scale
        child.scaleY = scale
        child.alpha = 1f - 0.28f * edgeFraction * edgeFraction
    }

    private fun resetTransform(child: View) {
        child.scaleX = 1f
        child.scaleY = 1f
        child.alpha = 1f
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        val content = getChildAt(0) ?: return
        val scrollRange = (content.height - height).coerceAtLeast(0)
        if (scrollRange == 0) return

        val now = android.os.SystemClock.uptimeMillis()
        val fadeProgress = ((now - indicatorVisibleUntilMs).toFloat() / INDICATOR_FADE_MS)
                .coerceIn(0f, 1f)
        val alpha = 1f - fadeProgress
        if (alpha <= 0.01f) return

        val strokeInset = trackPaint.strokeWidth / 2f + 2f * density
        val side = minOf(width, height).toFloat() - strokeInset * 2f
        indicatorBounds.set(
                (width - side) / 2f,
                (height - side) / 2f,
                (width + side) / 2f,
                (height + side) / 2f
        )

        trackPaint.alpha = (0.12f * alpha * 255f).toInt()
        canvas.drawArc(indicatorBounds, -ARC_SPAN / 2f, ARC_SPAN, false, trackPaint)

        val fraction = (scrollY.toFloat() / scrollRange).coerceIn(0f, 1f)
        val thumbStart = -ARC_SPAN / 2f + (ARC_SPAN - THUMB_SWEEP) * fraction
        trackPaint.alpha = (0.80f * alpha * 255f).toInt()
        canvas.drawArc(indicatorBounds, thumbStart, THUMB_SWEEP, false, trackPaint)

        if (now < indicatorVisibleUntilMs) {
            postInvalidateDelayed((indicatorVisibleUntilMs - now).coerceAtMost(INDICATOR_HOLD_MS))
        } else if (fadeProgress < 1f) {
            postInvalidateOnAnimation()
        }
    }

    private companion object {
        const val ARC_SPAN = 22f
        const val THUMB_SWEEP = 5.5f
        const val INDICATOR_HOLD_MS = 1_200L
        const val INDICATOR_FADE_MS = 300f
    }
}
