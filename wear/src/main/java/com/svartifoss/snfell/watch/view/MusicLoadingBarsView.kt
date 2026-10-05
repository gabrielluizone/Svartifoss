package com.svartifoss.snfell.watch.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.R as commonR

/**
 * Lifecycle-aware host for the shared three-bar music/loading animation - the View-based twin of
 * the Compose [com.svartifoss.snfell.watch.view.compose.LoadingBars], for the classic now-playing
 * screen, which is the last surface in the watch app that still showed a stock arc spinner. The
 * three Compose screens (queue, menu, lyrics) already draw these bars; sharing one animation
 * everywhere is the point - it reads as "your music app is working" where a rotating arc reads as
 * a generic platform shape.
 *
 * The animated vector repeats forever, so setting this View to GONE is not enough: the drawable
 * must be stopped when this View or an ancestor is hidden, when its window is hidden, and when it
 * leaves the hierarchy, or a stranded animator keeps burning frames off screen.
 */
class MusicLoadingBarsView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        setImageResource(commonR.drawable.ic_equalizer_bars_animated)
        scaleType = ScaleType.FIT_CENTER
        // The XML tint is the sage default so a fresh install has a sensible first frame. On a
        // paired watch, however, the last phone accent is already persisted locally before this
        // view is inflated. Use it immediately instead of showing a green loading animation while
        // MainActivity is still waiting for the preference/state observers to emit.
        restoredPhoneAccent()?.let(::setBarsColor)
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

    private fun restoredPhoneAccent(): Int? {
        val value = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(MiscPreferences.WEAR_PHONE_ACCENT_COLOR.key, null)
                ?.takeIf { it.isNotBlank() }
                ?: return null
        return try {
            Color.parseColor(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
