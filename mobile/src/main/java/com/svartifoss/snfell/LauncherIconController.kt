package com.svartifoss.snfell

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.svartifoss.snfell.common.AppIconStyle

/** Applies the Play Store launcher's selected alias without restarting the running app. */
object LauncherIconController {

    private const val SOLID_ALIAS = ".LauncherIconSolid"
    private const val GRADIENT_ALIAS = ".LauncherIconGradient"

    fun apply(context: Context, preferenceValue: String?) {
        // The aliases only exist in the Play manifest. The GitHub artifact must keep its original
        // launcher component and must never try to address the Play-only components.
        if (BuildConfig.FLAVOR != "play") return

        val packageManager = context.packageManager
        val selected = AppIconStyle.fromPreference(preferenceValue)
        val enabled = if (selected == AppIconStyle.SOLID) SOLID_ALIAS else GRADIENT_ALIAS
        val disabled = if (selected == AppIconStyle.SOLID) GRADIENT_ALIAS else SOLID_ALIAS

        // Enable first so the launcher never spends a refresh with neither icon available. The
        // second operation removes the old alias; DONT_KILL_APP keeps the current UI alive.
        packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, context.packageName + enabled),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP)
        packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, context.packageName + disabled),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
    }
}
