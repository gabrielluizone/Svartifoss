package com.svartifoss.snfell

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.svartifoss.snfell.common.AppIconStyle
import timber.log.Timber

/** Applies the Play Store launcher's selected alias without restarting the running app. */
object LauncherIconController {

    fun apply(context: Context, preferenceValue: String?) {
        // The aliases only exist in the Play manifest. The GitHub artifact must keep its original
        // launcher component and must never try to address the Play-only components.
        if (BuildConfig.FLAVOR != "play") return

        val selected = AppIconStyle.fromPreference(preferenceValue)

        // Enable first so the launcher never spends a refresh with no icon available. The other
        // aliases are removed afterwards; DONT_KILL_APP keeps the current UI alive.
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
            // A style with no alias in the manifest is a build mistake that AppIconStyleContractTest
            // exists to catch. Starting the app is not the place to find out, since this runs from
            // Application.onCreate: skipping one style costs an icon that does not change.
            Timber.w(e, "No launcher alias for the %s icon style", style.preferenceValue)
        }
    }
}
