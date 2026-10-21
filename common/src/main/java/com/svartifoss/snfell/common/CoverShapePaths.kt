package com.svartifoss.snfell.common

import android.graphics.Path
import android.graphics.RectF

/**
 * A cover's silhouette as an `android.graphics.Path`, for the places that draw with a Canvas (the
 * phone's miniature of the watch).
 *
 * One function per cut so a call site never chooses between `addRoundRect` and the superellipse
 * itself - that choice is the thing that used to be wrong: every consumer called `addRoundRect`
 * with the registry's radii, which is why the one shape that is not a rounded rectangle came out as
 * one. The watch's Compose faces reach the same outline through [Superellipse.outline].
 */
object CoverShapePaths {

    fun path(shape: CoverShape, rect: RectF): Path =
            if (shape.isSuperellipse) superellipse(rect)
            else rounded(rect, shape.radii(rect.width()))

    fun path(corners: CoverCorners, rect: RectF): Path =
            if (corners.superellipse) superellipse(rect)
            else rounded(rect, corners.radii(rect.width()))

    private fun rounded(rect: RectF, radii: FloatArray) = Path().apply {
        addRoundRect(rect, radii, Path.Direction.CW)
    }

    private fun superellipse(rect: RectF) = Path().apply {
        val points = Superellipse.outline(rect.left, rect.top, rect.right, rect.bottom)
        moveTo(points[0], points[1])
        for (index in 1 until Superellipse.POINT_COUNT) {
            lineTo(points[index * 2], points[index * 2 + 1])
        }
        close()
    }
}
