package com.svartifoss.snfell.watch

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.common.AppIconStyle

/** Applies the launcher alias selected on the phone and synchronized to this watch app. */
object LauncherIconController {

    private const val SOLID_ALIAS = ".LauncherIconSolid"
    private const val GRADIENT_ALIAS = ".LauncherIconGradient"

    fun apply(context: Context, preferenceValue: String?) {
        // GitHub watches have no aliases and retain the original launcher entry.
        if (BuildConfig.FLAVOR != "play") return

        val packageManager = context.packageManager
        val selected = AppIconStyle.fromPreference(preferenceValue)
        val enabled = if (selected == AppIconStyle.SOLID) SOLID_ALIAS else GRADIENT_ALIAS
        val disabled = if (selected == AppIconStyle.SOLID) GRADIENT_ALIAS else SOLID_ALIAS

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
