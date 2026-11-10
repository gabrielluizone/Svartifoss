package com.svartifoss.snfell.watch.view.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.svartifoss.snfell.common.MarqueeMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Scrolls a single line that does not fit, and dissolves its ends only while it is moving.
 *
 * This replaces `basicMarquee` plus a fixed fade mask. The mask could not know when the line was
 * standing still - `basicMarquee` keeps its offset private - so it stayed on through every rest, and a
 * title waiting at its start had its first letters faded out on both sides of the screen. Owning the
 * motion is what lets the fade follow it: [MarqueeMotion] says where the line is and how strongly its
 * ends dissolve, and the same object drives the classic View faces and the phone's miniature, so all
 * three renderers move and fade the same way.
 *
 * Two things it does differently from `basicMarquee`, both on purpose. The scroll is a **draw-time
 * translation**, never a placement, so a moving line redraws itself and nothing else - the queue
 * stuttered while its playing row re-laid the list out every frame. And a line that rests is not
 * redrawn at all: the coroutine sleeps through each rest instead of ticking frames that change nothing.
 *
 * The text keeps its own alignment while it fits: the child is measured with an unbounded maximum
 * width but the incoming *minimum*, so a `fillMaxWidth` caller still centres a short title. A line
 * that overflows rests on its start edge (the right one in a right-to-left layout, as `basicMarquee`
 * did) and scrolls towards the other.
 *
 * Vertically the clip is open, so a shadow or an outline that reaches past the line box is not cut off
 * while the line moves; only the sides are clipped.
 */
internal fun Modifier.trackMarquee(): Modifier = this then TrackMarqueeElement

private object TrackMarqueeElement : ModifierNodeElement<TrackMarqueeNode>() {
    override fun create(): TrackMarqueeNode = TrackMarqueeNode()
    override fun update(node: TrackMarqueeNode) = Unit
    override fun hashCode(): Int = javaClass.hashCode()
    override fun equals(other: Any?): Boolean = other === this
    override fun InspectorInfo.inspectableProperties() {
        name = "trackMarquee"
    }
}

private class TrackMarqueeNode : Modifier.Node(), LayoutModifierNode, DrawModifierNode {
    private var contentWidth by mutableIntStateOf(0)
    private var viewportWidth by mutableIntStateOf(0)
    private var offset by mutableFloatStateOf(0f)
    private var fade by mutableFloatStateOf(0f)
    private var motion: Job? = null
    private val layerPaint = Paint()

    override fun onAttach() {
        motion?.cancel()
        motion = coroutineScope.launch { run() }
    }

    override fun onDetach() {
        motion?.cancel()
        motion = null
        offset = 0f
        fade = 0f
    }

    /**
     * One loop per pair of widths. A new title, or a new room for it, starts the loop over from rest -
     * which is what a reader expects of a title that just changed.
     */
    private suspend fun run() {
        snapshotFlow { contentWidth to viewportWidth }.collectLatest { (content, viewport) ->
            offset = 0f
            fade = 0f
            if (!MarqueeMotion.overflows(content.toFloat(), viewport.toFloat())) return@collectLatest
            val density = requireDensity()
            val pxPerSecond = with(density) { MarqueeMotion.VELOCITY_DP_PER_SECOND.dp.toPx() }
            val fadePx = MarqueeMotion.fadeWidthPx(
                    with(density) { MarqueeMotion.FADE_WIDTH_DP.dp.toPx() }, viewport.toFloat())
            val loop = MarqueeMotion.loopPx(content.toFloat(), viewport.toFloat())
            var start = -1L
            while (true) {
                val now = withFrameMillis { it }
                if (start < 0L) start = now
                val elapsed = now - start
                val rest = MarqueeMotion.millisUntilMoving(elapsed, loop, pxPerSecond)
                if (rest > 0L) {
                    offset = 0f
                    fade = 0f
                    delay(rest)
                    continue
                }
                val at = MarqueeMotion.offsetAt(elapsed, loop, pxPerSecond)
                offset = at
                fade = MarqueeMotion.fadeStrength(at, loop, fadePx)
            }
        }
    }

    override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity))
        val width = constraints.constrainWidth(placeable.width)
        viewportWidth = width
        contentWidth = placeable.width
        return layout(width, placeable.height) { placeable.place(0, 0) }
    }

    // A scrolling line can always be squeezed, so it asks for nothing as its minimum - otherwise a
    // parent measuring intrinsics would reserve the whole title's width for it.
    override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurable: IntrinsicMeasurable,
            height: Int
    ): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurable: IntrinsicMeasurable,
            height: Int
    ): Int = measurable.maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurable: IntrinsicMeasurable,
            width: Int
    ): Int = measurable.minIntrinsicHeight(Constraints.Infinity)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurable: IntrinsicMeasurable,
            width: Int
    ): Int = measurable.maxIntrinsicHeight(Constraints.Infinity)

    override fun ContentDrawScope.draw() {
        val content = contentWidth.toFloat()
        // The room comes from measure, never from `size`. Compose draws a node that is both a
        // layout and a draw node with the coordinator *below* it (see NodeKind's
        // includeSelfInTraversal), so `size` here is the text's own size - the whole title. Read
        // as the room, every line "fit": nothing ever scrolled or was clipped, and a long title
        // ran straight off the round screen. basicMarquee keeps its container width for the same
        // reason.
        val viewport = viewportWidth.toFloat()
        if (!MarqueeMotion.overflows(content, viewport)) {
            drawContent()
            return
        }
        val loop = MarqueeMotion.loopPx(content, viewport)
        val at = offset
        val strength = fade
        // The first copy rests on the start edge; the second follows one loop behind it.
        val rtl = layoutDirection == LayoutDirection.Rtl
        val direction = if (rtl) 1f else -1f
        val firstX = (if (rtl) viewport - content else 0f) + direction * at
        val secondX = firstX - direction * loop
        val slack = size.height
        val layered = strength > 0f
        if (layered) {
            drawContext.canvas.saveLayer(
                    Rect(0f, -slack, viewport, size.height + slack), layerPaint)
        }
        clipRect(left = 0f, top = -slack, right = viewport, bottom = size.height + slack) {
            if (firstX < viewport && firstX + content > 0f) {
                translate(left = firstX) { this@draw.drawContent() }
            }
            if (at > 0f && secondX < viewport && secondX + content > 0f) {
                translate(left = secondX) { this@draw.drawContent() }
            }
        }
        if (layered) {
            val edge = MarqueeMotion.fadeWidthPx(
                    MarqueeMotion.FADE_WIDTH_DP.dp.toPx(), viewport) / viewport
            val outer = Color.Black.copy(alpha = 1f - strength)
            drawRect(
                    // Bounded explicitly: a brush is sized by the draw scope, not by the rect it
                    // fills, and the scope here is the whole title - left open, the right-hand
                    // dissolve landed past the end of the room and that end never faded.
                    brush = Brush.horizontalGradient(
                            0f to outer,
                            edge to Color.Black,
                            1f - edge to Color.Black,
                            1f to outer,
                            startX = 0f,
                            endX = viewport),
                    topLeft = Offset(0f, -slack),
                    size = Size(viewport, size.height + 2f * slack),
                    blendMode = BlendMode.DstIn)
            drawContext.canvas.restore()
        }
    }
}
