package com.svartifoss.snfell.watch.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.common.OverlayBackdrop
import com.svartifoss.snfell.common.OverlayBackdropPatterns
import com.svartifoss.snfell.common.PaletteTransforms
import kotlin.math.roundToInt

/**
 * Builds the full-screen background for a panel surface from the resolved [OverlayBackdrop].
 *
 * Extracted out of `MainActivity.applyOverlayBackdrop`, which was its only caller until the
 * dedicated volume and progress screens existed. Those screens are separate Activities, so with
 * the table living inside `MainActivity` they had no way to reach it and shipped with a hardcoded
 * black background - the "Shared panel appearance" setting applied to the transient overlays and
 * silently did nothing on the two screens built entirely out of that same panel. This is the usual
 * shared-resolver shape: one table, every panel surface reads it, so a backdrop added here cannot
 * reach one surface and miss the other.
 *
 * Pure apart from the two device measurements a few gradients need, which are passed in rather
 * than read from a `Context` so a caller in Compose can supply them from its own draw scope.
 */
object OverlayBackdropDrawables {

    private fun withAlpha(color: Int, alpha: Int) = ColorUtils.setAlphaComponent(color, alpha)

    /**
     * Traces this drawable's rim as a circle instead of a rectangle when the display is round.
     *
     * `GradientDrawable.setStroke` draws a border matching the drawable's own bounds - a
     * rectangle, since the View it backs (`overlay_dim`) is `match_parent` inside the square
     * content frame a round watch still lays out into. On a round display the rectangle's four
     * edge midpoints sit exactly on the physical bezel and its corners sit beyond it, off the
     * visible glass - so the stroke drew as four bright, disconnected line segments at the top,
     * bottom, left and right of the screen, invisible at the diagonals in between. The phone
     * preview never had this bug (`WatchPreviewView` draws a literal `canvas.drawCircle` on a
     * round face and a `drawRoundRect` on a square one); this is that same branch, brought to the
     * watch's own Drawable-based renderer for the three backdrops that carry a rim stroke.
     *
     * `GradientDrawable.OVAL` inscribes the whole shape - fill and stroke alike - within its
     * bounds, which for this square View is exactly the circle every other round-aware
     * measurement in this codebase already uses (`radius = minOf(width, height) / 2`). The four
     * corners of the square then show whatever sits behind this drawable instead of its own
     * fill, which is correct: those pixels are physically off the glass on real round hardware,
     * so nothing that was ever visible is lost. A square watch is untouched - shape stays the
     * default rectangle, so its fill and stroke keep covering the whole screen exactly as before.
     */
    private fun GradientDrawable.roundRimShape(isScreenRound: Boolean) {
        if (isScreenRound) shape = GradientDrawable.OVAL
    }

    private fun tonalSurface(accent: Int, lightness: Float = 0.28f): Int =
            PaletteTransforms.tonalSurface(accent, lightness)

    /**
     * @param screenWidthPx used for the radial gradients' radius - `GradientDrawable`'s
     *   programmatic radius has no fraction form (that exists only in XML).
     */
    fun build(
            backdrop: OverlayBackdrop,
            accent: Int,
            secondary: Int,
            tertiary: Int,
            density: Float,
            screenWidthPx: Int,
            isScreenRound: Boolean
    ): Drawable = when (backdrop) {
        // The album-tinted backdrops use the faces' wider saturation band (.30-.90) instead of
        // the chrome default (.25-.60) so an overlay opened over a face shares its palette
        // instead of showing a desaturated, mismatched tint.
        // The bleed-through the user saw was mostly the always-on clock and mini-button row
        // drawing OVER this backdrop (fixed separately in showOverlay by hiding them), not the
        // backdrop's own opacity - a first attempt raised these to near-opaque and over-shot
        // ("quase totalmente opaco"). Settled at a middle ground: clearly reads as a covering
        // surface without losing the frosted/acrylic look these styles are meant to have.
        OverlayBackdrop.ACRYLIC -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                        withAlpha(PaletteTransforms.tonalSurface(accent, .22f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT), 0xAE),
                        withAlpha(Color.BLACK, 0xD2)
                )
        )
        OverlayBackdrop.SOLID_BLACK -> ColorDrawable(Color.BLACK)
        OverlayBackdrop.SOLID_ALBUM -> ColorDrawable(PaletteTransforms.tonalSurface(accent, .22f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT))
        OverlayBackdrop.SOLID_SECONDARY -> ColorDrawable(
                PaletteTransforms.tonalSurface(
                        secondary, .24f,
                        PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT))
        OverlayBackdrop.SOLID_TERTIARY -> ColorDrawable(
                PaletteTransforms.tonalSurface(
                        tertiary, .24f,
                        PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT))
        OverlayBackdrop.GLASS -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x2EFFFFFF, 0xB8000000.toInt())
        )
        OverlayBackdrop.GRADIENT -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(tonalSurface(accent, .42f), tonalSurface(secondary, .18f))
        )
        OverlayBackdrop.DUOTONE -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(tonalSurface(accent, .30f), tonalSurface(secondary, .30f))
        )
        OverlayBackdrop.PRISM -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                        withAlpha(tonalSurface(tertiary, .25f), 0xE8),
                        withAlpha(tonalSurface(accent, .42f), 0xD8),
                        withAlpha(tonalSurface(secondary, .22f), 0xEA)
                )
        ).apply {
            setStroke((1f * density).roundToInt().coerceAtLeast(1), 0x66FFFFFF)
            roundRimShape(isScreenRound)
        }
        OverlayBackdrop.MESH -> LayerDrawable(arrayOf(
                ColorDrawable(tonalSurface(accent, .13f)),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(withAlpha(secondary, 0xB8), Color.TRANSPARENT)
                    setGradientCenter(.18f, .22f)
                    gradientRadius = screenWidthPx * .72f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(withAlpha(tertiary, 0xA0), Color.TRANSPARENT)
                    setGradientCenter(.86f, .78f)
                    gradientRadius = screenWidthPx * .68f
                }))
        OverlayBackdrop.AURORA -> LayerDrawable(arrayOf(
                // Aurora is two glows on black - the ribbon and its counterweight - not a linear
                // wash. Centres and relative radii are the player's, so the two surfaces read as
                // the same treatment.
                ColorDrawable(Color.BLACK),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .44f), 0xC4),
                            withAlpha(tonalSurface(accent, .12f), 0x4C),
                            Color.TRANSPARENT)
                    setGradientCenter(.18f, .14f)
                    gradientRadius = screenWidthPx * .78f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(secondary, .38f), 0x9C),
                            withAlpha(tonalSurface(tertiary, .18f), 0x3C),
                            Color.TRANSPARENT)
                    setGradientCenter(.88f, .72f)
                    gradientRadius = screenWidthPx * .72f
                }))
        OverlayBackdrop.SPOTLIGHT -> GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(
                    tonalSurface(accent, .68f),
                    tonalSurface(accent, .28f),
                    Color.BLACK)
            setGradientCenter(.5f, .38f)
            gradientRadius = screenWidthPx * .70f
        }
        OverlayBackdrop.VIGNETTE -> LayerDrawable(arrayOf(
                ColorDrawable(tonalSurface(accent, .24f)),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            Color.TRANSPARENT,
                            withAlpha(Color.BLACK, 0x28),
                            withAlpha(Color.BLACK, 0xF0))
                    setGradientCenter(.5f, .5f)
                    gradientRadius = screenWidthPx * .68f
                }))
        OverlayBackdrop.SPLIT -> LayerDrawable(arrayOf(
                // A split tone is two tones divided by a line, which is what the player's
                // `split_tone` draws and what the label on both sides has always said. This used
                // to be a left-to-right ramp - the same picture as DUOTONE two branches up, and
                // not a split of anything - so the name promised one thing on the panel and drew
                // another on the player, while duplicating a neighbour here.
                GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                                tonalSurface(accent, .30f),
                                tonalSurface(secondary, .20f),
                                0xFF060608.toInt())),
                PatternDrawable { canvas, bounds ->
                    val thickness = (bounds.width() * .006f).coerceAtLeast(density)
                    val y = bounds.centerY()
                    canvas.drawRect(
                            bounds.left,
                            y - thickness / 2f,
                            bounds.right,
                            y + thickness / 2f,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = withAlpha(Color.WHITE, 0x7A)
                            })
                }))
        OverlayBackdrop.BANDS -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                        tonalSurface(tertiary, .18f),
                        tonalSurface(accent, .36f),
                        tonalSurface(accent, .14f),
                        tonalSurface(secondary, .30f),
                        Color.BLACK))
        OverlayBackdrop.MIDNIGHT -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(tonalSurface(tertiary, .16f), 0xFF070914.toInt(), Color.BLACK))
        OverlayBackdrop.HALO -> GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(
                    withAlpha(tonalSurface(accent, .42f), 0xD8),
                    withAlpha(tonalSurface(secondary, .24f), 0xA0),
                    Color.BLACK)
            setGradientCenter(.5f, .5f)
            gradientRadius = screenWidthPx * .62f
        }
        OverlayBackdrop.SMOKE -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                        withAlpha(tonalSurface(tertiary, .24f), 0x78),
                        withAlpha(0xFF323238.toInt(), 0xC8),
                        withAlpha(Color.BLACK, 0xE8)))
        OverlayBackdrop.SUNRISE -> GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                intArrayOf(
                        0xFF35102C.toInt(),
                        withAlpha(0xFFFF5E62.toInt(), 0xD8),
                        withAlpha(0xFFFFC371.toInt(), 0xE8),
                        tonalSurface(tertiary, .24f)))
        OverlayBackdrop.DEEP_OCEAN -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                        0xFF031B2D.toInt(),
                        withAlpha(0xFF075D73.toInt(), 0xE8),
                        withAlpha(tonalSurface(tertiary, .20f), 0xE0),
                        Color.BLACK))
        // Nebula keeps the blurred cover visible and adds three separate, soft album-colour
        // clouds. The offset blooms are intentionally asymmetric: a generic centred radial
        // would read as another Spotlight/Halo variant on a small round display.
        OverlayBackdrop.NEBULA -> LayerDrawable(arrayOf(
                ColorDrawable(withAlpha(Color.BLACK, 0x78)),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .38f), 0xB0),
                            withAlpha(tonalSurface(accent, .08f), 0x10),
                            Color.TRANSPARENT)
                    setGradientCenter(.16f, .24f)
                    gradientRadius = screenWidthPx * .70f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(secondary, .34f), 0xA0),
                            withAlpha(tonalSurface(secondary, .08f), 0x10),
                            Color.TRANSPARENT)
                    setGradientCenter(.84f, .32f)
                    gradientRadius = screenWidthPx * .64f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(tertiary, .30f), 0x96),
                            Color.TRANSPARENT)
                    setGradientCenter(.50f, .94f)
                    gradientRadius = screenWidthPx * .58f
                }))
        OverlayBackdrop.EMBER -> LayerDrawable(arrayOf(
                // Ember is one warm glow tucked into a corner, and it has to be recognisably that
                // on both surfaces - a name that promises a different picture depending on where
                // it is applied is worse than no shared name at all. The player draws exactly this
                // glow at (.82, .84) with no base at all; a panel cannot go without a base, since
                // controls sit on it, so the dark field below is the one thing that differs.
                ColorDrawable(0xFF120A10.toInt()),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .46f), 0xCC),
                            withAlpha(0xFFC44536.toInt(), 0x5C),
                            Color.TRANSPARENT)
                    setGradientCenter(.82f, .84f)
                    // .46 of the width, which is what the player face's
                    // `size.minDimension * .46f` comes to on a square canvas.
                    gradientRadius = screenWidthPx * .46f
                }))
        OverlayBackdrop.TIDELINE -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                        0xFF031423.toInt(),
                        withAlpha(0xFF07516A.toInt(), 0xEA),
                        tonalSurface(secondary, .20f),
                        Color.BLACK))
        // Like Nebula, this uses the artwork as material, but the green-black base and two cyan
        // blooms make it a distinctly organic surface rather than another album gradient.
        OverlayBackdrop.BIOLUMINESCENCE -> LayerDrawable(arrayOf(
                ColorDrawable(withAlpha(0xFF041A19.toInt(), 0xC0)),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .54f), 0xC0),
                            withAlpha(0xFF0A6A62.toInt(), 0x70),
                            Color.TRANSPARENT)
                    setGradientCenter(.20f, .74f)
                    gradientRadius = screenWidthPx * .62f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(tertiary, .42f), 0xA8),
                            withAlpha(0xFF1AB5A2.toInt(), 0x40),
                            Color.TRANSPARENT)
                    setGradientCenter(.82f, .24f)
                    gradientRadius = screenWidthPx * .54f
                }))
        OverlayBackdrop.IRIDESCENT -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                        tonalSurface(tertiary, .28f),
                        0xFF4A2F72.toInt(),
                        tonalSurface(accent, .40f),
                        tonalSurface(secondary, .24f),
                        0xFF0B101A.toInt()))
        // Two low-contrast diagonal planes give Graphite its material grain while keeping it
        // calmer than the colour-led treatments around it.
        OverlayBackdrop.GRAPHITE -> LayerDrawable(arrayOf(
                ColorDrawable(0xFF111318.toInt()),
                GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xFF292D34.toInt(), 0xFF111318.toInt(), 0xFF1D2026.toInt())
                ).apply {
                    setStroke((1f * density).roundToInt().coerceAtLeast(1), 0x44FFFFFF)
                    roundRimShape(isScreenRound)
                }))
        OverlayBackdrop.CINEMA -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                        Color.BLACK,
                        Color.BLACK,
                        withAlpha(tonalSurface(accent, .42f), 0xE0),
                        withAlpha(tonalSurface(secondary, .26f), 0xD8),
                        Color.BLACK,
                        Color.BLACK))
        OverlayBackdrop.ORBIT -> LayerDrawable(arrayOf(
                ColorDrawable(tonalSurface(accent, .12f)),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(withAlpha(tonalSurface(secondary, .42f), 0xB8), Color.TRANSPARENT)
                    setGradientCenter(.16f, .30f)
                    gradientRadius = screenWidthPx * .64f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(withAlpha(tonalSurface(tertiary, .34f), 0xA8), Color.TRANSPARENT)
                    setGradientCenter(.84f, .72f)
                    gradientRadius = screenWidthPx * .62f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(withAlpha(tonalSurface(accent, .48f), 0x88), Color.TRANSPARENT)
                    setGradientCenter(.50f, .50f)
                    gradientRadius = screenWidthPx * .26f
                }))
        // Horizon is a line low on the screen: the player leaves everything above .72 untouched
        // and darkens only the band below it. The panel keeps a quiet field the whole way up
        // (again, controls) and puts the same lift in the same band rather than a bright middle.
        OverlayBackdrop.HORIZON -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                        0xFF070B12.toInt(),
                        0xFF070B12.toInt(),
                        tonalSurface(accent, .34f),
                        0xFF020204.toInt())).apply {
            // Stops rather than an even spread, so the transition sits at .72 the way the
            // player's does instead of gradating across the whole height.
            setGradientCenter(.5f, .72f)
        }
        // Ink Wash deliberately leaves the album blur as its paper: only a smoky diagonal tint
        // and a low black veil sit on top of it.
        OverlayBackdrop.INK_WASH -> LayerDrawable(arrayOf(
                ColorDrawable(withAlpha(Color.BLACK, 0x82)),
                GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(
                                withAlpha(tonalSurface(accent, .35f), 0xB0),
                                withAlpha(tonalSurface(secondary, .18f), 0x42),
                                Color.TRANSPARENT))
        ))
        OverlayBackdrop.BLOSSOM -> GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                intArrayOf(
                        0xFF160B1D.toInt(),
                        0xFF542047.toInt(),
                        withAlpha(0xFFB84B74.toInt(), 0xD8),
                        tonalSurface(tertiary, .30f),
                        0xFF08050B.toInt()))
        OverlayBackdrop.FJORD -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                        0xFF0A2030.toInt(),
                        tonalSurface(tertiary, .25f),
                        0xFF0A5960.toInt(),
                        Color.BLACK))
        // Deliberately the lightest wash of the set: the blurred cover underneath is the
        // material, and covering it would defeat the effect. The album tint is kept low-alpha
        // and lifted (.62 lightness) so it reads as coloured glass rather than as a filter,
        // and the near-white stroke is the pane's edge - the single cue that turns a
        // translucent wash into a surface with thickness.
        OverlayBackdrop.LIQUID_GLASS -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                        withAlpha(PaletteTransforms.tonalSurface(accent, .62f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT), 0x3D),
                        withAlpha(Color.WHITE, 0x14),
                        withAlpha(PaletteTransforms.tonalSurface(secondary, .30f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT), 0x66)
                )
        ).apply {
            setStroke((1.5f * density).roundToInt().coerceAtLeast(1), 0x8CFFFFFF.toInt())
            roundRimShape(isScreenRound)
        }
        // The authored Expressive background, rebuilt as an overlay: album wash, black
        // knock-back, then the vignette that makes the rim recede. Three stacked layers rather
        // than one gradient because that is what the face's own PlayerBackgroundDrawable draws,
        // and an overlay that only approximated it would read as a different treatment sharing
        // a name. The blurred/sharp pair differ *only* in what shows through underneath
        // (usesAlbumBlur), exactly as the album-art styles of the same name do.
        OverlayBackdrop.EXPRESSIVE, OverlayBackdrop.EXPRESSIVE_NO_BLUR -> LayerDrawable(
                arrayOf(
                        ColorDrawable(withAlpha(
                                PaletteTransforms.tonalSurface(accent, .30f, .30f, .90f), 0x73)),
                        ColorDrawable(withAlpha(Color.BLACK, 0x4D)),
                        GradientDrawable().apply {
                            gradientType = GradientDrawable.RADIAL_GRADIENT
                            colors = intArrayOf(
                                    Color.TRANSPARENT,
                                    Color.TRANSPARENT,
                                    withAlpha(Color.BLACK, 0xE0))
                            setGradientCenter(.5f, .5f)
                            // Full-screen overlay, so the face's own maxDimension * .68f is
                            // just the screen width.
                            gradientRadius = screenWidthPx * .68f
                        }))
        // Five structural patterns rather than washes - see OverlayBackdropPatterns, which both
        // this Drawable and the phone preview call directly so there is exactly one
        // implementation of each, not two that can drift.
        OverlayBackdrop.DOT_MATRIX -> PatternDrawable { canvas, bounds ->
            OverlayBackdropPatterns.drawDotMatrix(
                    canvas, bounds, density,
                    baseColor = tonalSurface(accent, .10f),
                    dotColor = withAlpha(tonalSurface(tertiary, .62f), 0x38))
        }
        OverlayBackdrop.SCANLINES -> PatternDrawable { canvas, bounds ->
            OverlayBackdropPatterns.drawScanlines(
                    canvas, bounds, density,
                    baseColor = tonalSurface(accent, .09f),
                    lineColor = tonalSurface(secondary, .55f))
        }
        OverlayBackdrop.RADAR -> PatternDrawable { canvas, bounds ->
            OverlayBackdropPatterns.drawRadarRings(
                    canvas, bounds, density,
                    cx = bounds.centerX(), cy = bounds.centerY(),
                    radius = minOf(bounds.width(), bounds.height()) / 2f,
                    baseColor = tonalSurface(accent, .08f),
                    ringColor = tonalSurface(tertiary, .58f),
                    sweepColor = tonalSurface(accent, .50f))
        }
        OverlayBackdrop.CONTOUR -> PatternDrawable { canvas, bounds ->
            OverlayBackdropPatterns.drawContourLines(
                    canvas, bounds, density,
                    cx = bounds.centerX(), cy = bounds.centerY(),
                    radius = minOf(bounds.width(), bounds.height()) / 2f,
                    baseColor = tonalSurface(accent, .09f),
                    lineColor = tonalSurface(secondary, .60f),
                    accent = accent)
        }
        OverlayBackdrop.FACETED -> PatternDrawable { canvas, bounds ->
            OverlayBackdropPatterns.drawFacetedCrystal(
                    canvas, bounds, density, accent, secondary, tertiary, accent)
        }
        // --- Carried over from the player catalogue. See OverlayBackdrop's own note on why these
        // keep the composition rather than the opacity.

        // The player leaves the top untouched and deepens only below .60. Six stops rather than
        // three because GradientDrawable spreads colours evenly: repeating the top colour is the
        // only way to hold the transition at .60 with the same array the preview uses.
        OverlayBackdrop.DUSK -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                        tonalSurface(tertiary, .17f),
                        tonalSurface(tertiary, .17f),
                        tonalSurface(tertiary, .17f),
                        tonalSurface(tertiary, .17f),
                        tonalSurface(tertiary, .09f),
                        0xFF050507.toInt()))

        OverlayBackdrop.ICE -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                        withAlpha(0xFF2E7C93.toInt(), 0xEE),
                        withAlpha(0xFF1B4A78.toInt(), 0xF2),
                        0xFF061426.toInt()))

        OverlayBackdrop.ROSE -> LayerDrawable(arrayOf(
                ColorDrawable(0xFF1B0810.toInt()),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(0xFFFF8CAB.toInt(), 0xB4),
                            withAlpha(tonalSurface(accent, .30f), 0x5E),
                            Color.TRANSPARENT)
                    setGradientCenter(.72f, .74f)
                    gradientRadius = screenWidthPx * .72f
                }))

        OverlayBackdrop.PAPER -> LayerDrawable(arrayOf(
                GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                                withAlpha(0xFF4A4336.toInt(), 0xF4),
                                withAlpha(0xFF4A4336.toInt(), 0xF4),
                                withAlpha(0xFF241F17.toInt(), 0xF8),
                                0xFF08070A.toInt())),
                PatternDrawable { canvas, bounds ->
                    // The fine inset rule the player draws inside its cream veil.
                    val inset = bounds.width() * .065f
                    val stroke = (bounds.width() * .009f).coerceAtLeast(density)
                    canvas.drawRect(
                            bounds.left + inset,
                            bounds.top + inset,
                            bounds.right - inset,
                            bounds.bottom - inset,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                strokeWidth = stroke
                                color = withAlpha(Color.WHITE, 0x70)
                            })
                }))

        OverlayBackdrop.MONOLITH -> LayerDrawable(arrayOf(
                ColorDrawable(0xFF060608.toInt()),
                PatternDrawable { canvas, bounds ->
                    // A slab down the left 48%, exactly the fraction the player uses; a
                    // LayerDrawable inset cannot express a fraction of an unknown width.
                    val slab = bounds.left + bounds.width() * .48f
                    canvas.drawRect(
                            bounds.left, bounds.top, slab, bounds.bottom,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                shader = android.graphics.LinearGradient(
                                        bounds.left, 0f, slab, 0f,
                                        intArrayOf(
                                                withAlpha(tonalSurface(accent, .42f), 0xEE),
                                                withAlpha(tonalSurface(secondary, .18f), 0x66),
                                                Color.TRANSPARENT),
                                        floatArrayOf(0f, .70f, 1f),
                                        android.graphics.Shader.TileMode.CLAMP)
                            })
                },
                GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                                Color.TRANSPARENT,
                                Color.TRANSPARENT,
                                Color.TRANSPARENT,
                                withAlpha(Color.BLACK, 0xC2)))))

        OverlayBackdrop.LANTERN -> LayerDrawable(arrayOf(
                GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                                0xFF07070A.toInt(),
                                tonalSurface(tertiary, .12f),
                                0xFF07070A.toInt())),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(0xFFFFC857.toInt(), 0xC8),
                            withAlpha(tonalSurface(accent, .34f), 0x52),
                            Color.TRANSPARENT)
                    setGradientCenter(.50f, .82f)
                    gradientRadius = screenWidthPx * .41f
                }))

        OverlayBackdrop.NOIR -> LayerDrawable(arrayOf(
                ColorDrawable(Color.BLACK),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(Color.WHITE, 0x2E),
                            Color.TRANSPARENT,
                            withAlpha(Color.BLACK, 0xD2))
                    setGradientCenter(.50f, .42f)
                    gradientRadius = screenWidthPx * .58f
                }))

        OverlayBackdrop.VELVET -> LayerDrawable(arrayOf(
                ColorDrawable(0xFF120B16.toInt()),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .38f), 0xA8),
                            Color.TRANSPARENT)
                    setGradientCenter(.34f, .76f)
                    gradientRadius = screenWidthPx * .62f
                }))

        OverlayBackdrop.MIRAGE -> LayerDrawable(arrayOf(
                ColorDrawable(0xFF0A0A0E.toInt()),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .40f), 0xBE),
                            Color.TRANSPARENT)
                    setGradientCenter(.08f, .38f)
                    gradientRadius = screenWidthPx * .58f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(secondary, .36f), 0xB4),
                            Color.TRANSPARENT)
                    setGradientCenter(.92f, .62f)
                    gradientRadius = screenWidthPx * .58f
                }))

        OverlayBackdrop.BLOOM -> LayerDrawable(arrayOf(
                ColorDrawable(0xFF0B0B0F.toInt()),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .38f), 0xAA),
                            Color.TRANSPARENT)
                    setGradientCenter(.22f, .26f)
                    gradientRadius = screenWidthPx * .52f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(secondary, .34f), 0x9A),
                            Color.TRANSPARENT)
                    setGradientCenter(.80f, .22f)
                    gradientRadius = screenWidthPx * .46f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(tertiary, .30f), 0x8E),
                            Color.TRANSPARENT)
                    setGradientCenter(.50f, .88f)
                    gradientRadius = screenWidthPx * .48f
                }))

        OverlayBackdrop.CLOUD -> LayerDrawable(arrayOf(
                ColorDrawable(0xFF0A0A0D.toInt()),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .34f), 0x92),
                            Color.TRANSPARENT)
                    setGradientCenter(.22f, .34f)
                    gradientRadius = screenWidthPx * .49f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(secondary, .32f), 0x8C),
                            Color.TRANSPARENT)
                    setGradientCenter(.74f, .30f)
                    gradientRadius = screenWidthPx * .49f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(tertiary, .30f), 0x86),
                            Color.TRANSPARENT)
                    setGradientCenter(.50f, .78f)
                    gradientRadius = screenWidthPx * .49f
                }))

        OverlayBackdrop.NOCTURNE -> LayerDrawable(arrayOf(
                ColorDrawable(0xFF070B25.toInt()),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(tertiary, .34f), 0x9E),
                            Color.TRANSPARENT)
                    setGradientCenter(.68f, .28f)
                    gradientRadius = screenWidthPx * .58f
                },
                PatternDrawable { canvas, bounds ->
                    OverlayBackdropPatterns.drawNocturneStars(
                            canvas, bounds, withAlpha(Color.WHITE, 0xB4))
                }))

        OverlayBackdrop.LIQUID -> LayerDrawable(arrayOf(
                ColorDrawable(0xFF08080B.toInt()),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .44f), 0xC8),
                            withAlpha(tonalSurface(accent, .16f), 0x3C),
                            Color.TRANSPARENT)
                    setGradientCenter(.18f, .72f)
                    gradientRadius = screenWidthPx * .38f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(secondary, .42f), 0xC0),
                            withAlpha(tonalSurface(secondary, .14f), 0x38),
                            Color.TRANSPARENT)
                    setGradientCenter(.62f, .42f)
                    gradientRadius = screenWidthPx * .38f
                },
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(tertiary, .40f), 0xBA),
                            withAlpha(tonalSurface(tertiary, .14f), 0x34),
                            Color.TRANSPARENT)
                    setGradientCenter(.86f, .76f)
                    gradientRadius = screenWidthPx * .38f
                }))

        OverlayBackdrop.TIDAL -> PatternDrawable { canvas, bounds ->
            OverlayBackdropPatterns.drawTidalWaves(
                    canvas,
                    bounds,
                    0xFF08080B.toInt(),
                    intArrayOf(
                            withAlpha(tonalSurface(accent, .40f), 0xC6),
                            withAlpha(tonalSurface(secondary, .36f), 0xC6),
                            withAlpha(tonalSurface(tertiary, .34f), 0xC6)))
        }

        OverlayBackdrop.VINYL -> LayerDrawable(arrayOf(
                ColorDrawable(0xFF050506.toInt()),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .36f), 0xA6),
                            withAlpha(tonalSurface(tertiary, .16f), 0x52),
                            Color.TRANSPARENT)
                    setGradientCenter(.64f, .38f)
                    gradientRadius = screenWidthPx * .69f
                }))

        OverlayBackdrop.CORONA -> PatternDrawable { canvas, bounds ->
            OverlayBackdropPatterns.drawSweepRing(
                    canvas,
                    bounds,
                    0xFF08080B.toInt(),
                    intArrayOf(
                            withAlpha(tonalSurface(tertiary, .34f), 0x96),
                            withAlpha(tonalSurface(accent, .40f), 0x96),
                            withAlpha(tonalSurface(secondary, .36f), 0x96),
                            withAlpha(tonalSurface(tertiary, .34f), 0x96)),
                    radiusFraction = .44f,
                    strokeFraction = .24f,
                    useMaxDimension = true,
                    roundCap = true)
        }

        OverlayBackdrop.CRESCENT -> PatternDrawable { canvas, bounds ->
            OverlayBackdropPatterns.drawSweepArc(
                    canvas,
                    bounds,
                    0xFF07070A.toInt(),
                    intArrayOf(
                            Color.TRANSPARENT,
                            withAlpha(tonalSurface(accent, .44f), 0xB8),
                            withAlpha(tonalSurface(secondary, .30f), 0x50),
                            Color.TRANSPARENT),
                    startAngle = 138f,
                    sweepAngle = 196f,
                    inflateFraction = .04f,
                    strokeFraction = .12f)
        }

        OverlayBackdrop.GRID -> PatternDrawable { canvas, bounds ->
            OverlayBackdropPatterns.drawGridLines(
                    canvas,
                    bounds,
                    tonalSurface(tertiary, .10f),
                    withAlpha(tonalSurface(accent, .34f), 0x6E),
                    divisions = 6)
        }

        // The one carried-over treatment whose identity is that it is *light*, so it gets a pale
        // wash instead of a dark field - and the wash has to be a real layer rather than a
        // transparent base, or the panel would composite it over black and show nothing.
        OverlayBackdrop.GLASS_VEIL -> LayerDrawable(arrayOf(
                GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(0x40FFFFFF, 0xC4000000.toInt())),
                PatternDrawable { canvas, bounds ->
            OverlayBackdropPatterns.drawSweepRing(
                    canvas,
                    bounds,
                    0,
                    intArrayOf(
                            withAlpha(Color.WHITE, 0xD2),
                            withAlpha(tonalSurface(accent, .48f), 0xC8),
                            withAlpha(Color.WHITE, 0xD2),
                            withAlpha(tonalSurface(secondary, .44f), 0xC8),
                            withAlpha(Color.WHITE, 0xD2)),
                    radiusFraction = .485f,
                    strokeFraction = .018f)
        }))

        OverlayBackdrop.MATERIAL -> LayerDrawable(arrayOf(
                ColorDrawable(Color.BLACK),
                GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                            withAlpha(tonalSurface(accent, .26f), 0xB8),
                            withAlpha(tonalSurface(accent, .26f), 0x62),
                            withAlpha(tonalSurface(accent, .26f), 0x1E),
                            Color.TRANSPARENT)
                    setGradientCenter(.50f, .50f)
                    gradientRadius = screenWidthPx * .85f
                }))

        OverlayBackdrop.POSTER -> LayerDrawable(arrayOf(
                ColorDrawable(withAlpha(tonalSurface(accent, .22f), 0xF0)),
                GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                                withAlpha(Color.BLACK, 0xC4),
                                withAlpha(Color.BLACK, 0x1E),
                                withAlpha(Color.BLACK, 0x66),
                                withAlpha(Color.BLACK, 0xF0))),
                GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(
                                withAlpha(Color.BLACK, 0x92),
                                Color.TRANSPARENT,
                                withAlpha(Color.BLACK, 0x92)))))

        OverlayBackdrop.STUDIO -> GradientDrawable(
                // The player lights this from the top right toward the bottom left.
                GradientDrawable.Orientation.TR_BL,
                intArrayOf(
                        withAlpha(tonalSurface(accent, .40f), 0xE2),
                        withAlpha(tonalSurface(secondary, .16f), 0xC8),
                        0xFF060608.toInt()))

        OverlayBackdrop.SPECTRUM -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                        withAlpha(tonalSurface(accent, .34f), 0xE8),
                        withAlpha(tonalSurface(tertiary, .14f), 0xF0),
                        0xFF040405.toInt()))

        OverlayBackdrop.FOLLOW_STYLE -> ColorDrawable(Color.BLACK) // resolved by the caller
    }
}

/**
 * The one Drawable in this file that isn't built from [GradientDrawable]/[LayerDrawable]
 * primitives: it exists purely to let [OverlayBackdropDrawables.build] hand a plain
 * `(Canvas, RectF) -> Unit` block - the exact shape [OverlayBackdropPatterns]' functions take -
 * straight to a `View.background` without a second Drawable subclass per pattern.
 */
private class PatternDrawable(
        private val onDraw: (Canvas, RectF) -> Unit
) : Drawable() {
    private var drawableAlpha = 255
    private val boundsF = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        boundsF.set(b)
        if (drawableAlpha >= 255) {
            onDraw(canvas, boundsF)
            return
        }
        val layer = canvas.saveLayerAlpha(boundsF, drawableAlpha)
        onDraw(canvas, boundsF)
        canvas.restoreToCount(layer)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // None of these patterns are ever colour-filtered by a caller today; accepted silently
        // rather than thrown, matching every other Drawable's contract in this file.
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
