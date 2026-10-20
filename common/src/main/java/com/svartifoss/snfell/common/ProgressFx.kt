package com.svartifoss.snfell.common

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drawing for the progress-ring styles that carry information about the track: [minutes] and
 * [hexCells]. Shared by the watch's `CircularProgressSeekBar` and the phone's miniature for the same
 * reason [VolumeFx] is - both draw with plain `android.graphics` - and decided by [ProgressShape].
 *
 * Like [VolumeFx], each function takes the paint and path it should use and leaves the paint clean.
 */
object ProgressFx {

    /**
     * The ring cut into one piece per minute of the track, filling piece by piece.
     *
     * The ring tells you the length of what is playing: three pieces is a three-minute song, sixty is
     * an hour. With no known length ([durationMs] of zero or less) it falls back to a plain ring, so a
     * live stream is not given a length it does not have.
     *
     * [bounds] is the arc's own rectangle (the stroke is centred on it), [startDegrees] and
     * [sweepDegrees] follow the Canvas convention, and a negative sweep runs the other way.
     */
    fun minutes(
            canvas: Canvas,
            bounds: RectF,
            startDegrees: Float,
            sweepDegrees: Float,
            progress: Float,
            durationMs: Long,
            strokePx: Float,
            playedColor: Int,
            restColor: Int,
            paint: Paint
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokePx
        paint.pathEffect = null
        paint.shader = null
        paint.strokeCap = Paint.Cap.BUTT

        val arcs = ProgressShape.minuteArcs(
                durationMs, sweepDegrees, ProgressShape.isClosed(sweepDegrees))
        if (arcs.isEmpty()) {
            paint.color = restColor
            canvas.drawArc(bounds, startDegrees, sweepDegrees, false, paint)
            if (progress > 0f) {
                paint.color = playedColor
                canvas.drawArc(bounds, startDegrees, sweepDegrees * progress, false, paint)
            }
            return
        }

        val direction = if (sweepDegrees >= 0f) 1f else -1f
        for (arc in arcs) {
            if (arc.lengthDegrees <= 0f) continue
            val from = startDegrees + direction * arc.offsetDegrees
            paint.color = restColor
            canvas.drawArc(bounds, from, direction * arc.lengthDegrees, false, paint)
            val played = arc.played(progress)
            if (played > 0f) {
                paint.color = playedColor
                canvas.drawArc(bounds, from, direction * arc.lengthDegrees * played, false, paint)
            }
        }
    }

    /**
     * A ring of small hexagons, the played ones filled and the rest left as outlines.
     *
     * The hexagon is the basalt column seen end-on - the cliff at Svartifoss - and the cells are
     * turned so a vertex always points along the ring, which keeps a curved row of them tight instead
     * of ragged. [radius] is the radius of the ring's centreline and [cellRadius] the circumradius of
     * one cell.
     */
    fun hexCells(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            startDegrees: Float,
            sweepDegrees: Float,
            progress: Float,
            cellRadius: Float,
            playedColor: Int,
            restColor: Int,
            paint: Paint,
            path: Path
    ) {
        paint.pathEffect = null
        paint.shader = null
        paint.strokeJoin = Paint.Join.ROUND
        val closed = ProgressShape.isClosed(sweepDegrees)
        val count = ProgressShape.hexCellCount(sweepDegrees)
        val vertex = FloatArray(2)
        for (index in 0 until count) {
            val fraction = ProgressShape.hexCellFraction(index, count, closed)
            val angle = Math.toRadians((startDegrees + fraction * sweepDegrees).toDouble())
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            val rotation = (startDegrees + fraction * sweepDegrees) + 90f

            path.reset()
            for (corner in 0 until 6) {
                ProgressShape.hexVertex(x, y, cellRadius, rotation, corner, vertex)
                if (corner == 0) path.moveTo(vertex[0], vertex[1]) else path.lineTo(vertex[0], vertex[1])
            }
            path.close()

            if (ProgressShape.hexCellPlayed(index, count, closed, progress)) {
                paint.style = Paint.Style.FILL
                paint.color = playedColor
                canvas.drawPath(path, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (cellRadius * 0.22f).coerceAtLeast(1f)
                // An outline needs more presence than a faint track fill would give it.
                paint.color = ColorUtils.setAlphaComponent(
                        restColor, android.graphics.Color.alpha(restColor).coerceAtLeast(0x50))
                canvas.drawPath(path, paint)
            }
        }
        paint.style = Paint.Style.STROKE
    }
}
