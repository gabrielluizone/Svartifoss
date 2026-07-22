package com.svartifoss.snfell.watch.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The row holding the quick panel's round action slots, able to arrange them as a straight row, an
 * arc, or a grid.
 *
 * It subclasses [LinearLayout] rather than extending [android.view.ViewGroup] directly so the
 * existing slot sizing keeps working untouched: `MainActivity.sizeRoundQuickButton` assigns
 * `LinearLayout.LayoutParams` to each slot, and [Arrangement.ROW] is literally unmodified
 * LinearLayout behaviour - the original look is not a reimplementation of itself.
 *
 * Both non-row arrangements only need the *positions* to change, so children are still measured by
 * `super`; only [onMeasure]'s reported height and [onLayout]'s placement differ.
 */
class QuickActionsRowLayout @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Arrangement {
        /** Straight horizontal row - the original composition. */
        ROW,
        /** Row bowed downwards so it follows the round screen's lower bezel. */
        ARC,
        /** Two-column grid; the last row is centred when the slot count is odd. */
        GRID
    }

    var arrangement: Arrangement = Arrangement.ROW
        set(value) {
            if (field != value) {
                field = value
                // A stale translation from ARC would otherwise persist into ROW/GRID, where
                // nothing assigns translationY at all.
                forEachVisibleChild { it.translationY = 0f }
                requestLayout()
            }
        }

    private val density = resources.displayMetrics.density

    /** How far the centre of the arc dips below its ends. */
    private val arcDepthPx = 16f * density

    /** Space between grid cells, both axes. */
    private val gridGapPx = (8f * density).roundToInt()

    private inline fun forEachVisibleChild(action: (View) -> Unit) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility != View.GONE) action(child)
        }
    }

    private fun visibleChildren(): List<View> =
            (0 until childCount).map(::getChildAt).filter { it.visibility != View.GONE }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        when (arrangement) {
            Arrangement.ROW -> Unit
            // The dip is applied as a translation, which does not grow the view. Reserving the
            // depth here is what stops the dipped centre slot from colliding with the Up Next row
            // that sits directly below in the panel's vertical stack.
            Arrangement.ARC -> setMeasuredDimension(
                    measuredWidth, measuredHeight + arcDepthPx.roundToInt())
            Arrangement.GRID -> {
                val children = visibleChildren()
                if (children.isEmpty()) return
                val rows = ceil(children.size / COLUMNS.toFloat()).toInt()
                val cellHeight = children.maxOf { it.measuredHeight }
                setMeasuredDimension(
                        MeasureSpec.getSize(widthMeasureSpec),
                        rows * cellHeight + (rows - 1) * gridGapPx)
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        when (arrangement) {
            Arrangement.ROW -> super.onLayout(changed, l, t, r, b)
            Arrangement.ARC -> {
                super.onLayout(changed, l, t, r, b)
                applyArcOffsets()
            }
            Arrangement.GRID -> layoutAsGrid(r - l)
        }
    }

    /**
     * Bows the row downwards: the centre slot sits [arcDepthPx] lower than the outermost ones, with
     * a parabolic falloff between. Applied as a translation so hit-testing follows automatically -
     * `ViewGroup` transforms touch coordinates by a child's translation, so the enlarged targets
     * stay exactly where they are drawn.
     */
    private fun applyArcOffsets() {
        val children = visibleChildren()
        if (children.size < 2) {
            children.forEach { it.translationY = 0f }
            return
        }
        val rowCenterX = (children.first().left + children.last().right) / 2f
        val halfSpan = (children.last().right - children.first().left) / 2f
        if (halfSpan <= 0f) return

        children.forEach { child ->
            // -1 at the left end, 0 at the centre, +1 at the right end.
            val t = ((child.left + child.right) / 2f - rowCenterX) / halfSpan
            child.translationY = arcDepthPx * (1f - t * t)
        }
    }

    /** Centred two-column grid; an odd final row is centred rather than left-aligned. */
    private fun layoutAsGrid(width: Int) {
        val children = visibleChildren()
        if (children.isEmpty()) return

        val cellHeight = children.maxOf { it.measuredHeight }
        children.chunked(COLUMNS).forEachIndexed { rowIndex, rowChildren ->
            val rowWidth = rowChildren.sumOf { it.measuredWidth } +
                    (rowChildren.size - 1) * gridGapPx
            var x = (width - rowWidth) / 2
            val y = rowIndex * (cellHeight + gridGapPx)
            rowChildren.forEach { child ->
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth + gridGapPx
            }
        }
    }

    private companion object {
        const val COLUMNS = 2
    }
}
