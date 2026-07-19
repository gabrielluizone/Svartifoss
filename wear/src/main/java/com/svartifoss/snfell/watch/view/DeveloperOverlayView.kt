package com.svartifoss.snfell.watch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils

/**
 * Lightweight on-watch diagnostics enabled from the phone's developer settings.
 *
 * It intentionally observes the already-laid-out hierarchy instead of changing layout params:
 * turning diagnostics on cannot perturb the geometry it is meant to inspect. The overlay is also
 * non-clickable and excluded from accessibility so every underlying player target stays usable.
 */
class DeveloperOverlayView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : View(context, attrs) {

    var showLayoutBounds: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var showPlayerInfo: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var playerInfo: String = ""
        set(value) {
            if (field == value) return
            field = value
            if (showPlayerInfo) invalidate()
        }

    private val density = resources.displayMetrics.density
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density.coerceAtLeast(1f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.MONOSPACE
        textSize = 8f * resources.displayMetrics.scaledDensity
    }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.MONOSPACE
        textSize = 9f * resources.displayMetrics.scaledDensity
    }
    private val infoBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDD08090C.toInt()
        style = Paint.Style.FILL
    }
    private val ownLocation = IntArray(2)
    private val childLocation = IntArray(2)
    private val bounds = RectF()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (showLayoutBounds) drawHierarchyBounds(canvas)
        if (showPlayerInfo && playerInfo.isNotBlank()) drawInfo(canvas)
    }

    private fun drawHierarchyBounds(canvas: Canvas) {
        val root = parent as? ViewGroup ?: return
        getLocationOnScreen(ownLocation)
        var visited = 0

        fun drawView(view: View, depth: Int) {
            if (view === this || view.visibility != VISIBLE || view.width <= 0 || view.height <= 0 ||
                    view.alpha <= 0f || visited++ >= MAX_DEBUG_VIEWS) return
            view.getLocationOnScreen(childLocation)
            bounds.set(
                    (childLocation[0] - ownLocation[0]).toFloat(),
                    (childLocation[1] - ownLocation[1]).toFloat(),
                    (childLocation[0] - ownLocation[0] + view.width).toFloat(),
                    (childLocation[1] - ownLocation[1] + view.height).toFloat())
            val base = when {
                view.isClickable -> 0xFF66E4FF.toInt()
                view is ViewGroup -> 0xFFFF70C7.toInt()
                else -> 0xFFFFD866.toInt()
            }
            outlinePaint.color = ColorUtils.setAlphaComponent(base, (0xE8 - depth * 12).coerceAtLeast(0x70))
            canvas.drawRect(bounds, outlinePaint)

            if ((view.isClickable || view.contentDescription != null) && bounds.width() >= 28f * density) {
                labelPaint.color = base
                val label = view.contentDescription?.toString()?.take(18)
                        ?: view.javaClass.simpleName.take(18)
                canvas.drawText(label, bounds.left + 2f * density,
                        bounds.top + 9f * density, labelPaint)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) drawView(view.getChildAt(index), depth + 1)
            }
        }

        for (index in 0 until root.childCount) drawView(root.getChildAt(index), 0)
    }

    private fun drawInfo(canvas: Canvas) {
        val lines = playerInfo.lineSequence().filter(String::isNotBlank).take(8).toList()
        if (lines.isEmpty()) return
        val pad = 6f * density
        val lineHeight = infoPaint.fontSpacing
        val widest = lines.maxOf { infoPaint.measureText(it) }
        val left = 7f * density
        val top = 18f * density
        val panel = RectF(
                left,
                top,
                (left + widest + pad * 2f).coerceAtMost(width - 7f * density),
                top + lineHeight * lines.size + pad * 2f)
        canvas.drawRoundRect(panel, 7f * density, 7f * density, infoBackgroundPaint)
        lines.forEachIndexed { index, line ->
            canvas.drawText(
                    line,
                    panel.left + pad,
                    panel.top + pad - infoPaint.fontMetrics.ascent + index * lineHeight,
                    infoPaint)
        }
    }

    private companion object {
        const val MAX_DEBUG_VIEWS = 160
    }
}
