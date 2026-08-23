package com.svartifoss.snfell.common

import kotlin.math.sqrt

/**
 * Fits a row of circular controls into a round screen, by size *and* by how high it sits.
 *
 * Flat side padding is only ever correct for the number of controls its author tested with. A row
 * near the bottom of a circular display has far less width than the screen does across its middle,
 * and the loss accelerates with depth: on a 192dp watch a two-circle row 10dp above the bottom edge
 * already reaches within a millimetre of the bezel, so the third circle beside it does not fit at
 * *any* size - shrinking it to the smallest still-pressable diameter is not enough. It has to come
 * up as well, which is why this returns both numbers rather than a diameter alone.
 *
 * The test is radial, not on the row's bounding box, because the controls are circles: the point
 * that leaves the display first is the one furthest along the line from the screen's centre through
 * the control's, so what must fit is `distance-to-centre + radius`. A width-only check passes rows
 * that visibly clip.
 *
 * Pure and free of `android.*`: the watch lays this row out in Compose and the phone previews it
 * with Canvas, and a preview fitting three circles the wrist cannot is exactly the lie the shared
 * resolvers exist to prevent.
 */
object RoundRowFit {

    /** Clearance kept between the outermost control and the bezel. Small, because this is applied
     *  after the caller's own padding, not instead of it. */
    const val DEFAULT_BEZEL_MARGIN_DP = 4f

    /** Below this a control stops being reliably hittable on a wrist, which is where shrinking to
     *  fit stops being the right answer. */
    const val DEFAULT_MINIMUM_DIAMETER_DP = 32f

    private const val STEP_DP = 0.5f

    /**
     * A fitted row: the diameter each control takes and how far its centre line sits above the
     * bottom of the screen.
     *
     * [clipped] is true when even the smallest allowance still leaves the row touching the bezel.
     * The caller is told rather than silently given something unusable: a control too small to
     * press is not a better outcome than one a hair outside the glass.
     */
    data class Row(
            val diameterDp: Float,
            val bottomInsetDp: Float,
            val clipped: Boolean = false
    )

    /**
     * The largest row that fits, preferring to keep [preferredDiameterDp] and only then to keep
     * [preferredBottomInsetDp].
     *
     * That order is the design decision: raising the row costs the composition above it vertical
     * space, so the row gives up size before it gives up its place - but only down to
     * [minimumDiameterDp], and it may not rise past [maxBottomInsetDp] whatever happens. A caller
     * that can afford no movement at all passes the two insets equal.
     */
    fun fitRow(
            screenDp: Float,
            count: Int,
            preferredDiameterDp: Float,
            gapDp: Float,
            preferredBottomInsetDp: Float,
            maxBottomInsetDp: Float = preferredBottomInsetDp,
            round: Boolean = true,
            bezelMarginDp: Float = DEFAULT_BEZEL_MARGIN_DP,
            minimumDiameterDp: Float = DEFAULT_MINIMUM_DIAMETER_DP
    ): Row {
        val unfitted = Row(preferredDiameterDp, preferredBottomInsetDp)
        if (!round || count <= 1 || screenDp <= 0f) return unfitted
        val minimum = minimumDiameterDp.coerceAtMost(preferredDiameterDp)
        val ceiling = maxBottomInsetDp.coerceAtLeast(preferredBottomInsetDp)

        var diameter = preferredDiameterDp
        while (diameter >= minimum) {
            val required = requiredBottomInset(
                    screenDp, count, diameter, gapDp, bezelMarginDp)
            if (required != null && required <= ceiling) {
                return Row(diameter, maxOf(preferredBottomInsetDp, required))
            }
            diameter -= STEP_DP
        }
        // Nothing clears the bezel. Keep the row pressable and as high as it is allowed to go,
        // and say so, rather than returning a diameter no finger can use.
        return Row(minimum, ceiling, clipped = true)
    }

    /**
     * How far above the bottom edge this row's centre line has to sit for its outermost control to
     * clear the bezel, or null when no height helps - the row is wider than the screen's own
     * diameter allows at any depth.
     */
    fun requiredBottomInset(
            screenDp: Float,
            count: Int,
            diameterDp: Float,
            gapDp: Float,
            bezelMarginDp: Float = DEFAULT_BEZEL_MARGIN_DP
    ): Float? {
        if (count <= 1) return 0f
        val screenRadius = screenDp / 2f
        val controlRadius = diameterDp / 2f
        // Distance the outermost control's centre may sit from the screen's centre, and how far it
        // sits from the vertical axis - the rest of that budget is its vertical freedom.
        val reach = screenRadius - controlRadius - bezelMarginDp
        val dx = (count - 1) * (diameterDp + gapDp) / 2f
        if (reach <= dx) return null
        val dy = sqrt(reach * reach - dx * dx)
        return (screenRadius - controlRadius - dy).coerceAtLeast(0f)
    }

    /** Whether the outermost control of such a row is entirely inside the display. */
    fun fits(
            screenDp: Float,
            count: Int,
            diameterDp: Float,
            gapDp: Float,
            bottomInsetDp: Float,
            bezelMarginDp: Float = DEFAULT_BEZEL_MARGIN_DP
    ): Boolean {
        if (count <= 0 || screenDp <= 0f) return true
        val screenRadius = screenDp / 2f
        val controlRadius = diameterDp / 2f
        val dy = screenRadius - (bottomInsetDp + controlRadius)
        val dx = (count - 1) * (diameterDp + gapDp) / 2f
        return sqrt(dx * dx + dy * dy) + controlRadius <= screenRadius - bezelMarginDp
    }
}
