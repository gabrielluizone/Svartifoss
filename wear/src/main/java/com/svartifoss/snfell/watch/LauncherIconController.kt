package com.svartifoss.snfell.watch

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.common.AppIconStyle
import timber.log.Timber

/** Applies the launcher alias selected on the phone and synchronized to this watch app. */
object LauncherIconController {

    fun apply(context: Context, preferenceValue: String?) {
        // GitHub watches have no aliases and retain the original launcher entry.
        if (BuildConfig.FLAVOR != "play") return

        val selected = AppIconStyle.fromPreference(preferenceValue)

        // Enable first so the launcher never spends a refresh with no icon available.
        setEnabled(context, selected, true)
        AppIconStyle.entries.filter { it != selected }.forEach { setEnabled(context, it, false) }
    }

    private fun setEnabled(context: Context, style: AppIconStyle, enabled: Boolean) {
        try {
            context.packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, context.packageName + style.aliasName),
                    if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP)
        } catch (e: IllegalArgumentException) {
            // See the phone's controller: a style with no alias is a build mistake pinned by a
            // test, and this runs from Application.onCreate and the preference receiver.
            Timber.w(e, "No launcher alias for the %s icon style", style.preferenceValue)
        }
    }
}
