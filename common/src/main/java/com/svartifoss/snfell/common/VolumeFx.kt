package com.svartifoss.snfell.common

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.ceil

/**
 * The drawing for the volume treatments that are not a recolouring of a stroke: [wave], [ruler],
 * and the [basalt] columns.
 *
 * Written once, here, for the same reason [OverlayBackdropPatterns] is: the watch's
 * `CircularVolumeBar` and the phone's `WatchPreviewView` both draw with plain `android.graphics`
 * calls, so a shared function leaves no second copy to drift. The decisions behind the shapes live
 * in [MeterShape] and are tested there; this file only turns them into pixels.
 *
 * Every function takes the [Paint] and [Path] it should use, because the callers already own
 * scratch instances and a meter is repainted often enough that allocating per frame would show up.
 * Each one leaves the paint without a shader or path effect, so a caller's next call starts clean.
 */
object VolumeFx {

    /** Spacing between samples when a track is turned into a polyline, in pixels. */
    private const val SAMPLE_STEP_PX = 2f

    // Scratch points. A moving wave is repainted thirty times a second, so these are reused rather
    // than allocated per call; every caller draws on its UI thread.
    private val point = FloatArray(2)
    private val normal = FloatArray(2)

    /**
     * A wave whose size is the level: nearly straight when quiet, sharply bent when loud.
     *
     * [strokePx] is the weight of the wave's line and [amplitudePx] how far it may swing from the
     * track when the level is full; they are separate because the right ratio depends on the
     * composition (a bezel arc has room to swing, a slim meter does not). The unplayed track stays a
     * straight, faint line so the wave reads as something the level *does* to the line rather than
     * as the line's shape. [amplitudeFollowsLevel] turns that growth off for callers that reuse the
     * wave for something other than a level.
     * With [inwardOnly] the crests only ever move toward the inside of an arc, which keeps the
     * outer edge on the bezel where the layout reserved room for it; a line has no inside and
     * oscillates evenly about its axis instead.
     * [phase] (a fraction of a wavelength, see [MeterShape.wavePhase]) slides the crests along the
     * track; a caller that animates it keeps the line moving the way the Expressive face's ring
     * keeps turning, and one that leaves it at zero draws the still wave.
     */
    fun wave(
            canvas: Canvas,
            track: MeterTrack,
            level: Float,
            strokePx: Float,
            amplitudePx: Float,
            density: Float,
            trackColor: Int,
            fillColor: Int,
            fillShader: Shader?,
            inwardOnly: Boolean,
            paint: Paint,
            path: Path,
            amplitudeFollowsLevel: Boolean = true,
            phase: Float = 0f
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.pathEffect = null
        paint.shader = null

        paint.color = trackColor
        paint.strokeWidth = strokePx * 0.6f
        buildPath(path, track, 0f, 1f, 0f, 0f, inwardOnly)
        canvas.drawPath(path, paint)

        val clamped = level.coerceIn(0f, 1f)
        if (clamped <= 0.005f) return

        val cycles = MeterShape.waveCycles(track.length, MeterShape.WAVELENGTH_DP * density)
        // A volume wave grows with the level; a progress wave is the same size all the way, since
        // how far along a track is has nothing to do with how loud it is.
        val amplitude = amplitudePx *
                if (amplitudeFollowsLevel) MeterShape.waveAmplitude(clamped) else 1f
        paint.strokeWidth = strokePx
        if (fillShader != null) paint.shader = fillShader else paint.color = fillColor
        buildPath(path, track, 0f, clamped, amplitude, cycles, inwardOnly, phase)
        canvas.drawPath(path, paint)
        paint.shader = null

        // A dot where the wave ends names the level; the wave itself can finish on any phase.
        track.pointAt(clamped, point)
        track.normalAt(clamped, normal)
        val offset = displacement(clamped, amplitude, cycles, inwardOnly, phase)
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        canvas.drawCircle(
                point[0] + normal[0] * offset,
                point[1] + normal[1] * offset,
                strokePx * 0.78f,
                paint)
        paint.style = Paint.Style.STROKE
    }

    /**
     * A graduated scale: a hairline with a mark every 5 percent, a longer one every 25, and a
     * bright pointer at the current level.
     *
     * The hierarchy is the difference from the segmented styles. Equal blocks tell you *how much*;
     * marks of two lengths tell you *where* - the long ones are quarters, so the level can be read
     * off against them without counting. The marks stand toward ([towardX], [towardY]) so that on a
     * bezel arc they point into the screen and on a free-standing meter they point at the middle of
     * it, whichever side the track happens to be drawn on.
     */
    fun ruler(
            canvas: Canvas,
            track: MeterTrack,
            level: Float,
            tickPx: Float,
            towardX: Float,
            towardY: Float,
            trackColor: Int,
            fillColor: Int,
            pointerColor: Int,
            paint: Paint,
            path: Path
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.pathEffect = null
        paint.shader = null

        val clamped = level.coerceIn(0f, 1f)
        track.pointAt(0.5f, point)
        track.normalAt(0.5f, normal)
        val side = if ((towardX - point[0]) * normal[0] + (towardY - point[1]) * normal[1] >= 0f) {
            1f
        } else {
            -1f
        }

        // The baseline: faint all the way, bright up to the level.
        paint.strokeWidth = tickPx * 0.2f
        paint.color = trackColor
        buildPath(path, track, 0f, 1f, 0f, 0f, false)
        canvas.drawPath(path, paint)
        if (clamped > 0.005f) {
            paint.color = fillColor
            buildPath(path, track, 0f, clamped, 0f, 0f, false)
            canvas.drawPath(path, paint)
        }

        repeat(MeterShape.RULER_MARKS) { index ->
            val mark = MeterShape.rulerMark(index)
            val t = MeterShape.rulerPosition(index)
            track.pointAt(t, point)
            track.normalAt(t, normal)
            val length = tickPx * MeterShape.rulerMarkLength(mark)
            paint.strokeWidth = tickPx * if (mark == MeterShape.RulerMark.MAJOR) 0.22f else 0.15f
            paint.color = if (MeterShape.rulerMarkLit(index, clamped)) fillColor else trackColor
            canvas.drawLine(
                    point[0], point[1],
                    point[0] + normal[0] * side * length,
                    point[1] + normal[1] * side * length,
                    paint)
        }

        // The pointer is longer than anything on the scale, so it is the one thing that moves
        // and the one thing that cannot be mistaken for a graduation.
        track.pointAt(clamped, point)
        track.normalAt(clamped, normal)
        paint.strokeWidth = tickPx * 0.3f
        paint.color = pointerColor
        canvas.drawLine(
                point[0] - normal[0] * side * tickPx * 0.35f,
                point[1] - normal[1] * side * tickPx * 0.35f,
                point[0] + normal[0] * side * tickPx * 1.5f,
                point[1] + normal[1] * side * tickPx * 1.5f,
                paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(point[0], point[1], tickPx * 0.3f, paint)
        paint.style = Paint.Style.STROKE
    }

    /**
     * Seven hexagonal basalt columns of uneven height, lit from the quiet end.
     *
     * Each column is drawn the way an isometric hexagonal prism is: a lighter rhombus for the top,
     * a mid-tone left face and a darker right face. The colours come from the style the meter is
     * being drawn in ([trackColor], [fillColor]), so this layout works with every one of them
     * instead of being a style of its own.
     *
     * Every column is also outlined in [outlineColor] at [outlinePx]. A style's track colour is
     * chosen to read on its own backdrop and the tonal ones are a shade of it, so without the
     * outline the unlit columns could vanish and the meter read as two stubs instead of a cliff.
     */
    fun basalt(
            canvas: Canvas,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            level: Float,
            trackColor: Int,
            fillColor: Int,
            outlineColor: Int,
            outlinePx: Float,
            paint: Paint,
            path: Path
    ) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.pathEffect = null

        val totalWidth = right - left
        val width = MeterShape.basaltColumnWidth(totalWidth)
        val cap = width * MeterShape.BASALT_CAP
        val maxHeight = bottom - top

        for (index in MeterShape.columnRange()) {
            val x0 = MeterShape.basaltColumnLeft(left, totalWidth, index)
            val x1 = x0 + width
            val cx = (x0 + x1) / 2f
            val height = MeterShape.basaltColumnHeight(maxHeight, index)
            val apex = bottom - height

            drawPrism(canvas, path, paint, x0, x1, cx, apex, bottom, cap,
                    top = trackColor,
                    leftFace = ColorUtils.setAlphaComponent(trackColor, (alphaOf(trackColor) * 0.85f).toInt()),
                    rightFace = ColorUtils.setAlphaComponent(trackColor, (alphaOf(trackColor) * 0.6f).toInt()))

            if (outlinePx > 0f) {
                outlinePrism(canvas, path, paint, x0, x1, cx, apex, bottom, cap,
                        outlineColor, outlinePx)
            }

            val fill = MeterShape.basaltFill(index, level)
            if (fill <= 0f) continue
            canvas.save()
            // The lit part is the lower share of the column, so the level rises inside it like
            // liquid in a tube; a full column reaches the apex and takes its top face too.
            canvas.clipRect(x0 - 1f, bottom - height * fill, x1 + 1f, bottom + 1f)
            drawPrism(canvas, path, paint, x0, x1, cx, apex, bottom, cap,
                    top = ColorUtils.blendARGB(fillColor, 0xFFFFFFFF.toInt(), 0.32f),
                    leftFace = fillColor,
                    rightFace = ColorUtils.blendARGB(fillColor, 0xFF000000.toInt(), 0.34f))
            canvas.restore()
        }
    }

    private fun alphaOf(color: Int): Int = (color ushr 24) and 0xFF

    /** The column's silhouette and the lower edges of its cap, as one hairline. */
    private fun outlinePrism(
            canvas: Canvas,
            path: Path,
            paint: Paint,
            x0: Float,
            x1: Float,
            cx: Float,
            apex: Float,
            bottom: Float,
            cap: Float,
            color: Int,
            strokePx: Float
    ) {
        val inset = strokePx / 2f
        path.reset()
        path.moveTo(cx, apex + inset)
        path.lineTo(x1 - inset, apex + cap)
        path.lineTo(x1 - inset, bottom - inset)
        path.lineTo(x0 + inset, bottom - inset)
        path.lineTo(x0 + inset, apex + cap)
        path.close()
        path.moveTo(x0 + inset, apex + cap)
        path.lineTo(cx, apex + cap * 2f)
        path.lineTo(x1 - inset, apex + cap)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = strokePx
        paint.color = color
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawPrism(
            canvas: Canvas,
            path: Path,
            paint: Paint,
            x0: Float,
            x1: Float,
            cx: Float,
            apex: Float,
            bottom: Float,
            cap: Float,
            top: Int,
            leftFace: Int,
            rightFace: Int
    ) {
        // Left face, then right face, then the top over both so the shared edge is the cap's.
        paint.color = leftFace
        path.reset()
        path.moveTo(x0, apex + cap)
        path.lineTo(cx, apex + cap * 2f)
        path.lineTo(cx, bottom)
        path.lineTo(x0, bottom)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = rightFace
        path.reset()
        path.moveTo(x1, apex + cap)
        path.lineTo(cx, apex + cap * 2f)
        path.lineTo(cx, bottom)
        path.lineTo(x1, bottom)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = top
        path.reset()
        path.moveTo(cx, apex)
        path.lineTo(x1, apex + cap)
        path.lineTo(cx, apex + cap * 2f)
        path.lineTo(x0, apex + cap)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun displacement(
            t: Float,
            amplitude: Float,
            cycles: Float,
            inwardOnly: Boolean,
            phase: Float = 0f
    ): Float {
        val offset = MeterShape.waveOffset(t, cycles, inwardOnly, phase)
        // An inward-only wave spans [-1, 0] rather than [-1, 1], so it is doubled to swing as far.
        return amplitude * (if (inwardOnly) 2f else 1f) * offset
    }

    /**
     * Rebuilds [path] as a polyline following [track] from fraction [from] to [to], with the wave
     * displacement applied when [amplitude] is non-zero.
     */
    private fun buildPath(
            path: Path,
            track: MeterTrack,
            from: Float,
            to: Float,
            amplitude: Float,
            cycles: Float,
            inwardOnly: Boolean,
            phase: Float = 0f
    ) {
        path.reset()
        val span = (to - from).coerceAtLeast(0f)
        val steps = ceil(track.length * span / SAMPLE_STEP_PX).toInt().coerceIn(2, 720)
        for (step in 0..steps) {
            val t = from + span * step / steps
            track.pointAt(t, point)
            var x = point[0]
            var y = point[1]
            if (amplitude != 0f) {
                track.normalAt(t, normal)
                val offset = displacement(t, amplitude, cycles, inwardOnly, phase)
                x += normal[0] * offset
                y += normal[1] * offset
            }
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }
}
