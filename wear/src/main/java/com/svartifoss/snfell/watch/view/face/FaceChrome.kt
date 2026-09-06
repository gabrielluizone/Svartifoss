package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.svartifoss.snfell.common.RoundScreenText
import com.svartifoss.snfell.common.TextBlockAlign
import com.svartifoss.snfell.common.TextBlockPosition
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.svartifoss.snfell.common.BitmapBlur
import com.svartifoss.snfell.common.OverlayBackdropPatterns
import com.svartifoss.snfell.common.WatchTypography
import com.svartifoss.snfell.common.TitleTextMode
import androidx.core.graphics.ColorUtils
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.R as commonR
import com.svartifoss.snfell.common.CoverShape
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.PlayerShadingStyle
import com.svartifoss.snfell.common.ResolvedBackgroundLayer
import com.svartifoss.snfell.common.SHADING_MAX_MULTIPLIER
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.view.compose.drawAccentFloorGlow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable

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
 * Draws everything between the artwork and this face's own content, bottom layer first.
 *
 * Shared by Expressive and every curated layout, which is the key distinction between a layout
 * (where controls live) and a background (how artwork/album colour fills the screen).
 *
 * One Canvas for the whole stack, deliberately. The order of these treatments is now a user
 * choice, and the only way a composable can express "this goes under that" is to be drawn earlier
 * in the same draw pass - splitting them across sibling composables would put the ordering back
 * into whichever order the file happens to call them in, which is exactly the fixed arrangement
 * the stack replaced.
 */
@Composable
internal fun PlayerBackgroundTreatment(state: NowPlayingFaceState) {
    val palette = backgroundWashPalette(state)
    val layers = state.backgroundLayers
    if (layers.isEmpty()) return

    Canvas(Modifier.fillMaxSize()) {
        layers.forEach { layer -> drawBackgroundLayer(layer, palette, ambient = state.ambient) }
    }
}

/**
 * The album tones every authored wash composes itself from, resolved once per draw.
 *
 * A wash never takes a single colour: each one is built from several tones of the record at fixed
 * relationships, which is why a [com.svartifoss.snfell.common.BackgroundLayerKind.WASH] layer has
 * no colour control while the other two kinds do.
 */
internal data class BackgroundWashPalette(
        val primary: Color,
        val secondary: Color,
        val tertiary: Color,
        val deep: Color,
        val surface: Color,
        val accent: Int,
        /** Raw, untuned - the shading treatments tone it differently from the washes. */
        val secondaryAccent: Int,
        val materialSurface: Int,
        val materialSoftened: Boolean
)

internal fun backgroundWashPalette(state: NowPlayingFaceState) = BackgroundWashPalette(
        primary = Color(PaletteTransforms.tunedFaceColor(state.accentColor, .62f, .74f)),
        secondary = Color(PaletteTransforms.tunedFaceColor(
                state.secondaryAccentColor, .58f, .70f)),
        tertiary = Color(PaletteTransforms.tunedFaceColor(
                state.tertiaryAccentColor, .62f, .72f)),
        deep = Color(PaletteTransforms.tunedFaceColor(state.accentColor, .075f, .48f)),
        surface = Color(PaletteTransforms.tunedFaceColor(
                state.secondaryAccentColor, .16f, .42f)),
        accent = state.accentColor,
        secondaryAccent = state.secondaryAccentColor,
        materialSurface = state.materialSurfaceColor,
        materialSoftened = state.materialSurfaceSoftened)

/** Dispatches one resolved layer to the drawing its kind already had. */
private fun DrawScope.drawBackgroundLayer(
        layer: ResolvedBackgroundLayer,
        palette: BackgroundWashPalette,
        ambient: Boolean
) {
    when (layer) {
        is ResolvedBackgroundLayer.Wash ->
            drawBackgroundWash(layer.style, layer.strength, palette)

        is ResolvedBackgroundLayer.Shade -> drawShadingTreatment(
                style = layer.style,
                rawStrength = layer.strength,
                shade = Color(layer.color),
                accent = palette.accent,
                secondaryAccent = palette.secondaryAccent)

        // Ambient must not carry a large lit area, and this is the one layer big and bright
        // enough to be it. The others are darkening passes, which cost an always-on panel nothing.
        is ResolvedBackgroundLayer.Floor ->
            if (!ambient) drawAccentFloorGlow(
                    Color(layer.color), layer.style, alphaScale = layer.strength)
    }
}

/**
 * One authored background treatment, at [authoredStrength] of its designed scrim depth.
 *
 * [authoredStrength] scales the *scrim* and not the style's own tint, which is the same thing the
 * intensity control has always modulated - a Poster without its warm cast is not a lighter Poster,
 * it is a different background. Fading a wash out of the composition entirely is done by removing
 * the layer, which is a thing the stack can express and the old single picker could not.
 */
@Suppress("CyclomaticComplexMethod")
private fun DrawScope.drawBackgroundWash(
        style: PlayerBackgroundStyle,
        authoredStrength: Float,
        palette: BackgroundWashPalette
) {
    fun opacity(base: Float): Float = base.coerceIn(0f, 1f)
    fun authoredOpacity(base: Float): Float =
            (base * authoredStrength).coerceIn(0f, 1f)

    val primary = palette.primary
    val secondary = palette.secondary
    val tertiary = palette.tertiary
    val deep = palette.deep
    val surface = palette.surface

    run {
        when (style) {
            PlayerBackgroundStyle.COVER,
            PlayerBackgroundStyle.BLUR,
            // Frosting lives in the bitmap itself (FrostedEdges), so like every other plain
            // artwork treatment this layer draws nothing on top of it.
            PlayerBackgroundStyle.FROSTED,
            PlayerBackgroundStyle.BLACK_AND_WHITE,
            PlayerBackgroundStyle.BLURRED_BLACK_AND_WHITE,
            PlayerBackgroundStyle.SQUARE_SHARP,
            PlayerBackgroundStyle.SQUARE_SOFT,
            PlayerBackgroundStyle.SQUARE,
            PlayerBackgroundStyle.FILTER_WARM,
            PlayerBackgroundStyle.FILTER_COOL,
            PlayerBackgroundStyle.FILTER_GOLDEN,
            PlayerBackgroundStyle.FILTER_ROSE,
            PlayerBackgroundStyle.FILTER_VINTAGE,
            PlayerBackgroundStyle.FILTER_FADED,
            PlayerBackgroundStyle.FILTER_MATTE,
            PlayerBackgroundStyle.FILTER_VIVID,
            PlayerBackgroundStyle.FILTER_PUNCH,
            PlayerBackgroundStyle.FILTER_PASTEL,
            PlayerBackgroundStyle.FILTER_SEPIA,
            PlayerBackgroundStyle.FILTER_CYANOTYPE,
            PlayerBackgroundStyle.FILTER_TEAL_ORANGE,
            PlayerBackgroundStyle.FILTER_HIGH_CONTRAST,
            PlayerBackgroundStyle.FILTER_SOFT_LIGHT,
            PlayerBackgroundStyle.FILTER_NIGHT -> Unit

            PlayerBackgroundStyle.EXPRESSIVE,
            PlayerBackgroundStyle.EXPRESSIVE_NO_BLUR -> {
                val tint = Color(PaletteTransforms.tonalSurface(
                        palette.accent, .30f, .30f, .90f))
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
                        palette.materialSurface,
                        lightness = if (palette.materialSoftened) .36f else .26f,
                        minSat = if (palette.materialSoftened) 0f else .30f,
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

            PlayerBackgroundStyle.OCEAN -> drawRect(brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    .48f to tertiary.copy(alpha = opacity(.22f)),
                    1f to Color(0xFF063C4C).copy(alpha = authoredOpacity(.78f))))

            PlayerBackgroundStyle.SUNSET -> drawRect(brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    .46f to Color(0xFFFF6F61).copy(alpha = opacity(.28f)),
                    .72f to Color(0xFFFFB347).copy(alpha = opacity(.34f)),
                    1f to Color.Black.copy(alpha = authoredOpacity(.62f)),
                    start = Offset.Zero, end = Offset(size.width, size.height)))

            PlayerBackgroundStyle.SPOTLIGHT -> drawRect(brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    .44f to deep.copy(alpha = opacity(.16f)),
                    1f to Color.Black.copy(alpha = authoredOpacity(.90f)),
                    center = center, radius = size.minDimension * .72f))

            PlayerBackgroundStyle.GLASS_VEIL -> {
                drawRect(Color.White.copy(alpha = opacity(.12f)))
                drawCircle(
                        brush = Brush.sweepGradient(
                                colors = listOf(Color.White, primary, Color.White, secondary, Color.White),
                                center = center),
                        radius = size.minDimension * .485f,
                        center = center,
                        alpha = opacity(.72f),
                        style = Stroke(width = size.minDimension * .018f))
            }

            PlayerBackgroundStyle.VELVET -> {
                drawRect(Color(0xFF120B16).copy(alpha = authoredOpacity(.74f)))
                drawCircle(
                        brush = Brush.radialGradient(
                                0f to primary.copy(alpha = opacity(.34f)),
                                1f to Color.Transparent),
                        radius = size.minDimension * .62f,
                        center = Offset(size.width * .34f, size.height * .76f))
            }

            PlayerBackgroundStyle.NOIR -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.58f)))
                drawRect(brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = opacity(.10f)),
                        .52f to Color.Transparent,
                        1f to Color.Black.copy(alpha = authoredOpacity(.82f)),
                        center = Offset(size.width * .5f, size.height * .42f),
                        radius = size.minDimension * .58f))
            }

            PlayerBackgroundStyle.ICE -> drawRect(brush = Brush.verticalGradient(
                    0f to Color(0xFFB9F3FF).copy(alpha = opacity(.34f)),
                    .48f to Color(0xFF3B82C4).copy(alpha = opacity(.22f)),
                    1f to Color(0xFF061426).copy(alpha = authoredOpacity(.76f))))

            PlayerBackgroundStyle.ROSE -> drawRect(brush = Brush.radialGradient(
                    0f to Color(0xFFFF8CAB).copy(alpha = opacity(.42f)),
                    .55f to primary.copy(alpha = opacity(.18f)),
                    1f to Color(0xFF1B0810).copy(alpha = authoredOpacity(.68f)),
                    center = Offset(size.width * .72f, size.height * .74f),
                    radius = size.minDimension * .72f))

            PlayerBackgroundStyle.PRISMATIC -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.28f)))
                drawRect(brush = Brush.linearGradient(
                        listOf(primary.copy(alpha = opacity(.48f)),
                                secondary.copy(alpha = opacity(.22f)),
                                tertiary.copy(alpha = opacity(.36f)),
                                Color.Black.copy(alpha = authoredOpacity(.72f))),
                        start = Offset.Zero, end = Offset(size.width, size.height)))
                drawCircle(brush = Brush.sweepGradient(listOf(primary, secondary, tertiary, primary)),
                        radius = size.minDimension * .41f,
                        style = Stroke(width = size.minDimension * .10f),
                        alpha = opacity(.42f))
            }

            PlayerBackgroundStyle.CRESCENT -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.34f)))
                drawArc(brush = Brush.sweepGradient(listOf(Color.Transparent, primary,
                        secondary.copy(alpha = .28f), Color.Transparent)),
                        startAngle = 138f, sweepAngle = 196f, useCenter = false,
                        topLeft = Offset(size.width * -.04f, size.height * -.04f),
                        size = androidx.compose.ui.geometry.Size(size.width * 1.08f,
                                size.height * 1.08f), style = Stroke(size.minDimension * .12f),
                        alpha = opacity(.72f))
            }

            PlayerBackgroundStyle.TIDAL -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.20f)))
                listOf(.34f to primary, .55f to secondary, .76f to tertiary).forEachIndexed { index, (y, color) ->
                    val wave = Path().apply {
                        moveTo(0f, size.height * y)
                        cubicTo(size.width * .24f, size.height * (y - .11f + index * .02f),
                                size.width * .70f, size.height * (y + .10f),
                                size.width, size.height * (y - .03f))
                    }
                    drawPath(wave, color = color, style = Stroke(size.minDimension *
                            (.11f - index * .03f)), alpha = authoredOpacity(.78f))
                }
            }

            PlayerBackgroundStyle.PAPER -> {
                drawRect(Color(0xFFFFF3DF).copy(alpha = authoredOpacity(.18f)))
                drawRect(brush = Brush.verticalGradient(
                        0f to Color.Transparent, .64f to Color.Transparent,
                        1f to Color.Black.copy(alpha = authoredOpacity(.62f))))
                drawRect(Color.White.copy(alpha = opacity(.44f)),
                        topLeft = Offset(size.minDimension * .065f, size.minDimension * .065f),
                        size = androidx.compose.ui.geometry.Size(
                                size.width - size.minDimension * .13f,
                                size.height - size.minDimension * .13f), style = Stroke(size.minDimension * .009f))
            }

            PlayerBackgroundStyle.LANTERN -> {
                drawRect(brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = authoredOpacity(.55f)),
                        .46f to Color.Transparent,
                        1f to deep.copy(alpha = authoredOpacity(.72f))))
                drawRect(brush = Brush.radialGradient(
                        0f to Color(0xFFFFC857).copy(alpha = opacity(.52f)),
                        .45f to primary.copy(alpha = opacity(.18f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .5f, size.height * .82f),
                        radius = size.minDimension * .41f))
            }

            PlayerBackgroundStyle.MIRAGE -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.18f)))
                drawRect(brush = Brush.radialGradient(0f to primary.copy(alpha = opacity(.46f)),
                        1f to Color.Transparent, center = Offset(size.width * .08f,
                                size.height * .38f), radius = size.minDimension * .58f))
                drawRect(brush = Brush.radialGradient(0f to secondary.copy(alpha = opacity(.42f)),
                        1f to Color.Transparent, center = Offset(size.width * .92f,
                                size.height * .62f), radius = size.minDimension * .58f))
            }

            PlayerBackgroundStyle.GRID -> {
                drawRect(deep.copy(alpha = authoredOpacity(.64f)))
                for (step in 1..5) {
                    val x = size.width * step / 6f
                    val y = size.height * step / 6f
                    drawLine(primary.copy(alpha = opacity(.22f)), Offset(x, 0f),
                            Offset(x, size.height), strokeWidth = size.minDimension * .006f,
                            alpha = authoredOpacity(.72f))
                    drawLine(primary.copy(alpha = opacity(.22f)), Offset(0f, y),
                            Offset(size.width, y), strokeWidth = size.minDimension * .006f,
                            alpha = authoredOpacity(.72f))
                }
            }

            PlayerBackgroundStyle.NOCTURNE -> {
                drawRect(Color(0xFF070B25).copy(alpha = authoredOpacity(.72f)))
                drawRect(brush = Brush.radialGradient(0f to tertiary.copy(alpha = opacity(.36f)),
                        1f to Color.Transparent, center = Offset(size.width * .68f,
                                size.height * .28f), radius = size.minDimension * .58f))
                listOf(.16f to .22f, .72f to .18f, .37f to .58f, .82f to .72f).forEach { (x, y) ->
                    drawCircle(Color.White.copy(alpha = opacity(.62f)),
                            radius = size.minDimension * .009f,
                            center = Offset(size.width * x, size.height * y))
                }
            }

            PlayerBackgroundStyle.CLOUD -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.16f)))
                listOf(Triple(.22f, .34f, primary), Triple(.74f, .30f, secondary),
                        Triple(.50f, .78f, tertiary)).forEach { (x, y, color) ->
                    drawRect(brush = Brush.radialGradient(0f to color.copy(alpha = opacity(.32f)),
                            1f to Color.Transparent, center = Offset(size.width * x,
                                    size.height * y), radius = size.minDimension * .49f))
                }
            }

            PlayerBackgroundStyle.LIQUID -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.30f)))
                listOf(Triple(.18f, .72f, primary), Triple(.62f, .42f, secondary),
                        Triple(.86f, .76f, tertiary)).forEach { (x, y, color) ->
                    drawRect(brush = Brush.radialGradient(0f to color.copy(alpha = opacity(.52f)),
                            .48f to color.copy(alpha = opacity(.12f)), 1f to Color.Transparent,
                            center = Offset(size.width * x, size.height * y),
                            radius = size.minDimension * .38f))
                }
            }

            PlayerBackgroundStyle.MONOLITH -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.58f)))
                drawRect(brush = Brush.horizontalGradient(
                        0f to primary.copy(alpha = opacity(.58f)),
                        .70f to secondary.copy(alpha = opacity(.12f)),
                        1f to Color.Transparent), size = size.copy(width = size.width * .48f))
                drawRect(brush = Brush.verticalGradient(0f to Color.Transparent,
                        .64f to Color.Transparent,
                        1f to Color.Black.copy(alpha = authoredOpacity(.76f))))
            }

            PlayerBackgroundStyle.SPLIT_TONE -> {
                drawRect(brush = Brush.verticalGradient(listOf(
                        primary.copy(alpha = opacity(.36f)),
                        secondary.copy(alpha = opacity(.20f)),
                        deep.copy(alpha = authoredOpacity(.78f)))))
                drawLine(Color.White.copy(alpha = opacity(.48f)),
                        Offset(0f, size.height * .50f), Offset(size.width, size.height * .50f),
                        strokeWidth = size.minDimension * .006f,
                        alpha = authoredOpacity(.76f))
            }

            PlayerBackgroundStyle.GRADIENT -> drawRect(brush = Brush.linearGradient(
                    0f to primary.copy(alpha = opacity(.46f)),
                    1f to secondary.copy(alpha = opacity(.30f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)))
            PlayerBackgroundStyle.DUOTONE -> drawRect(brush = Brush.horizontalGradient(
                    0f to primary.copy(alpha = opacity(.40f)),
                    1f to secondary.copy(alpha = opacity(.40f))))
            PlayerBackgroundStyle.BANDS -> drawRect(brush = Brush.verticalGradient(listOf(
                    tertiary.copy(alpha = opacity(.30f)),
                    primary.copy(alpha = opacity(.44f)),
                    primary.copy(alpha = opacity(.18f)),
                    secondary.copy(alpha = opacity(.38f)),
                    Color.Black.copy(alpha = authoredOpacity(.82f)))))
            PlayerBackgroundStyle.VIGNETTE -> {
                drawRect(primary.copy(alpha = opacity(.20f)))
                drawRect(brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        .52f to Color.Black.copy(alpha = authoredOpacity(.14f)),
                        1f to Color.Black.copy(alpha = authoredOpacity(.90f)),
                        center = center,
                        radius = size.minDimension * .68f))
            }
            PlayerBackgroundStyle.GRAPHITE -> {
                drawRect(Color(0xFF111318).copy(alpha = authoredOpacity(.86f)))
                drawRect(brush = Brush.linearGradient(
                        0f to Color(0xFF292D34).copy(alpha = authoredOpacity(.62f)),
                        .5f to Color.Transparent,
                        1f to Color(0xFF1D2026).copy(alpha = authoredOpacity(.62f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)))
            }
            PlayerBackgroundStyle.CINEMA -> drawRect(brush = Brush.verticalGradient(listOf(
                    Color.Black.copy(alpha = authoredOpacity(.94f)),
                    Color.Black.copy(alpha = authoredOpacity(.88f)),
                    primary.copy(alpha = opacity(.34f)),
                    secondary.copy(alpha = opacity(.24f)),
                    Color.Black.copy(alpha = authoredOpacity(.88f)),
                    Color.Black.copy(alpha = authoredOpacity(.94f)))))

            PlayerBackgroundStyle.ACRYLIC -> drawRect(brush = Brush.linearGradient(
                    0f to primary.copy(alpha = opacity(.40f)),
                    1f to Color.Black.copy(alpha = authoredOpacity(.72f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)))

            PlayerBackgroundStyle.MESH -> {
                drawRect(primary.copy(alpha = opacity(.14f)))
                drawRect(brush = Brush.radialGradient(
                        0f to secondary.copy(alpha = opacity(.44f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .18f, size.height * .22f),
                        radius = size.minDimension * .72f))
                drawRect(brush = Brush.radialGradient(
                        0f to tertiary.copy(alpha = opacity(.38f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .86f, size.height * .78f),
                        radius = size.minDimension * .68f))
            }

            PlayerBackgroundStyle.NEBULA -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.42f)))
                drawRect(brush = Brush.radialGradient(
                        0f to primary.copy(alpha = opacity(.42f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .16f, size.height * .24f),
                        radius = size.minDimension * .70f))
                drawRect(brush = Brush.radialGradient(
                        0f to secondary.copy(alpha = opacity(.38f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .84f, size.height * .32f),
                        radius = size.minDimension * .64f))
                drawRect(brush = Brush.radialGradient(
                        0f to tertiary.copy(alpha = opacity(.34f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .50f, size.height * .94f),
                        radius = size.minDimension * .58f))
            }

            PlayerBackgroundStyle.BIOLUMINESCENCE -> {
                drawRect(Color(0xFF041A19).copy(alpha = authoredOpacity(.66f)))
                drawRect(brush = Brush.radialGradient(
                        0f to primary.copy(alpha = opacity(.52f)),
                        0.5f to Color(0xFF0A6A62).copy(alpha = opacity(.26f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .20f, size.height * .74f),
                        radius = size.minDimension * .62f))
                drawRect(brush = Brush.radialGradient(
                        0f to tertiary.copy(alpha = opacity(.44f)),
                        0.5f to Color(0xFF1AB5A2).copy(alpha = opacity(.18f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .82f, size.height * .24f),
                        radius = size.minDimension * .54f))
            }

            PlayerBackgroundStyle.IRIDESCENT -> drawRect(brush = Brush.linearGradient(listOf(
                    tertiary.copy(alpha = opacity(.36f)),
                    Color(0xFF4A2F72).copy(alpha = opacity(.44f)),
                    primary.copy(alpha = opacity(.42f)),
                    secondary.copy(alpha = opacity(.30f)),
                    Color(0xFF0B101A).copy(alpha = authoredOpacity(.82f))),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)))

            PlayerBackgroundStyle.ORBIT -> {
                drawRect(primary.copy(alpha = opacity(.13f)))
                drawRect(brush = Brush.radialGradient(
                        0f to secondary.copy(alpha = opacity(.46f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .16f, size.height * .30f),
                        radius = size.minDimension * .64f))
                drawRect(brush = Brush.radialGradient(
                        0f to tertiary.copy(alpha = opacity(.38f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .84f, size.height * .72f),
                        radius = size.minDimension * .62f))
                drawRect(brush = Brush.radialGradient(
                        0f to primary.copy(alpha = opacity(.34f)),
                        1f to Color.Transparent,
                        center = Offset(size.width * .50f, size.height * .50f),
                        radius = size.minDimension * .26f))
            }

            PlayerBackgroundStyle.INK_WASH -> {
                drawRect(Color.Black.copy(alpha = authoredOpacity(.34f)))
                drawRect(brush = Brush.linearGradient(
                        0f to primary.copy(alpha = opacity(.44f)),
                        .5f to secondary.copy(alpha = opacity(.16f)),
                        1f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)))
            }

            PlayerBackgroundStyle.BLOSSOM -> drawRect(brush = Brush.linearGradient(listOf(
                    Color(0xFF160B1D).copy(alpha = authoredOpacity(.80f)),
                    Color(0xFF542047).copy(alpha = opacity(.52f)),
                    Color(0xFFB84B74).copy(alpha = opacity(.44f)),
                    tertiary.copy(alpha = opacity(.34f)),
                    Color(0xFF08050B).copy(alpha = authoredOpacity(.84f))),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f)))

            PlayerBackgroundStyle.FJORD -> drawRect(brush = Brush.linearGradient(listOf(
                    Color(0xFF0A2030).copy(alpha = authoredOpacity(.76f)),
                    tertiary.copy(alpha = opacity(.34f)),
                    Color(0xFF0A5960).copy(alpha = opacity(.44f)),
                    Color.Black.copy(alpha = authoredOpacity(.86f))),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)))

            // The drawn patterns go through the shared android.graphics implementation rather
            // than being re-authored in DrawScope: hand-drawn geometry written twice is the exact
            // shape of drift the preview-parity rules exist to prevent, and `nativeCanvas` makes
            // the second write unnecessary.
            PlayerBackgroundStyle.DOT_MATRIX -> drawIntoCanvas { target ->
                OverlayBackdropPatterns.drawDotMatrix(
                        target.nativeCanvas, patternBounds(), density,
                        baseColor = android.graphics.Color.TRANSPARENT,
                        dotColor = primary.copy(alpha = opacity(.44f)).toArgb())
            }
            PlayerBackgroundStyle.SCANLINES -> drawIntoCanvas { target ->
                OverlayBackdropPatterns.drawScanlines(
                        target.nativeCanvas, patternBounds(), density,
                        baseColor = Color.Black.copy(alpha = authoredOpacity(.34f)).toArgb(),
                        lineColor = Color.Black.copy(alpha = authoredOpacity(.52f)).toArgb())
            }
            PlayerBackgroundStyle.RADAR -> drawIntoCanvas { target ->
                val bounds = patternBounds()
                OverlayBackdropPatterns.drawRadarRings(
                        target.nativeCanvas, bounds, density,
                        cx = bounds.centerX(), cy = bounds.centerY(),
                        radius = minOf(bounds.width(), bounds.height()) / 2f,
                        baseColor = primary.copy(alpha = opacity(.12f)).toArgb(),
                        ringColor = tertiary.copy(alpha = opacity(.50f)).toArgb(),
                        sweepColor = primary.copy(alpha = opacity(.42f)).toArgb())
            }
            PlayerBackgroundStyle.CONTOUR -> drawIntoCanvas { target ->
                val bounds = patternBounds()
                OverlayBackdropPatterns.drawContourLines(
                        target.nativeCanvas, bounds, density,
                        cx = bounds.centerX(), cy = bounds.centerY(),
                        radius = minOf(bounds.width(), bounds.height()) / 2f,
                        baseColor = primary.copy(alpha = opacity(.12f)).toArgb(),
                        lineColor = secondary.copy(alpha = opacity(.46f)).toArgb(),
                        accent = primary.toArgb())
            }
            PlayerBackgroundStyle.FACETED -> drawIntoCanvas { target ->
                OverlayBackdropPatterns.drawFacetedCrystal(
                        target.nativeCanvas, patternBounds(), density,
                        primary = primary.copy(alpha = opacity(.42f)).toArgb(),
                        secondary = secondary.copy(alpha = opacity(.38f)).toArgb(),
                        tertiary = tertiary.copy(alpha = opacity(.34f)).toArgb(),
                        accent = primary.toArgb())
            }

            // The flat fills are opaque by definition: they hide the artwork, so no alpha.
            PlayerBackgroundStyle.SOLID_ALBUM -> drawRect(Color(flatFillArgb(palette, 0)))
            PlayerBackgroundStyle.SOLID_SECONDARY -> drawRect(Color(flatFillArgb(palette, 1)))
            PlayerBackgroundStyle.SOLID_TERTIARY -> drawRect(Color(flatFillArgb(palette, 2)))

            PlayerBackgroundStyle.GLASS -> drawRect(brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = opacity(.18f)),
                    1f to Color.Black.copy(alpha = authoredOpacity(.72f))))
            PlayerBackgroundStyle.MIDNIGHT -> drawRect(brush = Brush.verticalGradient(listOf(
                    tertiary.copy(alpha = opacity(.30f)),
                    Color(0xFF070914).copy(alpha = authoredOpacity(.66f)),
                    Color.Black.copy(alpha = authoredOpacity(.86f)))))
            PlayerBackgroundStyle.SMOKE -> drawRect(brush = Brush.linearGradient(listOf(
                    tertiary.copy(alpha = opacity(.24f)),
                    Color(0xFF323238).copy(alpha = authoredOpacity(.56f)),
                    Color.Black.copy(alpha = authoredOpacity(.82f))),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)))
            PlayerBackgroundStyle.TIDELINE -> drawRect(brush = Brush.verticalGradient(listOf(
                    Color(0xFF031423).copy(alpha = authoredOpacity(.72f)),
                    Color(0xFF07516A).copy(alpha = opacity(.52f)),
                    secondary.copy(alpha = opacity(.30f)),
                    Color.Black.copy(alpha = authoredOpacity(.84f)))))

            PlayerBackgroundStyle.ECLIPSE,
            PlayerBackgroundStyle.HIDDEN -> drawRect(Color.Black)
        }
    }
}

/**
 * The stack for a face that paints its own opaque backdrop.
 *
 * Split is the only one: its two-band panel *is* its background, so the shared layer underneath it
 * would simply be covered, and it places this immediately after its own backdrop instead. The base
 * treatment is deliberately not drawn here even in the legacy arrangement - it never reached the
 * screen on this face, and putting it back because the stack now names it would redesign Split.
 */
/** The DrawScope's own bounds as the `android.graphics.RectF` the shared pattern helpers take. */
/** The opaque tone a flat album fill paints, from the same triad every other surface reads. */
private fun flatFillArgb(palette: BackgroundWashPalette, slot: Int): Int =
        PaletteTransforms.tonalSurface(
                when (slot) {
                    0 -> palette.primary
                    1 -> palette.secondary
                    else -> palette.tertiary
                }.toArgb(),
                .24f,
                PaletteTransforms.FACE_MIN_SAT,
                PaletteTransforms.FACE_MAX_SAT)

private fun DrawScope.patternBounds(): android.graphics.RectF =
        android.graphics.RectF(0f, 0f, size.width, size.height)

@Composable
internal fun PlayerBackgroundLayers(state: NowPlayingFaceState) {
    val layers = state.backgroundLayers
    if (layers.isEmpty()) return
    val palette = backgroundWashPalette(state)
    // Above the backdrop and below the face content. Over the top it would tint the text; under an
    // opaque backdrop it would disappear.
    Canvas(Modifier.fillMaxSize()) {
        layers.forEach { layer -> drawBackgroundLayer(layer, palette, ambient = state.ambient) }
    }
}

/**
 * One shading treatment, at [strength] of its authored maximum, tinted [shade].
 *
 * [shade] is black by default, or the album/desaturated/custom tone the host resolved.
 * ALBUM_TINT/DUOTONE keep their own album tones regardless - they *are* album styles, so a colour
 * choice on them would be answering a question they already answer.
 */
@Suppress("CyclomaticComplexMethod")
private fun DrawScope.drawShadingTreatment(
        style: PlayerShadingStyle,
        rawStrength: Float,
        shade: Color,
        accent: Int,
        secondaryAccent: Int
) {
    val strength = rawStrength.coerceIn(0f, SHADING_MAX_MULTIPLIER)
    fun shaded(maxAlpha: Float) = shade.copy(alpha = (maxAlpha * strength).coerceIn(0f, 1f))
    val primary = Color(faceTonal(accent, .13f, .25f, .78f))
    val secondary = Color(faceTonal(secondaryAccent, .13f, .25f, .78f))

    run {
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

            PlayerShadingStyle.TOP_FADE -> drawRect(
                    brush = Brush.verticalGradient(
                            0f to shaded(.94f),
                            .58f to Color.Transparent,
                            1f to Color.Transparent))

            PlayerShadingStyle.CENTER_SPOTLIGHT -> drawRect(
                    brush = Brush.radialGradient(
                            0f to Color.Transparent,
                            .38f to Color.Transparent,
                            1f to shaded(.90f),
                            center = center,
                            radius = size.maxDimension * .64f))

            PlayerShadingStyle.DIAGONAL -> drawRect(
                    brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            .40f to Color.Transparent,
                            1f to shaded(.92f),
                            start = Offset(size.width, 0f),
                            end = Offset(0f, size.height)))

            PlayerShadingStyle.LEFT_CURTAIN -> drawRect(
                    brush = Brush.horizontalGradient(
                            0f to shaded(.90f),
                            .56f to Color.Transparent,
                            1f to Color.Transparent))

            PlayerShadingStyle.RIGHT_CURTAIN -> drawRect(
                    brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            .44f to Color.Transparent,
                            1f to shaded(.90f)))

            PlayerShadingStyle.CENTER_BAND -> drawRect(
                    brush = Brush.verticalGradient(
                            0f to shaded(.84f),
                            .40f to Color.Transparent,
                            .60f to Color.Transparent,
                            1f to shaded(.84f)))

            PlayerShadingStyle.CROSSFADE -> drawRect(
                    brush = Brush.linearGradient(
                            0f to shaded(.78f),
                            .5f to Color.Transparent,
                            1f to shaded(.78f),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)))

            // Never reaches a layer: `follow` is the absence of a choice, and the stack resolves
            // it to the neutral bottom fade before a layer is ever built from it.
            PlayerShadingStyle.FOLLOW -> Unit
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
@OptIn(ExperimentalFoundationApi::class)
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
        /** Optional long press. Exists for the mini buttons a face hosts: every slot carries a
         *  separate long-press action, and a face-drawn button that ran only the tap would be the
         *  same control doing less than the shared row's version of it. */
        onLongClick: (() -> Unit)? = null,
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
                    .then(
                            if (onLongClick == null) {
                                Modifier.clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        onClick = onClick)
                            } else {
                                Modifier.combinedClickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        onClick = onClick,
                                        onLongClick = onLongClick)
                            }
                    )
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
                        .align(state.blockPlacement(Alignment.Center))
                        .fillMaxWidth()
                        // The chord inset has to come *after* fillMaxWidth, not before: applied
                        // first it was re-expanded by the fill and the block sat on the bezel
                        // exactly as it had before. Chrono's block is centred by design, so that
                        // is the band it is measured in.
                        .padding(horizontal = maxOf(
                                screen * .08f,
                                state.blockSafeSideInset(
                                        screen,
                                        designedTop = CHRONO_BLOCK_TOP_FRACTION,
                                        designedHeight = CHRONO_BLOCK_HEIGHT_FRACTION)))
                        .padding(vertical = state.blockSafeVerticalInset(screen)),
                horizontalAlignment = state.blockAlignment(Alignment.CenterHorizontally)
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
                        horizontalArrangement = state.blockArrangement(Arrangement.Center),
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

/**
 * Resolves the shared [CoverShape] vocabulary into a Compose [Shape] for artwork of [size].
 *
 * Here rather than in one face because two draw a cover the user can reshape - Carousel's rail
 * cards and Note's disc - and the corner is a *fraction* of the size, so the two must derive it the
 * same way or the same choice reads as a different shape on each.
 */
internal fun CoverShape.toComposeShape(size: Dp): Shape =
        if (this == CoverShape.CIRCLE) CircleShape
        else RoundedCornerShape(size * cornerFraction)

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
        state: NowPlayingFaceState,
        fontSize: TextUnit,
        color: Color,
        modifier: Modifier = Modifier,
        fontWeight: FontWeight? = null,
        fontStyle: FontStyle? = null,
        fontFamily: FontFamily? = null,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        lineHeight: TextUnit = TextUnit.Unspecified,
        textAlign: TextAlign = TextAlign.Center,
        minFontSize: TextUnit = (fontSize.value * 0.62f).sp,
        typography: WatchTypography.TextSpec = WatchTypography.IDENTITY_TEXT,
        maxLines: Int? = null,
        shadow: Shadow? = state.titleShadow,
        outline: TextOutlinePaint? = state.titleOutline,
        backdrop: Color? = state.titleBackdrop,
        onLineCount: ((Int) -> Unit)? = null
) = AdaptiveTitleText(
        // Applied here rather than in the AnnotatedString overload below: that one is Note's own
        // "Artist: Title" sentence, which cases its artist and title spans independently *before*
        // building the AnnotatedString (see NoteLine) - a generic case transform run over the
        // finished string could shift multi-byte case expansions (German ß -> SS) across a span
        // boundary built for the un-cased text. A plain String has no such boundary to protect.
        text = AnnotatedString(typography.case.apply(text)),
        mode = mode,
        state = state,
        fontSize = fontSize,
        color = color,
        modifier = modifier,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
        textAlign = textAlign,
        minFontSize = minFontSize,
        typography = typography,
        maxLines = maxLines,
        shadow = shadow,
        outline = outline,
        backdrop = backdrop,
        onLineCount = onLineCount)

/**
 * As [AdaptiveTitleText], for a line that is more than one run of text.
 *
 * The overload exists for the Note face, whose whole composition is a single `Artist: Title`
 * sentence built as an [AnnotatedString] - two colours in one flowing line, which the `String`
 * entry point cannot express. It had no way in here and so drew a plain wrapped [Text], which
 * meant the Title text behaviour setting did nothing at all on that face. The cascade itself is
 * identical; only the text type differs, and the `String` version above is a thin delegate so
 * there is still exactly one implementation of each mode.
 */
@Composable
internal fun AdaptiveTitleText(
        text: AnnotatedString,
        mode: String,
        state: NowPlayingFaceState,
        fontSize: TextUnit,
        color: Color,
        modifier: Modifier = Modifier,
        fontWeight: FontWeight? = null,
        fontStyle: FontStyle? = null,
        fontFamily: FontFamily? = null,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        /** Passed through untouched, including to the shrunk-down text. An absolute value therefore
         *  does *not* scale with the size this settles on, and a title that shrinks two or three
         *  steps ends up with lines spaced further apart than they are tall. Leave it unspecified
         *  unless the face genuinely needs a fixed leading. */
        lineHeight: TextUnit = TextUnit.Unspecified,
        textAlign: TextAlign = TextAlign.Center,
        minFontSize: TextUnit = (fontSize.value * 0.62f).sp,
        typography: WatchTypography.TextSpec = WatchTypography.IDENTITY_TEXT,
        /**
         * Hard ceiling on the line count, for a face whose title sits in a band of fixed height.
         *
         * The mode is the user's choice and stays theirs; this only says how much room the
         * composition has for the answer. Null - every face but Frame - means "as many as the mode
         * asks for", which is what every caller did before this existed.
         */
        maxLines: Int? = null,
        /**
         * The resolved title shadow, or null for none.
         *
         * Delivered through [LocalTextStyle] rather than as a parameter on every `Text` below,
         * because this function has four rendering branches and two of them are separate
         * composables with signatures of their own. A composition local reaches all four -
         * including the shrink cascade's intermediate measurements - without threading a parameter
         * through each, and a shadow that reached only some of the title modes would be exactly
         * the quiet per-mode drift this function exists to end.
         *
         * Defaulted off [NowPlayingFaceState] rather than left at null: every one of the fifteen
         * call sites had to opt *in* to a title effect that the host had already resolved for
         * them, and not one of them did - so `wear_title_shadow_style`, the outline and the
         * backdrop reached the classic View face and the artist line and were inert on every
         * Compose face. Reading the host's answer here is what makes opting out impossible.
         */
        shadow: Shadow? = state.titleShadow,
        /**
         * The resolved outline, or null for none.
         *
         * An outline is genuinely **two drawing passes** - no text API on either platform strokes
         * and fills in one - so this branch renders the whole cascade twice, stroke under fill,
         * and every input but the colour and draw style is identical. Three details keep the two
         * copies honest. The caller's `modifier` goes on the [Box] rather than on either copy, so
         * a `weight` or `align` from an enclosing Row/Column still lands on a child of that scope.
         * `propagateMinConstraints` hands the children the Box's own minimum width, which is what
         * makes a `fillMaxWidth` caller behave exactly as it did before this existed - without it,
         * a filled title would become wrap-content and its internal `textAlign` would stop
         * meaning anything. And `onLineCount` is reported by the fill pass alone: the stroke copy
         * measures identically, so a second report would be a duplicate rather than news.
         *
         * The one visible cost is a scrolling title, where both copies run their own
         * `basicMarquee`. They enter composition in the same frame with identical parameters, so
         * they travel in step; nothing enforces that beyond their inputs being the same.
         *
         * Defaults off [NowPlayingFaceState] for [shadow]'s reason.
         */
        outline: TextOutlinePaint? = state.titleOutline,
        /**
         * A filled box behind each line, or null for none.
         *
         * Delivered through [LocalTextStyle] like the shadow, and applied *before* the outline
         * branch below so the box lands under both drawing passes rather than only under the fill.
         *
         * Defaults off [NowPlayingFaceState] for [shadow]'s reason.
         */
        backdrop: Color? = state.titleBackdrop,
        // How many lines the text actually settled on. A face on a round screen cannot inset its
        // title correctly without this: the usable chord depends on how deep the block reaches, so
        // the caller has to know whether it wrapped before it can pick a width (see
        // RoundScreenText). Reported after the size/line cascade below has converged, not on every
        // intermediate measurement, or the caller would chase sizes that are about to change.
        onLineCount: ((Int) -> Unit)? = null
) {
    // Resolved once, here, rather than at each of the four rendering branches below - two of which
    // are private composables that never receive the state. Every title path in the app delegates
    // into this overload, so this is the single point at which the user's alignment override can
    // reach all of them, and a face cannot opt out of it by passing its own value.
    @Suppress("NAME_SHADOWING") val textAlign = state.blockTextAlign(textAlign)
    if (backdrop != null) {
        CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(background = backdrop)) {
            AdaptiveTitleText(
                    text, mode, state, fontSize, color, modifier, fontWeight, fontStyle,
                    fontFamily, letterSpacing, lineHeight, textAlign, minFontSize, typography,
                    maxLines, shadow = shadow, outline = outline, backdrop = null,
                    onLineCount = onLineCount)
        }
        return
    }
    if (outline != null) {
        val strokeWidth = with(LocalDensity.current) {
            (typography.scaled(fontSize.value).sp.toPx() * outline.widthFraction)
                    .coerceAtLeast(outline.minWidthPx)
        }
        Box(modifier = modifier, propagateMinConstraints = true) {
            CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.copy(
                            drawStyle = Stroke(width = strokeWidth))) {
                AdaptiveTitleText(
                        text, mode, state, fontSize, outline.color, Modifier, fontWeight,
                        fontStyle, fontFamily, letterSpacing, lineHeight, textAlign, minFontSize,
                        typography, maxLines, shadow = shadow, outline = null, backdrop = null,
                        onLineCount = null)
            }
            AdaptiveTitleText(
                    text, mode, state, fontSize, color, Modifier, fontWeight, fontStyle,
                    fontFamily, letterSpacing, lineHeight, textAlign, minFontSize, typography,
                    maxLines,
                    // The shadow rides the stroke pass, which is the outermost thing drawn - a
                    // shadow cast by the fill would sit inside its own outline and be invisible.
                    shadow = null, outline = null, backdrop = null, onLineCount = onLineCount)
        }
        return
    }
    if (shadow != null) {
        CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(shadow = shadow)) {
            AdaptiveTitleText(
                    text, mode, state, fontSize, color, modifier, fontWeight, fontStyle,
                    fontFamily, letterSpacing, lineHeight, textAlign, minFontSize, typography,
                    maxLines,
                    shadow = null, outline = null, backdrop = null, onLineCount = onLineCount)
        }
        return
    }
    // Each field's default value means "keep what this face designed for this line", not "use a
    // plain 400/upright/unspaced default" - otherwise simply shipping these controls would flatten
    // every face that deliberately sets its title Bold or its artist line wide-tracked. Only a
    // value the user actually moved away from the identity overrides the face.
    val fontSize = if (typography.scale == 1f) fontSize else typography.scaled(fontSize.value).sp
    val minFontSize =
            if (typography.scale == 1f) minFontSize else typography.scaled(minFontSize.value).sp
    val fontWeight = if (typography.weight == 400) fontWeight else FontWeight(typography.weight)
    val fontStyle = if (typography.italic) FontStyle.Italic else fontStyle
    val letterSpacing =
            if (typography.trackingEm == 0f) letterSpacing else typography.trackingEm.em
    val color = if (typography.alpha == 1f) color else color.copy(alpha = color.alpha * typography.alpha)

    val wrapLines = TitleTextMode.wrapLines(mode)?.let {
        if (maxLines == null) it else minOf(it, maxLines)
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
                modifier = modifier,
                onTextLayout = { onLineCount?.invoke(it.lineCount) }
        )
        mode == "shrink" -> ShrinkToFitTitleText(
                text, fontSize, minFontSize, color, fontWeight, fontStyle, fontFamily,
                letterSpacing, textAlign, modifier, onLineCount
        )
        mode == "smart" -> SmartTitleText(
                text, fontSize, minFontSize, color, fontWeight, fontStyle, fontFamily,
                letterSpacing, lineHeight, textAlign, modifier, maxLines, onLineCount
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
                modifier = modifier.basicMarquee(),
                onTextLayout = { onLineCount?.invoke(1) }
        )
    }
}

/** Single line, font size stepped down until it fits - no wrap, no scroll. */
@Composable
private fun ShrinkToFitTitleText(
        text: AnnotatedString,
        maxFontSize: TextUnit,
        minFontSize: TextUnit,
        color: Color,
        fontWeight: FontWeight?,
        fontStyle: FontStyle?,
        fontFamily: FontFamily?,
        letterSpacing: TextUnit,
        textAlign: TextAlign,
        modifier: Modifier,
        onLineCount: ((Int) -> Unit)? = null
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
                    // Always one line by construction; reported anyway so a caller can size its
                    // band the same way regardless of which mode the user picked.
                    onLineCount?.invoke(1)
                }
            }
    )
}

/** Cascades shrink (one line, stepped down to [minFontSize]) then wrap (two lines at that floor
 *  size) - the same combination the classic face's "Automatic" title mode already offers. */
@Composable
private fun SmartTitleText(
        text: AnnotatedString,
        maxFontSize: TextUnit,
        minFontSize: TextUnit,
        color: Color,
        fontWeight: FontWeight?,
        fontStyle: FontStyle?,
        fontFamily: FontFamily?,
        letterSpacing: TextUnit,
        lineHeight: TextUnit,
        textAlign: TextAlign,
        modifier: Modifier,
        /** See [AdaptiveTitleText]'s own `maxLines`: null leaves the two-line escalation intact. */
        lineCeiling: Int? = null,
        onLineCount: ((Int) -> Unit)? = null
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
                    !result.hasVisualOverflow -> {
                        ready = true
                        onLineCount?.invoke(result.lineCount)
                    }
                    fontSize > minFontSize ->
                        fontSize = (fontSize.value - 1f).coerceAtLeast(minFontSize.value).sp
                    maxLines == 1 && (lineCeiling == null || lineCeiling > 1) -> maxLines = 2
                    // Already at the floor size on two lines and still overflowing: accept the
                    // ellipsis rather than looping forever on a title with no room left to give.
                    else -> {
                        ready = true
                        onLineCount?.invoke(result.lineCount)
                    }
                }
            }
    )
}

/**
 * The colour a face should draw its title in: the user's choice when they made one, otherwise the
 * [designed] colour that face chose for itself.
 *
 * The face's own **alpha is always preserved**, which is the whole reason this is a function rather
 * than a plain null-coalesce. Several faces deliberately sit their title at .88-.94 opacity against
 * a bright cover; taking a user colour wholesale would snap all of them to fully opaque and quietly
 * restyle compositions the user never touched. Only the hue changes.
 */
@Composable
internal fun titleTextColor(state: NowPlayingFaceState, designed: Color): Color {
    val chosen = state.titleColor ?: return designed
    return Color(chosen).copy(alpha = designed.alpha)
}

/**
 * Where this face's block of track text should line up, given what the face itself composed.
 *
 * The three [blockAlignment]/[blockTextAlign]/[blockPlacement] helpers are the whole of the
 * placement contract, and the shape of them is deliberate: a face passes what it *designed*, and
 * gets back either that value or the user's override. `follow` - the default for both keys - is
 * what makes it safe to offer these controls on every face rather than on a hand-kept allow-list,
 * because until somebody chooses a side, every one of these returns the face's own answer and the
 * composition is untouched.
 *
 * The alternative shape, a face reading [NowPlayingFaceState.textBlockAlign] and switching on it,
 * is the one this exists to prevent: it puts the `follow` fallback at every call site, which is the
 * arrangement that has already gone wrong twice here (the title effects that defaulted to null and
 * were never opted into, and the artist lines that styled a bare Text). Here the fallback is in one
 * function and a call site cannot silently opt out of it - it can only fail to call it at all,
 * which is what `TextBlockPlacementTest` sweeps for.
 */
internal fun NowPlayingFaceState.blockAlignment(
        designed: Alignment.Horizontal
): Alignment.Horizontal = when (textBlockAlign) {
    TextBlockAlign.FOLLOW -> designed
    TextBlockAlign.START -> Alignment.Start
    TextBlockAlign.CENTER -> Alignment.CenterHorizontally
    TextBlockAlign.END -> Alignment.End
}

/**
 * The text alignment inside the block - see [blockAlignment].
 *
 * Applied inside [AdaptiveTitleText], [ArtistLineText], [TitleLineText] and [TrackTimeText] at the
 * point each finally hands an alignment to a `Text`, rather than at their call sites. That is what
 * makes the control reach *every* face without twenty-one edits and without any of them being able
 * to opt out by accident - a face passes the alignment it designed, exactly as before, and the
 * override is resolved on top of it. It is idempotent, so a nested helper re-applying it is
 * harmless.
 *
 * What it cannot do on its own is move a wrap-content line: a `Text` that does not fill its parent
 * has no spare width for an alignment to act in, so a face whose text is centred by its *container*
 * also needs [blockAlignment] on that container. Both are called from one place per face for that
 * reason.
 */
internal fun NowPlayingFaceState.blockTextAlign(designed: TextAlign): TextAlign =
        when (textBlockAlign) {
            TextBlockAlign.FOLLOW -> designed
            TextBlockAlign.START -> TextAlign.Start
            TextBlockAlign.CENTER -> TextAlign.Center
            TextBlockAlign.END -> TextAlign.End
        }

/**
 * [blockTextAlign] for a helper whose alignment parameter is optional.
 *
 * Null means "leave it to Compose", which is [TitleLineText]'s default, and stays null while the
 * user has chosen `follow` - an override has to be able to act on a line that had no explicit
 * alignment, or the control would silently miss exactly the faces that never named one.
 */
internal fun NowPlayingFaceState.blockTextAlignOrNull(designed: TextAlign?): TextAlign? =
        when (textBlockAlign) {
            TextBlockAlign.FOLLOW -> designed
            else -> blockTextAlign(designed ?: TextAlign.Center)
        }

/** The one text mode that needs no cascade: a single ellipsized line at the designed size. */
private const val STATIC_TEXT_MODE = "static"

/** Row-level arrangement for a block whose line is a Row (an icon beside the text). */
internal fun NowPlayingFaceState.blockArrangement(
        designed: Arrangement.Horizontal
): Arrangement.Horizontal = when (textBlockAlign) {
    TextBlockAlign.FOLLOW -> designed
    TextBlockAlign.START -> Arrangement.Start
    TextBlockAlign.CENTER -> Arrangement.Center
    TextBlockAlign.END -> Arrangement.End
}

/**
 * Where a block of track text actually ends up, as top/bottom depths in screen-height fractions.
 *
 * Depth is what [RoundScreenText] measures against, and the whole difficulty is that a moved block
 * is not at the depth its author composed it at. [designedTop] and [designedHeight] describe the
 * band the *face* laid out; this resolves that against the placement the user chose, so every
 * inset below is measured where the text will really be rather than where it was drawn.
 *
 * `follow` returns the face's own band unchanged, which is what keeps an unmoved face pixel-identical.
 */
internal fun NowPlayingFaceState.blockBand(
        designedTop: Float,
        designedHeight: Float
): ClosedFloatingPointRange<Float> {
    val height = designedHeight.coerceIn(0f, 1f)
    val top = when (textBlockPosition) {
        TextBlockPosition.FOLLOW -> designedTop
        TextBlockPosition.TOP -> blockTopClearanceFraction()
        TextBlockPosition.MIDDLE -> (1f - height) / 2f
        TextBlockPosition.BOTTOM -> 1f - blockBottomClearanceFraction() - height
    }.coerceIn(0f, 1f)
    return top..(top + height).coerceAtMost(1f)
}

/**
 * Extra side inset a text block needs once the user has moved it out of the band it was composed in.
 *
 * The screen is round, so the usable chord collapses towards the top and bottom, *and* towards the
 * sides at any depth away from centre. Every face's own side padding was tuned for one depth and
 * one alignment - a block its author centred, which never reaches the glass at all. Both halves of
 * that assumption are what these controls break, and until this function was corrected only one of
 * them was covered: it keyed off [textBlockPosition] alone and returned zero whenever that was
 * `follow`, so *lining a block up against an edge without moving it vertically* - the commonest
 * thing a user does here, and the one the picker offers first - got no protection at all. On a
 * face whose text is grounded low by design (Immersive, Depth) that put the artist line, one line
 * deeper than the title, straight under the bezel.
 *
 * Zero only while **both** axes still follow the face. Otherwise the band is resolved through
 * [blockBand] and measured for real, so a face that passes its own [designedTop]/[designedHeight]
 * gets an answer for where its text is rather than for the conservative stand-in the defaults
 * describe - roughly two lines of track text centred on the screen, which over-insets a short
 * block slightly rather than under-insetting a tall one.
 *
 * This is still **one inset for the whole block**, i.e. the deepest line's answer applied to all of
 * them. That is the right keep-out and the wrong layout; a face that wants each line to stop at the
 * glass on its own terms uses [blockLineInsets] instead.
 */
internal fun NowPlayingFaceState.blockSafeSideInset(
        screen: Dp,
        designedTop: Float = DEFAULT_DESIGNED_BLOCK_TOP,
        designedHeight: Float = ASSUMED_BLOCK_FRACTION
): Dp {
    if (!blockPlacementOverridden) return 0.dp
    val band = blockBand(designedTop, designedHeight)
    return screen * RoundScreenText.sideInsetFor(band.start, band.endInclusive)
}

/**
 * Per-element side insets for a block whose lines should each stop at the glass on their own terms.
 *
 * The complaint [blockSafeSideInset] cannot answer: aligning a block to an edge moved the title,
 * the artist and the elapsed readout to the *same* x, and on a round screen that is only ever
 * correct for one of the three. The artist sits a line below the title, where the chord is
 * narrower, so it either clipped or - once the shared inset was widened enough to save it - the
 * title lost width it had every right to. Each element gets its own answer here, so an
 * edge-aligned block steps inwards as it descends and every line ends up against the glass rather
 * than against a margin borrowed from its neighbour.
 *
 * [elementHeights] are the elements' real heights in draw order, gaps folded into the element above
 * them (see [RoundScreenText.lineSideInsets]); [designedAnchor]/[designedEdgeFraction] say where
 * the face itself put the block, which is the band measured while the placement still follows it.
 * [floor] is the face's own designed padding,
 * which survives as a minimum: near the centre the circle constrains nothing and the composition
 * still wants its margin there.
 *
 * Returns [BlockLineInsets], which is split into an `outer` container padding plus a per-element
 * `extra` rather than one padding per element, because the container is what an alignment acts in:
 * padding only the children would leave a `fillMaxWidth` column measuring the full screen and the
 * outermost line would still be placed on the bezel.
 */
internal fun NowPlayingFaceState.blockLineInsets(
        screen: Dp,
        designedAnchor: BlockAnchor,
        designedEdgeFraction: Float,
        elementHeights: List<Dp>,
        floor: Dp = 0.dp
): BlockLineInsets {
    val floorFraction = if (screen > 0.dp) floor / screen else 0f
    if (!blockPlacementOverridden || elementHeights.isEmpty()) {
        return BlockLineInsets(floor, List(elementHeights.size) { 0.dp })
    }
    val fractions = elementHeights.map { if (screen > 0.dp) it / screen else 0f }
    val height = fractions.sum()
    val designedTop = when (designedAnchor) {
        BlockAnchor.TOP -> designedEdgeFraction
        BlockAnchor.CENTER -> (1f - height) / 2f
        BlockAnchor.BOTTOM -> 1f - designedEdgeFraction - height
    }
    val band = blockBand(designedTop, height)
    val insets = RoundScreenText.lineSideInsets(band.start, fractions)
            .map { maxOf(it, floorFraction) }
    val outer = insets.min()
    return BlockLineInsets(screen * outer, insets.map { screen * (it - outer) })
}

/**
 * Which edge a face's own composition anchors its text block to.
 *
 * Only ever used to *reconstruct where the face put the block* so the chord can be measured there
 * under `follow`; the placement the user chose is resolved separately in [blockBand]. It is an
 * anchor plus a distance rather than a bare top fraction because a centred block does not know its
 * own top until its elements have been measured, and making every call site compute
 * `(1 - height) / 2` itself is three chances to get the same expression wrong.
 */
internal enum class BlockAnchor { TOP, CENTER, BOTTOM }

/**
 * The result of [blockLineInsets]: what the container reserves, and what each element adds to it.
 *
 * Kept as two halves rather than as one padding per element so a caller cannot apply the second
 * without the first - see [blockLineInsets] for why the container's own inset is load-bearing.
 */
internal data class BlockLineInsets(val outer: Dp, private val extras: List<Dp>) {
    /** Additional padding for the element at [index], on top of [outer]. Zero past the end. */
    fun extra(index: Int): Dp = extras.getOrElse(index) { 0.dp }
}

/** True once the user has moved this block on either axis, which is when the circle starts to bind. */
internal val NowPlayingFaceState.blockPlacementOverridden: Boolean
    get() = textBlockAlign != TextBlockAlign.FOLLOW || textBlockPosition != TextBlockPosition.FOLLOW

/**
 * The narrowest a fixed-width text column is allowed to become once the chord has taken its cut.
 *
 * A face that composes its metadata at a fixed fraction of the screen has to subtract the inset
 * from that width rather than pad around it - padding leaves the column exactly as wide as it was
 * and simply hangs the surplus off the far edge. Subtracting can in principle reach zero on a very
 * low band, so it stops here instead: a clipped line is legible and says the block is too low,
 * where a one-character column says nothing at all. The same argument
 * [RoundScreenText]'s own `MAX_INSET` makes.
 */
internal val MIN_TEXT_COLUMN: Dp = 48.dp

/** Chrono's clock-plus-two-lines block, centred by design - the band its chord is measured in. */
private const val CHRONO_BLOCK_TOP_FRACTION = .33f
private const val CHRONO_BLOCK_HEIGHT_FRACTION = .34f

/**
 * The vertical keep-out required by a block explicitly moved to an edge.
 *
 * `Alignment.Top` and `Alignment.Bottom` otherwise mean the rectangular Compose parent, not the
 * round display. In particular Top placed text under FaceClock, which occupies the first 5dp plus
 * its measured glyph height. The clock's typography is user-scalable, so reserve a conservative
 * fraction instead of hard-coding its default 15sp metrics. Faces add these insets to the metadata
 * container only when the user has actually overridden its designed placement.
 */
internal fun NowPlayingFaceState.blockSafeVerticalInset(screen: Dp): Dp = when (textBlockPosition) {
    TextBlockPosition.TOP -> screen * blockTopClearanceFraction()
    TextBlockPosition.BOTTOM -> screen * blockBottomClearanceFraction()
    TextBlockPosition.FOLLOW, TextBlockPosition.MIDDLE -> 0.dp
}

/** Keep a face's authored offset only while its placement still follows that face. */
internal fun NowPlayingFaceState.blockDesignedTopPadding(designed: Dp): Dp =
        if (textBlockPosition == TextBlockPosition.FOLLOW) designed else 0.dp

/** Keep a face's authored bottom offset only while its placement still follows that face. */
internal fun NowPlayingFaceState.blockDesignedBottomPadding(designed: Dp): Dp =
        if (textBlockPosition == TextBlockPosition.FOLLOW) designed else 0.dp

/** Vertical arrangement for a full-height metadata container. */
internal fun NowPlayingFaceState.blockVerticalArrangement(
        designed: Arrangement.Vertical
): Arrangement.Vertical = when (textBlockPosition) {
    TextBlockPosition.FOLLOW -> designed
    TextBlockPosition.TOP -> Arrangement.Top
    TextBlockPosition.MIDDLE -> Arrangement.Center
    TextBlockPosition.BOTTOM -> Arrangement.Bottom
}

private fun NowPlayingFaceState.blockTopClearanceFraction(): Float =
        if (showClock) CLOCK_CLEARANCE_FRACTION else EDGE_MARGIN_FRACTION

/** The shared mini-button row or Up Next pill occupies the bottom band when its top is below 1. */
private fun NowPlayingFaceState.blockBottomClearanceFraction(): Float = maxOf(
        EDGE_MARGIN_FRACTION,
        (1f - miniButtonsTopFraction).coerceAtLeast(0f))

/** Roughly two lines of track text plus its breathing room - the block most faces compose. */
private const val ASSUMED_BLOCK_FRACTION = 0.20f

/**
 * The stand-in band for a caller that has not told [blockSafeSideInset] where its text lives: a
 * block of [ASSUMED_BLOCK_FRACTION] centred on the screen. Conservative in the safe direction -
 * the middle is the widest part of the glass, so a face that actually composes lower gets *less*
 * inset than it needs rather than a wrong one, and every face that can be moved passes its own
 * numbers instead.
 */
private const val DEFAULT_DESIGNED_BLOCK_TOP = 0.5f - ASSUMED_BLOCK_FRACTION / 2f

/** How close to the glass a moved block is allowed to sit before the chord is measured. */
private const val EDGE_MARGIN_FRACTION = 0.08f

/** Clock top padding + its maximum configurable glyph height + a visible gap, in screen fractions. */
private const val CLOCK_CLEARANCE_FRACTION = 0.22f

/**
 * Where the block sits in the face's own Box, on both axes at once.
 *
 * Both halves come from [designed] when the user has not overridden them, so a face that grounds
 * its text ([Alignment.BottomCenter], as Immersive does) keeps doing exactly that. The two axes are
 * resolved independently: choosing only a side must not also re-anchor the block vertically.
 */
internal fun NowPlayingFaceState.blockPlacement(designed: Alignment): Alignment {
    val horizontal = when (textBlockAlign) {
        TextBlockAlign.FOLLOW -> null
        TextBlockAlign.START -> Alignment.Start
        TextBlockAlign.CENTER -> Alignment.CenterHorizontally
        TextBlockAlign.END -> Alignment.End
    }
    val vertical = when (textBlockPosition) {
        TextBlockPosition.FOLLOW -> null
        TextBlockPosition.TOP -> Alignment.Top
        TextBlockPosition.MIDDLE -> Alignment.CenterVertically
        TextBlockPosition.BOTTOM -> Alignment.Bottom
    }
    if (horizontal == null && vertical == null) return designed
    val resolvedHorizontal = horizontal ?: designedHorizontal(designed)
    val resolvedVertical = vertical ?: designedVertical(designed)
    return combine(resolvedHorizontal, resolvedVertical)
}

/**
 * The two-axis [Alignment] for a horizontal and a vertical half.
 *
 * Compose offers no combinator for this, so the nine named values are listed. Deliberately not a
 * hand-built `BiasAlignment`: the named values are what every face already passes, and matching
 * them keeps `blockPlacement` returning the identical object a face would have used itself
 * whenever the user has overridden neither axis.
 */
private fun combine(
        horizontal: Alignment.Horizontal,
        vertical: Alignment.Vertical
): Alignment = when (vertical) {
    Alignment.Top -> when (horizontal) {
        Alignment.Start -> Alignment.TopStart
        Alignment.End -> Alignment.TopEnd
        else -> Alignment.TopCenter
    }
    Alignment.Bottom -> when (horizontal) {
        Alignment.Start -> Alignment.BottomStart
        Alignment.End -> Alignment.BottomEnd
        else -> Alignment.BottomCenter
    }
    else -> when (horizontal) {
        Alignment.Start -> Alignment.CenterStart
        Alignment.End -> Alignment.CenterEnd
        else -> Alignment.Center
    }
}

/**
 * Splits a two-axis [Alignment] back into the axis a caller did not override.
 *
 * Compose exposes no accessor for either half, so this compares against the nine standard values -
 * which is every value a face here actually passes. An unrecognised custom [Alignment] (a
 * `BiasAlignment` with an odd bias) falls back to centre on that axis rather than throwing; a face
 * would have to construct one deliberately, and none does.
 */
private fun designedHorizontal(alignment: Alignment): Alignment.Horizontal = when (alignment) {
    Alignment.TopStart, Alignment.CenterStart, Alignment.BottomStart -> Alignment.Start
    Alignment.TopEnd, Alignment.CenterEnd, Alignment.BottomEnd -> Alignment.End
    else -> Alignment.CenterHorizontally
}

private fun designedVertical(alignment: Alignment): Alignment.Vertical = when (alignment) {
    Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd -> Alignment.Top
    Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd -> Alignment.Bottom
    else -> Alignment.CenterVertically
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
    // The user's size/opacity controls are applied here rather than at each face's call site, so
    // every layout honours them from one place and the trailing Spacer keeps its proportion to the
    // glyph (a resized icon with a fixed gap reads as misaligned against the artist line).
    val spec = state.sourceIconTypography
    val size = if (spec.scale == 1f) size else size * spec.scale
    val tint = if (spec.alpha == 1f) tint else tint.copy(alpha = tint.alpha * spec.alpha)
    Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // The notification small icon is a flat white template: tinted to the caller's own
            // artist colour (alpha included) it reads as part of that line instead of a foreign
            // white blob. The launcher-icon fallback is real artwork and stays untinted - its own
            // opacity is applied through alpha instead.
            colorFilter = if (state.sourceIconTemplate) ColorFilter.tint(tint) else null,
            alpha = if (state.sourceIconTemplate) 1f else spec.alpha,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * .27f))
    )
    Spacer(Modifier.width(size * .33f))
}

/**
 * A track artist line honouring the user's per-element typography ([NowPlayingFaceState.artistTypography])
 * and, since [MiscPreferences.WEAR_ARTIST_TEXT_MODE][com.svartifoss.snfell.common.MiscPreferences.WEAR_ARTIST_TEXT_MODE],
 * their chosen behaviour for a line that does not fit.
 *
 * It used to be a plain single ellipsized line on every face, on the reasoning that only titles are
 * long enough to need [AdaptiveTitleText]'s strategies. That was never true of the artist - a long
 * credit was simply cut everywhere, with no marquee, no shrink-to-fit, and no way to stop a face
 * that wrapped it - so the two lines now share one vocabulary and one implementation of each mode.
 *
 * `static` keeps the old fast path deliberately, and it is the default: a single ellipsized line at
 * the fixed size *is* what every face drew before this control existed, so adding it changes
 * nothing anywhere until a behaviour is picked. It is also genuinely cheaper - no measure/settle
 * cascade and no sibling composables - which matters on a line most faces draw at every state
 * change.
 */
@Composable
internal fun ArtistLineText(
        text: String,
        state: NowPlayingFaceState,
        color: Color,
        fontSize: TextUnit,
        modifier: Modifier = Modifier,
        fontWeight: FontWeight? = null,
        lineHeight: TextUnit = TextUnit.Unspecified,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        textAlign: TextAlign = TextAlign.Center,
        /** Defaulted off the state inside the helper, never at the call site: fifteen faces call
         *  this, and a parameter they each had to opt into is how the title effects ended up
         *  resolved, published and read by nothing. */
        mode: String = state.artistTextMode
) {
    if (mode != STATIC_TEXT_MODE) {
        // Every other mode is a measure-and-settle cascade that already exists once, for the
        // title. Reusing it is what keeps "marquee" meaning the same thing on both lines rather
        // than becoming two implementations that drift; the artist's own typography, colour,
        // family and effects are all passed through, so it is still the artist line in every
        // respect but the overflow strategy.
        AdaptiveTitleText(
                text = text,
                mode = mode,
                state = state,
                fontSize = fontSize,
                color = color,
                modifier = modifier,
                fontWeight = fontWeight,
                fontStyle = state.artistFontStyle,
                fontFamily = state.artistFont,
                letterSpacing = letterSpacing,
                lineHeight = lineHeight,
                textAlign = textAlign,
                typography = state.artistTypography,
                shadow = state.artistShadow,
                outline = state.artistOutline,
                backdrop = state.artistBackdrop)
        return
    }
    val spec = state.artistTypography
    state.artistOutline?.let { outline ->
        val strokeWidth = with(LocalDensity.current) {
            (spec.scaled(fontSize.value).sp.toPx() * outline.widthFraction)
                    .coerceAtLeast(outline.minWidthPx)
        }
        Box(modifier = modifier, propagateMinConstraints = true) {
            ArtistLineTextPass(
                    text, state, outline.color, fontSize, Modifier, fontWeight, lineHeight,
                    letterSpacing, textAlign, Stroke(width = strokeWidth), state.artistShadow)
            ArtistLineTextPass(
                    text, state, color, fontSize, Modifier, fontWeight, lineHeight, letterSpacing,
                    textAlign, drawStyle = null, shadow = null)
        }
        return
    }
    ArtistLineTextPass(
            text, state, color, fontSize, modifier, fontWeight, lineHeight, letterSpacing,
            textAlign, drawStyle = null, shadow = state.artistShadow)
}

/**
 * One drawing pass of the artist line.
 *
 * Split out so an outline can render the identical line twice - stroke under fill - without a
 * second copy of the typography merge. A null [drawStyle] is the ordinary filled pass.
 */
@Composable
private fun ArtistLineTextPass(
        text: String,
        state: NowPlayingFaceState,
        color: Color,
        fontSize: TextUnit,
        modifier: Modifier,
        fontWeight: FontWeight?,
        lineHeight: TextUnit,
        letterSpacing: TextUnit,
        textAlign: TextAlign,
        drawStyle: DrawStyle?,
        shadow: Shadow?
) {
    val spec = state.artistTypography
    Text(
            // Unlike AdaptiveTitleText there is no size cascade and no sibling composables here,
            // so the style can be handed straight to the one Text.
            style = LocalTextStyle.current.let { base ->
                var style = base
                if (shadow != null) style = style.copy(shadow = shadow)
                if (drawStyle != null) style = style.copy(drawStyle = drawStyle)
                // Only the stroke pass paints the box. Painting it on both would lay the fill
                // pass's opaque-ish plate over the stroke that was just drawn under it.
                val backdrop = state.artistBackdrop
                if (backdrop != null && (drawStyle != null || state.artistOutline == null)) {
                    style = style.copy(background = backdrop)
                }
                style
            },
            text = spec.case.apply(text),
            color = if (spec.alpha == 1f) color else color.copy(alpha = color.alpha * spec.alpha),
            fontSize = if (spec.scale == 1f) fontSize else spec.scaled(fontSize.value).sp,
            fontWeight = if (spec.weight == 400) fontWeight else FontWeight(spec.weight),
            fontStyle = state.artistFontStyle,
            fontFamily = state.artistFont,
            lineHeight = lineHeight,
            letterSpacing = if (spec.trackingEm == 0f) letterSpacing else spec.trackingEm.em,
            textAlign = state.blockTextAlign(textAlign),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
    )
}

/**
 * A track title drawn as one plain line (or a fixed number of them).
 *
 * [AdaptiveTitleText] is the entry point for a title that owns its band and may shrink, wrap or
 * scroll to fit it. Two faces cannot use that cascade: Chat's title sits start-aligned inside a
 * bubble it does not size, and Metadata's is a fixed two-line header above a table whose row
 * budget is measured. Both drew a bare [Text] instead, which meant they read whichever properties
 * they happened to name - Chat hardcoded Bold, so the weight control did nothing there - and none
 * of the shadow, outline or backdrop the host had already resolved.
 *
 * So this is [ArtistLineText] for the title: the same merge, the same two-pass outline, and the
 * same rule that a default value means "keep what this face designed".
 */
@Composable
internal fun TitleLineText(
        text: String,
        state: NowPlayingFaceState,
        color: Color,
        fontSize: TextUnit,
        modifier: Modifier = Modifier,
        fontWeight: FontWeight? = null,
        fontFamily: FontFamily? = state.titleFont,
        lineHeight: TextUnit = TextUnit.Unspecified,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        textAlign: TextAlign? = null,
        maxLines: Int = 1
) {
    val spec = state.titleTypography
    state.titleOutline?.let { outline ->
        val strokeWidth = with(LocalDensity.current) {
            (spec.scaled(fontSize.value).sp.toPx() * outline.widthFraction)
                    .coerceAtLeast(outline.minWidthPx)
        }
        Box(modifier = modifier, propagateMinConstraints = true) {
            TitleLineTextPass(
                    text, state, outline.color, fontSize, Modifier, fontWeight, fontFamily,
                    lineHeight, letterSpacing, textAlign, maxLines,
                    Stroke(width = strokeWidth), state.titleShadow)
            TitleLineTextPass(
                    text, state, color, fontSize, Modifier, fontWeight, fontFamily, lineHeight,
                    letterSpacing, textAlign, maxLines, drawStyle = null, shadow = null)
        }
        return
    }
    TitleLineTextPass(
            text, state, color, fontSize, modifier, fontWeight, fontFamily, lineHeight,
            letterSpacing, textAlign, maxLines, drawStyle = null, shadow = state.titleShadow)
}

/** One drawing pass of [TitleLineText] - see [ArtistLineTextPass] for why this is split out. */
@Composable
private fun TitleLineTextPass(
        text: String,
        state: NowPlayingFaceState,
        color: Color,
        fontSize: TextUnit,
        modifier: Modifier,
        fontWeight: FontWeight?,
        fontFamily: FontFamily?,
        lineHeight: TextUnit,
        letterSpacing: TextUnit,
        textAlign: TextAlign?,
        maxLines: Int,
        drawStyle: DrawStyle?,
        shadow: Shadow?
) {
    val spec = state.titleTypography
    Text(
            style = LocalTextStyle.current.let { base ->
                var style = base
                if (shadow != null) style = style.copy(shadow = shadow)
                if (drawStyle != null) style = style.copy(drawStyle = drawStyle)
                // Only the stroke pass paints the box, exactly as ArtistLineTextPass does.
                val backdrop = state.titleBackdrop
                if (backdrop != null && (drawStyle != null || state.titleOutline == null)) {
                    style = style.copy(background = backdrop)
                }
                style
            },
            text = spec.case.apply(text),
            color = if (spec.alpha == 1f) color else color.copy(alpha = color.alpha * spec.alpha),
            fontSize = if (spec.scale == 1f) fontSize else spec.scaled(fontSize.value).sp,
            fontWeight = if (spec.weight == 400) fontWeight else FontWeight(spec.weight),
            fontStyle = state.titleFontStyle,
            fontFamily = fontFamily,
            lineHeight = lineHeight,
            letterSpacing = if (spec.trackingEm == 0f) letterSpacing else spec.trackingEm.em,
            textAlign = state.blockTextAlignOrNull(textAlign),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
    )
}

/**
 * An elapsed/total playback readout honoring [NowPlayingFaceState.trackTimeTypography].
 *
 * The family parameter is each composition's historical choice. When the user leaves Track time
 * on “Follow the design”, it stays intact; a selected family, including Google Sans Flex,
 * overrides it consistently across every Compose face.
 */
@Composable
internal fun TrackTimeText(
        text: String,
        state: NowPlayingFaceState,
        color: Color,
        fontSize: TextUnit,
        modifier: Modifier = Modifier,
        fontFamily: FontFamily? = null,
        fontWeight: FontWeight? = null,
        fontStyle: FontStyle? = null,
        lineHeight: TextUnit = TextUnit.Unspecified,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        textAlign: TextAlign = TextAlign.Center,
        maxLines: Int = 1,
        overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
            text = text,
            color = state.trackTimeColor(color),
            fontSize = state.trackTimeTextSize(fontSize),
            fontWeight = state.trackTimeFontWeight ?: fontWeight,
            fontStyle = state.trackTimeFontStyle ?: fontStyle,
            fontFamily = state.trackTimeFont ?: fontFamily,
            lineHeight = if (lineHeight == TextUnit.Unspecified) {
                lineHeight
            } else {
                state.trackTimeTextSize(lineHeight)
            },
            letterSpacing = state.trackTimeLetterSpacing(letterSpacing),
            textAlign = state.blockTextAlign(textAlign),
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier
    )
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

/**
 * Invisible centre region carrying the shared tap / double-tap / long-press contract.
 *
 * Most Compose faces hang those gestures off their own central play control, which works because
 * they have one. A face whose composition puts something else in the middle - Chat's voice bubble,
 * Split's cover/panel seam - has no such control, and wiring nothing leaves it with **no working
 * centre at all**: the host's `center_tap_zone` View is `GONE` for every Compose face, so nothing
 * underneath picks the gestures up. That is exactly how centre play/pause and the long-press face
 * picker came to do nothing on the first version of Chat.
 *
 * Generous on purpose. A small hit area for "long press to change the face" is the difference
 * between a gesture people find and one they never do.
 *
 * Place it *before* the face's own content so interactive controls layered above still take their
 * own taps, and keep the content itself non-clickable unless it genuinely needs to be - anything
 * clickable over this region swallows the centre gestures.
 *
 * The one exception is a face whose composition is entirely **opaque** over the centre - Frame's
 * card, Ribbon's cover rail. Nothing there takes its own taps, and drawing the region first buries
 * its own confirmation underneath the artwork: the tap works, and looks exactly like a tap that
 * missed. Those faces place it last instead, and pass [state] so the confirmation is the play/pause
 * glyph itself rather than a ring the composition would have swallowed anyway.
 */
@Composable
internal fun CenterGestureRegion(
        listener: NowPlayingFaceListener,
        size: Dp,
        /** Radius the confirmation ring expands to. Defaults to the region itself; pass a smaller
         *  value when the region is much larger than the thing the user thinks they tapped. */
        pulseSize: Dp = size,
        /**
         * When non-null, a centre *tap* also flashes the play/pause glyph for the state the tap is
         * entering - the confirmation a face with a visible transport control gets for free from
         * that control changing shape. Only the tap: a double tap opens the quick panel and a long
         * press the face picker, and neither is a playback command.
         */
        state: NowPlayingFaceState? = null
) {
    // The same expanding-ring flash the curated faces draw around their play control, and Classic
    // draws in center_tap_pulse. Without it a centre tap on a face with no visible button gives no
    // feedback at all: the artwork does not move, so a tap that worked and one that missed look
    // identical until playback happens to change.
    var pulseNonce by remember { mutableStateOf(0) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(pulseNonce) {
        if (pulseNonce > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(300))
        }
    }
    // The glyph rides its own nonce rather than the ring's: every gesture confirms with the ring,
    // but only a tap is a playback command, and flashing "pause" while the queue opens would be
    // reporting something that did not happen.
    var tapNonce by remember { mutableStateOf(0) }
    var enteringPlay by remember { mutableStateOf(false) }
    val glyph = remember { Animatable(0f) }
    LaunchedEffect(tapNonce) {
        if (tapNonce > 0) {
            glyph.snapTo(1f)
            glyph.animateTo(0f, tween(GLYPH_FLASH_FADE_MS, delayMillis = GLYPH_FLASH_HOLD_MS))
        }
    }
    Box(
            modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
                    .size(size)
                    .pointerInput(listener) {
                        detectTapGestures(
                                onTap = {
                                    pulseNonce++
                                    // The state being *entered*, sampled before the command goes
                                    // out: the phone's answer is a Bluetooth round trip away, and a
                                    // confirmation that waits for it is not a confirmation.
                                    enteringPlay = state?.playing != true
                                    tapNonce++
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
                            val target = pulseSize.toPx() / 2f
                            drawCircle(
                                    color = Color.White.copy(alpha = .55f * (1f - fraction)),
                                    radius = target * (.45f + .55f * fraction),
                                    style = Stroke(2.dp.toPx())
                            )
                        }
                    },
            contentAlignment = Alignment.Center
    ) {
        // Honours the same visibility contract as every other control: the "hidden" screen theme
        // keeps hit targets alive and draws no icons, and this is an icon.
        if (state != null && state.showControls && glyph.value > 0f) {
            val diameter = pulseSize * GLYPH_FLASH_DIAMETER_FRACTION
            Box(
                    modifier = Modifier
                            .size(diameter)
                            .graphicsLayer {
                                alpha = glyph.value
                                // Settles rather than pops: full size the instant it appears would
                                // read as an element of the face flickering, not as an answer.
                                val scale = 1f - .12f * glyph.value
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            // A cover can be any colour, so the glyph carries its own ground
                            // instead of trusting whatever the composition put behind it.
                            .background(Color.Black.copy(alpha = .38f)),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        painter = painterResource(
                                if (enteringPlay) commonR.drawable.action_play_filled
                                else commonR.drawable.action_pause_filled),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = state.screenTheme.tokens.iconAlpha),
                        modifier = Modifier.size(diameter * .5f)
                )
            }
        }
    }
}

/** How long the play/pause confirmation holds at full strength before fading, and how long the
 *  fade itself takes. Long enough to register on a wrist glance, short enough that it is gone
 *  before anyone reads it as part of the face. */
private const val GLYPH_FLASH_HOLD_MS = 220
private const val GLYPH_FLASH_FADE_MS = 320
/** Sized against the ring's own radius so the two confirmations read as one gesture. */
private const val GLYPH_FLASH_DIAMETER_FRACTION = .46f

/**
 * A blurred copy of a cover, made once per cover and radius.
 *
 * [BitmapBlur] rather than Compose's `Modifier.blur`: that modifier is a no-op below Android 12
 * and this module supports API 26, so a face relying on it would simply show a sharp photograph on
 * older watches - not a degraded version of the design but a different one. The shared blur runs
 * everywhere and is the same one the phone's preview uses, so the two agree.
 *
 * Keyed on the bitmap, so a track change pays for it and nothing else does. Falls back to the
 * sharp cover if the blur throws (a recycled bitmap on a fast skip), which is wrong-looking for one
 * frame rather than a crash. A caller whose composition needs a *minimum* amount of blur applies
 * that floor to [radiusPx] itself - that is a property of the design, not of blurring.
 */
@Composable
internal fun rememberBlurredCover(art: ImageBitmap, radiusPx: Float): ImageBitmap =
        remember(art, radiusPx) {
            runCatching {
                BitmapBlur.blur(art.asAndroidBitmap(), radiusPx).asImageBitmap()
            }.getOrDefault(art)
        }
