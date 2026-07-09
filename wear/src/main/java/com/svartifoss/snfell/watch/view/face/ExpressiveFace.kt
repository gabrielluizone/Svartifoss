package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import com.svartifoss.snfell.common.R as commonR
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The "expressive" now-playing face, mirroring the Material 3 Expressive Wear OS system media
 * controls: a soft 12-lobe "cookie" play/pause button (morphs to a plain circle while paused)
 * wrapped in a progress ring that follows the cookie's scalloped contour, flanked by large
 * round prev/next buttons in the album accent's light container tone, over the album art
 * darkened by an accent tint and a radial black vignette. When the user has no mini buttons
 * configured, the reference's bottom trio (queue / volume / overflow) shows as translucent
 * glass pills.
 *
 * Only the buttons are hit-testable; touches everywhere else fall straight through this
 * composable to the shared input layers underneath (quadrant taps, swipe gestures), so the
 * user's configured gestures keep working. The trade-off: a swipe that *starts* on a button is
 * consumed by it - unlike the classic center tap zone, which mirrors fling handling. Kept out
 * of v1 deliberately; revisit if it bothers anyone in practice.
 *
 * The ring is display-only for now ([NowPlayingFaceListener.onSeek] is reserved for a future
 * scrub interaction) and, matching the reference stills, draws in neutral white rather than
 * the classic face's configurable progress color - drag-to-seek and progress tinting stay
 * available on the classic face and via the rotary crown.
 */
@Composable
fun ExpressiveFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) {
    if (state.idle) {
        // Nothing to render - the shared idle ("nothing playing") group shows through, and
        // stopped-config gestures on the layers below remain the way to start playback.
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth

        // Non-null while the user is dragging the ring to scrub (central seek mode). Drives both
        // the ring sweep and the track-time readout so they follow the finger, and is committed to
        // the phone via listener.onSeek on release.
        var scrubFraction by remember { mutableStateOf<Float?>(null) }

        // --- Background treatment (non-hit-testable, so touches keep falling through). The
        // album art itself is the shared ImageView below this ComposeView; these layers turn
        // it into the reference's dark accent-tinted monochrome with black bezel edges.
        val tint = Color(tonal(state.accentColor, lightness = 0.30f, minSat = 0.30f, maxSat = 0.80f))
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = tint.copy(alpha = 0.45f))
            drawRect(color = Color.Black.copy(alpha = 0.30f))
            drawRect(
                    brush = Brush.radialGradient(
                            0.0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.88f)
                    )
            )
        }

        CurvedClock(visible = true)

        val sideContainer = Color(tonal(state.accentColor, lightness = 0.74f, minSat = 0.40f, maxSat = 0.85f))
        val centerContainer = Color(tonal(state.accentColor, lightness = 0.87f, minSat = 0.30f, maxSat = 0.70f))
        val onContainer = Color(tonal(state.accentColor, lightness = 0.16f, minSat = 0.25f, maxSat = 0.60f))

        // Title/artist near the top, like the reference (the transport row owns the center).
        Column(
                modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = screen * 0.13f, start = 26.dp, end = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                    text = state.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
            )
            if (state.artist.isNotEmpty()) {
                Text(
                        text = state.artist,
                        color = Color(state.artistColor),
                        fontSize = 13.sp,
                        fontFamily = GoogleSansFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(screen * 0.02f)
        ) {
            RoundTransportButton(
                    iconRes = commonR.drawable.action_skip_prev,
                    contentDescription = stringResource(R.string.action_name_skip_prev),
                    diameter = screen * 0.235f,
                    container = sideContainer,
                    content = onContainer,
                    onClick = listener::onSkipPreviousTap
            )
            CookiePlayButton(
                    state = state,
                    boxSize = screen * 0.34f,
                    cookieSize = screen * 0.215f,
                    container = centerContainer,
                    content = onContainer,
                    listener = listener,
                    scrubFraction = scrubFraction,
                    onScrub = { scrubFraction = it },
                    onScrubCommit = {
                        val committed = scrubFraction
                        scrubFraction = null
                        committed?.let(listener::onSeek)
                    }
            )
            RoundTransportButton(
                    iconRes = commonR.drawable.action_skip_next,
                    contentDescription = stringResource(R.string.action_name_skip_next),
                    diameter = screen * 0.235f,
                    container = sideContainer,
                    content = onContainer,
                    onClick = listener::onSkipNextTap
            )
        }

        val scrubbing = scrubFraction != null
        if (state.showTrackTime || scrubbing) {
            // While scrubbing, show the position the finger is pointing at (and brighten it) even
            // if the user normally hides the track time - it's the only readout of where the seek
            // will land.
            val shownPositionMs = scrubFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs
            Text(
                    text = stringResource(
                            R.string.playback_time_format,
                            formatFaceTime(shownPositionMs),
                            formatFaceTime(state.durationMs)
                    ),
                    color = if (scrubbing) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = GoogleSansFamily,
                    modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = screen * 0.17f + 12.dp)
            )
        }

        // The reference's bottom trio, shown only while the user has no mini buttons
        // configured - configured mini buttons take over this part of the screen.
        if (state.showDefaultBottomPills) {
            val pillWidth = screen * 0.235f
            val pillHeight = screen * 0.155f
            GlassPill(
                    width = pillWidth,
                    height = pillHeight,
                    label = stringResource(R.string.quick_action_up_next),
                    onClick = listener::onQueueTap,
                    modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(x = -screen * 0.275f, y = -screen * 0.152f)
            ) {
                Icon(
                        painter = painterResource(R.drawable.ic_queue_music),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                )
            }
            GlassPill(
                    width = pillWidth,
                    height = pillHeight,
                    label = stringResource(R.string.action_name_volume_up),
                    onClick = listener::onVolumeTap,
                    modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = -screen * 0.032f)
            ) {
                Icon(
                        painter = painterResource(R.drawable.volume_icon_up_outline),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                )
            }
            GlassPill(
                    width = pillWidth,
                    height = pillHeight,
                    label = stringResource(R.string.action_name_open_menu),
                    onClick = listener::onOverflowTap,
                    modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(x = screen * 0.275f, y = -screen * 0.152f)
            ) {
                OverflowDots()
            }
        }
    }
}

private fun formatFaceTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    return String.format(java.util.Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** Polar angle of [pos] around [center] as a 0f..1f fraction, measured clockwise from 12
 *  o'clock - the same mapping the classic CircularProgressSeekBar uses, so a given finger
 *  position seeks to the same spot on either face. */
private fun ringFractionAt(pos: Offset, center: Offset): Float {
    val angleFromTop = (Math.toDegrees(
            atan2((pos.y - center.y).toDouble(), (pos.x - center.x).toDouble())
    ) + 90.0 + 360.0) % 360.0
    return (angleFromTop / 360.0).toFloat().coerceIn(0f, 1f)
}

/** HSL-derived tonal color from the album accent, clamping saturation into a readable band -
 *  the same idea as WatchTheme.accentForSurface, parameterized for the M3-style container /
 *  on-container pairs this face needs. */
private fun tonal(accent: Int, lightness: Float, minSat: Float, maxSat: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent, hsl)
    hsl[1] = hsl[1].coerceIn(minSat, maxSat)
    hsl[2] = lightness
    return ColorUtils.HSLToColor(hsl)
}

@Composable
private fun RoundTransportButton(
        iconRes: Int,
        contentDescription: String,
        diameter: Dp,
        container: Color,
        content: Color,
        onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "transportPressScale")

    Box(
            modifier = Modifier
                    .size(diameter)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(container)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
    ) {
        Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = content,
                modifier = Modifier.size(diameter * 0.42f)
        )
    }
}

@Composable
private fun GlassPill(
        width: Dp,
        height: Dp,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "pillPressScale")

    Box(
            modifier = modifier
                    .size(width = width, height = height)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** The reference's "⋮" overflow glyph, drawn directly so no new icon resource is needed. */
@Composable
private fun OverflowDots() {
    Canvas(Modifier.size(18.dp)) {
        val r = 1.8.dp.toPx()
        val gap = 5.5.dp.toPx()
        for (i in -1..1) {
            drawCircle(Color.White, radius = r, center = Offset(center.x, center.y + i * gap))
        }
    }
}

// --- Cookie geometry ---------------------------------------------------------------------

private const val COOKIE_LOBES = 12

/** tanh() gently flattens the cosine's crests and valleys, turning a pointy star into the
 *  reference's soft scallops. */
private const val COOKIE_SOFTNESS = 1.5f

/** Cookie lobe amplitude as a fraction of its base radius. */
private const val COOKIE_MODULATION = 0.08f

/** The ring undulates noticeably less than the cookie it wraps, as in the reference. */
private const val RING_MODULATION = 0.045f

/** Gap (degrees) the ring leaves around the progress thumb and the 12 o'clock start. */
private const val RING_GAP_DEGREES = 7f

/** Radius multiplier for the given polar [angleRad]: 1f ± [modulation], lobe crests at the
 *  cardinal points (12 divides the 90° offsets used below evenly). */
private fun cookieProfile(angleRad: Float, modulation: Float): Float {
    val wave = tanh(COOKIE_SOFTNESS * cos(COOKIE_LOBES * angleRad)) / tanh(COOKIE_SOFTNESS)
    return 1f + modulation * wave
}

/** Point on the cookie contour at [degreesFromTop] (clockwise), for a base [radius]. */
private fun contourPoint(center: Offset, radius: Float, modulation: Float, degreesFromTop: Float): Offset {
    val angleRad = Math.toRadians((degreesFromTop - 90f).toDouble()).toFloat()
    val r = radius * cookieProfile(angleRad, modulation)
    return Offset(center.x + r * cos(angleRad), center.y + r * sin(angleRad))
}

private fun contourPath(center: Offset, radius: Float, modulation: Float, fromDeg: Float, toDeg: Float): Path {
    val path = Path()
    var degrees = fromDeg
    var first = true
    while (degrees <= toDeg) {
        val point = contourPoint(center, radius, modulation, degrees)
        if (first) {
            path.moveTo(point.x, point.y)
            first = false
        } else {
            path.lineTo(point.x, point.y)
        }
        degrees += 1.5f
    }
    return path
}

/** A 12-lobe soft scallop ("cookie"); [amplitudeFraction] 0f is a plain circle, so animating
 *  it morphs between the paused (circle) and playing (cookie) shapes. Always drawn upright -
 *  no rotation, one lobe centered at 12 o'clock. */
private class CookieShape(private val amplitudeFraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val center = Offset(size.width / 2f, size.height / 2f)
        val modulation = COOKIE_MODULATION * amplitudeFraction.coerceIn(0f, 1f)
        val baseRadius = min(size.width, size.height) / 2f / (1f + COOKIE_MODULATION)

        val path = contourPath(center, baseRadius, modulation, fromDeg = 0f, toDeg = 360f)
        path.close()
        return Outline.Generic(path)
    }
}

/** The cookie play/pause button plus the contour-following progress ring around it. */
@Composable
private fun CookiePlayButton(
        state: NowPlayingFaceState,
        boxSize: Dp,
        cookieSize: Dp,
        container: Color,
        content: Color,
        listener: NowPlayingFaceListener,
        scrubFraction: Float?,
        onScrub: (Float) -> Unit,
        onScrubCommit: () -> Unit
) {
    // Only enable ring scrubbing when the phone allows seeking and the user picked the central
    // seek mode; otherwise the ring stays display-only and taps still fall through to the cookie.
    val scrubEnabled = state.seekable && state.centralSeekEnabled
    // Paused flattens both the cookie and the ring's undulation into plain circles.
    val morph by animateFloatAsState(
            targetValue = if (state.playing) 1f else 0f,
            animationSpec = tween(450),
            label = "cookieMorph"
    )
    // Smooths the 500ms position ticks into continuous ring motion (the classic
    // CircularProgressSeekBar does the same with a ValueAnimator).
    val progress = animateFloatAsState(
            targetValue = state.progress.coerceIn(0f, 1f),
            animationSpec = tween(600, easing = LinearEasing),
            label = "ringProgress"
    )

    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "cookiePressScale")

    val ringDragModifier = if (scrubEnabled) {
        // Drags on the ring band (the area of this box outside the inner cookie, which owns taps)
        // scrub. Position → angle → fraction uses the same clockwise-from-12-o'clock mapping as
        // the classic CircularProgressSeekBar. A tap on the cookie is consumed by its own gesture
        // detector, so it never reaches this drag detector.
        Modifier.pointerInput(Unit) {
            val ringCenter = Offset(size.width / 2f, size.height / 2f)
            detectDragGestures(
                    onDragStart = { pos -> onScrub(ringFractionAt(pos, ringCenter)) },
                    onDrag = { change, _ ->
                        change.consume()
                        onScrub(ringFractionAt(change.position, ringCenter))
                    },
                    onDragEnd = { onScrubCommit() },
                    onDragCancel = { onScrubCommit() }
            )
        }
    } else {
        Modifier
    }

    Box(Modifier.size(boxSize).then(ringDragModifier), contentAlignment = Alignment.Center) {
        // Animated values are read inside the draw lambda, so ring motion only re-draws. While the
        // user is scrubbing, the sweep follows the finger directly instead of the animated value.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val ringModulation = RING_MODULATION * morph
            val baseRadius = (size.minDimension / 2f - stroke) / (1f + RING_MODULATION)
            val sweep = (scrubFraction ?: progress.value) * 360f

            // Track: from just past the thumb around to just short of 12 o'clock, leaving the
            // M3-style gaps on both sides of the played portion.
            val trackFrom = sweep + RING_GAP_DEGREES
            val trackTo = 360f - RING_GAP_DEGREES
            if (trackTo > trackFrom) {
                drawContourStroke(center, baseRadius, ringModulation, trackFrom, trackTo,
                        Color.White.copy(alpha = 0.30f), stroke)
            }

            // Played portion + thumb dot at its end.
            if (sweep > RING_GAP_DEGREES) {
                drawContourStroke(center, baseRadius, ringModulation, 0f, sweep - RING_GAP_DEGREES / 2f,
                        Color.White, stroke)
            }
            drawCircle(
                    color = Color.White,
                    // Grows while scrubbing so the grabbed thumb reads clearly under the finger.
                    radius = (if (scrubFraction != null) 5.5.dp else 3.5.dp).toPx(),
                    center = contourPoint(center, baseRadius, ringModulation, sweep)
            )
        }

        Box(
                modifier = Modifier
                        .size(cookieSize)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .clip(CookieShape(morph))
                        .background(container)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                    onPress = {
                                        pressed = true
                                        tryAwaitRelease()
                                        pressed = false
                                    },
                                    onTap = { listener.onPlayPauseTap() },
                                    onDoubleTap = { listener.onCenterDoubleTap() },
                                    onLongPress = { listener.onCenterLongPress() }
                            )
                        },
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    painter = painterResource(
                            if (state.playing) commonR.drawable.action_pause else commonR.drawable.action_play
                    ),
                    contentDescription = stringResource(R.string.action_name_play_pause),
                    tint = content,
                    modifier = Modifier.size(cookieSize * 0.48f)
            )
        }
    }
}

private fun DrawScope.drawContourStroke(
        center: Offset,
        radius: Float,
        modulation: Float,
        fromDeg: Float,
        toDeg: Float,
        color: Color,
        strokeWidth: Float
) {
    drawPath(
            path = contourPath(center, radius, modulation, fromDeg, toDeg),
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}
