package com.svartifoss.snfell.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import com.svartifoss.snfell.common.R as commonR

/**
 * Lifecycle-aware host for the shared three-bar music/loading animation.
 *
 * The animated vector repeats forever, so merely setting this View to GONE is not enough: it must
 * be stopped when this View or one of its ancestors is hidden, when its window is hidden, and when
 * it leaves the hierarchy. Keeping that ownership here prevents RecyclerView rows and standalone
 * loading states from retaining an animator after they are no longer visible.
 */
class MusicLoadingBarsView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        setImageResource(commonR.drawable.ic_equalizer_bars_animated)
        scaleType = ScaleType.CENTER_INSIDE
    }

    /** Tints all three bars while retaining the drawable's animated geometry. */
    fun setBarsColor(color: Int) {
        imageTintList = ColorStateList.valueOf(color)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncAnimation()
    }

    private fun syncAnimation() {
        val animation = drawable as? Animatable ?: return
        val shouldRun = isAttachedToWindow && isShown && windowVisibility == View.VISIBLE
        if (shouldRun) {
            if (!animation.isRunning) animation.start()
        } else {
            animation.stop()
        }
    }

    private fun stopAnimation() {
        (drawable as? Animatable)?.stop()
    }
}
