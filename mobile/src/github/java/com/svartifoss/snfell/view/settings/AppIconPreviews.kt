package com.svartifoss.snfell.view.settings

import android.content.Context
import android.graphics.drawable.Drawable
import com.svartifoss.snfell.common.AppIconStyle

/**
 * GitHub (sideload) build: the launcher icon is not selectable, so the picker never opens and
 * there is nothing to preview. The real implementation, and the reasons it exists, live in
 * `src/play`; `src/main` only reaches this object through the picker. AppIconStyleContractTest
 * pins the split.
 */
object AppIconPreviews {

    @Suppress("UNUSED_PARAMETER")
    fun phone(context: Context, style: AppIconStyle): Drawable? = null

    @Suppress("UNUSED_PARAMETER")
    fun watch(context: Context, style: AppIconStyle): Drawable? = null
}
