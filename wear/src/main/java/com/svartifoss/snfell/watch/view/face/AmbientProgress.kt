package com.svartifoss.snfell.watch.view.face

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.svartifoss.snfell.watch.view.RingStyle
import kotlin.math.cos
import kotlin.math.sin

/** Low-emission stroke tokens derived from the same RingStyle used by the interactive bezel. */
internal data class AmbientRingStrokes(
        val track: Stroke,
        val played: Stroke,
        val cometHead: Boolean
)

internal fun ambientRingStrokes(styleKey: String, baseWidth: Float): AmbientRingStrokes {
    val style = RingStyle.fromPref(styleKey)
    val pathEffect = when (style) {
        RingStyle.DASHED -> PathEffect.dashPathEffect(
                floatArrayOf(baseWidth * 1.9f, baseWidth * 1.5f))
        RingStyle.DOTS -> PathEffect.dashPathEffect(
                floatArrayOf(0.01f, baseWidth * 2.6f))
        else -> null
    }
    val cap = when (style) {
        RingStyle.DASHED -> StrokeCap.Butt
        RingStyle.DOTS -> StrokeCap.Round
        else -> StrokeCap.Round
    }
    val trackWidth = when (style) {
        RingStyle.HAIRLINE -> baseWidth * 0.30f
        RingStyle.COMET -> baseWidth * 0.50f
        else -> baseWidth
    }
    val playedWidth = if (style == RingStyle.HAIRLINE) baseWidth * 0.45f else baseWidth
    return AmbientRingStrokes(
            track = Stroke(width = trackWidth, cap = cap, pathEffect = pathEffect),
            played = Stroke(width = playedWidth, cap = cap, pathEffect = pathEffect),
            cometHead = style == RingStyle.COMET
    )
}

/** Draws a circular/elliptical AOD ring using the universal progress style. */
internal fun DrawScope.drawAmbientCircularProgress(
        progress: Float,
        styleKey: String,
        trackColor: Color,
        playedColor: Color,
        baseWidth: Float,
        startAngle: Float,
        totalSweep: Float,
        topLeft: Offset,
        arcSize: Size
) {
    val strokes = ambientRingStrokes(styleKey, baseWidth)
    drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = totalSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = strokes.track
    )
    val playedSweep = totalSweep * progress.coerceIn(0f, 1f)
    if (playedSweep <= 0f) return
    drawArc(
            color = playedColor,
            startAngle = startAngle,
            sweepAngle = playedSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = strokes.played
    )
    if (strokes.cometHead) {
        val angle = Math.toRadians((startAngle + playedSweep).toDouble())
        val centerX = topLeft.x + arcSize.width / 2f
        val centerY = topLeft.y + arcSize.height / 2f
        drawCircle(
                color = playedColor,
                radius = baseWidth * 0.9f,
                center = Offset(
                        centerX + arcSize.width / 2f * cos(angle).toFloat(),
                        centerY + arcSize.height / 2f * sin(angle).toFloat()
                )
        )
    }
}
