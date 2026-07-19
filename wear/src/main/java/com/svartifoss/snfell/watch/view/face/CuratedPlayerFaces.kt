package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.common.PlayerShadingStyle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.view.compose.FaceClock
import com.svartifoss.snfell.common.R as commonR
import kotlin.math.abs
import kotlin.math.sin

/**
 * Curated, watch-first player collection. Every face follows the same interaction contract:
 * the single visual focus toggles playback, double tap opens quick actions, and long press opens
 * the queue. Previous/next/menu/volume are intentionally left to the three configurable mini
 * buttons and gestures, so the user owns that scarce space instead of each layout filling it.
 */
private enum class CuratedLayout { VINYL, POSTER, STUDIO, HALO, AURORA, ECLIPSE, SPECTRUM, MATERIAL }

@Composable fun VinylFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) =
        CuratedPlayerFace(state, listener, CuratedLayout.VINYL)

@Composable fun PosterFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) =
        CuratedPlayerFace(state, listener, CuratedLayout.POSTER)

@Composable fun StudioFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) =
        CuratedPlayerFace(state, listener, CuratedLayout.STUDIO)

@Composable fun HaloFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) =
        CuratedPlayerFace(state, listener, CuratedLayout.HALO)

@Composable fun AuroraFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) =
        CuratedPlayerFace(state, listener, CuratedLayout.AURORA)

@Composable fun EclipseFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) =
        CuratedPlayerFace(state, listener, CuratedLayout.ECLIPSE)

@Composable fun SpectrumFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) =
        CuratedPlayerFace(state, listener, CuratedLayout.SPECTRUM)

@Composable fun MaterialFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) =
        CuratedPlayerFace(state, listener, CuratedLayout.MATERIAL)

@Composable
private fun CuratedPlayerFace(
        state: NowPlayingFaceState,
        listener: NowPlayingFaceListener,
        layout: CuratedLayout
) {
    if (state.idle) return
    if (state.ambient) {
        CuratedAmbientFace(state, layout)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        val palette = remember(
                state.accentColor,
                state.secondaryAccentColor,
                state.tertiaryAccentColor
        ) {
            CuratedPalette.from(
                    state.accentColor,
                    state.secondaryAccentColor,
                    state.tertiaryAccentColor
            )
        }
        val animatedProgress = animateFloatAsState(
                targetValue = state.progress.coerceIn(0f, 1f),
                animationSpec = tween(600, easing = LinearEasing),
                label = "curatedProgress"
        )
        // Read the animation only in Canvas draw phases. This avoids recomposing album art,
        // typography and layout on every animation frame on the watch.
        val progress = remember(animatedProgress) {
            { animatedProgress.value.coerceIn(0f, 1f) }
        }

        CuratedBackdrop(layout, palette, state)
        // Poster owns a full-bleed cover inside its composition, so its shading must be inserted
        // there after the cover. Every other face can share this background-level placement.
        if (layout != CuratedLayout.POSTER) {
            PlayerShadingOverlay(state)
        }

        when (layout) {
            CuratedLayout.VINYL -> VinylComposition(state, listener, palette, progress, screen)
            CuratedLayout.POSTER -> PosterComposition(state, listener, palette, progress, screen)
            CuratedLayout.STUDIO -> StudioComposition(state, listener, palette, progress, screen)
            CuratedLayout.HALO -> HaloComposition(state, listener, palette, progress, screen)
            CuratedLayout.AURORA -> AuroraComposition(state, listener, palette, progress, screen)
            CuratedLayout.ECLIPSE -> EclipseComposition(state, listener, palette, progress, screen)
            CuratedLayout.SPECTRUM -> SpectrumComposition(state, listener, palette, progress, screen)
            CuratedLayout.MATERIAL -> MaterialComposition(state, listener, progress, screen)
        }

        FaceClock(visible = state.showClock)
    }
}

private data class CuratedPalette(
        val primary: Color,
        val secondary: Color,
        val tertiary: Color,
        val deep: Color,
        val surface: Color,
        val onLight: Color = Color(0xFF202124.toInt())
) {
    companion object {
        fun from(primary: Int, secondary: Int, tertiary: Int): CuratedPalette {
            val resolvedSecondary = if (secondary == primary) {
                // Missing swatches become lightness variants of the real album accent. Never
                // invent a complementary hue: that made a blue cover look red or a green one
                // purple when the extractor returned only one usable colour.
                tunedFaceColor(primary, .46f, .64f)
            } else {
                tunedFaceColor(secondary, .58f, .70f)
            }
            val resolvedTertiary = if (tertiary == primary || tertiary == resolvedSecondary) {
                tunedFaceColor(primary, .76f, .56f)
            } else {
                tunedFaceColor(tertiary, .62f, .72f)
            }
            return CuratedPalette(
                    primary = Color(tunedFaceColor(primary, .62f, .74f)),
                    secondary = Color(resolvedSecondary),
                    tertiary = Color(resolvedTertiary),
                    deep = Color(tunedFaceColor(primary, .075f, .48f)),
                    surface = Color(tunedFaceColor(resolvedSecondary, .16f, .42f))
            )
        }
    }
}

private fun tunedFaceColor(color: Int, lightness: Float, saturation: Float): Int =
        PaletteTransforms.tunedFaceColor(color, lightness, saturation)

/**
 * Scales a face's baked scrim alpha by the user's dim preference. The shipped defaults (dim on,
 * strength 80%) resolve to exactly 1f, so every curated face keeps its original look until the
 * user actually moves the slider; dim off resolves to the lightest treatment that still keeps
 * each face's text legible over bright art.
 */
internal fun curatedDimFactor(dimEnabled: Boolean, dimStrength: Float): Float =
        if (dimEnabled) (dimStrength.coerceIn(0f, 1f) / .8f).coerceAtMost(1.25f) else 0f

@Composable
private fun CuratedBackdrop(layout: CuratedLayout, p: CuratedPalette, state: NowPlayingFaceState) {
    // Explicit styles replace, rather than stack on top of, each face's baked black scrims.
    // Decorative album-colour glows remain part of the face identity.
    val dim = if (state.backdropShadingStyle == PlayerShadingStyle.FOLLOW) {
        curatedDimFactor(state.backdropDimEnabled, state.backdropDimStrength)
    } else {
        0f
    }
    fun scrim(base: Float): Float = (base * dim).coerceIn(0f, 1f)
    Canvas(Modifier.fillMaxSize()) {
        when (layout) {
            // Eclipse's identity is a true-black AMOLED canvas - it deliberately ignores dim.
            CuratedLayout.ECLIPSE -> drawRect(Color.Black)
            CuratedLayout.MATERIAL -> {
                drawRect(Color.Black)
                // The host supplies a raw or softened album surface according to the active color
                // options. Preserve that saturation (including true neutrals) while lifting the
                // backdrop out of the near-black range where blue and green covers disappeared.
                val tint = Color(PaletteTransforms.tonalSurface(
                        state.materialSurfaceColor,
                        lightness = if (state.materialSurfaceSoftened) 0.36f else 0.26f,
                        minSat = if (state.materialSurfaceSoftened) 0.0f else 0.30f,
                        maxSat = 0.80f
                ))
                drawCircle(
                        brush = Brush.radialGradient(
                                0.0f to tint.copy(alpha = 0.72f),
                                0.50f to tint.copy(alpha = 0.38f),
                                0.80f to tint.copy(alpha = 0.12f),
                                1.0f to Color.Transparent
                        ),
                        radius = size.minDimension * 0.85f,
                        center = center
                )
            }
            CuratedLayout.VINYL -> {
                drawRect(Color.Black.copy(alpha = scrim(.68f)))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to p.primary.copy(alpha = .32f),
                                .55f to p.deep.copy(alpha = .20f),
                                1f to Color.Transparent
                        ),
                        radius = size.minDimension * .69f,
                        center = Offset(size.width * .64f, size.height * .38f)
                )
            }
            CuratedLayout.POSTER -> drawRect(Color.Black.copy(alpha = scrim(.24f)))
            CuratedLayout.STUDIO -> {
                drawRect(Color.Black.copy(alpha = scrim(.48f)))
                drawRect(brush = Brush.linearGradient(
                        listOf(p.primary.copy(alpha = .44f), p.secondary.copy(alpha = .15f), Color.Transparent),
                        start = Offset(size.width, 0f), end = Offset(0f, size.height)))
            }
            CuratedLayout.HALO -> {
                drawRect(Color.Black.copy(alpha = scrim(.68f)))
                drawCircle(brush = Brush.radialGradient(
                        0f to p.primary.copy(alpha = .50f),
                        .48f to p.secondary.copy(alpha = .18f),
                        1f to Color.Transparent), radius = size.minDimension * .62f)
            }
            CuratedLayout.AURORA -> {
                // Aurora's canvas is black by identity, but the dim preference now lets the
                // host's album art glow through instead of being unconditionally painted over.
                drawRect(Color.Black.copy(alpha = scrim(1f)))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to p.primary.copy(alpha = .48f),
                                .42f to p.deep.copy(alpha = .30f),
                                1f to Color.Transparent
                        ),
                        radius = size.minDimension * .78f,
                        center = Offset(size.width * .18f, size.height * .14f)
                )
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to p.secondary.copy(alpha = .38f),
                                .48f to p.tertiary.copy(alpha = .18f),
                                1f to Color.Transparent
                        ),
                        radius = size.minDimension * .72f,
                        center = Offset(size.width * .88f, size.height * .72f)
                )
                val ribbons = listOf(
                        Triple(.30f, .52f, p.primary),
                        Triple(.43f, .63f, p.secondary),
                        Triple(.56f, .72f, p.tertiary)
                )
                ribbons.forEachIndexed { index, (startY, endY, color) ->
                    val ribbon = Path().apply {
                        moveTo(-size.width * .14f, size.height * startY)
                        cubicTo(
                                size.width * .18f,
                                size.height * (startY - .24f + index * .025f),
                                size.width * .58f,
                                size.height * (endY + .18f - index * .02f),
                                size.width * 1.14f,
                                size.height * endY
                        )
                    }
                    drawPath(
                            ribbon,
                            brush = Brush.linearGradient(listOf(color, p.primary, p.secondary)),
                            style = Stroke(
                                    size.minDimension * (.085f - index * .012f),
                                    cap = StrokeCap.Round
                            ),
                            alpha = .32f - index * .045f
                    )
                }
                drawRect(brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .06f),
                        .62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .78f)
                ))
            }
            CuratedLayout.SPECTRUM -> {
                drawRect(Color.Black.copy(alpha = scrim(.58f)))
                drawRect(brush = Brush.verticalGradient(
                        listOf(
                                p.surface.copy(alpha = scrim(.78f)),
                                p.deep.copy(alpha = scrim(.90f)),
                                Color.Black.copy(alpha = scrim(.88f))
                        )))
            }
        }
    }
}

@Composable
private fun BoxScope.VinylComposition(
        state: NowPlayingFaceState, listener: NowPlayingFaceListener, p: CuratedPalette,
        progress: () -> Float, screen: Dp
) {
    if (state.showTitle || state.showArtist) {
        VinylMetadata(state, screen)
    }
    val disc = screen * .46f
    InteractiveFocus(
            state, listener, CircleShape,
            modifier = Modifier.align(Alignment.Center).size(disc)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val edge = 3.dp.toPx()
            drawCircle(
                    brush = Brush.radialGradient(
                            0f to p.deep,
                            .34f to Color(0xFF18191C),
                            .74f to Color(0xFF08090B),
                            1f to Color.Black
                    )
            )
            repeat(11) { index ->
                drawCircle(
                        Color.White.copy(alpha = if (index % 3 == 0) .075f else .035f),
                        radius = size.minDimension * (.18f + index * .028f),
                        style = Stroke(if (index % 3 == 0) .75.dp.toPx() else .45.dp.toPx())
                )
            }
            drawArc(
                    Color.White.copy(alpha = .12f),
                    startAngle = 206f,
                    sweepAngle = 76f,
                    useCenter = false,
                    topLeft = Offset(size.width * .09f, size.height * .09f),
                    size = Size(size.width * .82f, size.height * .82f),
                    style = Stroke(size.minDimension * .075f, cap = StrokeCap.Round)
            )
            if (state.showInternalProgress) {
                drawArc(Color.White.copy(alpha = .16f), -90f, 360f, false,
                        topLeft = Offset(edge, edge), size = Size(size.width - edge * 2, size.height - edge * 2),
                        style = Stroke(edge, cap = StrokeCap.Round))
                drawArc(Color(state.progressColor), -90f, progress() * 360f, false,
                        topLeft = Offset(edge, edge), size = Size(size.width - edge * 2, size.height - edge * 2),
                        style = Stroke(edge, cap = StrokeCap.Round))
            }
        }
        Box(
                Modifier.align(Alignment.Center).size(disc * .38f).clip(CircleShape),
                contentAlignment = Alignment.Center
        ) {
            AlbumArtwork(state, CircleShape, p)
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.Black.copy(alpha = .26f))
                drawCircle(Color.White.copy(alpha = .22f), style = Stroke(.7.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = .72f), radius = size.minDimension * .07f)
            }
        }
        PlayPauseGlyph(state, Color.White, screen * .078f)
    }
    TrackFooter(state, y = screen * .30f, screen = screen, width = screen * .68f)
}

@Composable
private fun BoxScope.PosterComposition(
        state: NowPlayingFaceState, listener: NowPlayingFaceListener, p: CuratedPalette,
        progress: () -> Float, screen: Dp
) {
    InteractiveFocus(
            state = state,
            listener = listener,
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxSize(),
            animateHero = false
    ) {
        AlbumArtwork(state, RoundedCornerShape(0.dp), p)
        if (state.backdropShadingStyle == PlayerShadingStyle.FOLLOW) {
            val authored = curatedDimFactor(state.backdropDimEnabled, state.backdropDimStrength)
            Canvas(Modifier.fillMaxSize()) {
                drawRect(p.primary.copy(alpha = .12f * authored))
                drawRect(
                        brush = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = .48f * authored),
                                .36f to Color.Black.copy(alpha = .06f * authored),
                                .68f to Color.Black.copy(alpha = .25f * authored),
                                1f to Color.Black.copy(alpha = (.94f * authored).coerceAtMost(1f))
                        )
                )
                drawRect(
                        brush = Brush.horizontalGradient(
                                listOf(
                                        Color.Black.copy(alpha = .36f * authored),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = .36f * authored))
                        )
                )
            }
        }
        PlayerShadingOverlay(state)
        if (state.showTitle || state.showArtist) PosterMetadata(state, screen)
    }
    val posterProgressShown = state.showDefaultBottomPills && state.showInternalProgress
    if (posterProgressShown) {
        LinearProgress(progress, Modifier.offset(y = screen * .32f)
                .width(screen * .54f), Color(state.progressColor))
    }
    // With no progress line above it the time sits on the floor of the screen instead of
    // floating in the empty band the line used to occupy.
    TrackFooter(state, y = screen * (if (posterProgressShown) .38f else .42f),
            screen = screen, width = screen * .72f,
            fontSize = 8.sp, color = Color.White.copy(alpha = .76f),
            letterSpacing = 1.55.sp, fontFamily = state.artistFont)
}

@Composable
private fun BoxScope.StudioComposition(
        state: NowPlayingFaceState, listener: NowPlayingFaceListener, p: CuratedPalette,
        progress: () -> Float, screen: Dp
) {
    if (state.showTitle || state.showArtist) {
        StudioMetadata(state, screen, p)
    }
    val orbR = screen * .16f
    InteractiveFocus(state, listener, RoundedCornerShape(0.dp),
            Modifier.fillMaxSize()) {
        if (state.showInternalProgress) {
            Canvas(Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = screen * .04f)
                    .size(orbR * 2)) {
                val stroke = 4.dp.toPx()
                drawArc(Color.White.copy(alpha = .24f), -90f, 360f, false,
                        topLeft = Offset(stroke, stroke), size = Size(size.width - stroke * 2, size.height - stroke * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(Color(state.progressColor), -90f, progress() * 360f, false,
                        topLeft = Offset(stroke, stroke), size = Size(size.width - stroke * 2, size.height - stroke * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
    // Inside the bottom progress orb when it's drawn; anchored to the floor when it isn't,
    // instead of floating in the space the absent orb used to occupy.
    TrackFooter(state, y = screen * (if (state.showInternalProgress) .30f else .42f),
            screen = screen, width = screen * .72f,
            fontSize = 8.sp, color = Color.White.copy(alpha = .62f),
            letterSpacing = 1.2.sp, fontFamily = state.artistFont)
}

@Composable
private fun BoxScope.HaloComposition(
        state: NowPlayingFaceState, listener: NowPlayingFaceListener, p: CuratedPalette,
        progress: () -> Float, screen: Dp
) {
    if (state.showTitle || state.showArtist) {
        HaloMetadata(state, screen)
    }
    val halo = screen * .43f
    val hasMiniButtons = !state.showDefaultBottomPills
    val haloFieldScale = if (hasMiniButtons) 1.15f else 1.42f
    Box(Modifier.align(Alignment.Center).size(halo * haloFieldScale),
            contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(3) { index ->
                drawCircle(
                        color = listOf(p.primary, p.secondary, p.tertiary)[index].copy(alpha = .20f - index * .035f),
                        radius = size.minDimension * (.26f + index * .09f),
                        style = Stroke((7 - index * 1.5f).dp.toPx())
                )
            }
        }
        InteractiveFocus(state, listener, CircleShape, Modifier.size(halo)) {
            AlbumArtwork(state, CircleShape, p)
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                if (state.showInternalProgress) {
                    drawArc(Color.White.copy(alpha = .18f), -90f, 360f, false,
                            topLeft = Offset(stroke, stroke), size = Size(size.width - stroke * 2, size.height - stroke * 2),
                            style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(Color(state.progressColor), -90f, progress() * 360f, false,
                            topLeft = Offset(stroke, stroke), size = Size(size.width - stroke * 2, size.height - stroke * 2),
                            style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            if (state.showControls) {
                PlayPauseGlyph(state, Color.White, halo * .24f)
            }
        }
    }
    TrackFooter(state, y = screen * .30f, screen = screen, width = screen * .68f)
}

@Composable
private fun BoxScope.AuroraComposition(
        state: NowPlayingFaceState, listener: NowPlayingFaceListener, p: CuratedPalette,
        progress: () -> Float, screen: Dp
) {
    val hasMetadata = state.showTitle || state.showArtist
    val hasMiniButtons = !state.showDefaultBottomPills
    val cardWidth = screen * .72f
    val cardHeight = screen * if (hasMetadata) .50f else .34f
    val cardShape = RoundedCornerShape(screen * .075f)
    Canvas(
            Modifier.align(Alignment.Center)
                    .size(cardWidth, cardHeight)
                    .clip(cardShape)
    ) {
        drawRoundRect(
                brush = Brush.linearGradient(
                        listOf(
                                p.deep.copy(alpha = .96f),
                                p.primary.copy(alpha = .52f),
                                p.secondary.copy(alpha = .34f),
                                p.surface.copy(alpha = .96f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                ),
                cornerRadius = CornerRadius(size.minDimension * .16f)
        )
        drawCircle(
                brush = Brush.radialGradient(
                        0f to p.tertiary.copy(alpha = .56f),
                        .42f to p.primary.copy(alpha = .15f),
                        1f to Color.Transparent
                ),
                radius = size.minDimension * .82f,
                center = Offset(size.width * .12f, size.height * .05f)
        )
        val flare = Path().apply {
            moveTo(-size.width * .12f, size.height * .78f)
            cubicTo(
                    size.width * .28f, size.height * .30f,
                    size.width * .64f, size.height * 1.08f,
                    size.width * 1.10f, size.height * .36f
            )
        }
        drawPath(
                flare,
                brush = Brush.linearGradient(listOf(p.tertiary, p.secondary, p.primary)),
                style = Stroke(size.minDimension * .12f, cap = StrokeCap.Round),
                alpha = .26f
        )
        drawRoundRect(
                color = Color.White.copy(alpha = .24f),
                cornerRadius = CornerRadius(size.minDimension * .16f),
                style = Stroke(.8.dp.toPx())
        )
    }
    if (hasMetadata) {
        Column(
                Modifier.align(Alignment.Center)
                        .offset(
                                x = cardWidth * -.06f,
                                y = cardHeight * -.08f + metadataSingleLineOffset(state)
                        )
                        .width(cardWidth * .70f),
                horizontalAlignment = Alignment.Start
        ) {
            if (state.showArtist) {
                Text(
                        artistOrStatus(state).uppercase(),
                        color = Color.White.copy(alpha = .70f),
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        letterSpacing = 1.8.sp,
                        fontFamily = state.artistFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
            if (state.showTitle) {
                AdaptiveTitleText(
                        state.title,
                        mode = state.titleTextMode,
                        color = Color.White,
                        fontSize = 19.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = state.titleFont,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(top = if (state.showArtist) 3.dp else 0.dp)
                )
            }
        }
    }
    val button = screen * if (hasMiniButtons) .19f else .22f
    val buttonOffsetX = if (hasMetadata) cardWidth * .29f else 0.dp
    val buttonOffsetY = if (hasMetadata) cardHeight * .30f else 0.dp
    InteractiveFocus(
            state,
            listener,
            CircleShape,
            Modifier.align(Alignment.Center)
                    .offset(x = buttonOffsetX, y = buttonOffsetY)
                    .size(button)
    ) {
        if (state.showControls) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.Black.copy(alpha = .48f))
                drawCircle(
                        brush = Brush.sweepGradient(
                                listOf(p.primary, p.tertiary, p.secondary, p.primary)
                        ),
                        style = Stroke(2.dp.toPx())
                )
                drawCircle(Color.White.copy(alpha = .10f), radius = size.minDimension * .38f)
            }
            PlayPauseGlyph(state, Color.White, button * .34f)
        }
    }
    if (state.showInternalProgress) {
        val progressOffsetX = if (hasMetadata) cardWidth * -.15f else 0.dp
        val progressOffsetY = if (hasMetadata) cardHeight * .31f else cardHeight * .26f
        LinearProgress(
                progress,
                Modifier.offset(x = progressOffsetX, y = progressOffsetY)
                        .width(if (hasMetadata) cardWidth * .43f else cardWidth * .56f),
                Color(state.progressColor)
        )
    }
    TrackFooter(state, y = screen * .39f, screen = screen, width = screen * .60f)
}

@Composable
private fun BoxScope.EclipseComposition(
        state: NowPlayingFaceState, listener: NowPlayingFaceListener, p: CuratedPalette,
        progress: () -> Float, screen: Dp
) {
    if (state.showInternalProgress) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = size.minDimension * .075f
            drawArc(p.primary.copy(alpha = .20f), 145f, 250f, false,
                    topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color(state.progressColor), 145f, progress() * 250f, false,
                    topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
    if (state.showTitle || state.showArtist) {
        EclipseMetadata(state, screen)
    }
    val core = screen * .29f
    InteractiveFocus(state, listener, CircleShape,
            Modifier.align(Alignment.Center).size(core)) {
        if (state.showControls) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.Black)
                drawCircle(
                        brush = Brush.sweepGradient(
                                listOf(p.primary, p.secondary, p.tertiary, p.primary)
                        ),
                        style = Stroke(2.dp.toPx())
                )
                drawCircle(p.surface.copy(alpha = .42f), radius = size.minDimension * .36f)
            }
            PlayPauseGlyph(state, Color.White, core * .35f)
        }
    }
    TrackFooter(state, y = screen * .31f, screen = screen, width = screen * .61f)
}

@Composable
private fun BoxScope.SpectrumComposition(
        state: NowPlayingFaceState, listener: NowPlayingFaceListener, p: CuratedPalette,
        progress: () -> Float, screen: Dp
) {
    if (state.showTitle || state.showArtist) {
        SpectrumHeader(state, screen, showArtist = true)
    }
    val groupWidth = screen * .80f
    val playSize = 48.dp
    val groupGap = 8.dp
    val field = maxOf(72.dp, groupWidth - playSize - groupGap)
    val fieldHeight = screen * .34f
    val groupY = if (state.showTitle || state.showArtist) screen * .05f else 0.dp
    val playX = -groupWidth / 2f + playSize / 2f
    val fieldX = groupWidth / 2f - field / 2f
    val trackSeed = remember(state.title, state.artist, state.durationMs) {
        spectrumTrackSeed(state.title, state.artist, state.durationMs)
    }
    // This state is intentionally consumed only by the Canvas draw block. Frame updates
    // invalidate that draw layer without recomposing artwork, text or the interaction tree.
    val animationPhase = remember(trackSeed) { mutableFloatStateOf(0f) }
    LaunchedEffect(state.playing, trackSeed) {
        if (!state.playing) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            previousFrame = withFrameNanos { frameTime ->
                val elapsedSeconds = ((frameTime - previousFrame).coerceAtLeast(0L) / 1_000_000_000f)
                        .coerceAtMost(.05f)
                animationPhase.floatValue = (animationPhase.floatValue + elapsedSeconds) % 120f
                frameTime
            }
        }
    }

    InteractiveFocus(
            state, listener, CircleShape,
            Modifier.align(Alignment.Center)
                    .offset(x = playX, y = groupY)
                    .size(playSize),
            captureSwipes = true
    ) {
        if (state.showControls) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(p.surface.copy(alpha = .28f))
                drawCircle(Color.White.copy(alpha = .30f), style = Stroke(1.dp.toPx()))
            }
            PlayPauseGlyph(state, Color.White, 19.dp)
        }
    }

    InteractiveFocus(state, listener, RoundedCornerShape(30.dp),
            Modifier.align(Alignment.Center)
                    .offset(x = fieldX, y = groupY)
                    .size(field, fieldHeight),
            animateHero = false,
            onSurfaceTap = if (state.seekable && state.durationMs > 0L) {
                { fraction -> listener.onSeek(fraction) }
            } else null,
            surfaceProgress = state.progress.coerceIn(0f, 1f),
            captureSwipes = state.seekable && state.durationMs > 0L,
            showCoreInteraction = false) {
        Canvas(Modifier.fillMaxSize()) {
            val bars = 15
            val barWidth = size.width / (bars * 1.72f)
            val gap = (size.width - bars * barWidth) / (bars - 1)
            val activeColor = Color(state.progressColor)
            // Do not observe the progress animation when its visual encoding is disabled. Seek
            // semantics still receive state.progress above, while this draw layer only animates
            // the neutral audio field.
            val fraction = if (state.showInternalProgress) progress() else -1f
            val phaseSeconds = animationPhase.floatValue
            repeat(bars) { index ->
                val barProgress = index / (bars - 1f)
                val wave = spectrumBarHeight(trackSeed, index, phaseSeconds)
                val height = size.height * wave
                val topLeft = Offset(index * (barWidth + gap), (size.height - height) / 2f)
                if (state.showInternalProgress && barProgress <= fraction) {
                    drawRoundRect(
                            color = activeColor,
                            topLeft = topLeft,
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(barWidth / 2f),
                            alpha = .92f
                    )
                } else {
                    drawRoundRect(
                            color = Color.White.copy(alpha = .16f),
                            topLeft = topLeft,
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(barWidth / 2f)
                    )
                }
            }
        }
    }
    TrackFooter(state, y = screen * .31f, screen = screen, width = screen * .64f)
}

@Composable
private fun BoxScope.SpectrumHeader(
        state: NowPlayingFaceState,
        screen: Dp,
        showArtist: Boolean
) {
    Column(
            Modifier.align(Alignment.TopCenter)
                    .padding(top = screen * .15f + metadataSingleLineOffset(state))
                    .width(screen * .68f),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.showTitle) {
            AdaptiveTitleText(state.title.uppercase(), mode = state.titleTextMode,
                    color = Color.White.copy(alpha = .92f), fontSize = 12.sp, lineHeight = 14.sp,
                    letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold,
                    fontFamily = state.titleFont, textAlign = TextAlign.Center)
        }
        if (showArtist && state.showArtist) {
            Text(artistOrStatus(state), color = Color(state.artistColor).copy(alpha = .78f),
                    fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = .35.sp, fontFamily = state.artistFont,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun BoxScope.VinylMetadata(state: NowPlayingFaceState, screen: Dp) {
    Column(
            Modifier.align(Alignment.TopCenter)
                    .padding(top = screen * .14f + metadataSingleLineOffset(state))
                    .width(screen * .62f),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.showArtist) {
            Text(
                    artistOrStatus(state).uppercase(),
                    color = Color.White.copy(alpha = .58f),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    letterSpacing = 2.sp,
                    fontFamily = state.artistFont,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
        if (state.showTitle) {
            AdaptiveTitleText(
                    state.title.uppercase(),
                    mode = state.titleTextMode,
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    letterSpacing = 1.15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = state.titleFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = if (state.showArtist) 2.dp else 0.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.PosterMetadata(state: NowPlayingFaceState, screen: Dp) {
    Column(
            Modifier.align(Alignment.Center)
                    .padding(horizontal = screen * .08f)
                    .width(screen * .72f),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.showTitle) {
            // Always reserve exactly two lines (instead of the user's adaptive text mode): the
            // Poster/Studio compositions center this block, so a title jumping between one and
            // two lines shifted everything below it on every track change.
            Text(
                    state.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = state.titleFont,
                    textAlign = TextAlign.Center,
                    minLines = 1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
            )
        }
        if (state.showArtist) {
            Text(
                    artistOrStatus(state).uppercase(),
                    color = artistOrStatusColor(state, .76f),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    letterSpacing = 1.55.sp,
                    fontFamily = state.artistFont,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (state.showTitle) 6.dp else 0.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.StudioMetadata(state: NowPlayingFaceState, screen: Dp, p: CuratedPalette) {
    Column(
            Modifier.align(Alignment.Center)
                    .padding(horizontal = screen * .08f)
                    .width(screen * .72f),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.showTitle) {
            // Fixed two lines for the same layout-stability reason as PosterMetadata above.
            Text(
                    state.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = state.titleFont,
                    textAlign = TextAlign.Center,
                    minLines = 1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
            )
        }
        if (state.showArtist) {
            Text(
                    artistOrStatus(state).uppercase(),
                    color = artistOrStatusColor(state, .62f),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    letterSpacing = 1.2.sp,
                    fontFamily = state.artistFont,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (state.showTitle) 5.dp else 0.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.HaloMetadata(state: NowPlayingFaceState, screen: Dp) {
    Column(
            Modifier.align(Alignment.TopCenter)
                    .padding(top = screen * .145f + metadataSingleLineOffset(state))
                    .width(screen * .66f),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.showTitle) {
            AdaptiveTitleText(
                    state.title,
                    mode = state.titleTextMode,
                    color = Color.White.copy(alpha = .94f),
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    letterSpacing = .75.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = state.titleFont,
                    textAlign = TextAlign.Center
            )
        }
        if (state.showArtist) {
            Text(
                    artistOrStatus(state),
                    color = Color.White.copy(alpha = .70f),
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    letterSpacing = .55.sp,
                    fontFamily = state.artistFont,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (state.showTitle) 3.dp else 0.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = .09f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.EclipseMetadata(state: NowPlayingFaceState, screen: Dp) {
    Column(
            Modifier.align(Alignment.TopCenter)
                    .padding(top = screen * .15f + metadataSingleLineOffset(state))
                    .width(screen * .64f),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.showTitle) {
            AdaptiveTitleText(
                    state.title.uppercase(),
                    mode = state.titleTextMode,
                    color = Color.White.copy(alpha = .88f),
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = state.titleFont,
                    textAlign = TextAlign.Center
            )
        }
        if (state.showArtist) {
            Text(
                    artistOrStatus(state).uppercase(),
                    color = Color.White.copy(alpha = .48f),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    letterSpacing = 2.4.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = state.artistFont,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (state.showTitle) 3.dp else 0.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.TrackFooter(
        state: NowPlayingFaceState,
        y: Dp,
        screen: Dp,
        width: Dp,
        fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
        color: Color = Color.White,
        letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
        fontFamily: androidx.compose.ui.text.font.FontFamily = GoogleSansFamily
) {
    // Matches Classic's track-time line exactly (text_playback_time in activity_main.xml):
    // fully opaque white at 13sp, visible whenever there's a position to show - Classic never
    // hides its time line just because mini buttons are configured, so this doesn't either.
    if (!state.showTrackTime) return
    // Same defensive clearance as ExpressiveFace's track time: clamp above wherever the
    // mini-button row actually settled (see NowPlayingFaceState.miniButtonsTopFraction) instead
    // of the row occupying a fixed lower band this text used to just avoid by disappearing.
    // The clearance term goes negative when the row settles high (fraction near its .20f floor);
    // coerceAtLeast keeps this from yanking the time up over the face's central focus. The floor
    // is the caller's desired y, but never above screen*0.16f, so it always stays below center.
    val miniRowClearance =
            screen * state.miniButtonsTopFraction.coerceIn(.20f, .95f) - screen * .50f - 10.dp
    val clampedY = minOf(y, miniRowClearance).coerceAtLeast(minOf(y, screen * 0.16f))
    Text(
            text = stringResource(R.string.playback_time_format,
                    formatFaceClockTime(state.positionMs), formatFaceClockTime(state.durationMs)),
            color = color, fontSize = fontSize, lineHeight = fontSize, fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            modifier = Modifier.align(Alignment.Center).offset(y = clampedY).width(width),
            textAlign = TextAlign.Center, maxLines = 1
    )
}

@Composable
private fun BoxScope.LinearProgress(
        progress: () -> Float, modifier: Modifier, activeColor: Color
) {
    Canvas(Modifier.align(Alignment.Center).then(modifier).height(4.dp)) {
        drawRoundRect(Color.White.copy(alpha = .17f), cornerRadius = CornerRadius(size.height / 2f))
        val fraction = progress()
        if (fraction > 0f) {
            drawRoundRect(color = activeColor,
                    size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2f))
        }
    }
}

@Composable
private fun InteractiveFocus(
        state: NowPlayingFaceState,
        listener: NowPlayingFaceListener,
        shape: Shape,
        modifier: Modifier,
        touchOffsetY: Dp = 0.dp,
        animateHero: Boolean = true,
        onSurfaceTap: ((Float) -> Unit)? = null,
        surfaceProgress: Float? = null,
        captureSwipes: Boolean = false,
        showCoreInteraction: Boolean = true,
        content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .95f else 1f, label = "curatedPress")
    // Expanding-ring flash mirroring Classic's center_tap_pulse: fires the moment a tap lands
    // on the core so every curated face confirms the touch, not just the .95 press squeeze.
    var pulseNonce by remember { mutableStateOf(0) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(pulseNonce) {
        if (pulseNonce > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(300))
        }
    }
    val actionDescription = stringResource(
            if (state.playing) R.string.action_name_pause else R.string.action_name_play
    )
    val seekDescription = stringResource(R.string.action_name_seek)
    val swipeModifier = if (captureSwipes) {
        Modifier.pointerInput(listener) {
            var distance = Offset.Zero
            val threshold = 32.dp.toPx()
            detectDragGestures(
                    onDragStart = { distance = Offset.Zero },
                    onDrag = { change, amount ->
                        distance += amount
                        change.consume()
                    },
                    onDragEnd = {
                        if (abs(distance.x) > abs(distance.y)) {
                            if (distance.x < -threshold) listener.onSwipeLeft()
                        } else if (abs(distance.y) > threshold) {
                            if (distance.y < 0f) listener.onSwipeUp() else listener.onSwipeDown()
                        }
                    },
                    onDragCancel = { distance = Offset.Zero }
            )
        }
    } else {
        Modifier
    }
    Box(
            modifier = modifier.then(swipeModifier),
            contentAlignment = Alignment.Center
    ) {
        Box(
                modifier = Modifier.fillMaxSize()
                        .graphicsLayer {
                            if (animateHero) {
                                scaleX = scale
                                scaleY = scale
                            }
                        }
                        .clip(shape),
                contentAlignment = Alignment.Center,
                content = content
        )
        if (onSurfaceTap != null) {
            Box(
                    Modifier.fillMaxSize()
                            .pointerInput(listener, onSurfaceTap) {
                                detectTapGestures(onTap = { point ->
                                    if (size.width > 0) {
                                        onSurfaceTap(spectrumSeekFraction(point.x, size.width.toFloat()))
                                    }
                                })
                            }
                            .semantics {
                                contentDescription = seekDescription
                                progressBarRangeInfo = ProgressBarRangeInfo(
                                        surfaceProgress?.coerceIn(0f, 1f) ?: 0f,
                                        0f..1f
                                )
                                setProgress { target ->
                                    onSurfaceTap(target.coerceIn(0f, 1f))
                                    true
                                }
                            }
            )
        }
        // Only this accessible 52dp core captures input. The surrounding cover/gradient remains
        // visual, so swipes that begin outside the central control still reach the host gestures.
        if (showCoreInteraction) Box(
                Modifier.align(Alignment.Center)
                        .offset(y = touchOffsetY)
                        .size(52.dp)
                        .pointerInput(listener) {
                        detectTapGestures(
                                onPress = {
                                    pressed = true
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        pressed = false
                                    }
                                },
                                onTap = {
                                    pulseNonce++
                                    listener.onPlayPauseTap()
                                },
                                onDoubleTap = {
                                    pulseNonce++
                                    listener.onCenterDoubleTap()
                                },
                                onLongPress = {
                                    pulseNonce++
                                    listener.onCenterLongPress()
                                }
                        )
                        }
                        .drawBehind {
                            val fraction = pulse.value
                            if (fraction < 1f) {
                                drawCircle(
                                        color = Color.White.copy(alpha = .9f * (1f - fraction)),
                                        radius = size.minDimension / 2f * (.6f + .65f * fraction),
                                        style = Stroke(2.dp.toPx())
                                )
                            }
                        }
                        .semantics(mergeDescendants = true) {
                            contentDescription = actionDescription
                            role = Role.Button
                            onClick(actionDescription) {
                                listener.onPlayPauseTap()
                                true
                            }
                        }
        )
    }
}

/** Renders the cover honoring MiscPreferences.ALBUM_ART_STYLE (mirrors what the classic host
 *  ImageView already applies to itself - see MainActivity.applyMainAlbumArtDisplay). "hidden"
 *  falls back to the same palette gradient already used before any art has arrived; "bw" and
 *  "blur"/"blur_bw" desaturate and/or blur the cover in place. */
@Composable
private fun AlbumArtwork(state: NowPlayingFaceState, shape: Shape, p: CuratedPalette) {
    Box(Modifier.fillMaxSize().clip(shape), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val grayscaleFilter = remember(state.albumArtGrayscale) {
            if (state.albumArtGrayscale) {
                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            } else {
                null
            }
        }
        val blurModifier = if (state.albumArtBlurred) {
            Modifier.blur(with(density) { state.albumArtBlurRadiusPx.toDp() })
        } else {
            Modifier
        }
        // Same crossfade the classic host ImageView applies (fadeToAlbumArt): track changes on
        // faces that draw the cover themselves used to swap instantly, making WEAR_ALBUM_ART_FADE
        // appear broken on Poster in particular.
        Crossfade(
                targetState = state.albumArt.takeUnless { state.albumArtHidden },
                animationSpec = tween(if (state.albumArtFade) 300 else 0),
                label = "curatedArtFade"
        ) { art ->
            if (art != null) {
                Image(BitmapPainter(art), null, contentScale = ContentScale.Crop,
                        colorFilter = grayscaleFilter,
                        modifier = Modifier.fillMaxSize().then(blurModifier))
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(brush = Brush.linearGradient(listOf(p.primary, p.secondary, p.tertiary)))
                }
            }
        }
    }
}

@Composable
private fun PlayPauseGlyph(state: NowPlayingFaceState, tint: Color, size: Dp) {
    if (!state.showControls) return
    Icon(
            painter = painterResource(
                    if (state.playing) commonR.drawable.action_pause_filled
                    else commonR.drawable.action_play_filled
            ),
            contentDescription = null,
            tint = tint.copy(alpha = state.screenTheme.tokens.iconAlpha),
            modifier = Modifier.size(size * state.screenTheme.tokens.iconScale)
    )
}

@Composable
private fun artistOrStatus(state: NowPlayingFaceState): String =
        if (state.playing) state.artist else stringResource(R.string.playback_stopped)

/** Album tint belongs to real metadata; operational stopped/status copy remains neutral white. */
private fun artistOrStatusColor(state: NowPlayingFaceState, alpha: Float): Color =
        (if (state.playing) Color(state.artistColor) else Color.White).copy(alpha = alpha)

/** Keeps either remaining metadata line centred in the vertical slot previously shared by two
 * lines. With both lines hidden, the face-specific hero moves to the geometric centre instead. */
private fun metadataSingleLineOffset(state: NowPlayingFaceState): Dp =
        if (state.showTitle.xor(state.showArtist)) 5.dp else 0.dp

/** Pure mapping shared with unit tests; clamping also protects accessibility/synthetic pointer
 * events that can report a coordinate just outside the laid-out timeline. */
internal fun spectrumSeekFraction(x: Float, width: Float): Float =
        if (width <= 0f) 0f else (x / width).coerceIn(0f, 1f)

/** Stable per-track seed: bars change with the media item, but never flicker because of a
 * recomposition or a progress update. */
internal fun spectrumTrackSeed(title: String, artist: String, durationMs: Long): Int {
    var result = 17
    result = 31 * result + title.hashCode()
    result = 31 * result + artist.hashCode()
    result = 31 * result + (durationMs xor (durationMs ushr 32)).toInt()
    return result
}

/** Pseudo-random bar envelope. [phaseSeconds] animates smoothly while playback is active; the
 * three independent hashes give every bar its own resting height, phase and speed. */
internal fun spectrumBarHeight(seed: Int, index: Int, phaseSeconds: Float): Float {
    val rest = spectrumUnitHash(seed, index, 0x2C1B3C6D)
    val offset = spectrumUnitHash(seed, index, 0x6A09E667) * (Math.PI * 2.0).toFloat()
    val speed = .75f + spectrumUnitHash(seed, index, 0x3C6EF372) * 1.35f
    val pulse = .5f + .5f * sin((offset + phaseSeconds * speed).toDouble()).toFloat()
    return (.20f + rest * .20f + pulse * (.42f + (1f - rest) * .16f)).coerceIn(.20f, .94f)
}

private fun spectrumUnitHash(seed: Int, index: Int, salt: Int): Float {
    var value = seed xor salt xor (index * 0x45D9F3B)
    value = (value xor (value ushr 16)) * 0x45D9F3B
    value = (value xor (value ushr 16)) * 0x45D9F3B
    value = value xor (value ushr 16)
    return (value ushr 8) / 16_777_215f
}

/** Static, burn-in-conscious AOD counterpart shared by the curated faces. */
@Composable
private fun CuratedAmbientFace(state: NowPlayingFaceState, layout: CuratedLayout) {
    val intensity = state.ambientIntensity.coerceIn(.2f, 1f)
    val tint = Color(state.ambientTint)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            // Non-AMOLED layouts leave the host's optional ambient artwork visible. Eclipse is
            // deliberately the exception: its identity is a true-black, low-emission canvas.
            if (layout == CuratedLayout.ECLIPSE) drawRect(Color.Black)
            if (layout != CuratedLayout.MATERIAL) {
                if (state.ambientShowTransport && state.ambientShowProgress) {
                    val stroke = 1.5.dp.toPx()
                    val inset = size.minDimension * .16f
                    drawAmbientCircularProgress(
                            progress = state.progress,
                            styleKey = state.progressRingStyle,
                            trackColor = tint.copy(alpha = .18f * intensity),
                            playedColor = tint.copy(alpha = .68f * intensity),
                            baseWidth = stroke,
                            startAngle = 130f,
                            totalSweep = 280f,
                            topLeft = Offset(inset, inset),
                            arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    )
                }
                if (state.ambientShowTransport &&
                        layout !in setOf(
                                CuratedLayout.ECLIPSE,
                                CuratedLayout.POSTER,
                                CuratedLayout.STUDIO
                        )) {
                    drawCircle(tint.copy(alpha = .42f * intensity), radius = size.minDimension * .12f,
                            style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        if (state.ambientShowTrackInfo && (state.showTitle || state.showArtist)) {
            val metadataModifier = if (
                layout == CuratedLayout.POSTER || layout == CuratedLayout.STUDIO
            ) {
                Modifier.align(Alignment.Center).width(screen * .72f)
            } else {
                Modifier.align(Alignment.TopCenter).padding(top = screen * .20f).width(screen * .62f)
            }
            Column(metadataModifier,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.showTitle) {
                    Text(state.title, color = tint.copy(alpha = .82f * intensity),
                            fontSize = if (layout == CuratedLayout.POSTER) 17.sp else 15.sp,
                            fontWeight = FontWeight.Bold, fontFamily = state.titleFont,
                            textAlign = TextAlign.Center,
                            maxLines = if (layout == CuratedLayout.POSTER ||
                                    layout == CuratedLayout.STUDIO) 2 else 1,
                            overflow = TextOverflow.Ellipsis)
                }
                if (state.showArtist) {
                    Text(state.artist, color = tint.copy(alpha = .48f * intensity), fontSize = 10.sp,
                            fontFamily = state.artistFont, textAlign = TextAlign.Center,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = if (state.showTitle) 4.dp else 0.dp))
                }
            }
        }
        if (state.ambientShowTransport) {
            if (layout == CuratedLayout.MATERIAL) {
                Row(
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.leftActionIcon != null) {
                        Icon(
                                bitmap = state.leftActionIcon,
                                contentDescription = null,
                                // AOD deliberately flattens even artwork to the configured
                                // low-emission tint; awake mode preserves full-colour artwork.
                                tint = tint.copy(alpha = .5f * intensity),
                                modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Icon(
                                painter = painterResource(commonR.drawable.action_skip_prev),
                                contentDescription = null,
                                tint = tint.copy(alpha = .5f * intensity),
                                modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Box(
                            modifier = Modifier
                                    .size(screen * 0.30f),
                            contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 2.dp.toPx()
                            val inset = stroke / 2f
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            if (state.ambientShowProgress) {
                                drawAmbientCircularProgress(
                                        progress = state.progress,
                                        styleKey = state.progressRingStyle,
                                        trackColor = tint.copy(alpha = .4f * intensity),
                                        playedColor = tint.copy(alpha = .72f * intensity),
                                        baseWidth = stroke,
                                        startAngle = -90f,
                                        totalSweep = 360f,
                                        topLeft = Offset(inset, inset),
                                        arcSize = arcSize
                                )
                            } else {
                                drawArc(
                                        tint.copy(alpha = .4f * intensity),
                                        -90f,
                                        360f,
                                        false,
                                        Offset(inset, inset),
                                        arcSize,
                                        style = Stroke(stroke)
                                )
                            }
                        }
                        Icon(
                                painter = painterResource(if (state.playing) commonR.drawable.action_pause_filled else commonR.drawable.action_play_filled),
                                contentDescription = null,
                                tint = tint.copy(alpha = .7f * intensity),
                                modifier = Modifier.size(if (state.playing) 24.dp else 28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    if (state.rightActionIcon != null) {
                        Icon(
                                bitmap = state.rightActionIcon,
                                contentDescription = null,
                                tint = tint.copy(alpha = .5f * intensity),
                                modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Icon(
                                painter = painterResource(commonR.drawable.action_skip_next),
                                contentDescription = null,
                                tint = tint.copy(alpha = .5f * intensity),
                                modifier = Modifier.size(30.dp)
                        )
                    }
                }
            } else if (layout != CuratedLayout.POSTER && layout != CuratedLayout.STUDIO) {
                Icon(painterResource(if (state.playing) commonR.drawable.action_pause else commonR.drawable.action_play),
                        null, tint = tint.copy(alpha = .66f * intensity), modifier = Modifier.align(Alignment.Center)
                        .size(screen * .09f))
            }
        }
        if (layout == CuratedLayout.MATERIAL && state.ambientShowPills) {
            AmbientUpNextPill(
                    state = state,
                    screen = screen,
                    tint = tint,
                    intensity = intensity,
                    modifier = Modifier
                            .align(Alignment.BottomCenter)
                            // Keeps the compact row fully below Material's center progress disc.
                            .padding(bottom = screen * .03f)
            )
        }
    }
}

@Composable
private fun BoxScope.MaterialComposition(
        state: NowPlayingFaceState,
        listener: NowPlayingFaceListener,
        progress: () -> Float,
        screen: Dp
) {
    // The awake typography is larger than Material AOD's, so 17% places the visible baselines in
    // the same clock-to-control band without letting the artist line touch the center disc.
    Column(
            modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = screen * 0.17f, start = screen * 0.12f, end = screen * 0.12f)
                    .width(screen * 0.76f),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.showTitle) {
            AdaptiveTitleText(
                    text = state.title,
                    mode = state.titleTextMode,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = state.titleFont,
                    textAlign = TextAlign.Center,
                    minFontSize = 13.sp
            )
        }
        if (state.showArtist && state.artist.isNotEmpty()) {
            Text(
                    text = state.artist,
                    color = artistOrStatusColor(state, 0.70f),
                    fontSize = 13.sp,
                    fontFamily = state.artistFont,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
            )
        }
    }

    val discSize = screen * 0.30f
    Row(
            modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = screen * 0.05f),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSideAction(
                iconRes = commonR.drawable.action_skip_prev,
                iconOverride = state.leftActionIcon,
                iconOverrideTintable = state.leftActionIconTintable,
                description = state.leftActionDescription
                        ?: stringResource(R.string.action_name_skip_prev),
                visible = state.showControls,
                iconAlpha = state.screenTheme.tokens.iconAlpha,
                iconScale = state.screenTheme.tokens.iconScale,
                onClick = listener::onSkipPreviousTap
        )

        InteractiveFocus(
                state = state,
                listener = listener,
                shape = CircleShape,
                modifier = Modifier.size(discSize)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.5.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)

                // This ring is the Material center control's defining outline, like the contour
                // ring on Expressive, so it remains present independently of the optional extra
                // progress indicators used by the other curated layouts.
                drawArc(
                        color = Color.White.copy(alpha = 0.18f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                )
                drawArc(
                        color = Color(state.progressColor),
                        startAngle = -90f,
                        sweepAngle = progress() * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                if (state.showControls) {
                    val innerR = size.minDimension / 2f - stroke
                    drawCircle(
                            color = Color.White.copy(alpha = 0.10f),
                            radius = innerR
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.showControls) {
                    Icon(
                            painter = painterResource(
                                    if (state.playing) commonR.drawable.action_pause_filled
                                    else commonR.drawable.action_play_filled
                            ),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = state.screenTheme.tokens.iconAlpha),
                            // The narrower play triangle gets a small optical increase and offset;
                            // only the glyph grows, while the surrounding control stays unchanged.
                            modifier = Modifier
                                    .size(
                                            (if (state.playing) 34.dp else 38.dp) *
                                                    state.screenTheme.tokens.iconScale
                                    )
                                    .offset(x = if (state.playing) 0.dp else 1.dp)
                    )
                }
            }
        }

        MaterialSideAction(
                iconRes = commonR.drawable.action_skip_next,
                iconOverride = state.rightActionIcon,
                iconOverrideTintable = state.rightActionIconTintable,
                description = state.rightActionDescription
                        ?: stringResource(R.string.action_name_skip_next),
                visible = state.showControls,
                iconAlpha = state.screenTheme.tokens.iconAlpha,
                iconScale = state.screenTheme.tokens.iconScale,
                onClick = listener::onSkipNextTap
        )
    }
}

/** The Material face's side actions use a 48dp hit target while keeping their original 30dp
 * visual weight. Configured quadrant icons and descriptions travel together so the glyph, spoken
 * label and action can never disagree. */
@Composable
private fun MaterialSideAction(
        iconRes: Int,
        iconOverride: ImageBitmap?,
        iconOverrideTintable: Boolean,
        description: String,
        visible: Boolean,
        iconAlpha: Float,
        iconScale: Float,
        onClick: () -> Unit
) {
    Box(
            modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick)
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                    },
            contentAlignment = Alignment.Center
    ) {
        if (!visible) return@Box
        if (iconOverride != null) {
            Icon(
                    bitmap = iconOverride,
                    contentDescription = null,
                    tint = if (iconOverrideTintable) {
                        Color.White.copy(alpha = iconAlpha)
                    } else {
                        Color.Unspecified
                    },
                    modifier = Modifier
                            .size(30.dp * iconScale)
                            .then(
                                    if (iconOverrideTintable) Modifier
                                    else Modifier.alpha(iconAlpha)
                            )
            )
        } else {
            Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = iconAlpha),
                    modifier = Modifier.size(30.dp * iconScale)
            )
        }
    }
}
