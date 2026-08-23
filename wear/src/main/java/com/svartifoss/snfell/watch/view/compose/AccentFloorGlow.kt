package com.svartifoss.snfell.watch.view.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.svartifoss.snfell.common.AccentFloorStyle

/**
 * A wash of [accentColor] pooled along the bottom of the screen, **concentric with the display**.
 *
 * Shared by the lyrics screen and the Verse face, which are the same idea on two surfaces and must
 * not drift into two slightly different glows.
 *
 * A flat vertical gradient is the obvious way to draw this and the wrong one on a round watch: its
 * edge cuts a chord across the circle and reads as a rectangle peeking out from behind the bezel.
 * Making the falloff a function of distance from the centre puts the band's edge on a circle
 * concentric with the case, so it follows it instead.
 *
 * Two passes into one layer: a ring whose falloff runs along the radius, then a vertical mask that
 * keeps only its lower part. The `saveLayer` is required, not tidiness - `DstIn` composites against
 * whatever is already in the buffer, so run straight onto the screen it would erase the backdrop
 * behind this as well.
 *
 * The band is kept well clear of the vertical midpoint deliberately: it is a floor, not a backdrop,
 * and both surfaces read text across the middle of the screen.
 *
 * The shape values come from [AccentFloorStyle], so "how much of it" is a user choice on every
 * face rather than a constant belonging to whichever face it was first drawn on.
 *
 * @param enabled false in ambient, where this is exactly the kind of large lit area an always-on
 *   panel must not carry.
 */
fun Modifier.accentFloorGlow(
        accentColor: Color,
        style: AccentFloorStyle = AccentFloorStyle.STANDARD,
        enabled: Boolean = true,
): Modifier = drawBehind {
    if (!enabled || !style.isVisible) return@drawBehind

    // minDimension, not height: on the square Wear devices that still exist this keeps the arc
    // tangent to the shorter edge instead of spilling off the sides.
    val radius = size.minDimension / 2f
    val middle = Offset(size.width / 2f, size.height / 2f)

    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())

        drawCircle(
                brush = Brush.radialGradient(
                        colorStops = arrayOf(
                                style.innerStop to Color.Transparent,
                                1f to accentColor.copy(alpha = style.maxAlpha)),
                        center = middle,
                        radius = radius),
                radius = radius,
                center = middle)

        drawRect(
                brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = size.height * style.maskStart,
                        endY = size.height * AccentFloorStyle.MASK_END),
                blendMode = BlendMode.DstIn)

        canvas.restore()
    }
}
