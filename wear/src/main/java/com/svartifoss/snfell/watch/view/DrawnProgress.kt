package com.svartifoss.snfell.watch.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import kotlin.math.roundToInt

/**
 * How finely a progress ring or bar is redrawn while it eases between position ticks.
 *
 * The position arrives twice a second and every progress element eases towards it over a little
 * longer than that - which means the animation never rests while a track plays, and each of its
 * frames used to redraw the element: sixty redraws a second, for as long as the screen was on, to
 * move a ring by a fraction of a pixel each time. A three-minute track advances a full-screen ring
 * by well under ten pixels a second.
 *
 * So the animation still runs frame by frame, but what is *drawn* is snapped to [STEPS] positions
 * around the whole: on the largest round watch that is about half a pixel of ring per step, below
 * what anyone can see move, and it turns sixty redraws a second into about fifteen. Pure, so the
 * View ring and the Compose faces share the one resolution.
 */
object DrawnProgress {
    /** Positions per full sweep - about half a pixel of ring per step on a 480px display. */
    const val STEPS = 3_000

    /** Duration of the ease between two position ticks - see the class doc. */
    const val EASE_MS = 600

    /** [progress] snapped to the nearest of [STEPS] positions. */
    fun quantize(progress: Float): Float =
            (progress.coerceIn(0f, 1f) * STEPS).roundToInt() / STEPS.toFloat()

    /** Whether moving from [drawn] to [next] is large enough to be worth a redraw. */
    fun visiblyMoved(drawn: Float, next: Float): Boolean = quantize(drawn) != quantize(next)
}

/**
 * [target] eased over [DrawnProgress.EASE_MS], as the value a progress element should draw.
 *
 * Read it inside a draw block: the derived state only changes - and so only invalidates that
 * draw - when the eased value crosses a [DrawnProgress] step, instead of on every animation frame.
 */
@Composable
internal fun rememberDrawnProgress(target: Float, label: String): State<Float> {
    val eased = animateFloatAsState(
            targetValue = target.coerceIn(0f, 1f),
            animationSpec = tween(DrawnProgress.EASE_MS, easing = LinearEasing),
            label = label)
    return remember(eased) { derivedStateOf { DrawnProgress.quantize(eased.value) } }
}
