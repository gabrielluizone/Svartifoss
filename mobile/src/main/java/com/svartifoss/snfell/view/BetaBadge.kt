package com.svartifoss.snfell.view

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.R

/**
 * Paints this view as the "BETA" pill: accent text and outline over a faint wash of the accent.
 *
 * One look for every feature marked as not finished - the streaming shortcuts drew it first, and
 * the hand gesture reuses it rather than inventing a second badge. Painted from the runtime accent
 * rather than the static theme colour, and tagged so the app-wide accent pass leaves it alone,
 * because its fill and outline are two tones of one accent that the pass would only half update.
 */
internal fun TextView.styleAsBetaBadge() {
    val density = resources.displayMetrics.density
    val accent = LyraAccent.resolve(context)
    val foreground = LyraAccent.contrastSafe(context, accent)
    setTag(R.id.tag_handles_accent_locally, true)
    setTextColor(foreground)
    background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 16f * density
        setColor(ColorUtils.setAlphaComponent(accent, 24))
        setStroke((density + 0.5f).toInt(), foreground)
    }
}
