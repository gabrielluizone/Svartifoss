package com.svartifoss.snfell.watch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.svartifoss.snfell.common.PlayerChromeLayout

/**
 * The player's clock, curved along the top bezel.
 *
 * It exists because the clock and the top quadrant hint both want the apex of a round screen. The
 * answers tried before were to delete one of them (the hint was hidden outright whenever *Always
 * show time* was on) and then to shrink them (a 16dp hint shoved sideways, a clock scaled to 24% of
 * the screen width) - both of which read as a mistake rather than as a design. Curving the clock
 * costs no vertical band at all: it moves into the corner space a round display already wastes, and
 * the hint keeps the apex just inside it. Both stay centred on 12 o'clock, so the pair is symmetric.
 *
 * This is a **host View drawn above every face**, like the four hints and for the same reason: a
 * clock on the bezel is chrome, not part of a composition. One implementation therefore serves all
 * fifteen faces, where the straight clock has two (`FaceClock` in Compose, `ambient_clock` for the
 * View faces) and would have needed a third here. While this is showing, the Compose faces are told
 * not to draw their own (`NowPlayingFaceState.showClock`) and `ambient_clock` stays hidden.
 *
 * Deliberately *not* the existing `CurvedClock` composable: that one is the list screens' chrome,
 * fixed at 16sp white-75% in the UI font, while the player's clock carries the user's own font,
 * size, tracking and resolved colour mode. Only the geometry is shared, and that lives in
 * [PlayerChromeLayout] so the phone's miniature draws the identical arc.
 *
 * Ambient is not its business: AOD keeps the straight `ambient_clock`, which is the burn-in-audited
 * one, and the host hides this view on the way in.
 */
class CurvedClockView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        // LEFT, not CENTER: the run is centred by starting the arc half its sweep before the apex
        // (see PlayerChromeLayout.curvedClockStartAngleDegrees), which is exact, where centring
        // along a path depends on how the platform measures the path's own midpoint.
        textAlign = Paint.Align.LEFT
    }
    private val path = Path()
    private val oval = RectF()

    var text: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /**
     * Everything the straight clock already resolved, applied to the arc unchanged.
     *
     * Taking the resolved values rather than the preferences is what keeps the two clocks from
     * drifting: `applyClockAppearance` works the font key, the Flex axes, the typography scale and
     * the colour mode out once and hands the answer to both.
     */
    fun setClockPaint(
            typeface: Typeface?,
            textSizePx: Float,
            color: Int,
            letterSpacingEm: Float
    ) {
        paint.typeface = typeface
        paint.textSize = textSizePx
        paint.color = color
        paint.letterSpacing = letterSpacingEm
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (text.isEmpty() || width <= 0 || height <= 0 || paint.textSize <= 0f) {
            return
        }
        val density = resources.displayMetrics.density
        // The inscribed circle, so a display that is not exactly square still gets a real arc.
        val diameterPx = minOf(width, height).toFloat()
        val screenDp = diameterPx / density

        // The glyphs sit outside the baseline - the outward normal of a top arc read left to right
        // points away from the centre - so the ascent is what decides how deep the baseline runs.
        val ascentDp = -paint.fontMetrics.ascent / density
        val radiusDp = PlayerChromeLayout.curvedClockBaselineRadiusDp(screenDp, ascentDp)
        val sweep = PlayerChromeLayout.curvedClockSweepDegrees(
                paint.measureText(text) / density, radiusDp)
        if (sweep <= 0f) return

        val radiusPx = radiusDp * density
        val cx = width / 2f
        val cy = height / 2f
        oval.set(cx - radiusPx, cy - radiusPx, cx + radiusPx, cy + radiusPx)
        path.rewind()
        path.addArc(oval, PlayerChromeLayout.curvedClockStartAngleDegrees(sweep), sweep)
        canvas.drawTextOnPath(text, path, 0f, 0f, paint)
    }
}
