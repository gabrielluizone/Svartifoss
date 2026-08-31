package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.common.FaceGeometry
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.view.compose.FaceClock
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
 * darkened by an accent tint and a radial black vignette. Queue/volume/menu access is left
 * entirely to the user's configured mini buttons (see [NowPlayingFaceState.miniButtonsTopFraction])
 * rather than a fixed default trio, so there is never a second, conflicting set of shortcuts.
 *
 * Buttons retain their own taps and the ring retains drag-to-seek, while MainActivity's
 * full-screen Compose bridge observes swipe streams claimed by either. Configured swipes can
 * therefore begin on controls as well as on the non-interactive artwork around them.
 */
@Composable
fun ExpressiveFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) {
    if (state.idle) {
        // Nothing to render - the shared idle ("nothing playing") group shows through, and
        // stopped-config gestures on the layers below remain the way to start playback.
        return
    }

    if (state.ambient) {
        ExpressiveAmbientFace(state)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        val metrics = expressiveMetrics(screen)
        // The ring's bottom edge below screen center - the transport row is centered and the ring
        // fills the cookie box, so half the box height. Layout clamps (title height, track-time
        // offset) hang off this instead of a magic fraction, so they track the real button size.
        val ringBottom = metrics.cookieBox / 2
        val theme = state.screenTheme
        val themeTokens = theme.tokens
        // Every other control-style theme only fades or scales these icons; only Hidden zeroes
        // them out. Expressive's cookie/transport row is its one visual focus (there is no
        // quadrant/gesture fallback the way Classic and the curated faces have), so this face
        // always shows its icons at full opacity - Hidden becomes a no-op here.
        val expressiveIconAlpha = themeTokens.iconAlpha.takeIf { it > 0f } ?: 1f
        val surfaceAccent = state.accentColor

        // Non-null while the user is dragging the ring to scrub (central seek mode). Drives both
        // the ring sweep and the track-time readout so they follow the finger, and is committed to
        // the phone via listener.onSeek on release.
        var scrubFraction by remember { mutableStateOf<Float?>(null) }

        // The visual treatment is independent from this layout: Material/Poster/Aurora and every
        // other background can be combined with Expressive's control geometry.
        PlayerBackgroundTreatment(state)

        FaceClock(
                visible = state.showClock,
                color = Color(state.clockColor),
                fontFamily = state.clockFont,
                typography = state.clockTypography
        )

        val sideContainer = Color(tonal(surfaceAccent, 0.74f, 0.40f, 0.92f))
        val centerContainer = Color(tonal(surfaceAccent, 0.87f, 0.30f, 0.90f))
        val onOpaque = Color(tonal(surfaceAccent, 0.16f, 0.25f, 0.70f))
        val sideContent = onOpaque
        val centerContent = onOpaque
        val sideStroke = Color.Transparent
        val centerStroke = Color.Transparent

        // Keep metadata in the same band as the ambient composition: visually centered between
        // the clock and the transport row instead of crowding the clock at the bezel.
        // On a round screen the visible width near the top is much narrower than the square
        // bounds, so a flat 16dp inset let long titles spill past the left/right edges. Inset
        // horizontally by a fraction of the diameter there so the text stays inside the circle.
        val isRound = LocalConfiguration.current.isScreenRound
        val titleHorizontalPadding = if (isRound) screen * 0.16f else 16.dp
        if (state.showTitle || state.showArtist) {
            Column(
                    modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = screen * 0.17f,
                                    start = titleHorizontalPadding, end = titleHorizontalPadding)
                            // Bound the title block to the top section (17% top margin down to the
                            // ring top) and clip. Without this, a two-line "wrap" title plus artist
                            // spills past the ring top and paints over the transport row; clipping a
                            // rare overflowing second line is far better than overlapping the
                            // controls. Ring top from screen top = center (0.5) minus half the ring.
                            .heightIn(max = (screen * 0.33f - ringBottom).coerceAtLeast(screen * 0.14f))
                            .clipToBounds(),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.showTitle) {
                    AdaptiveTitleText(
                            text = state.title,
                            mode = state.titleTextMode,
                            typography = state.titleTypography,
                            color = titleTextColor(state, Color.White),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = state.titleFont,
                            textAlign = TextAlign.Center,
                            minFontSize = 12.sp
                    )
                }
                if (state.showArtist && state.artist.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(top = if (state.showTitle) 2.dp else 0.dp)) {
                        SourceIconGlyph(state, 13.dp, Color(state.artistColor))
                        ArtistLineText(
                                text = state.artist,
                                state = state,
                                color = Color(state.artistColor),
                                fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.spacing)
        ) {
            RoundTransportButton(
                    iconRes = commonR.drawable.action_skip_prev,
                    contentDescription = state.leftActionDescription
                            ?: stringResource(R.string.action_name_skip_prev),
                    width = metrics.side,
                    height = metrics.sideHeight,
                    container = sideContainer,
                    content = sideContent,
                    borderColor = sideStroke,
                    visible = state.showControls,
                    iconAlpha = expressiveIconAlpha,
                    iconScale = themeTokens.iconScale,
                    iconOverride = state.leftActionIcon,
                    iconOverrideTintable = state.leftActionIconTintable,
                    onClick = listener::onSkipPreviousTap
            )
            CookiePlayButton(
                    state = state,
                    boxSize = metrics.cookieBox,
                    cookieSize = metrics.cookie,
                    ringStroke = metrics.ringStroke,
                    container = centerContainer,
                    content = centerContent,
                    borderColor = centerStroke,
                    iconAlpha = expressiveIconAlpha,
                    iconScale = themeTokens.iconScale,
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
                    contentDescription = state.rightActionDescription
                            ?: stringResource(R.string.action_name_skip_next),
                    width = metrics.side,
                    height = metrics.sideHeight,
                    container = sideContainer,
                    content = sideContent,
                    borderColor = sideStroke,
                    visible = state.showControls,
                    iconAlpha = expressiveIconAlpha,
                    iconScale = themeTokens.iconScale,
                    iconOverride = state.rightActionIcon,
                    iconOverrideTintable = state.rightActionIconTintable,
                    onClick = listener::onSkipNextTap
            )
        }

        val scrubbing = scrubFraction != null
        if (state.showTrackTime || scrubbing) {
            // While scrubbing, show the position the finger is pointing at (and brighten it) even
            // if the user normally hides the track time - it's the only readout of where the seek
            // will land.
            val shownPositionMs = scrubFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs
            // The default bottom trio is gone (mini buttons own that row now), so leave clearance
            // for the real row position. Material consumes the same metric for exact parity.
            val timeOffset = centeredTransportTrackTimeOffset(
                    screen, state.miniButtonsTopFraction)
            TrackTimeText(
                    text = stringResource(
                            R.string.playback_time_format,
                            formatFaceTime(shownPositionMs),
                            formatFaceTime(state.durationMs)
                    ),
                    state = state,
                    color = if (scrubbing) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = GoogleSansFamily,
                    modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = timeOffset)
            )
        }

        if (state.showUpNextPill) {
            AwakeUpNextPill(
                    state = state,
                    screen = screen,
                    onClick = listener::onQueueTap,
                    modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = screen * .07f)
            )
        }
    }
}

/**
 * The expressive face's always-on-display variant (see MiscPreferences.WEAR_AOD_STYLE): the same
 * layout skeleton - title/artist up top, prev / cookie / next across the center - but rendered
 * as thin outlines over black instead of tonal fills (the Wear OS 6
 * system media controls' AOD look), with every animation, marquee and gesture handler gone. Lit
 * pixels are kept to a minimum on purpose: AOD runs for hours, so fills would both drain AMOLED
 * battery and risk burn-in. The outline color follows [NowPlayingFaceState.ambientTint] (white,
 * album accent or custom); the title takes the same tint (the host pre-lifts its lightness so it
 * stays legible on black), and every alpha scales with
 * [NowPlayingFaceState.ambientIntensity]. The transport row, progress ring and pills are each
 * individually toggleable. The host hides its straight ambient clock only when the user disables
 * it - this variant never draws its own.
 */
@Composable
private fun ExpressiveAmbientFace(state: NowPlayingFaceState) {
    val i = state.ambientIntensity.coerceIn(0.2f, 1f)
    val tint = Color(state.ambientTint)
    val stroke = tint.copy(alpha = 0.50f * i)
    val glyph = tint.copy(alpha = 0.75f * i)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        val metrics = expressiveMetrics(screen)

        if (state.ambientShowTrackInfo && (state.showTitle || state.showArtist)) {
            // Matches the interactive metadata band: both sit between the top clock and the
            // center transport row, so switching into AOD does not make the text jump.
            Column(
                    modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = screen * 0.17f, start = 26.dp, end = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.showTitle) {
                    Text(
                            text = state.title,
                            color = tint.copy(alpha = 0.85f * i),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = state.titleFont,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.showArtist && state.artist.isNotEmpty()) {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                    ) {
                        AmbientSourceIconGlyph(state, 13.dp, tint.copy(alpha = 0.55f * i))
                        Text(
                                text = state.artist,
                                color = tint.copy(alpha = 0.55f * i),
                                fontSize = 13.sp,
                                fontFamily = state.artistFont,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (state.ambientShowTransport) {
            Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.spacing)
            ) {
                OutlinedAmbientButton(
                        iconRes = commonR.drawable.action_skip_prev,
                        width = metrics.side,
                        height = metrics.sideHeight,
                        stroke = stroke,
                        glyph = glyph,
                        iconOverride = state.leftActionIcon,
                        // AOD intentionally flattens artwork to the low-emission ambient tint.
                        iconOverrideTintable = true
                )
                AmbientCookie(
                        state = state,
                        boxSize = metrics.cookieBox,
                        cookieSize = metrics.cookie,
                        stroke = stroke,
                        glyph = glyph
                )
                OutlinedAmbientButton(
                        iconRes = commonR.drawable.action_skip_next,
                        width = metrics.side,
                        height = metrics.sideHeight,
                        stroke = stroke,
                        glyph = glyph,
                        iconOverride = state.rightActionIcon,
                        iconOverrideTintable = true
                )
            }
        }

        // One useful, glanceable row fills the lower AOD band without pretending to be
        // interactive. It is independent of configured mini buttons because those are hidden in
        // ambient mode anyway.
        if (state.ambientShowPills) {
            AmbientUpNextPill(
                    state = state,
                    screen = screen,
                    tint = tint,
                    intensity = i,
                    modifier = Modifier
                            .align(Alignment.BottomCenter)
                            // Raised into the usable round-screen band while remaining below the
                            // center cookie. Width and height deliberately stay unchanged.
                            .padding(bottom = screen * .06f)
            )
        }

    }
}

/** A transport button reduced to a hairline capsule outline and a dim glyph - no fill, no
 *  interaction. Same flattened geometry as the awake [RoundTransportButton] so a wake-up tap
 *  lands on the button the AOD showed. */
@Composable
private fun OutlinedAmbientButton(
        iconRes: Int,
        width: Dp,
        height: Dp,
        stroke: Color,
        glyph: Color,
        iconOverride: androidx.compose.ui.graphics.ImageBitmap? = null,
        iconOverrideTintable: Boolean = true
) {
    Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 1.dp.toPx()
            // Corner radius = half the SMALLER dimension, so the capsule fully rounds along its
            // short axis whichever way the button is oriented (here: rounded top and bottom).
            val radius = (minOf(size.width, size.height) - 2 * inset) / 2f
            drawRoundRect(
                    color = stroke,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(width = 1.5.dp.toPx())
            )
        }
        if (iconOverride != null) {
            Icon(
                    bitmap = iconOverride,
                    contentDescription = null,
                    tint = if (iconOverrideTintable) glyph else Color.Unspecified,
                    modifier = Modifier.size(height * 0.5f)
            )
        } else {
            Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = glyph,
                    modifier = Modifier.size(height * 0.5f)
            )
        }
    }
}

/** The cookie + contour ring as static outlines: cookie contour stroked (full scallop while
 *  playing, plain circle while paused - no morph animation), ring frozen at the last known
 *  progress (position only refreshes about once a minute in ambient, so animating it would
 *  just be wasted frames). The ring honors [NowPlayingFaceState.ambientShowProgress]. */
@Composable
private fun AmbientCookie(
        state: NowPlayingFaceState,
        boxSize: Dp,
        cookieSize: Dp,
        stroke: Color,
        glyph: Color
) {
    Box(Modifier.size(boxSize), contentAlignment = Alignment.Center) {
        if (state.ambientShowProgress) {
            Canvas(Modifier.fillMaxSize()) {
                val strokePx = 2.dp.toPx()
                val ringStrokes = ambientRingStrokes(state.progressRingStyle, strokePx)
                val ringModulation = if (state.playing) RING_MODULATION else 0f
                val baseRadius = (size.minDimension / 2f - strokePx * 2f) / (1f + RING_MODULATION)
                val sweep = state.progress.coerceIn(0f, 1f) * 360f
                val halfGap = RING_GAP_DEGREES / 2f

                // Same single-gap-at-the-playhead geometry as the awake ring (no gap at the start).
                val trackFrom = sweep + halfGap
                if (trackFrom < 360f) {
                    drawContourStroke(center, baseRadius, ringModulation, trackFrom, 360f,
                            stroke.copy(alpha = stroke.alpha * 0.4f), strokePx,
                            ringStrokes.track)
                }
                if (sweep > halfGap) {
                    drawContourStroke(center, baseRadius, ringModulation, 0f, sweep - halfGap,
                            stroke, strokePx, ringStrokes.played)
                }
                if (ringStrokes.cometHead && sweep > 0f) {
                    drawCircle(
                            color = stroke,
                            radius = strokePx * 0.9f,
                            center = contourPoint(center, baseRadius, ringModulation, sweep)
                    )
                }
            }
        }

        Box(Modifier.size(cookieSize), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val strokePx = 1.5.dp.toPx()
                val modulation = if (state.playing) COOKIE_MODULATION else 0f
                val baseRadius = (size.minDimension / 2f - strokePx) / (1f + COOKIE_MODULATION)
                val path = contourPath(center, baseRadius, modulation, fromDeg = 0f, toDeg = 360f)
                path.close()
                drawPath(path, color = stroke, style = Stroke(width = strokePx))
            }
            Icon(
                    painter = painterResource(
                            if (state.playing) commonR.drawable.action_pause_expressive
                            else commonR.drawable.action_play
                    ),
                    contentDescription = null,
                    tint = glyph,
                    modifier = Modifier.size(cookieSize * 0.48f)
            )
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
 *  on-container pairs this face needs. Shared with the overlay chrome and the phone preview via
 *  [PaletteTransforms] so the face and the overlays that open over it never disagree on tint. */
private fun tonal(accent: Int, lightness: Float, minSat: Float, maxSat: Float): Int =
        PaletteTransforms.tonalSurface(accent, lightness, minSat, maxSat)

@Composable
private fun RoundTransportButton(
        iconRes: Int,
        contentDescription: String,
        width: Dp,
        height: Dp,
        container: Color,
        content: Color,
        borderColor: Color,
        visible: Boolean,
        onClick: () -> Unit,
        iconAlpha: Float = 1f,
        iconScale: Float = 1f,
        /** When set, drawn instead of [iconRes] - the configured quadrant action's icon, so the
         *  side buttons mirror the user's Controls config. */
        iconOverride: androidx.compose.ui.graphics.ImageBitmap? = null,
        iconOverrideTintable: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "transportPressScale")

    // CircleShape on the non-square box renders as a gently flattened capsule (see
    // ExpressiveMetrics.sideHeight), not a full circle.
    Box(
            modifier = Modifier
                    .size(width, height)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
    ) {
        Box(
                modifier = Modifier
                        .size(width, height)
                        .alpha(if (visible) 1f else 0f)
                        .clip(CircleShape)
                        .background(container)
                        .then(
                                if (borderColor.alpha > 0f) {
                                    Modifier.border(1.5.dp, borderColor, CircleShape)
                                } else {
                                    Modifier
                                }
                        ),
                contentAlignment = Alignment.Center
        ) {
            if (iconOverride != null) {
                Icon(
                        bitmap = iconOverride,
                        contentDescription = contentDescription,
                        tint = if (iconOverrideTintable) {
                            content.copy(alpha = iconAlpha)
                        } else {
                            Color.Unspecified
                        },
                        modifier = Modifier
                                .size(height * 0.5f * iconScale)
                                .then(
                                        if (iconOverrideTintable) Modifier
                                        else Modifier.alpha(iconAlpha)
                                )
                )
            } else {
                Icon(
                        painter = painterResource(iconRes),
                        contentDescription = contentDescription,
                        tint = content.copy(alpha = iconAlpha),
                        modifier = Modifier.size(height * 0.5f * iconScale)
                )
            }
        }
    }
}

// --- Transport-row sizing ----------------------------------------------------------------

/** Compact transport-row sizes in dp, with a 225dp breakpoint for larger watches. The visual
 *  controls are deliberately smaller than the Wear OS media spec so they leave more breathing room
 *  around the artwork. [cookieBox] remains larger than the cookie itself, so the progress ring
 *  keeps a visible ~3dp air gap from the cookie's scalloped crest instead of hugging it
 *  ([RING_MODULATION]/[COOKIE_MODULATION] crests eat most of a naive box-minus-cookie margin).
 *  The awake and ambient faces read the same metrics so a wake-up tap lands on the same button it
 *  would while awake. */
private data class ExpressiveMetrics(
        val side: Dp, val sideHeight: Dp, val cookieBox: Dp, val cookie: Dp, val spacing: Dp,
        val ringStroke: Dp)

private fun expressiveMetrics(screen: Dp): ExpressiveMetrics {
    val large = screen >= 225.dp
    return ExpressiveMetrics(
            side = if (large) 48.dp else 42.dp,
            // TALLER than wide: the sides read as vertically-oriented capsules (CircleShape on a
            // non-square box rounds the top and bottom into semicircles), hugging the round bezel
            // like the Wear OS media reference's prev/next buttons - not full circles, and squeezed
            // top-to-bottom rather than side-to-side.
            sideHeight = if (large) 58.dp else 50.dp,
            cookieBox = if (large) 78.dp else 62.dp,
            cookie = if (large) 62.dp else 48.dp,
            spacing = 4.dp,
            ringStroke = if (large) 4.dp else 3.dp
    )
}

// --- Cookie geometry ---------------------------------------------------------------------

private const val COOKIE_LOBES = FaceGeometry.Expressive.COOKIE_LOBES

/** tanh() gently flattens the cosine's crests and valleys, turning a pointy star into the
 *  reference's soft scallops. Lower = closer to a pure sinusoid (rounder, softer lobes). */
private const val COOKIE_SOFTNESS = FaceGeometry.Expressive.COOKIE_SOFTNESS

/** Cookie lobe amplitude as a fraction of its base radius. */
private const val COOKIE_MODULATION = FaceGeometry.Expressive.COOKIE_MODULATION

/** The ring undulates noticeably less than the cookie it wraps, as in the reference. */
private const val RING_MODULATION = FaceGeometry.Expressive.RING_MODULATION

/** Gap (degrees) the ring leaves around the progress thumb and the 12 o'clock start. */
private const val RING_GAP_DEGREES = FaceGeometry.Expressive.RING_GAP_DEGREES

/** Radius multiplier for the given polar [angleRad]: 1f ± [modulation]. The phase term anchors a
 *  lobe crest at 12 o'clock for any lobe count ([angleRad] is measured from 3 o'clock, so the
 *  +π/2 shift re-references the cosine to the top of the dial). */
private fun cookieProfile(angleRad: Float, modulation: Float): Float {
    val angleFromTop = angleRad + (Math.PI / 2.0).toFloat()
    val wave = tanh(COOKIE_SOFTNESS * cos(COOKIE_LOBES * angleFromTop)) / tanh(COOKIE_SOFTNESS)
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
        ringStroke: Dp,
        container: Color,
        content: Color,
        borderColor: Color,
        listener: NowPlayingFaceListener,
        scrubFraction: Float?,
        onScrub: (Float) -> Unit,
        onScrubCommit: () -> Unit,
        iconAlpha: Float = 1f,
        iconScale: Float = 1f
) {
    // The Expressive ring is structural: the curated-layout progress preference neither hides it
    // nor disables its scrub target.
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
    val actionDescription = stringResource(R.string.action_name_play_pause)

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
                val stroke = ringStroke.toPx()
                val ringModulation = RING_MODULATION * morph
                val baseRadius = (size.minDimension / 2f - stroke) / (1f + RING_MODULATION)
                val sweep = (scrubFraction ?: progress.value) * 360f
                val halfGap = RING_GAP_DEGREES / 2f

                // Track (unplayed): from just past the playhead all the way back to 12 o'clock
                // (360°), so it meets the played bar at the start with NO gap there. The only break
                // in the ring is the single M3-style gap straddling the playhead - the start of the
                // ring is the played bar itself, not empty space.
                val trackFrom = sweep + halfGap
                if (trackFrom < 360f) {
                    drawContourStroke(center, baseRadius, ringModulation, trackFrom, 360f,
                            Color.White.copy(alpha = 0.30f), stroke)
                }

                // Played portion: 12 o'clock to just before the playhead, in the progress colour.
                // No thumb dot (removed by user request - the playhead gap marks the position, and
                // scrub still works via the sweep + time readout).
                if (sweep > halfGap) {
                    drawContourStroke(center, baseRadius, ringModulation, 0f, sweep - halfGap,
                            Color(state.progressColor), stroke)
                }
            }

        Box(
                modifier = Modifier
                        .size(cookieSize)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .clip(CookieShape(morph))
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
                        }
                        .semantics(mergeDescendants = true) {
                            contentDescription = actionDescription
                            role = Role.Button
                            onClick(actionDescription) {
                                listener.onPlayPauseTap()
                                true
                            }
                        },
                contentAlignment = Alignment.Center
        ) {
            Box(
                    modifier = Modifier
                            .size(cookieSize)
                            .alpha(if (state.showControls) 1f else 0f)
                            .clip(CookieShape(morph))
                            .background(container)
                            .then(
                                    if (borderColor.alpha > 0f) {
                                        Modifier.border(1.5.dp, borderColor, CookieShape(morph))
                                    } else {
                                        Modifier
                                    }
                            ),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        painter = painterResource(
                                if (state.playing) commonR.drawable.action_pause_expressive
                                else commonR.drawable.action_play_filled
                        ),
                        contentDescription = null,
                        tint = content.copy(alpha = iconAlpha),
                        modifier = Modifier.size(cookieSize * 0.48f * iconScale)
                )
            }
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
        strokeWidth: Float,
        strokeStyle: Stroke? = null
) {
    drawPath(
            path = contourPath(center, radius, modulation, fromDeg, toDeg),
            color = color,
            style = strokeStyle ?: Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}
