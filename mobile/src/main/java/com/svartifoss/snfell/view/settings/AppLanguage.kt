package com.svartifoss.snfell.view.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.common.AppLocales
import com.svartifoss.snfell.common.MiscPreferences

/**
 * Applies [MiscPreferences.APP_LANGUAGE] to the phone app.
 *
 * `AppCompatDelegate.setApplicationLocales` is used rather than wrapping contexts by hand (the way
 * the watch has to): every phone activity is an `AppCompatActivity`, and on Android 13+ this hands
 * the choice to the framework's per-app language setting, so it also appears in system Settings and
 * survives outside the app's own UI.
 *
 * The value is still stored in our own SharedPreferences, not left to AppCompat's `autoStoreLocales`
 * persistence, because it has to be a normal syncing preference for the watch to receive it.
 */
object AppLanguage {

    /** Applies the stored language. Call once per process start, before any UI is shown. */
    fun applyStored(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        apply(Preferences.getString(preferences, MiscPreferences.APP_LANGUAGE))
    }

    fun apply(value: String?) {
        val locale = AppLocales.localeFor(value)
        AppCompatDelegate.setApplicationLocales(
                if (locale == null) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.create(locale)
                }
        )
    }
}
