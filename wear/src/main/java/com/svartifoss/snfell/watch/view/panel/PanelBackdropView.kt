package com.svartifoss.snfell.watch.view.panel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.doOnLayout
import com.svartifoss.snfell.watch.view.OverlayBackdropDrawables
import com.svartifoss.snfell.watch.view.PlayerBackgroundDrawable

/**
 * The player's backdrop plus a panel background on top of it - the exact stack
 * `activity_main.xml` puts under the quick-actions, volume and seek overlays, in the same order:
 * artwork, the Square style's sharp inset, the background treatment, the shading scrim, then the
 * overlay's blurred cover and its own background.
 *
 * This exists because the dedicated volume and progress screens are separate Activities with none
 * of the player beneath them. Painting only the panel background there left every translucent
 * treatment resolving over black, so the *same* Shared panel appearance setting produced two
 * visibly different screens - the panel over a live player on the double-tap quick panel, and a
 * flat wash on the dedicated ones.
 */
class PanelBackdropView(context: Context) : FrameLayout(context) {

    private var squareCornerFraction = 0.10f

    private val artwork = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    /** The Square styles' sharp, uncropped copy over the blurred backdrop. `MATRIX` because the
     *  fit is computed against the inset square, not this view's own match_parent bounds. */
    private val squareInset = ImageView(context).apply {
        scaleType = ImageView.ScaleType.MATRIX
        visibility = View.GONE
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val playerBackground = View(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val shading = View(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    /** The overlay's own blurred copy of the cover, for the backdrops that declare
     *  `usesAlbumBlur`. Black behind it, exactly as `overlay_blur_image` carries in the layout. */
    private val overlayBlur = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = ColorDrawable(Color.BLACK)
        visibility = View.GONE
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val overlayDim = View(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    init {
        setBackgroundColor(Color.BLACK)
        squareInset.clipToOutline = true
        squareInset.outlineProvider =
                PlayerBackdropRenderer.squareInsetOutlineProvider { squareCornerFraction }
        addView(artwork)
        addView(squareInset)
        addView(playerBackground)
        addView(shading)
        addView(overlayBlur)
        addView(overlayDim)
    }

    fun render(appearance: PanelAppearance, backdrop: PanelBackdrop, albumArt: Bitmap?) {
        val source = backdrop.frostedArtwork(albumArt)

        PlayerBackdropRenderer.applyArtwork(
                artwork,
                source = source,
                blurred = backdrop.albumArtStyle.blurredArtwork,
                artworkFilter = backdrop.albumArtFilter,
                hidden = backdrop.albumArtStyle.hidesArtwork,
                blurRadiusPx = backdrop.blurRadiusPx)

        val cornerFraction = backdrop.albumArtStyle.squareCornerRadiusFraction
        val insetSource = source?.takeIf {
            cornerFraction != null && !backdrop.albumArtStyle.hidesArtwork
        }
        if (insetSource == null) {
            squareInset.visibility = View.GONE
            squareInset.colorFilter = null
            squareInset.setImageBitmap(null)
        } else {
            squareCornerFraction = cornerFraction ?: 0.10f
            squareInset.visibility = View.VISIBLE
            squareInset.colorFilter = backdrop.albumArtFilter.androidColorFilter
            squareInset.setImageBitmap(insetSource)
            applySquareInsetMatrix(insetSource)
        }

        // The whole stack, shading included, in one drawing - see PlayerBackgroundDrawable. The
        // separate scrim View below it is left empty rather than removed: it is what keeps the
        // panel's own background one child higher than the player's treatments.
        playerBackground.background = PlayerBackgroundDrawable(
                layers = backdrop.layers,
                primary = backdrop.globalTriad.primary,
                secondary = backdrop.globalTriad.secondary,
                tertiary = backdrop.globalTriad.tertiary,
                materialSurface = backdrop.globalTriad.primary,
                materialSurfaceSoftened = backdrop.materialSurfaceSoftened,
                density = resources.displayMetrics.density)

        shading.background = null

        if (appearance.usesAlbumBlur && albumArt != null && !albumArt.isRecycled) {
            overlayBlur.visibility = View.VISIBLE
            PlayerBackdropRenderer.applyArtwork(
                    overlayBlur,
                    source = albumArt,
                    blurred = true,
                    artworkFilter = backdrop.albumArtFilter,
                    hidden = false,
                    // Matches the artwork blur when the background is already blurred, so
                    // revealing the overlay never makes the blur jump to a different strength.
                    blurRadiusPx = if (backdrop.albumArtStyle.blurredArtwork) {
                        backdrop.blurRadiusPx
                    } else {
                        backdrop.overlayBlurRadiusPx
                    })
        } else {
            overlayBlur.visibility = View.GONE
            overlayBlur.setImageBitmap(null)
        }

        overlayDim.background = OverlayBackdropDrawables.build(
                appearance.backdrop,
                appearance.triad.primary,
                appearance.triad.secondary,
                appearance.triad.tertiary,
                resources.displayMetrics.density,
                resources.displayMetrics.widthPixels,
                resources.configuration.isScreenRound)
    }

    /** Deferred to the next layout pass when the view has not been measured yet, which is the
     *  normal case on the first render inside a freshly composed `AndroidView`. */
    private fun applySquareInsetMatrix(source: Bitmap) {
        if (squareInset.width == 0 || squareInset.height == 0) {
            squareInset.doOnLayout { applySquareInsetMatrix(source) }
            return
        }
        val bounds = PlayerBackdropRenderer.squareInsetBounds(squareInset) ?: return
        squareInset.imageMatrix = PlayerBackdropRenderer.squareInsetFitMatrix(bounds, source)
        squareInset.invalidateOutline()
    }
}
