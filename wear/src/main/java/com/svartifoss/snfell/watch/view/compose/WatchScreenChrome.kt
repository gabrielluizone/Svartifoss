package com.svartifoss.snfell.watch.view.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.text.format.DateFormat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.basicCurvedText
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen furniture shared by the full-screen Compose list screens (queue, actions menu) so they
 * all get the same clock, scroll indicator and loading spinner without re-implementing them.
 */

/**
 * Wall-clock time refreshed every 15s while the composable is on screen. Follows the system
 * 12/24h setting but never appends AM/PM - the suffix adds clutter without information on a
 * watch-sized clock.
 */
@Composable
internal fun rememberWallClockTime(): String {
    val context = LocalContext.current
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val time by produceState(initialValue = currentTime(pattern), pattern) {
        while (true) {
            value = currentTime(pattern)
            delay(15_000L)
        }
    }
    return time
}

private fun currentTime(pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date())

/**
 * Clock curved along the top bezel. Callers fade it out (via [visible]) once the user scrolls
 * down, so it never overlaps list content.
 *
 * The fade alpha is applied in a graphicsLayer block (draw phase) rather than read during
 * composition, so the 200ms fade that fires exactly when a scroll starts doesn't recompose
 * anything mid-scroll. [contentAlpha] lets an interactive player theme quiet the clock without
 * changing visibility or affecting list screens, whose default remains fully opaque.
 */
@Composable
internal fun CurvedClock(visible: Boolean, contentAlpha: Float = 1f) {
    val clockAlpha = animateFloatAsState(
            targetValue = if (visible) contentAlpha.coerceIn(0f, 1f) else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "clockAlpha"
    )

    // Read here: CurvedLayout's content lambda is a CurvedScope, not a composable context.
    val time = rememberWallClockTime()

    CurvedLayout(
            modifier = Modifier.graphicsLayer { alpha = clockAlpha.value },
            anchor = 270f,
            anchorType = AnchorType.Center
    ) {
        basicCurvedText(
                text = time,
                style = CurvedTextStyle(
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.Normal
                )
        )
    }
}

/**
 * Straight top-center clock matching the Classic face's interactive clock (the `ambient_clock`
 * TextView, 5dp below the top edge). The player faces use this instead of [CurvedClock] so
 * switching faces never moves or re-styles the clock; the curved variant stays for the list
 * screens (queue, menu), where hugging the bezel is the point.
 *
 * [color] and [fontFamily] are fully resolved by the host (opacity baked into [color], the
 * dynamic/album/custom colour mode already applied), so this is purely presentational - the
 * identical values drive the Classic View clock. Default arguments keep the historical
 * 15sp Google Sans at #99FFFFFF look for any caller that hasn't wired the prefs through yet.
 */
@Composable
internal fun FaceClock(
        visible: Boolean,
        color: Color = Color(0x99FFFFFF),
        fontFamily: FontFamily = GoogleSansFamily
) {
    val clockAlpha = animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "faceClockAlpha"
    )
    val time = rememberWallClockTime()
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = clockAlpha.value }) {
        Text(
                text = time,
                color = color,
                fontSize = 15.sp,
                fontFamily = fontFamily,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 5.dp)
        )
    }
}

/**
 * Thin curved scroll indicator that hugs the right bezel and auto-hides ~1.2s after scrolling stops.
 *
 * All scroll-dependent state ([ScalingLazyListState.layoutInfo], centerItemIndex, the fade
 * alpha) is read inside the Canvas draw lambda, NOT during composition - layoutInfo changes on
 * every scrolled pixel, and reading it in composition recomposed this indicator (and with it,
 * part of the host screen) on every single scroll frame.
 */
@Composable
internal fun BoxScope.CurvedScrollIndicator(listState: ScalingLazyListState) {
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            active = true
        } else {
            delay(1200L)
            active = false
        }
    }
    val alphaState = animateFloatAsState(
            targetValue = if (active) 1f else 0f,
            animationSpec = tween(300),
            label = "scrollIndicatorAlpha"
    )

    // Short track with a small FIXED-size thumb. (Sizing the thumb by visible-item count made it
    // jump/resize oddly when scrolling with the rotary crown, so it's a constant length now.)
    // Stroke 5.5 dp matches the Wear OS standard indicator weight; arcSpan 22° is compact enough
    // that the bar doesn't feel stretched vertically on the bezel.
    val arcSpan = 22f
    val thumbSweep = 5.5f
    Canvas(Modifier.fillMaxSize()) {
        val alpha = alphaState.value
        if (alpha <= 0.01f) return@Canvas

        val total = listState.layoutInfo.totalItemsCount
        if (total <= 1) return@Canvas

        val scrollFraction = (listState.centerItemIndex.toFloat() / (total - 1)).coerceIn(0f, 1f)

        val stroke = 4.dp.toPx()
        val inset = stroke / 2f + 2.dp.toPx()
        val side = size.minDimension - inset * 2f
        val arcSize = Size(side, side)
        val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)

        drawArc(
                color = Color.White.copy(alpha = 0.12f * alpha),
                startAngle = -arcSpan / 2f,
                sweepAngle = arcSpan,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val thumbStart = -arcSpan / 2f + (arcSpan - thumbSweep) * scrollFraction
        drawArc(
                color = Color.White.copy(alpha = 0.8f * alpha),
                startAngle = thumbStart,
                sweepAngle = thumbSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/** Small indeterminate arc spinner. Callers position/center it themselves. */
@Composable
internal fun LoadingSpinner(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loadingSpinner")
    val startAngle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "spinnerAngle"
    )
    Canvas(modifier.size(36.dp)) {
        inset(2.dp.toPx()) {
            drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
