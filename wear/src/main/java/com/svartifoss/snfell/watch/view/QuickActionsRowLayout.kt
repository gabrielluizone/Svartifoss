package com.svartifoss.snfell.watch.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The row holding the quick panel's round action slots. The catalog deliberately keeps geometry
 * here (rather than baking positions into MainActivity) so every arrangement measures, draws and
 * exposes touch targets in the same coordinate space.
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
        GRID,
        /** Cards splay around the centre like a hand of cards. */
        FAN,
        /** Three points around the upper half of an orbit. */
        ORBIT,
        /** A low, compact row resembling a bottom app dock. */
        DOCK,
        /** One action per line. */
        COLUMN,
        /** One leading action opposite a two-action stack. */
        SPLIT,
        /** One action above two, forming a diamond/triangle. */
        DIAMOND,
        /** Horizontal deck with an enlarged centre card. */
        CAROUSEL
    }

    var arrangement: Arrangement = Arrangement.ROW
        set(value) {
            if (field != value) {
                field = value
                resetTransforms()
                requestLayout()
            }
        }

    private val density = resources.displayMetrics.density

    /** How far the centre of the arc dips below its ends. */
    private val arcDepthPx = 16f * density

    /** Space between grid cells, both axes. */
    private val gridGapPx = (8f * density).roundToInt()

    private val compactGapPx = (6f * density).roundToInt()

    private inline fun forEachVisibleChild(action: (View) -> Unit) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility != View.GONE) action(child)
        }
    }

    private fun visibleChildren(): List<View> =
            (0 until childCount).map(::getChildAt).filter { it.visibility != View.GONE }

    private fun resetTransforms() = forEachVisibleChild {
        it.translationX = 0f
        it.translationY = 0f
        it.rotation = 0f
        it.scaleX = 1f
        it.scaleY = 1f
        it.alpha = 1f
        it.translationZ = 0f
    }

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
            Arrangement.FAN, Arrangement.ORBIT -> setMeasuredDimension(
                    MeasureSpec.getSize(widthMeasureSpec),
                    measuredHeight + (22f * density).roundToInt())
            Arrangement.DOCK -> setMeasuredDimension(
                    measuredWidth, measuredHeight + (8f * density).roundToInt())
            Arrangement.COLUMN -> {
                val children = visibleChildren()
                if (children.isEmpty()) return
                setMeasuredDimension(
                        MeasureSpec.getSize(widthMeasureSpec),
                        children.sumOf { it.measuredHeight } +
                                compactGapPx * (children.size - 1))
            }
            Arrangement.SPLIT -> {
                val children = visibleChildren()
                if (children.isEmpty()) return
                val cellHeight = children.maxOf { it.measuredHeight }
                setMeasuredDimension(
                        MeasureSpec.getSize(widthMeasureSpec),
                        if (children.size <= 1) cellHeight else cellHeight * 2 + compactGapPx)
            }
            Arrangement.DIAMOND -> {
                val children = visibleChildren()
                if (children.isEmpty()) return
                val cellHeight = children.maxOf { it.measuredHeight }
                setMeasuredDimension(
                        MeasureSpec.getSize(widthMeasureSpec),
                        if (children.size <= 1) cellHeight else cellHeight * 2 + compactGapPx)
            }
            Arrangement.CAROUSEL -> setMeasuredDimension(
                    measuredWidth, measuredHeight + (12f * density).roundToInt())
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Visibility can change without changing the arrangement (for example when a media
        // session exposes a different action count). Clear the previous deck/orbit transforms on
        // every pass so a formerly-centre Carousel card never keeps its scale or alpha as a side
        // card in the next layout.
        resetTransforms()
        when (arrangement) {
            Arrangement.ROW -> super.onLayout(changed, l, t, r, b)
            Arrangement.ARC -> {
                super.onLayout(changed, l, t, r, b)
                applyArcOffsets()
            }
            Arrangement.GRID -> layoutAsGrid(r - l)
            Arrangement.FAN -> {
                super.onLayout(changed, l, t, r, b)
                applyFanOffsets()
            }
            Arrangement.ORBIT -> layoutAsOrbit(r - l)
            Arrangement.DOCK -> {
                super.onLayout(changed, l, t, r, b)
                visibleChildren().forEach { it.translationY = 7f * density }
            }
            Arrangement.COLUMN -> layoutAsColumn(r - l)
            Arrangement.SPLIT -> layoutAsSplit(r - l)
            Arrangement.DIAMOND -> layoutAsDiamond(r - l)
            Arrangement.CAROUSEL -> {
                super.onLayout(changed, l, t, r, b)
                applyCarouselEmphasis()
            }
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

    private fun applyFanOffsets() {
        val children = visibleChildren()
        val center = (children.size - 1) / 2f
        children.forEachIndexed { index, child ->
            val distance = index - center
            child.rotation = distance * 10f
            child.translationY = kotlin.math.abs(distance) * 8f * density
        }
    }

    private fun layoutAsOrbit(width: Int) {
        val children = visibleChildren()
        if (children.isEmpty()) return
        val cellWidth = children.maxOf { it.measuredWidth }
        val cellHeight = children.maxOf { it.measuredHeight }
        val usable = (width - cellWidth).coerceAtLeast(0)
        children.forEachIndexed { index, child ->
            val fraction = if (children.size == 1) .5f else index / (children.size - 1f)
            val x = (usable * fraction).roundToInt()
            val distanceFromCenter = kotlin.math.abs(fraction - .5f) * 2f
            val y = (distanceFromCenter * 20f * density).roundToInt()
            child.layout(x, y, x + child.measuredWidth, y + cellHeight)
            child.rotation = (fraction - .5f) * 20f
        }
    }

    private fun layoutAsColumn(width: Int) {
        var y = 0
        visibleChildren().forEach { child ->
            val x = (width - child.measuredWidth) / 2
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            y += child.measuredHeight + compactGapPx
        }
    }

    private fun layoutAsSplit(width: Int) {
        val children = visibleChildren()
        if (children.isEmpty()) return
        val cellHeight = children.maxOf { it.measuredHeight }
        val left = children.first()
        val leftX = compactGapPx
        val leftY = ((measuredHeight - left.measuredHeight) / 2f).roundToInt()
        left.layout(leftX, leftY, leftX + left.measuredWidth, leftY + left.measuredHeight)
        children.drop(1).forEachIndexed { index, child ->
            val x = width - child.measuredWidth - compactGapPx
            val y = index * (cellHeight + compactGapPx)
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    private fun layoutAsDiamond(width: Int) {
        val children = visibleChildren()
        if (children.isEmpty()) return
        val first = children.first()
        val firstX = (width - first.measuredWidth) / 2
        first.layout(firstX, 0, firstX + first.measuredWidth, first.measuredHeight)
        val lower = children.drop(1)
        if (lower.isEmpty()) return
        val y = first.measuredHeight + compactGapPx
        val rowWidth = lower.sumOf { it.measuredWidth } + compactGapPx * (lower.size - 1)
        var x = (width - rowWidth) / 2
        lower.forEach { child ->
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + compactGapPx
        }
    }

    private fun applyCarouselEmphasis() {
        val children = visibleChildren()
        if (children.isEmpty()) return
        val center = children.size / 2
        children.forEachIndexed { index, child ->
            if (index == center) {
                child.scaleX = 1.14f
                child.scaleY = 1.14f
                child.translationY = -2f * density
                child.translationZ = 2f * density
            } else {
                child.scaleX = .88f
                child.scaleY = .88f
                child.alpha = .82f
            }
        }
    }

    private companion object {
        const val COLUMNS = 2
    }
}
