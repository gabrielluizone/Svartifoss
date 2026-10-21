package com.svartifoss.snfell.watch.view.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.svartifoss.snfell.common.Superellipse

/**
 * The continuous-curvature app-icon silhouette as a Compose [Shape], built from the same points
 * the phone's miniature draws ([Superellipse.outline]) so the two cannot disagree about it.
 *
 * A rectangle with rounded corners cannot stand in for it - the curve bends along the whole side -
 * which is why `CoverShape.SUPERELLIPSE` is the one cut that does not go through
 * `RoundedCornerShape`. Stateless and symmetric, so one instance serves every cover.
 */
object SuperellipseShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val points = Superellipse.outline(0f, 0f, size.width, size.height)
        return Outline.Generic(Path().apply {
            moveTo(points[0], points[1])
            for (index in 1 until Superellipse.POINT_COUNT) {
                lineTo(points[index * 2], points[index * 2 + 1])
            }
            close()
        })
    }
}
