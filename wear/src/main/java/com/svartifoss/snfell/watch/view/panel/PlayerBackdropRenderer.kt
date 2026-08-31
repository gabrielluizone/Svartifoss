package com.svartifoss.snfell.watch.view.panel

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.svartifoss.snfell.common.BitmapBlur
import com.svartifoss.snfell.common.AlbumArtFilter
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The artwork half of the player's backdrop, shared by `MainActivity` and [PanelBackdropView].
 *
 * The dedicated volume and progress screens have to reproduce the player's backdrop exactly,
 * because the panel backgrounds are composited *over* it - the quick-actions panel, the volume
 * overlay and the seek overlay all sit on top of the live player in `activity_main.xml`, so a
 * translucent one shows the artwork, its treatment and its shading through. Approximating that
 * with a plain blur over black is what made the same setting look like two different backgrounds.
 */
object PlayerBackdropRenderer {

    /**
     * Fills [view] with the artwork under the treatment the background style asks for.
     *
     * On API 31+ the blur is a real GPU Gaussian via [RenderEffect]; older watches fall back to
     * [BitmapBlur]. CLAMP, not DECAL: the art fills the whole (centerCrop) view, so the kernel
     * samples past the bitmap edges - DECAL treats those as transparent and fades the blur out at
     * the bezel as a dark ring, while CLAMP extends the edge pixels so the blur stays opaque.
     */
    fun applyArtwork(
            view: ImageView,
            source: Bitmap?,
            blurred: Boolean,
            artworkFilter: AlbumArtFilter,
            hidden: Boolean,
            blurRadiusPx: Float
    ) {
        view.colorFilter = artworkFilter.androidColorFilter

        if (source == null || hidden) {
            view.setImageBitmap(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) view.setRenderEffect(null)
            return
        }
        if (!blurred) {
            view.setImageBitmap(source)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) view.setRenderEffect(null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setImageBitmap(source)
            view.setRenderEffect(
                    RenderEffect.createBlurEffect(
                            blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP))
        } else {
            // Not recycled: the same bitmap is handed to consumers that never report when they
            // have finished with it. Since minSdk 26 the pixels are tied to the object.
            view.setImageBitmap(BitmapBlur.blur(source, blurRadiusPx))
        }
    }

    /**
     * The inscribed square the Square album-art styles draw their sharp copy into, shared by the
     * outline that clips the view and the matrix that fits the bitmap, so the two can never drift
     * into a clip region and a scale that disagree.
     */
    fun squareInsetBounds(view: View): RectF? {
        val side = minOf(view.width, view.height) / sqrt(2f)
        if (side <= 0f) return null
        val left = (view.width - side) / 2f
        val top = (view.height - side) / 2f
        return RectF(left, top, left + side, top + side)
    }

    /**
     * Contain-fit into [bounds] - the smaller of the two axis scales, never the larger - so a
     * source that isn't square is letterboxed inside the inset rather than cropped, with the
     * blurred backdrop showing through the gap.
     */
    fun squareInsetFitMatrix(bounds: RectF, source: Bitmap): Matrix {
        val scale = minOf(bounds.width() / source.width, bounds.height() / source.height)
        return Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                    bounds.centerX() - source.width * scale / 2f,
                    bounds.centerY() - source.height * scale / 2f)
        }
    }

    /** Clips a view to [squareInsetBounds], rounded by whatever [radiusFraction] the active Square
     *  variant reports at the moment the outline is queried. */
    fun squareInsetOutlineProvider(radiusFraction: () -> Float): ViewOutlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val bounds = squareInsetBounds(view) ?: return
                    outline.setRoundRect(
                            bounds.left.roundToInt(), bounds.top.roundToInt(),
                            bounds.right.roundToInt(), bounds.bottom.roundToInt(),
                            bounds.width() * radiusFraction())
                }
            }
}
