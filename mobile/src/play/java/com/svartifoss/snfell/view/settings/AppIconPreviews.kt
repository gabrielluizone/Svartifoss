package com.svartifoss.snfell.view.settings

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AppIconStyle

/**
 * Play build: the small pictures the launcher-icon picker shows beside each choice, so the user
 * sees the icon instead of only its name. One choice changes two icons at once - the phone's and
 * the watch's - and the watch's is drawn here too, from the phone's own resources, because the
 * wear module's icons are not visible from this one.
 *
 * Both lookups are exhaustive over [AppIconStyle] on purpose: a style added to the enum without a
 * preview does not compile, which is how a picker row with no picture is prevented rather than
 * noticed. The github build has an inert twin in `src/github`: its launcher icon is not
 * selectable, so there is nothing to preview there.
 */
object AppIconPreviews {

    fun phone(context: Context, style: AppIconStyle): Drawable? = ContextCompat.getDrawable(
            context,
            when (style) {
                AppIconStyle.SOLID -> R.drawable.launcher_preview_phone_solid
                AppIconStyle.GRADIENT -> R.drawable.launcher_preview_phone_gradient
                AppIconStyle.CREAM -> R.drawable.launcher_preview_phone_cream
                AppIconStyle.FEATHER -> R.drawable.launcher_preview_phone_feather
            })

    fun watch(context: Context, style: AppIconStyle): Drawable? = ContextCompat.getDrawable(
            context,
            when (style) {
                AppIconStyle.SOLID -> R.drawable.launcher_preview_watch_solid
                AppIconStyle.GRADIENT -> R.drawable.launcher_preview_watch_gradient
                AppIconStyle.CREAM -> R.drawable.launcher_preview_watch_cream
                AppIconStyle.FEATHER -> R.drawable.launcher_preview_watch_feather
            })
}
