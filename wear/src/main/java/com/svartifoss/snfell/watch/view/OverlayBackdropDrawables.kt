package com.svartifoss.snfell.watch.view

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.common.OverlayBackdrop
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
            screenWidthPx: Int
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
        OverlayBackdrop.AURORA -> GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                intArrayOf(
                        tonalSurface(tertiary, .11f),
                        tonalSurface(accent, .36f),
                        tonalSurface(secondary, .24f),
                        Color.BLACK))
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
        OverlayBackdrop.SPLIT -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(tonalSurface(accent, .20f), tonalSurface(secondary, .34f)))
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
        OverlayBackdrop.FOLLOW_STYLE -> ColorDrawable(Color.BLACK) // resolved by the caller
    }
}
