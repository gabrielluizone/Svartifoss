package com.svartifoss.snfell.watch.view.face

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight

/** Measure the complete block before fitting it. In particular, an unweighted artist/source
 * row must not take all of a constrained Column's height away from its weighted title. */
@Composable
internal fun FittedFaceContent(modifier: Modifier = Modifier, unboundedWidth: Boolean = false,
        content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val children = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity,
                    maxWidth = if (unboundedWidth) Constraints.Infinity else constraints.maxWidth))
        }
        val naturalWidth = children.maxOfOrNull { it.width } ?: 0
        val naturalHeight = children.maxOfOrNull { it.height } ?: 0
        val width = constraints.constrainWidth(naturalWidth)
        val height = constraints.constrainHeight(naturalHeight)
        val scale = minOf(1f, width.toFloat() / naturalWidth.coerceAtLeast(1),
                height.toFloat() / naturalHeight.coerceAtLeast(1))
        layout(width, height) {
            children.forEach { child ->
                child.placeWithLayer(((width - child.width * scale) / 2f).toInt(), 0) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }
}
