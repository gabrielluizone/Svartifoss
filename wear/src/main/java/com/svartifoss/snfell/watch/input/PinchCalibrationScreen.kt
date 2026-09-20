package com.svartifoss.snfell.watch.input

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.watch.input.PinchCalibrationTimeline.Kind
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.CurvedScrollIndicator
import com.svartifoss.snfell.watch.view.compose.LoadingBars

/** What the calibration screen is doing; each has its own layout. */
internal enum class CalibrationMode { IDLE, CAPTURING, TESTING, SENDING }

internal enum class MessageTone { NEUTRAL, SUCCESS, ERROR }

internal data class CalibrationMessage(val text: String, val tone: MessageTone)

internal data class CalibrationUiState(
        val mode: CalibrationMode,
        val step: PinchCalibrationTimeline.Step,
        val testProgress: Float,
        val testSecondsLeft: Int,
        val detections: Int,
        val message: CalibrationMessage,
        /** Per-attempt numbers under the message, after a capture; null before one. */
        val detail: String?,
        /** A profile from this session is ready to test and save. */
        val profileReady: Boolean,
        /** At least one capture ran here, so the start button reads "Start again". */
        val attempted: Boolean,
        /** The acceleration a pinch has to reach, drawn across the graph; null until known. */
        val targetLine: Float?,
        /** No sample has arrived for a while - said on the graph instead of drawing a flat line. */
        val signalLost: Boolean
)

/**
 * The player's colours, so this screen reads as part of the same app rather than as a settings
 * page. Derived from the album triad the player itself extracted (see `AlbumPaletteCache`), with
 * the primary lifted for text on black the same way the player lifts its own labels.
 */
internal data class CalibrationColors(
        val accent: Color,
        val secondary: Color,
        val container: Color,
        val onAccent: Color
) {
    companion object {
        fun from(primary: Int, secondary: Int): CalibrationColors {
            val accent = WatchTheme.accentForText(primary)
            return CalibrationColors(
                    accent = Color(accent),
                    secondary = Color(WatchTheme.accentForText(secondary)),
                    container = Color(PaletteTransforms.sameHueTone(primary, .16f)),
                    onAccent = if (ColorUtils.calculateLuminance(accent) > .45) Color.Black else Color.White)
        }
    }
}

/**
 * The last few seconds of the motion signal, for the live graph.
 *
 * A plain ring buffer rather than state: samples arrive at ~50 Hz, and only the graph's draw
 * reads it. [version] is the one piece of Compose state, read inside the draw lambda, so each
 * sample invalidates that Canvas alone instead of recomposing the screen.
 */
internal class SignalTrace(val capacity: Int = 150) {
    private val motion = FloatArray(capacity)
    private val rotation = FloatArray(capacity)
    private val marks = BooleanArray(capacity)
    private var head = 0
    var size = 0
        private set
    var version by mutableIntStateOf(0)
        private set

    fun push(acceleration: Double, turn: Double) {
        motion[head] = acceleration.toFloat()
        rotation[head] = turn.toFloat()
        marks[head] = false
        head = (head + 1) % capacity
        if (size < capacity) size++
        version++
    }

    /** Marks the newest sample as the one a detection fired on. */
    fun markLatest() {
        if (size == 0) return
        marks[(head - 1 + capacity) % capacity] = true
        version++
    }

    fun clear() {
        head = 0
        size = 0
        version++
    }

    /** Oldest first: [index] 0 is [size] samples ago. */
    fun motionAt(index: Int) = motion[slot(index)]
    fun rotationAt(index: Int) = rotation[slot(index)]
    fun markedAt(index: Int) = marks[slot(index)]
    private fun slot(index: Int) = (head - size + index + capacity) % capacity
}

private val TRACK = Color.White.copy(alpha = .12f)
private val ERROR = Color(0xFFFFB4A9)
private val FONT = GoogleSansFamily

/**
 * The guided calibration, drawn for a round screen.
 *
 * It replaces a View layout that stacked a title, a paragraph and four stock full-width buttons in
 * a ScrollView - on a round watch the buttons ran into the bezel and the one instruction that
 * mattered during a capture ("pinch now") was a line of body text. The capture now owns the
 * whole screen: what to do in large type, a ring for how long is left, the live signal, and one
 * dot per attempt. Nothing is tappable while it runs, because a tap is exactly the kind of motion
 * the recording must not contain; a right swipe still leaves, as it does everywhere on Wear OS.
 *
 * Always Google Sans rather than the configurable UI font: this is a diagnostic surface, and its
 * numbers and the graph legend have to stay legible whatever typeface the player wears.
 */
@Composable
internal fun PinchCalibrationScreen(
        state: CalibrationUiState,
        colors: CalibrationColors,
        trace: SignalTrace,
        onStart: () -> Unit,
        onTest: () -> Unit,
        onSave: () -> Unit,
        onClose: () -> Unit
) {
    var dismissed by remember { mutableStateOf(false) }
    SwipeToDismissBox(onDismissed = { if (!dismissed) { dismissed = true; onClose() } }) { background ->
        if (background) return@SwipeToDismissBox
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (state.mode) {
                CalibrationMode.CAPTURING -> CaptureView(state, colors, trace)
                CalibrationMode.TESTING -> TestView(state, colors, trace)
                CalibrationMode.IDLE, CalibrationMode.SENDING ->
                    MenuView(state, colors, onStart, onTest, onSave, onClose)
            }
        }
    }
}

@Composable
private fun MenuView(
        state: CalibrationUiState,
        colors: CalibrationColors,
        onStart: () -> Unit,
        onTest: () -> Unit,
        onSave: () -> Unit,
        onClose: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val sending = state.mode == CalibrationMode.SENDING
    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 32.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState)
        ) {
            item {
                Text(
                        text = stringResource(R.string.pinch_cal_title),
                        color = colors.accent,
                        fontFamily = FONT,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
            }
            item {
                Text(
                        text = state.message.text,
                        color = when (state.message.tone) {
                            MessageTone.NEUTRAL -> Color.White.copy(alpha = .8f)
                            MessageTone.SUCCESS -> colors.accent
                            MessageTone.ERROR -> ERROR
                        },
                        fontFamily = FONT,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
            }
            state.detail?.let { detail ->
                item {
                    Text(
                            text = detail,
                            color = Color.White.copy(alpha = .6f),
                            fontFamily = FONT,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp))
                }
            }
            if (sending) {
                item {
                    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        LoadingBars(colors.accent)
                    }
                }
            } else {
                if (state.profileReady) {
                    item { Pill(R.string.pinch_cal_save, PillStyle.PRIMARY, colors, onSave) }
                    item { Pill(R.string.pinch_cal_test, PillStyle.SECONDARY, colors, onTest) }
                    item { Pill(R.string.pinch_cal_restart, PillStyle.SECONDARY, colors, onStart) }
                } else {
                    item {
                        Pill(if (state.attempted) R.string.pinch_cal_restart else R.string.pinch_cal_start,
                                PillStyle.PRIMARY, colors, onStart)
                    }
                }
                item { Pill(R.string.pinch_cal_close, PillStyle.QUIET, colors, onClose) }
            }
        }
        CurvedScrollIndicator(listState)
    }
}

private enum class PillStyle { PRIMARY, SECONDARY, QUIET }

@Composable
private fun Pill(label: Int, style: PillStyle, colors: CalibrationColors, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val base = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
    val filled = when (style) {
        PillStyle.PRIMARY -> base.background(colors.accent)
        PillStyle.SECONDARY -> base.background(colors.container)
        PillStyle.QUIET -> base.border(1.dp, colors.accent.copy(alpha = .35f), shape)
    }
    Box(filled.clickable(onClick = onClick).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center) {
        FitText(
                text = stringResource(label),
                maxSize = 14.sp,
                minSize = 10.sp,
                maxLines = 2,
                color = if (style == PillStyle.PRIMARY) colors.onAccent else Color.White,
                fontWeight = FontWeight.Bold,
                lineHeightRatio = 1.15f)
    }
}

/**
 * Text that gives up size before it gives up words.
 *
 * These lines were laid out for the English strings, and a translation routinely runs a third
 * longer - which on a round screen would otherwise mean an ellipsis in the one instruction that
 * matters during a capture. It steps down a point at a time until the text fits [maxLines], never
 * below [minSize], and only then falls back to the ellipsis. Hidden until it settles, so the
 * too-large frames the layout pass measures to trigger each step are never drawn (the idiom
 * `FaceChrome`'s title shrink uses).
 */
@Composable
private fun FitText(
        text: String,
        maxSize: TextUnit,
        minSize: TextUnit,
        maxLines: Int,
        color: Color,
        modifier: Modifier = Modifier,
        fontWeight: FontWeight? = null,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        lineHeightRatio: Float = 1.2f
) {
    var size by remember(text, maxSize) { mutableFloatStateOf(maxSize.value) }
    var ready by remember(text, maxSize) { mutableStateOf(false) }
    Text(
            text = text,
            color = color,
            fontFamily = FONT,
            fontWeight = fontWeight,
            fontSize = size.sp,
            lineHeight = (size * lineHeightRatio).sp,
            letterSpacing = letterSpacing,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.graphicsLayer { alpha = if (ready) 1f else 0f },
            onTextLayout = { result ->
                if (result.hasVisualOverflow && size > minSize.value) {
                    size = (size - 1f).coerceAtLeast(minSize.value)
                } else {
                    ready = true
                }
            })
}

@Composable
private fun CaptureView(state: CalibrationUiState, colors: CalibrationColors, trace: SignalTrace) {
    val step = state.step
    val pinching = step.kind == Kind.PINCH
    Box(Modifier.fillMaxSize()) {
        ProgressRing(step.stepProgress, if (pinching) colors.accent else Color.White.copy(alpha = .5f))
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val screen = maxWidth
            Column(
                    modifier = Modifier.width(screen * .74f),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Caption(when (step.kind) {
                    Kind.PINCH, Kind.WAIT -> stringResource(R.string.pinch_cal_step_attempt,
                            step.attempt, PinchCalibrationTimeline.ATTEMPTS)
                    Kind.COUNTDOWN -> stringResource(R.string.pinch_cal_step_ready)
                    else -> stringResource(R.string.pinch_cal_title)
                })
                Spacer(Modifier.height(2.dp))
                FitText(
                        // The countdown is the one step whose headline is its number.
                        text = if (step.kind == Kind.COUNTDOWN) step.secondsLeft.toString()
                                else stringResource(headlineFor(step.kind)),
                        maxSize = if (step.kind == Kind.COUNTDOWN) 30.sp else 20.sp,
                        minSize = 12.sp,
                        maxLines = 1,
                        color = if (pinching) colors.accent else Color.White,
                        fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                FitText(
                        text = stringResource(hintFor(step.kind)),
                        maxSize = 11.sp,
                        minSize = 9.sp,
                        maxLines = 2,
                        color = Color.White.copy(alpha = .7f),
                        lineHeightRatio = 14f / 11f)
                Spacer(Modifier.height(8.dp))
                SignalGraph(trace, state.targetLine, state.signalLost, colors, screen * .70f)
                Spacer(Modifier.height(8.dp))
                AttemptDots(step, colors)
            }
        }
    }
}

@Composable
private fun TestView(state: CalibrationUiState, colors: CalibrationColors, trace: SignalTrace) {
    Box(Modifier.fillMaxSize()) {
        ProgressRing(state.testProgress, colors.accent)
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val screen = maxWidth
            Column(
                    modifier = Modifier.width(screen * .74f),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Caption(stringResource(R.string.pinch_cal_testing))
                Text(
                        text = state.detections.toString(),
                        color = colors.accent,
                        fontFamily = FONT,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                        lineHeight = 36.sp)
                FitText(
                        text = stringResource(R.string.pinch_cal_detections),
                        maxSize = 13.sp,
                        minSize = 10.sp,
                        maxLines = 1,
                        color = Color.White,
                        fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SignalGraph(trace, state.targetLine, state.signalLost, colors, screen * .70f)
                Spacer(Modifier.height(6.dp))
                FitText(
                        text = stringResource(R.string.pinch_cal_test_hint, state.testSecondsLeft),
                        maxSize = 11.sp,
                        minSize = 9.sp,
                        maxLines = 2,
                        color = Color.White.copy(alpha = .7f))
            }
        }
    }
}

/**
 * The live motion signal: filtered acceleration (the thing a pinch spikes) in the accent,
 * rotation underneath in the secondary colour, the level a pinch must reach as a dashed line,
 * and a dot wherever the test's detector fired.
 *
 * It exists to answer one question the calibration could not: *is anything arriving at all?* A
 * refusal that says "not recognised" cannot tell a silent sensor from taps that were simply too
 * soft, and on the wrist those need opposite fixes. Both series are scaled to what is on screen
 * (never below a small floor, so resting noise stays flat), because the absolute numbers differ
 * between watches and a fixed scale would be empty on one and clipped on another.
 */
@Composable
private fun SignalGraph(
        trace: SignalTrace,
        target: Float?,
        signalLost: Boolean,
        colors: CalibrationColors,
        width: Dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
                modifier = Modifier
                        .width(width)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = .06f)),
                contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
                // Read inside the draw lambda: a new sample redraws this Canvas and nothing else.
                @Suppress("UNUSED_VARIABLE") val tick = trace.version
                val count = trace.size
                if (count < 2) return@Canvas
                var motionMax = 0f
                var rotationMax = 0f
                for (i in 0 until count) {
                    motionMax = maxOf(motionMax, trace.motionAt(i))
                    rotationMax = maxOf(rotationMax, trace.rotationAt(i))
                }
                val motionScale = maxOf(MOTION_FLOOR, motionMax, (target ?: 0f) * 1.4f)
                val rotationScale = maxOf(ROTATION_FLOOR, rotationMax)
                val step = size.width / (trace.capacity - 1)
                // Newest sample on the right edge, so the trace scrolls leftwards as it fills.
                val offset = (trace.capacity - count) * step
                fun y(value: Float, scale: Float) =
                        size.height - (value / scale).coerceIn(0f, 1f) * size.height

                val rotationPath = Path()
                val motionPath = Path()
                for (i in 0 until count) {
                    val x = offset + i * step
                    if (i == 0) {
                        rotationPath.moveTo(x, y(trace.rotationAt(i), rotationScale))
                        motionPath.moveTo(x, y(trace.motionAt(i), motionScale))
                    } else {
                        rotationPath.lineTo(x, y(trace.rotationAt(i), rotationScale))
                        motionPath.lineTo(x, y(trace.motionAt(i), motionScale))
                    }
                }
                drawPath(rotationPath, colors.secondary.copy(alpha = .55f),
                        style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
                if (target != null) {
                    val lineY = y(target, motionScale)
                    drawLine(Color.White.copy(alpha = .6f), Offset(0f, lineY), Offset(size.width, lineY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                }
                drawPath(motionPath, colors.accent,
                        style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                for (i in 0 until count) {
                    if (trace.markedAt(i)) {
                        drawCircle(colors.accent, 3.dp.toPx(), Offset(offset + i * step, 3.dp.toPx()))
                    }
                }
            }
            if (signalLost) {
                Text(
                        text = stringResource(R.string.pinch_cal_no_signal),
                        color = ERROR,
                        fontFamily = FONT,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(3.dp))
        // Bound to the graph's width and shared between the labels: three translated words side
        // by side are wider than the column they used to be measured against.
        Row(
                modifier = Modifier.width(width),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
        ) {
            val share = Modifier.weight(1f, fill = false)
            LegendItem(colors.accent, stringResource(R.string.pinch_cal_legend_motion), share)
            LegendItem(colors.secondary.copy(alpha = .75f), stringResource(R.string.pinch_cal_legend_rotation), share)
            if (target != null) {
                LegendItem(Color.White.copy(alpha = .6f), stringResource(R.string.pinch_cal_legend_target), share,
                        dashed = true)
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, modifier: Modifier, dashed: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 8.dp, height = 6.dp)) {
            if (dashed) {
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 1.5.dp.toPx())))
            } else {
                drawCircle(color, size.height / 2)
            }
        }
        Spacer(Modifier.width(3.dp))
        FitText(text = label, maxSize = 9.sp, minSize = 7.sp, maxLines = 1, color = Color.White.copy(alpha = .65f))
    }
}

@Composable
private fun Caption(text: String) {
    FitText(
            text = text.uppercase(),
            maxSize = 11.sp,
            minSize = 8.sp,
            maxLines = 1,
            color = Color.White.copy(alpha = .6f),
            fontWeight = FontWeight.Medium,
            letterSpacing = .6.sp)
}

/** One dot per guided attempt: filled once done, ringed while it is the current one. */
@Composable
private fun AttemptDots(step: PinchCalibrationTimeline.Step, colors: CalibrationColors) {
    val done = when (step.kind) {
        Kind.COUNTDOWN, Kind.REST -> 0
        Kind.PINCH -> step.attempt - 1
        Kind.WAIT -> step.attempt
        else -> PinchCalibrationTimeline.ATTEMPTS
    }
    val current = if (step.kind == Kind.PINCH) step.attempt else 0
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (index in 1..PinchCalibrationTimeline.ATTEMPTS) {
            val modifier = Modifier.size(7.dp).clip(CircleShape)
            Box(when {
                index <= done -> modifier.background(colors.accent)
                index == current -> modifier.border(1.5.dp, colors.accent, CircleShape)
                else -> modifier.background(TRACK)
            })
        }
    }
}

/** A thin ring just inside the bezel - the round screen's own progress bar. */
@Composable
private fun ProgressRing(progress: Float, color: Color) {
    Canvas(Modifier.fillMaxSize().padding(5.dp)) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(TRACK, -90f, 360f, useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(stroke))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Below these the graph does not zoom in, so a still wrist draws a flat line, not noise. */
private const val MOTION_FLOOR = 0.3f
private const val ROTATION_FLOOR = 1.0f

private fun headlineFor(kind: Kind): Int = when (kind) {
    Kind.COUNTDOWN -> R.string.pinch_cal_step_ready
    Kind.REST -> R.string.pinch_cal_step_rest
    Kind.PINCH -> R.string.pinch_cal_step_pinch
    Kind.WAIT -> R.string.pinch_cal_step_wait
    Kind.PREPARE_MOVE -> R.string.pinch_cal_step_prepare
    Kind.MOVE, Kind.DONE -> R.string.pinch_cal_step_move
}

private fun hintFor(kind: Kind): Int = when (kind) {
    Kind.COUNTDOWN -> R.string.pinch_cal_step_ready_hint
    Kind.REST -> R.string.pinch_cal_step_rest_hint
    Kind.PINCH -> R.string.pinch_cal_step_pinch_hint
    Kind.WAIT -> R.string.pinch_cal_step_wait_hint
    Kind.PREPARE_MOVE -> R.string.pinch_cal_step_prepare_hint
    Kind.MOVE, Kind.DONE -> R.string.pinch_cal_step_move_hint
}
