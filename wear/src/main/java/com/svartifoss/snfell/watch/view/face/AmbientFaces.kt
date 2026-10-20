package com.svartifoss.snfell.watch.view.face

import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.common.ProgressFx
import com.svartifoss.snfell.common.ProgressShape

/**
 * Two always-on faces that say something about the track rather than only showing the time.
 *
 * Both are standalone like Chrono - pure black, no artwork, outline-weight strokes, nothing that
 * animates - and both are built on the same two ideas the rest of the app now shares: a ring that
 * is cut into one piece per minute of the song, and the hexagon of the basalt columns at
 * Svartifoss. The ambient screen is redrawn about once a minute, which is exactly the resolution a
 * minute ring needs, so the pieces fill at the moment the panel wakes to draw them.
 */

/**
 * A hair-thin ring around the edge, divided into one piece per minute of the track and filled as it
 * plays, with the clock and the title inside it. The length of what is playing is readable from the
 * ring alone, which a smooth arc cannot say.
 */
@Composable
internal fun MinuteRingAmbientFace(state: NowPlayingFaceState) {
    val intensity = state.ambientIntensity.coerceIn(.2f, 1f)
    val tint = Color(state.ambientTint)
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val bounds = remember { RectF() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.2.dp.toPx()
            val inset = stroke / 2f + 6.dp.toPx()
            bounds.set(inset, inset, size.width - inset, size.height - inset)
            drawIntoCanvas { canvas ->
                ProgressFx.minutes(
                        canvas.nativeCanvas, bounds, -90f, 360f, state.progress, state.durationMs,
                        stroke,
                        ColorUtils.setAlphaComponent(
                                tint.toArgb(), (0xC8 * intensity).toInt()),
                        ColorUtils.setAlphaComponent(
                                tint.toArgb(), (0x38 * intensity).toInt()),
                        paint)
            }
        }
        // Read here, in the face, so the switch is visibly honoured by each one.
        AmbientTrackBlock(state, tint, intensity, screen * .66f, state.ambientShowTrackInfo)
    }
}

/**
 * The hexagon of a basalt column, drawn as a single outline with the clock and the title inside it.
 * The part of the outline behind the playback position is brighter than the rest, so the hexagon is
 * also the progress bar - starting at the top vertex and running clockwise.
 */
@Composable
internal fun HexFrameAmbientFace(state: NowPlayingFaceState) {
    val intensity = state.ambientIntensity.coerceIn(.2f, 1f)
    val tint = Color(state.ambientTint)
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
    val outline = remember { android.graphics.Path() }
    val played = remember { android.graphics.Path() }
    val vertex = remember { FloatArray(2) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .47f
            val cx = size.width / 2f
            val cy = size.height / 2f
            outline.reset()
            // Pointy-top, so the first vertex is straight up and the path runs clockwise from there.
            for (corner in 0..6) {
                ProgressShape.hexVertex(cx, cy, radius, -90f, corner % 6, vertex)
                if (corner == 0) outline.moveTo(vertex[0], vertex[1]) else outline.lineTo(vertex[0], vertex[1])
            }
            paint.strokeWidth = 1.6.dp.toPx()
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            drawIntoCanvas { canvas ->
                paint.color = ColorUtils.setAlphaComponent(
                        tint.toArgb(), (0x48 * intensity).toInt())
                canvas.nativeCanvas.drawPath(outline, paint)
                val progress = state.progress.coerceIn(0f, 1f)
                if (progress > 0.002f) {
                    val measure = PathMeasure(outline, false)
                    played.reset()
                    measure.getSegment(0f, measure.length * progress, played, true)
                    paint.color = ColorUtils.setAlphaComponent(
                            tint.toArgb(), (0xD0 * intensity).toInt())
                    paint.strokeWidth = 2.2.dp.toPx()
                    canvas.nativeCanvas.drawPath(played, paint)
                }
            }
        }
        AmbientTrackBlock(state, tint, intensity, screen * .6f, state.ambientShowTrackInfo)
    }
}

/**
 * The clock, title and artist, centred and kept to [width] so a long title ellipsizes inside the
 * shape instead of running out through it. Shared by both faces so they cannot drift apart.
 */
@Composable
private fun BoxScope.AmbientTrackBlock(
        state: NowPlayingFaceState,
        tint: Color,
        intensity: Float,
        width: Dp,
        showTrackInfo: Boolean
) {
    Column(
            modifier = Modifier
                    .align(state.blockPlacement(Alignment.Center))
                    .widthIn(max = width)
                    .padding(vertical = state.blockSafeVerticalInset(width)),
            horizontalAlignment = state.blockAlignment(Alignment.CenterHorizontally)
    ) {
        Text(
                text = state.clockText,
                color = tint.copy(alpha = .92f * intensity),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = state.clockFont,
                maxLines = 1
        )
        if (showTrackInfo && state.showTitle && state.title.isNotBlank()) {
            Text(
                    text = state.title,
                    color = tint.copy(alpha = .80f * intensity),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = state.titleFont,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (showTrackInfo && state.showArtist && state.artist.isNotBlank()) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = state.blockArrangement(Arrangement.Center),
                    modifier = Modifier.padding(top = 2.dp)
            ) {
                AmbientSourceIconGlyph(state, 10.dp, tint.copy(alpha = .55f * intensity))
                Text(
                        text = state.artist,
                        color = tint.copy(alpha = .55f * intensity),
                        fontSize = 10.sp,
                        fontFamily = state.artistFont,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
