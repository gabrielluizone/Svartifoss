package com.svartifoss.snfell.watch.view.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.basicCurvedText
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.common.WatchTypography
import com.svartifoss.snfell.common.FaceGeometry
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
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
    val fontFamily = LocalWatchUiFontFamily.current

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
                        fontFamily = fontFamily,
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
        // Deliberately not LocalWatchUiFontFamily: that local is the *chrome* font (menu, queue),
        // while every player face resolves and passes its own per-face font explicitly. This
        // default only covers a caller that hasn't wired the prefs through yet.
        fontFamily: FontFamily = GoogleSansFamily,
        // Identity by default, meaning "the size and weight this chrome was designed at". Only a
        // value the user moved away from identity changes anything here.
        typography: WatchTypography.TextSpec = WatchTypography.IDENTITY_TEXT
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
                fontSize = typography.scaled(FACE_CLOCK_SP).sp,
                fontWeight = FontWeight(typography.weight),
                fontStyle = if (typography.italic) FontStyle.Italic else null,
                fontFamily = fontFamily,
                letterSpacing = if (typography.trackingEm == 0f) {
                    TextUnit.Unspecified
                } else {
                    typography.trackingEm.em
                },
                modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = FaceGeometry.Classic.CLOCK_TOP_PADDING_DP.dp)
        )
    }
}

/** The Compose faces' designed clock size, matching the classic face's CLASSIC_CLOCK_SP. */
private const val FACE_CLOCK_SP = FaceGeometry.Classic.CLOCK_SP

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

/**
 * Three pulsing bars - the app's one "something is happening" animation.
 *
 * It began as the queue's now-playing marker and is shared from here because it is now also what
 * *waiting* looks like everywhere in the watch app: the queue loading, a page of it loading, the
 * action menu populating, a lyric being fetched. An arc spinner did that job before, and the reason
 * to drop it is not that it looked bad on its own but that it was a second vocabulary - a generic
 * platform shape sitting inside a screen that already had a house animation for exactly this, and
 * one the user had learned to read as "audio". Three bars pulsing say "your music app is working"
 * in a way a rotating arc never does.
 *
 * Runs unconditionally, including through a scroll. It was frozen to a static pose while the list
 * moved back when each bar was a Box animating `fillMaxHeight` - that re-ran *layout* for the whole
 * row every animation frame and genuinely did cost the scroll its frame budget. Drawing all three
 * in one Canvas and reading the animated values inside the draw lambda (not in composition) makes
 * each frame a redraw-only invalidation of one small node, cheap enough that freezing it bought
 * nothing and only made the playing row look stopped exactly when the user was moving.
 *
 * The three bars carry deliberately mismatched, non-harmonic periods (480/360/560 ms). Equal ones
 * would beat in step and read as a single block breathing rather than as three independent bars.
 */
@Composable
internal fun EqualizerBars(
        color: Color,
        modifier: Modifier = Modifier,
        barWidth: Dp = 3.dp,
        barGap: Dp = 2.dp,
        height: Dp = 16.dp
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val h1 by transition.animateFloat(
            initialValue = 0.30f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse), label = "bar1"
    )
    val h2 by transition.animateFloat(
            initialValue = 1.0f, targetValue = 0.40f,
            animationSpec = infiniteRepeatable(tween(360), RepeatMode.Reverse), label = "bar2"
    )
    val h3 by transition.animateFloat(
            initialValue = 0.55f, targetValue = 0.90f,
            animationSpec = infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "bar3"
    )
    Canvas(modifier.size(width = barWidth * 3 + barGap * 2, height = height)) {
        val widthPx = barWidth.toPx()
        val gapPx = barGap.toPx()
        // Half the bar width, so a bar reads as a rounded column rather than as a lozenge - and it
        // scales with the bar, which a fixed radius would not: the loading size is nearly twice the
        // row size and a 2dp corner on it looks square.
        val corner = CornerRadius(widthPx / 2f)
        listOf(h1, h2, h3).forEachIndexed { index, fraction ->
            // Floored at the bar width so a bar at its lowest is still a visible dot rather than a
            // sliver: the animation reads as three bars dancing, not as bars vanishing.
            val barHeight = (size.height * fraction).coerceAtLeast(widthPx)
            drawRoundRect(
                    color = color,
                    topLeft = Offset(index * (widthPx + gapPx), size.height - barHeight),
                    size = Size(widthPx, barHeight),
                    cornerRadius = corner
            )
        }
    }
}

/**
 * [EqualizerBars] at the size a standalone "loading" state wants.
 *
 * A separate entry point rather than a default, because these are two different things that happen
 * to share a drawing: the row marker is an ornament beside text and must not out-weigh it, while
 * this one is alone in the middle of an empty screen and has to be findable there. It replaced a
 * 36dp arc spinner, so it is sized to hold that much of the eye.
 */
@Composable
internal fun LoadingBars(color: Color, modifier: Modifier = Modifier) {
    EqualizerBars(
            color = color,
            modifier = modifier,
            barWidth = 5.dp,
            barGap = 4.dp,
            height = 26.dp)
}
