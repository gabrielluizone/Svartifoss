package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

/**
 * Small building blocks shared by the Beta Compose faces ([VinylFace], [PosterFace]).
 * [ExpressiveFace] predates this file and keeps private copies of its own equivalents -
 * fold them in here only if a change ever has to touch all faces at once.
 */

/** HSL-derived tonal color from the album accent, saturation clamped into a readable band. */
internal fun faceTonal(accent: Int, lightness: Float, minSat: Float = 0.25f, maxSat: Float = 0.70f): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent, hsl)
    hsl[1] = hsl[1].coerceIn(minSat, maxSat)
    hsl[2] = lightness
    return ColorUtils.HSLToColor(hsl)
}

internal fun formatFaceClockTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    return String.format(java.util.Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** A press-scaling, background-filled tap target of any [shape] - the shared skeleton for the
 *  Beta faces' buttons, pills and chips. Only this box is hit-testable; everything around it
 *  falls through to the host's gesture layers. */
@Composable
internal fun FaceTapTarget(
        width: Dp,
        height: Dp,
        shape: Shape,
        background: Color,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "faceTapScale")

    Box(
            modifier = modifier
                    .size(width = width, height = height)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(shape)
                    .background(background)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Translucent glass pill, same look as the expressive face's bottom trio. */
@Composable
internal fun FaceGlassPill(
        width: Dp,
        height: Dp,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
) = FaceTapTarget(width, height, RoundedCornerShape(50), Color.White.copy(alpha = 0.16f),
        label, onClick, modifier, content)

/** The "⋮" overflow glyph, drawn directly so no icon resource is needed. */
@Composable
internal fun FaceOverflowDots(color: Color = Color.White) {
    Canvas(Modifier.size(18.dp)) {
        val r = 1.8.dp.toPx()
        val gap = 5.5.dp.toPx()
        for (i in -1..1) {
            drawCircle(color, radius = r, center = Offset(center.x, center.y + i * gap))
        }
    }
}
