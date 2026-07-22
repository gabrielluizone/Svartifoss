package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.PlayerShadingStyle
import com.svartifoss.snfell.common.SHADING_MAX_MULTIPLIER
import com.svartifoss.snfell.watch.theme.GoogleSansFamily

/**
 * Small building blocks shared by the Beta Compose faces ([VinylFace], [PosterFace]).
 * [ExpressiveFace] predates this file and keeps private copies of its own equivalents -
 * fold them in here only if a change ever has to touch all faces at once.
 */

/** HSL-derived tonal color from the album accent, saturation clamped into a readable band. */
internal fun faceTonal(accent: Int, lightness: Float, minSat: Float = 0.25f, maxSat: Float = 0.70f): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent, hsl)
    hsl[1] = hsl[1].coerceIn(minSat, maxSat)
    hsl[2] = lightness
    return ColorUtils.HSLToColor(hsl)
}

/**
 * Draws the selected background treatment without making any decision about control geometry.
 * It is shared by Expressive and every curated layout, which is the key distinction between a
 * layout (where controls live) and a background (how artwork/album colour fills the screen).
 */
@Composable
internal fun PlayerBackgroundTreatment(state: NowPlayingFaceState) {
    val style = state.backgroundStyle
    // An authored background style owns its designed look regardless of the "Dim album art"
    // toggle (that toggle governs the separate legibility scrim over plain artwork). The intensity
    // still modulates its depth, and an explicit shading style replaces (not stacks) it.
    val authoredStrength = if (
        state.backdropShadingStyle == PlayerShadingStyle.FOLLOW && !style.isPlainArtworkTreatment
    ) {
        (state.backdropDimStrength / .8f).coerceIn(0f, SHADING_MAX_MULTIPLIER / .8f)
    } else {
        0f
    }
    fun opacity(base: Float): Float = base.coerceIn(0f, 1f)
    fun authoredOpacity(base: Float): Float =
            (base * authoredStrength).coerceIn(0f, 1f)

    val primary = Color(PaletteTransforms.tunedFaceColor(state.accentColor, .62f, .74f))
    val secondary = Color(PaletteTransforms.tunedFaceColor(
            state.secondaryAccentColor, .58f, .70f))
    val tertiary = Color(PaletteTransforms.tunedFaceColor(
            state.tertiaryAccentColor, .62f, .72f))
    val deep = Color(PaletteTransforms.tunedFaceColor(state.accentColor, .075f, .48f))
    val surface = Color(PaletteTransforms.tunedFaceColor(
            state.secondaryAccentColor, .16f, .42f))

    Canvas(Modifier.fillMaxSize()) {
        when (style) {
            PlayerBackgroundStyle.COVER,
            PlayerBackgroundStyle.BLUR,
            PlayerBackgroundStyle.BLACK_AND_WHITE,
            PlayerBackgroundStyle.BLURRED_BLACK_AND_WHITE,
            PlayerBackgroundStyle.SQUARE_SHARP,
            PlayerBackgroundStyle.SQUARE_SOFT,
            PlayerBackgroundStyle.SQUARE -> Unit

            PlayerBackgroundStyle.EXPRESSIVE,
            PlayerBackgroundStyle.EXPRESSIVE_NO_BLUR -> {
                val tint = Color(PaletteTransforms.tonalSurface(
                        state.accentColor, .30f, .30f, .90f))
                drawRect(tint.copy(alpha = opacity(.45f)))
                drawRect(Color.Black.copy(alpha = authoredOpacity(.30f)))
                drawRect(brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        .55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = authoredOpacity(.88f)),
                        center = center,
                        radius = size.maxDimension * .68f))
            }

            PlayerBackgroundStyle.MATERIAL -> {
                drawRect(Color.Black)
                val tint = Color(PaletteTransforms.tonalSurface(
                        state.materialSurfaceColor,
                        lightness = if (state.materialSurfaceSoftened) .36f else .26f,
                        minSat = if (state.materialSurfaceSoftened) 0f else .30f,
                        maxSat = .80f))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to tint.copy(alpha = .72f),
                                .50f to tint.copy(alpha = .38f),
                                .80f to tint.copy(alpha = .12f),
                                1f to Color.Transparent),
                        radius = size.minDimension * .85f,
                        center = center)
            }

            PlayerBackgroundStyle.POSTER -> {
                drawRect(primary.copy(alpha = opacity(.12f)))
                drawRect(brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = authoredOpacity(.48f)),
                        .36f to Color.Black.copy(alpha = authoredOpacity(.06f)),
                        .68f to Color.Black.copy(alpha = authoredOpacity(.25f)),
                        1f to Color.Black.copy(alpha = authoredOpacity(.94f))))
                drawRect(brush = Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = authoredOpacity(.36f)),
                        .50f to Color.Transparent,
                        1f to Color.Black.copy(alpha = authoredOpacity(.36f))))
            }

            PlayerBackgroundStyle.STUDIO -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.48f)))
                drawRect(brush = Brush.linearGradient(
                        colors = listOf(
                                primary.copy(alpha = .44f),
                                secondary.copy(alpha = .15f),
                                Color.Transparent),
                        start = Offset(size.width, 0f),
                        end = Offset(0f, size.height)))
            }

            PlayerBackgroundStyle.VINYL -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.68f)))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to primary.copy(alpha = .32f),
                                .55f to deep.copy(alpha = .20f),
                                1f to Color.Transparent),
                        radius = size.minDimension * .69f,
                        center = Offset(size.width * .64f, size.height * .38f))
            }

            PlayerBackgroundStyle.HALO -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.68f)))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to primary.copy(alpha = .50f),
                                .48f to secondary.copy(alpha = .18f),
                                1f to Color.Transparent),
                        radius = size.minDimension * .62f,
                        center = center)
            }

            PlayerBackgroundStyle.AURORA -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(1f)))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to primary.copy(alpha = .48f),
                                .42f to deep.copy(alpha = .30f),
                                1f to Color.Transparent),
                        radius = size.minDimension * .78f,
                        center = Offset(size.width * .18f, size.height * .14f))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to secondary.copy(alpha = .38f),
                                .48f to tertiary.copy(alpha = .18f),
                                1f to Color.Transparent),
                        radius = size.minDimension * .72f,
                        center = Offset(size.width * .88f, size.height * .72f))
                listOf(
                        Triple(.30f, .52f, primary),
                        Triple(.43f, .63f, secondary),
                        Triple(.56f, .72f, tertiary)
                ).forEachIndexed { index, (startY, endY, color) ->
                    val ribbon = Path().apply {
                        moveTo(-size.width * .14f, size.height * startY)
                        cubicTo(
                                size.width * .18f,
                                size.height * (startY - .24f + index * .025f),
                                size.width * .58f,
                                size.height * (endY + .18f - index * .02f),
                                size.width * 1.14f,
                                size.height * endY)
                    }
                    drawPath(
                            ribbon,
                            brush = Brush.linearGradient(
                                    colors = listOf(color, primary, secondary),
                                    start = Offset(0f, size.height * .50f),
                                    end = Offset(size.width, size.height * .50f)),
                            style = Stroke(
                                    size.minDimension * (.085f - index * .012f),
                                    cap = StrokeCap.Round),
                            alpha = .32f - index * .045f)
                }
                drawRect(brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = authoredOpacity(.06f)),
                        .62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = authoredOpacity(.78f))))
            }

            PlayerBackgroundStyle.SPECTRUM -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.58f)))
                drawRect(brush = Brush.verticalGradient(
                        colors = listOf(
                                surface.copy(alpha = opacity(.78f)),
                                deep.copy(alpha = opacity(.90f)),
                                Color.Black.copy(alpha = authoredOpacity(.88f)))))
            }

            PlayerBackgroundStyle.CORONA -> {
                // Color lives only in a soft ring hugging the rim - a wide stroked circle, not a
                // full-bleed fill - so the cover stays fully legible through its center and only
                // the border picks up the sweep's hues.
                drawRect(Color.Black.copy(alpha = authoredOpacity(.16f)))
                drawCircle(
                        brush = Brush.sweepGradient(
                                colors = listOf(tertiary, primary, secondary, tertiary),
                                center = center),
                        radius = size.maxDimension * .44f,
                        center = center,
                        alpha = opacity(.58f),
                        style = Stroke(width = size.minDimension * .24f, cap = StrokeCap.Round))
            }

            PlayerBackgroundStyle.DUSK -> {
                // No base fill at all - the fade itself is the only treatment, so the top of the
                // cover stays untouched and only the lower band darkens toward black.
                drawRect(brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        .60f to deep.copy(alpha = opacity(.38f)),
                        1f to Color.Black.copy(alpha = authoredOpacity(.70f))))
            }

            PlayerBackgroundStyle.BLOOM -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.16f)))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to primary.copy(alpha = opacity(.38f)),
                                .85f to Color.Transparent),
                        radius = size.minDimension * .52f,
                        center = Offset(size.width * .22f, size.height * .26f))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to secondary.copy(alpha = opacity(.32f)),
                                .85f to Color.Transparent),
                        radius = size.minDimension * .46f,
                        center = Offset(size.width * .80f, size.height * .22f))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to tertiary.copy(alpha = opacity(.28f)),
                                .85f to Color.Transparent),
                        radius = size.minDimension * .48f,
                        center = Offset(size.width * .50f, size.height * .88f))
            }

            PlayerBackgroundStyle.HORIZON -> {
                drawRect(brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        .72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = authoredOpacity(.62f))))
            }

            PlayerBackgroundStyle.EMBER -> {
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to primary.copy(alpha = opacity(.40f)),
                                .5f to deep.copy(alpha = opacity(.22f)),
                                1f to Color.Transparent),
                        radius = size.minDimension * .46f,
                        center = Offset(size.width * .82f, size.height * .84f))
            }

            PlayerBackgroundStyle.ECLIPSE,
            PlayerBackgroundStyle.HIDDEN -> drawRect(Color.Black)
        }
    }
}

/**
 * Shared player-shading layer for every Compose face. FOLLOW lets a named background use its
 * authored scrim, while plain artwork gets the neutral bottom fade; explicit values replace it.
 */
@Composable
internal fun PlayerShadingOverlay(state: NowPlayingFaceState) {
    if (!state.backdropDimEnabled) return
    val style = when {
        state.backdropShadingStyle != PlayerShadingStyle.FOLLOW -> state.backdropShadingStyle
        state.backgroundStyle.isPlainArtworkTreatment -> PlayerShadingStyle.BOTTOM_FADE
        else -> return // Named backgrounds already rendered their scaled authored scrim above.
    }
    val strength = state.backdropDimStrength.coerceIn(0f, SHADING_MAX_MULTIPLIER)
    // Shading gradient colour: black by default, or the album/desaturated/custom tone resolved by
    // the host. ALBUM_TINT/DUOTONE keep their own album tones regardless (they are album styles).
    val shade = Color(state.backdropShadingColor)
    fun shaded(maxAlpha: Float) = shade.copy(alpha = (maxAlpha * strength).coerceIn(0f, 1f))
    val primary = Color(faceTonal(state.accentColor, .13f, .25f, .78f))
    val secondary = Color(faceTonal(state.secondaryAccentColor, .13f, .25f, .78f))

    Canvas(Modifier.fillMaxSize()) {
        when (style) {
            PlayerShadingStyle.EDGE_VIGNETTE,
            PlayerShadingStyle.EDGE_VIGNETTE_STRONG,
            PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> {
                val baseStop = when (style) {
                    PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> 0.0f
                    PlayerShadingStyle.EDGE_VIGNETTE_STRONG -> 0.15f
                    else -> 0.46f
                }
                val radiusMult = when (style) {
                    PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> 0.475f
                    PlayerShadingStyle.EDGE_VIGNETTE_STRONG -> 0.525f
                    else -> 0.67f
                }
                val outerAlpha = when (style) {
                    PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> 1.0f
                    PlayerShadingStyle.EDGE_VIGNETTE_STRONG -> 1.0f
                    else -> 0.82f
                }
                drawRect(
                        brush = Brush.radialGradient(
                                0f to Color.Transparent,
                                baseStop to Color.Transparent,
                                1f to shaded(outerAlpha),
                                center = center,
                                radius = size.maxDimension * radiusMult))
            }

            PlayerShadingStyle.BOTTOM_CORNER -> drawRect(
                    brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            .42f to Color.Transparent,
                            1f to shaded(.94f),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)))

            PlayerShadingStyle.BOTTOM_FADE -> drawRect(
                    brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            .34f to Color.Transparent,
                            1f to shaded(.94f)))

            PlayerShadingStyle.FLOOR_CEILING -> drawRect(
                    brush = Brush.verticalGradient(
                            0f to shaded(.55f),
                            .30f to Color.Transparent,
                            .60f to Color.Transparent,
                            1f to shaded(.88f)))

            PlayerShadingStyle.FULL_FILTER -> drawRect(shaded(.55f))
            PlayerShadingStyle.ALBUM_TINT -> drawRect(primary.copy(alpha = (.52f * strength).coerceIn(0f, 1f)))
            PlayerShadingStyle.DUOTONE -> drawRect(
                    brush = Brush.linearGradient(
                            colors = listOf(
                                    primary.copy(alpha = (.58f * strength).coerceIn(0f, 1f)),
                                    secondary.copy(alpha = (.58f * strength).coerceIn(0f, 1f))),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)))

            PlayerShadingStyle.SIDE_CURTAINS -> drawRect(
                    brush = Brush.horizontalGradient(
                            0f to shaded(.72f),
                            .34f to Color.Transparent,
                            .66f to Color.Transparent,
                            1f to shaded(.72f)))

            PlayerShadingStyle.FOLLOW -> Unit // Resolved to BOTTOM_FADE above.
        }
    }
}

internal fun formatFaceClockTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    return String.format(java.util.Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/**
 * Vertical offset shared by Expressive and Material for their interactive track-time readout.
 * Both faces keep their transport focus centered, so using one metric prevents the Material
 * layout from drifting when mini buttons need extra clearance near the lower bezel.
 */
internal fun centeredTransportTrackTimeOffset(
        screen: Dp,
        miniButtonsTopFraction: Float
): Dp {
    val ringBottom = if (screen >= 225.dp) 39.dp else 31.dp
    val desiredOffset = ringBottom + 14.dp
    val miniRowClearance = screen * miniButtonsTopFraction.coerceIn(.20f, .95f) -
            screen * .50f - 10.dp
    return minOf(desiredOffset, miniRowClearance).coerceAtLeast(ringBottom + 10.dp)
}

/** A press-scaling tap target of any [shape] - the shared skeleton for the Beta faces' buttons,
 *  pills and chips. Theme visuals may inset the painted fill/outline, but this outer box retains
 *  its original hit geometry; everything around it falls through to the host gesture layers. */
@Composable
internal fun FaceTapTarget(
        width: Dp,
        height: Dp,
        shape: Shape,
        background: Color,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        visualInset: Dp = 0.dp,
        borderColor: Color = Color.Transparent,
        borderWidth: Dp = 0.dp,
        content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "faceTapScale")

    val visualWidth = maxOf(1.dp, width - visualInset * 2f)
    val visualHeight = maxOf(1.dp, height - visualInset * 2f)

    Box(
            modifier = modifier
                    .size(width = width, height = height)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(shape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
    ) {
        Box(
                modifier = Modifier
                        .size(width = visualWidth, height = visualHeight)
                        .clip(shape)
                        .background(background)
                        .then(
                                if (borderWidth > 0.dp && borderColor.alpha > 0f) {
                                    Modifier.border(borderWidth, borderColor, shape)
                                } else {
                                    Modifier
                                }
                        ),
                contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/** Translucent glass pill, same look as the expressive face's bottom trio. */
@Composable
internal fun FaceGlassPill(
        width: Dp,
        height: Dp,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        background: Color = Color.White.copy(alpha = 0.16f),
        visualInset: Dp = 0.dp,
        borderColor: Color = Color.Transparent,
        borderWidth: Dp = 0.dp,
        content: @Composable () -> Unit
) = FaceTapTarget(
        width = width,
        height = height,
        shape = RoundedCornerShape(50),
        background = background,
        label = label,
        onClick = onClick,
        modifier = modifier,
        visualInset = visualInset,
        borderColor = borderColor,
        borderWidth = borderWidth,
        content = content
)

/**
 * Static queue preview used by the visual AODs. It deliberately has no clickable/role semantics:
 * a tap in ambient mode only wakes the display, and advertising an action there would be
 * misleading. The hairline outline and two short text lines keep the emitted pixel budget low.
 */
@Composable
internal fun AmbientUpNextPill(
        state: NowPlayingFaceState,
        screen: Dp,
        tint: Color,
        intensity: Float,
        modifier: Modifier = Modifier
) {
    val alpha = intensity.coerceIn(.2f, 1f)
    val label = stringResource(R.string.quick_action_up_next)
    val title = state.upNextTitle.takeIf(String::isNotBlank)
            ?: stringResource(R.string.queue_empty)
    val detail = state.upNextArtist.takeIf(String::isNotBlank)?.let { "$title · $it" } ?: title
    // 25% is 48dp on the common 192dp canvas: visually the same row height as the awake panel,
    // but clamped so larger watches do not grow a card that reaches the center transport disc.
    val pillHeight = (screen * .25f).coerceIn(44.dp, 52.dp)

    Row(
            modifier = modifier
                    .size(width = screen * .88f, height = pillHeight)
                    .border(1.25.dp, tint.copy(alpha = .42f * alpha), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // AOD deliberately never publishes or paints album bitmaps: the monochrome queue glyph
        // keeps emitted pixels and GPU work low while the two text lines still identify the track.
        Icon(
                painter = painterResource(R.drawable.ic_queue_music),
                contentDescription = null,
                tint = tint.copy(alpha = .68f * alpha),
                modifier = Modifier.size(24.dp)
        )
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                    text = label,
                    color = tint.copy(alpha = .58f * alpha),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansFamily,
                    maxLines = 1
            )
            Text(
                    text = detail,
                    color = tint.copy(alpha = .82f * alpha),
                    fontSize = 13.sp,
                    fontFamily = state.titleFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The awake Up Next pill shown at the bottom of the player when
 * MiscPreferences.WEAR_SHOW_UP_NEXT_PILL is on - the same information the AOD pill carries, brought
 * to the main screen to fill the space the mini-buttons row would otherwise take. Its background and
 * text colour come from the shared Up Next pill style (WEAR_UP_NEXT_PILL_STYLE), resolved by the
 * host into [NowPlayingFaceState.upNextPillFill] / [upNextPillTextColor], so it matches the
 * quick-panel pill for the same style (including a fully transparent background). Tapping opens the
 * queue.
 */
@Composable
internal fun AwakeUpNextPill(
        state: NowPlayingFaceState,
        screen: Dp,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.quick_action_up_next)
    val title = state.upNextTitle.takeIf(String::isNotBlank)
            ?: stringResource(R.string.queue_empty)
    val detail = state.upNextArtist.takeIf(String::isNotBlank)?.let { "$title · $it" } ?: title
    val pillHeight = (screen * .25f).coerceIn(44.dp, 52.dp)
    val content = Color(state.upNextPillTextColor)
    Row(
            modifier = modifier
                    .size(width = screen * .84f, height = pillHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Color(state.upNextPillFill))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                painter = painterResource(R.drawable.ic_queue_music),
                contentDescription = label,
                tint = content.copy(alpha = .82f),
                modifier = Modifier.size(22.dp)
        )
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                    text = label,
                    color = content.copy(alpha = .60f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansFamily,
                    maxLines = 1
            )
            Text(
                    text = detail,
                    color = content.copy(alpha = .90f),
                    fontSize = 13.sp,
                    fontFamily = state.titleFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The "Chrono" always-on face: a large clock as the hero, with a small track title, the artist
 * (prefixed by the app glyph) beneath it, on pure black. Outline-/dim-only and animation-free like
 * every AOD variant. The clock text comes from [NowPlayingFaceState.clockText] (kept fresh by the
 * host's per-minute ambient update) rather than a Compose timer, so it never goes stale in ambient.
 */
@Composable
internal fun ChronoAmbientFace(state: NowPlayingFaceState) {
    val intensity = state.ambientIntensity.coerceIn(.2f, 1f)
    val tint = Color(state.ambientTint)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        Column(
                modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = screen * .08f),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                    text = state.clockText,
                    color = tint.copy(alpha = .92f * intensity),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = state.clockFont,
                    maxLines = 1
            )
            if (state.ambientShowTrackInfo && state.showTitle && state.title.isNotBlank()) {
                Text(
                        text = state.title,
                        color = tint.copy(alpha = .80f * intensity),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = state.titleFont,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (state.ambientShowTrackInfo && state.showArtist && state.artist.isNotBlank()) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = 2.dp)
                ) {
                    AmbientSourceIconGlyph(state, 11.dp, tint.copy(alpha = .55f * intensity))
                    Text(
                            text = state.artist,
                            color = tint.copy(alpha = .55f * intensity),
                            fontSize = 11.sp,
                            fontFamily = state.artistFont,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The "⋮" overflow glyph, drawn directly so no icon resource is needed. */
@Composable
internal fun FaceOverflowDots(color: Color = Color.White, scale: Float = 1f) {
    Canvas(Modifier.size(18.dp * scale)) {
        val r = (1.8f * scale).dp.toPx()
        val gap = (5.5f * scale).dp.toPx()
        for (i in -1..1) {
            drawCircle(color, radius = r, center = Offset(center.x, center.y + i * gap))
        }
    }
}

/**
 * Renders a title/artist line honoring the user's [MiscPreferences.WEAR_TITLE_TEXT_MODE] choice -
 * every face reads this through here instead of hardcoding one fixed overflow strategy (the
 * situation before this existed: Vinyl/Halo/Eclipse/Expressive always scrolled, Poster/Studio/
 * Aurora always wrapped to two lines and truncated anything longer, and Spectrum only ever
 * truncated). "marquee" scrolls a single line; "wrap"/"wrap3"/"wrap5" allow up to two/three/five
 * lines with an ellipsis; "static" is a single line at the fixed font size, ellipsized and never
 * shrunk or scrolled; "shrink" keeps one line and reduces the font size to fit; "smart" (the
 * default) cascades shrink then wrap, mirroring the classic face's own
 * OutlineTextView.enableSmartWordSizing.
 */
@Composable
internal fun AdaptiveTitleText(
        text: String,
        mode: String,
        fontSize: TextUnit,
        color: Color,
        modifier: Modifier = Modifier,
        fontWeight: FontWeight? = null,
        fontStyle: FontStyle? = null,
        fontFamily: FontFamily? = null,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        lineHeight: TextUnit = TextUnit.Unspecified,
        textAlign: TextAlign = TextAlign.Center,
        minFontSize: TextUnit = (fontSize.value * 0.62f).sp
) {
    val wrapLines = when (mode) {
        "static" -> 1
        "wrap" -> 2
        "wrap3" -> 3
        "wrap5" -> 5
        else -> null
    }
    when {
        wrapLines != null -> Text(
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                lineHeight = lineHeight,
                textAlign = textAlign,
                maxLines = wrapLines,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
        )
        mode == "shrink" -> ShrinkToFitTitleText(
                text, fontSize, minFontSize, color, fontWeight, fontStyle, fontFamily,
                letterSpacing, textAlign, modifier
        )
        mode == "smart" -> SmartTitleText(
                text, fontSize, minFontSize, color, fontWeight, fontStyle, fontFamily,
                letterSpacing, lineHeight, textAlign, modifier
        )
        else -> Text( // "marquee", and the fallback for any value this build doesn't know yet.
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier.basicMarquee()
        )
    }
}

/** Single line, font size stepped down until it fits - no wrap, no scroll. */
@Composable
private fun ShrinkToFitTitleText(
        text: String,
        maxFontSize: TextUnit,
        minFontSize: TextUnit,
        color: Color,
        fontWeight: FontWeight?,
        fontStyle: FontStyle?,
        fontFamily: FontFamily?,
        letterSpacing: TextUnit,
        textAlign: TextAlign,
        modifier: Modifier
) {
    var fontSize by remember(text, maxFontSize) { mutableStateOf(maxFontSize) }
    var ready by remember(text, maxFontSize) { mutableStateOf(false) }
    Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            // Each shrink step is invisible until the size settles, so the user never sees the
            // intermediate too-large frame the layout pass measured to trigger the next step.
            modifier = modifier.graphicsLayer { alpha = if (ready) 1f else 0f },
            onTextLayout = { result ->
                if (result.hasVisualOverflow && fontSize > minFontSize) {
                    fontSize = (fontSize.value - 1f).coerceAtLeast(minFontSize.value).sp
                } else {
                    ready = true
                }
            }
    )
}

/** Cascades shrink (one line, stepped down to [minFontSize]) then wrap (two lines at that floor
 *  size) - the same combination the classic face's "Automatic" title mode already offers. */
@Composable
private fun SmartTitleText(
        text: String,
        maxFontSize: TextUnit,
        minFontSize: TextUnit,
        color: Color,
        fontWeight: FontWeight?,
        fontStyle: FontStyle?,
        fontFamily: FontFamily?,
        letterSpacing: TextUnit,
        lineHeight: TextUnit,
        textAlign: TextAlign,
        modifier: Modifier
) {
    var fontSize by remember(text, maxFontSize) { mutableStateOf(maxFontSize) }
    var maxLines by remember(text, maxFontSize) { mutableStateOf(1) }
    var ready by remember(text, maxFontSize) { mutableStateOf(false) }
    Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            // Only meaningful once maxLines reaches 2; harmless while still on a single line.
            lineHeight = lineHeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.graphicsLayer { alpha = if (ready) 1f else 0f },
            onTextLayout = { result ->
                when {
                    !result.hasVisualOverflow -> ready = true
                    fontSize > minFontSize ->
                        fontSize = (fontSize.value - 1f).coerceAtLeast(minFontSize.value).sp
                    maxLines == 1 -> maxLines = 2
                    // Already at the floor size on two lines and still overflowing: accept the
                    // ellipsis rather than looping forever on a title with no room left to give.
                    else -> ready = true
                }
            }
    )
}

/**
 * The playing app's source icon, drawn immediately before an artist line (see
 * [NowPlayingFaceState.sourceIcon] and MiscPreferences.WEAR_SHOW_SOURCE_ICON).
 *
 * The phone normally sends the media notification's small icon - a monochrome template glyph
 * already rasterized on a padded optical canvas - so it is fitted whole rather than
 * circle-cropped, which would clip the glyph's edges; the launcher-icon fallback stays
 * presentable under the same treatment. Callers pass a [size] proportional to their own artist
 * typography: the curated faces set 8sp uppercase micro-type against Immersive's 13sp, and a
 * single shared icon size would dominate those lines instead of annotating them.
 *
 * Emits nothing in ambient mode - the AOD contract is outline-only and free of filled artwork.
 */
@Composable
internal fun SourceIconGlyph(state: NowPlayingFaceState, size: Dp, tint: Color) {
    val icon = state.sourceIcon
    if (icon == null || state.ambient) return
    Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // The notification small icon is a flat white template: tinted to the caller's own
            // artist colour (alpha included) it reads as part of that line instead of a foreign
            // white blob. The launcher-icon fallback is real artwork and stays untinted.
            colorFilter = if (state.sourceIconTemplate) ColorFilter.tint(tint) else null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * .27f))
    )
    Spacer(Modifier.width(size * .33f))
}

/**
 * The source-app glyph for ambient (AOD) mode. Unlike [SourceIconGlyph] this renders *only* a
 * template (monochrome notification small-icon), flattened to the ambient [tint] - honouring the
 * outline-only AOD contract, which is why a full-colour launcher icon is deliberately skipped here.
 * Placed next to the ambient artist line so the AOD shows which app is playing, matching the awake
 * faces.
 */
@Composable
internal fun AmbientSourceIconGlyph(state: NowPlayingFaceState, size: Dp, tint: Color) {
    val icon = state.sourceIcon
    if (icon == null || !state.sourceIconTemplate) return
    Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * .27f))
    )
    Spacer(Modifier.width(size * .3f))
}
