package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import com.svartifoss.snfell.common.R as commonR

/**
 * The "Poster (Beta)" now-playing face: an editorial, typography-first take - a flat deep
 * tonal backdrop derived from the album accent (the face owns its background; the art view
 * underneath is deliberately covered), a big two-line centered title with the artist in the
 * accent underneath, squared transport buttons across the center, and a straight progress bar
 * (with position/total at its ends) instead of any ring.
 *
 * The transport row and the bottom pill trio sit at the exact same coordinates as the
 * expressive face's, so the shared mini-buttons row, quick panel and muscle memory carry over
 * unchanged. Only the buttons are hit-testable - everything else falls through to the host's
 * gesture layers.
 */
@Composable
fun PosterFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) {
    if (state.idle) {
        return
    }

    if (state.ambient) {
        PosterAmbientFace(state)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        val backdrop = Color(faceTonal(state.accentColor, 0.13f, minSat = 0.20f, maxSat = 0.55f))
        val backdropDeep = Color(faceTonal(state.accentColor, 0.07f, minSat = 0.20f, maxSat = 0.55f))
        val container = Color(faceTonal(state.accentColor, 0.78f, minSat = 0.35f, maxSat = 0.80f))
        val onContainer = Color(faceTonal(state.accentColor, 0.14f))

        // Flat poster backdrop with a subtle vertical falloff - fully opaque on purpose.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(
                    0.0f to backdrop,
                    1.0f to backdropDeep
            ))
        }

        CurvedClock(visible = true)

        Column(
                modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = screen * 0.115f, start = 26.dp, end = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                    text = state.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
            )
            if (state.artist.isNotEmpty()) {
                Text(
                        text = state.artist.uppercase(),
                        color = Color(state.artistColor),
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontFamily = GoogleSansFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        // Squared transport row - same center position as the expressive face's row.
        Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(screen * 0.035f)
        ) {
            SquareTransportButton(
                    iconRes = commonR.drawable.action_skip_prev,
                    label = stringResource(R.string.action_name_skip_prev),
                    sizeDp = screen * 0.20f,
                    background = Color.White.copy(alpha = 0.14f),
                    iconTint = Color.White,
                    onClick = listener::onSkipPreviousTap
            )
            FaceTapTarget(
                    width = screen * 0.26f,
                    height = screen * 0.26f,
                    shape = RoundedCornerShape(28),
                    background = container,
                    label = stringResource(R.string.action_name_play_pause),
                    onClick = listener::onPlayPauseTap
            ) {
                Icon(
                        painter = painterResource(
                                if (state.playing) commonR.drawable.action_pause
                                else commonR.drawable.action_play
                        ),
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier.size(screen * 0.26f * 0.46f)
                )
            }
            SquareTransportButton(
                    iconRes = commonR.drawable.action_skip_next,
                    label = stringResource(R.string.action_name_skip_next),
                    sizeDp = screen * 0.20f,
                    background = Color.White.copy(alpha = 0.14f),
                    iconTint = Color.White,
                    onClick = listener::onSkipNextTap
            )
        }

        // Straight progress bar with the time readout under it. Animated the same way the
        // other faces smooth their 500ms position ticks.
        val animatedProgress = animateFloatAsState(
                targetValue = state.progress.coerceIn(0f, 1f),
                animationSpec = tween(600, easing = LinearEasing),
                label = "posterProgress"
        )
        Column(
                modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = screen * 0.235f),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.width(screen * 0.52f).size(width = screen * 0.52f, height = 4.dp)) {
                drawRoundRect(
                        color = Color.White.copy(alpha = 0.25f),
                        cornerRadius = CornerRadius(size.height / 2f)
                )
                val fill = size.width * animatedProgress.value
                if (fill > 1f) {
                    drawRoundRect(
                            color = Color(state.progressColor),
                            size = Size(fill, size.height),
                            cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
            }
            if (state.showTrackTime) {
                Text(
                        text = stringResource(
                                R.string.playback_time_format,
                                formatFaceClockTime(state.positionMs),
                                formatFaceClockTime(state.durationMs)
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = GoogleSansFamily,
                        modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Bottom trio as squared chips - poster's take on the shared positions.
        if (state.showDefaultBottomPills) {
            val chipWidth = screen * 0.235f
            val chipHeight = screen * 0.155f
            val chipShape = RoundedCornerShape(30)
            FaceTapTarget(
                    width = chipWidth, height = chipHeight, shape = chipShape,
                    background = Color.White.copy(alpha = 0.14f),
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
            FaceTapTarget(
                    width = chipWidth, height = chipHeight, shape = chipShape,
                    background = Color.White.copy(alpha = 0.14f),
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
            FaceTapTarget(
                    width = chipWidth, height = chipHeight, shape = chipShape,
                    background = Color.White.copy(alpha = 0.14f),
                    label = stringResource(R.string.action_name_open_menu),
                    onClick = listener::onOverflowTap,
                    modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(x = screen * 0.275f, y = -screen * 0.152f)
            ) {
                FaceOverflowDots()
            }
        }
    }
}

@Composable
private fun SquareTransportButton(
        iconRes: Int,
        label: String,
        sizeDp: Dp,
        background: Color,
        iconTint: Color,
        onClick: () -> Unit
) {
    FaceTapTarget(
            width = sizeDp,
            height = sizeDp,
            shape = RoundedCornerShape(28),
            background = background,
            label = label,
            onClick = onClick
    ) {
        Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(sizeDp * 0.46f)
        )
    }
}

/**
 * The poster face's AOD: black background (no fills - the tonal backdrop would light the whole
 * AMOLED panel for hours), the title/artist as dim text, a hairline progress bar frozen at the
 * last position, and the transport squares as hairline outlines at their interactive positions.
 */
@Composable
private fun PosterAmbientFace(state: NowPlayingFaceState) {
    val i = state.ambientIntensity.coerceIn(0.2f, 1f)
    val tint = Color(state.ambientTint)
    val stroke = tint.copy(alpha = 0.50f * i)
    val glyph = tint.copy(alpha = 0.75f * i)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth

        if (state.ambientShowTrackInfo) {
            Column(
                    modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = screen * 0.15f, start = 26.dp, end = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        text = state.title,
                        color = tint.copy(alpha = 0.85f * i),
                        fontSize = 18.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                )
                if (state.artist.isNotEmpty()) {
                    Text(
                            text = state.artist.uppercase(),
                            color = Color.White.copy(alpha = 0.55f * i),
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            fontFamily = GoogleSansFamily,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }

        if (state.ambientShowTransport) {
            Canvas(Modifier.align(Alignment.Center).fillMaxSize()) {
                val strokePx = 1.5.dp.toPx()
                val small = screen.toPx() * 0.20f
                val big = screen.toPx() * 0.26f
                val gap = screen.toPx() * 0.035f
                val corner = CornerRadius(big * 0.28f)

                fun outlineSquare(cx: Float, side: Float) {
                    drawRoundRect(
                            color = stroke,
                            topLeft = Offset(cx - side / 2f, center.y - side / 2f),
                            size = Size(side, side),
                            cornerRadius = corner,
                            style = Stroke(strokePx)
                    )
                }
                outlineSquare(center.x, big)
                outlineSquare(center.x - big / 2f - gap - small / 2f, small)
                outlineSquare(center.x + big / 2f + gap + small / 2f, small)
            }
            // Transport glyphs, dim, at their interactive positions.
            Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(screen * 0.035f)
            ) {
                Box(Modifier.size(screen * 0.20f), contentAlignment = Alignment.Center) {
                    Icon(painterResource(commonR.drawable.action_skip_prev), null,
                            tint = glyph, modifier = Modifier.size(screen * 0.20f * 0.46f))
                }
                Box(Modifier.size(screen * 0.26f), contentAlignment = Alignment.Center) {
                    Icon(painterResource(
                            if (state.playing) commonR.drawable.action_pause
                            else commonR.drawable.action_play), null,
                            tint = glyph, modifier = Modifier.size(screen * 0.26f * 0.46f))
                }
                Box(Modifier.size(screen * 0.20f), contentAlignment = Alignment.Center) {
                    Icon(painterResource(commonR.drawable.action_skip_next), null,
                            tint = glyph, modifier = Modifier.size(screen * 0.20f * 0.46f))
                }
            }
        }

        if (state.ambientShowProgress) {
            Canvas(
                    Modifier
                            .align(Alignment.Center)
                            .offset(y = screen * 0.235f)
                            .size(width = screen * 0.52f, height = 2.dp)
            ) {
                drawRoundRect(
                        color = stroke.copy(alpha = stroke.alpha * 0.4f),
                        cornerRadius = CornerRadius(size.height / 2f)
                )
                val fill = size.width * state.progress.coerceIn(0f, 1f)
                if (fill > 1f) {
                    drawRoundRect(
                            color = stroke,
                            size = Size(fill, size.height),
                            cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
            }
        }

        if (state.showDefaultBottomPills && state.ambientShowPills) {
            Canvas(Modifier.fillMaxSize()) {
                val strokePx = 1.5.dp.toPx()
                val chipWidth = screen.toPx() * 0.235f
                val chipHeight = screen.toPx() * 0.155f
                val corner = CornerRadius(chipHeight * 0.30f)
                val centers = listOf(
                        Offset(center.x - screen.toPx() * 0.275f,
                                size.height - screen.toPx() * 0.152f - chipHeight / 2f),
                        Offset(center.x,
                                size.height - screen.toPx() * 0.032f - chipHeight / 2f),
                        Offset(center.x + screen.toPx() * 0.275f,
                                size.height - screen.toPx() * 0.152f - chipHeight / 2f)
                )
                for (c in centers) {
                    drawRoundRect(
                            color = stroke,
                            topLeft = Offset(c.x - chipWidth / 2f, c.y - chipHeight / 2f),
                            size = Size(chipWidth, chipHeight),
                            cornerRadius = corner,
                            style = Stroke(strokePx)
                    )
                }
            }
        }
    }
}
