package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import com.svartifoss.snfell.common.R as commonR
import kotlin.math.cos
import kotlin.math.sin

/**
 * The "Vinyl (Beta)" now-playing face: the album art becomes a slowly spinning record - groove
 * rings, accent label and spindle hole - with the playback progress drawn as an accent arc
 * hugging the record's rim, small glass prev/next buttons flanking it and the title/artist up
 * top. Tapping the record toggles play/pause (a translucent play badge shows while paused);
 * double tap opens the quick panel and long-press the queue, matching the other faces' center
 * gestures. The bottom pill trio appears when no mini buttons are configured, in the exact
 * same positions as the expressive face so the shared mini-buttons/quick-panel geometry keeps
 * working unchanged.
 *
 * Only the record and the buttons are hit-testable; everything else falls through to the
 * host's shared gesture layers (quadrant taps, swipes). The face paints its own opaque black
 * backdrop, so the host's background art view (and the album-art style prefs that shape it)
 * doesn't show behind it - the record IS the art here.
 */
@Composable
fun VinylFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) {
    if (state.idle) {
        return
    }

    if (state.ambient) {
        VinylAmbientFace(state)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        val accent = Color(state.accentColor)

        // Opaque backdrop: black with a faint tonal glow bleeding out from behind the record.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black)
            drawCircle(
                    brush = Brush.radialGradient(
                            0.0f to Color(faceTonal(state.accentColor, 0.22f)).copy(alpha = 0.55f),
                            1.0f to Color.Transparent
                    ),
                    radius = size.minDimension * 0.42f,
                    center = center
            )
        }

        CurvedClock(visible = true)

        Column(
                modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = screen * 0.10f, start = 30.dp, end = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                    text = state.title,
                    color = Color.White,
                    fontSize = 15.sp,
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
                        fontSize = 12.sp,
                        fontFamily = GoogleSansFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }

        SpinningRecord(
                state = state,
                diameter = screen * 0.46f,
                progressColor = Color(state.progressColor),
                listener = listener,
                modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = screen * 0.045f)
        )

        // Prev / next as small glass circles flanking the record.
        val sideSize = screen * 0.16f
        FaceTapTarget(
                width = sideSize,
                height = sideSize,
                shape = CircleShape,
                background = Color.White.copy(alpha = 0.16f),
                label = stringResource(R.string.action_name_skip_prev),
                onClick = listener::onSkipPreviousTap,
                modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = -screen * 0.375f, y = screen * 0.045f)
        ) {
            Icon(
                    painter = painterResource(commonR.drawable.action_skip_prev),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(sideSize * 0.5f)
            )
        }
        FaceTapTarget(
                width = sideSize,
                height = sideSize,
                shape = CircleShape,
                background = Color.White.copy(alpha = 0.16f),
                label = stringResource(R.string.action_name_skip_next),
                onClick = listener::onSkipNextTap,
                modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = screen * 0.375f, y = screen * 0.045f)
        ) {
            Icon(
                    painter = painterResource(commonR.drawable.action_skip_next),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(sideSize * 0.5f)
            )
        }

        if (state.showTrackTime) {
            Text(
                    text = stringResource(
                            R.string.playback_time_format,
                            formatFaceClockTime(state.positionMs),
                            formatFaceClockTime(state.durationMs)
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = GoogleSansFamily,
                    modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = screen * 0.31f)
            )
        }

        if (state.showDefaultBottomPills) {
            VinylBottomPills(screen = screen, listener = listener, boxScope = this)
        }
    }
}

/** Bottom queue/volume/overflow trio - same sizes and positions as the expressive face, so a
 *  user switching faces finds the buttons where their muscle memory expects them. */
@Composable
private fun VinylBottomPills(
        screen: androidx.compose.ui.unit.Dp,
        listener: NowPlayingFaceListener,
        boxScope: androidx.compose.foundation.layout.BoxScope
) = with(boxScope) {
    val pillWidth = screen * 0.235f
    val pillHeight = screen * 0.155f
    FaceGlassPill(
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
    FaceGlassPill(
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
    FaceGlassPill(
            width = pillWidth,
            height = pillHeight,
            label = stringResource(R.string.action_name_open_menu),
            onClick = listener::onOverflowTap,
            modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(x = screen * 0.275f, y = -screen * 0.152f)
    ) {
        FaceOverflowDots()
    }
}

/** The record itself: art clipped in a circle, groove rings, accent center label with spindle
 *  hole, spinning ~5 rpm while playing, wrapped by the rim progress arc. */
@Composable
private fun SpinningRecord(
        state: NowPlayingFaceState,
        diameter: androidx.compose.ui.unit.Dp,
        progressColor: Color,
        listener: NowPlayingFaceListener,
        modifier: Modifier = Modifier
) {
    // Continuous rotation while playing. The transition always runs; the angle is only applied
    // while playing, and the record eases back upright on pause via the freeze value.
    val spin = rememberInfiniteTransition(label = "vinylSpin")
    val angle by spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 12_000, easing = LinearEasing)),
            label = "vinylAngle"
    )
    var frozenAngle by remember { mutableStateOf(0f) }
    val appliedAngle = if (state.playing) {
        frozenAngle = angle
        angle
    } else {
        frozenAngle
    }

    val pausedBadge by animateFloatAsState(
            targetValue = if (state.playing) 0f else 1f,
            animationSpec = tween(300),
            label = "vinylPausedBadge"
    )
    val progress = androidx.compose.animation.core.animateFloatAsState(
            targetValue = state.progress.coerceIn(0f, 1f),
            animationSpec = tween(600, easing = LinearEasing),
            label = "vinylProgress"
    )

    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "vinylPress")

    Box(modifier.size(diameter * 1.22f), contentAlignment = Alignment.Center) {
        // Rim progress arc, outside the record.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.5.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                    color = Color.White.copy(alpha = 0.22f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round)
            )
            val sweep = progress.value * 360f
            if (sweep > 1f) {
                drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }

        Box(
                modifier = Modifier
                        .size(diameter)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                            rotationZ = appliedAngle
                        }
                        .clip(CircleShape)
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
            RecordSurface(state.albumArt, Color(faceTonal(state.accentColor, 0.55f)))
        }

        // Paused: translucent scrim + play badge over the (now static) record.
        if (pausedBadge > 0.01f) {
            Canvas(Modifier.size(diameter)) {
                drawCircle(Color.Black.copy(alpha = 0.45f * pausedBadge))
            }
            Icon(
                    painter = painterResource(commonR.drawable.action_play),
                    contentDescription = stringResource(R.string.action_name_play_pause),
                    tint = Color.White.copy(alpha = pausedBadge),
                    modifier = Modifier.size(diameter * 0.3f)
            )
        }
    }
}

/** Draws the record: album art (or a tonal disc when there is none), groove rings, label and
 *  spindle hole. Rotates as a whole via the caller's graphicsLayer. */
@Composable
private fun RecordSurface(albumArt: ImageBitmap?, labelColor: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (albumArt != null) {
            androidx.compose.foundation.Image(
                    painter = BitmapPainter(albumArt),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            if (albumArt == null) {
                drawCircle(labelColor.copy(alpha = 0.35f))
            }
            // Vinyl treatment: edge vignette + groove rings + label + spindle hole.
            drawCircle(
                    brush = Brush.radialGradient(
                            0.0f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.55f)
                    )
            )
            val grooves = 4
            for (i in 0 until grooves) {
                val r = size.minDimension / 2f * (0.52f + 0.115f * i)
                drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = r,
                        style = Stroke(width = 1.dp.toPx())
                )
            }
            val labelRadius = size.minDimension * 0.155f
            drawCircle(color = labelColor, radius = labelRadius)
            drawCircle(color = Color.Black.copy(alpha = 0.85f), radius = labelRadius * 0.22f)
        }
    }
}

/**
 * The vinyl face's AOD: the record reduced to a hairline circle with the spindle hole and two
 * groove hints, the frozen rim progress arc, and the title up top - all in the ambient tint,
 * scaled by the ambient intensity, zero fills. The side buttons and pills render as hairline
 * outlines in their interactive positions when their toggles allow, so wake-up taps land where
 * the buttons will be.
 */
@Composable
private fun VinylAmbientFace(state: NowPlayingFaceState) {
    val i = state.ambientIntensity.coerceIn(0.2f, 1f)
    val tint = Color(state.ambientTint)
    val stroke = tint.copy(alpha = 0.50f * i)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth

        if (state.ambientShowTrackInfo) {
            Column(
                    modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = screen * 0.14f, start = 30.dp, end = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        text = state.title,
                        color = tint.copy(alpha = 0.85f * i),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                if (state.artist.isNotEmpty()) {
                    Text(
                            text = state.artist,
                            color = Color.White.copy(alpha = 0.55f * i),
                            fontSize = 12.sp,
                            fontFamily = GoogleSansFamily,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (state.ambientShowTransport) {
            Canvas(
                    Modifier
                            .align(Alignment.Center)
                            .offset(y = screen * 0.045f)
                            .size(screen * 0.46f * 1.22f)
            ) {
                val strokePx = 1.5.dp.toPx()
                val recordRadius = size.minDimension / 2f / 1.22f

                if (state.ambientShowProgress) {
                    val inset = strokePx / 2f
                    val sweep = state.progress.coerceIn(0f, 1f) * 360f
                    drawArc(
                            color = stroke.copy(alpha = stroke.alpha * 0.4f),
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(
                                    size.width - strokePx, size.height - strokePx),
                            style = Stroke(strokePx)
                    )
                    if (sweep > 1f) {
                        drawArc(
                                color = stroke,
                                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = androidx.compose.ui.geometry.Size(
                                        size.width - strokePx, size.height - strokePx),
                                style = Stroke(strokePx, cap = StrokeCap.Round)
                        )
                    }
                }

                drawCircle(color = stroke, radius = recordRadius, style = Stroke(strokePx))
                drawCircle(color = stroke.copy(alpha = stroke.alpha * 0.5f),
                        radius = recordRadius * 0.62f, style = Stroke(1.dp.toPx()))
                drawCircle(color = stroke, radius = recordRadius * 0.155f, style = Stroke(strokePx))
                drawCircle(color = stroke, radius = recordRadius * 0.035f)

                // Side prev/next outlines at their interactive positions.
                val sideRadius = screen.toPx() * 0.08f
                val sideOffset = screen.toPx() * 0.375f
                drawCircle(color = stroke, radius = sideRadius, style = Stroke(strokePx),
                        center = Offset(center.x - sideOffset, center.y))
                drawCircle(color = stroke, radius = sideRadius, style = Stroke(strokePx),
                        center = Offset(center.x + sideOffset, center.y))
            }
        }

        if (state.showDefaultBottomPills && state.ambientShowPills) {
            Canvas(Modifier.fillMaxSize()) {
                val strokePx = 1.5.dp.toPx()
                val pillWidth = screen.toPx() * 0.235f
                val pillHeight = screen.toPx() * 0.155f
                val centers = listOf(
                        Offset(center.x - screen.toPx() * 0.275f,
                                size.height - screen.toPx() * 0.152f - pillHeight / 2f),
                        Offset(center.x,
                                size.height - screen.toPx() * 0.032f - pillHeight / 2f),
                        Offset(center.x + screen.toPx() * 0.275f,
                                size.height - screen.toPx() * 0.152f - pillHeight / 2f)
                )
                for (c in centers) {
                    drawRoundRect(
                            color = stroke,
                            topLeft = Offset(c.x - pillWidth / 2f, c.y - pillHeight / 2f),
                            size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillHeight / 2f),
                            style = Stroke(strokePx)
                    )
                }
            }
        }
    }
}
