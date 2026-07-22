package com.svartifoss.snfell.watch.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.common.AppLocales
import com.svartifoss.snfell.common.MiscPreferences

/**
 * Applies the phone-chosen UI language ([MiscPreferences.APP_LANGUAGE]) to the watch app.
 *
 * The watch cannot use `AppCompatDelegate.setApplicationLocales` the way the phone does:
 * `MenuActivity` and `QueueActivity` are plain `ComponentActivity`, and AppCompat's pre-API-33
 * locale backport only recreates `AppCompatActivity` instances - those two screens would keep
 * rendering in the device language. Wrapping the base context in [attach] works on every API level
 * and for every activity base class.
 *
 * The value arrives through the normal preference sync (it is in `MiscPreferences.EXPORTABLE`), so
 * there is nothing watch-specific to store or request.
 */
object WatchLanguage {

    /** Call from `Activity.attachBaseContext`. */
    fun attach(base: Context): Context = AppLocales.wrap(base, storedTag(base))

    /** The normalised language tag currently stored, for change detection. */
    fun storedTag(context: Context): String =
            tagFrom(PreferenceManager.getDefaultSharedPreferences(context))

    fun tagFrom(preferences: SharedPreferences): String =
            AppLocales.normalize(Preferences.getString(preferences, MiscPreferences.APP_LANGUAGE))
}
