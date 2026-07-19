package com.svartifoss.snfell.logging

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.common.MiscPreferences

/**
 * Single privacy gate for every Crashlytics entry point in the phone process.
 *
 * Automatic collection stays disabled permanently. Crashlytics may still finalize a crash locally,
 * but reports are uploaded only through [FirebaseCrashlytics.sendUnsentReports] after this object
 * has read the user's choice. The preference defaults to true; opting out gates every custom entry
 * point immediately and deletes reports that are still queued on the device.
 */
object CrashReporting : SharedPreferences.OnSharedPreferenceChangeListener {
    @Volatile
    var enabled: Boolean = true
        private set

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        preferences?.unregisterOnSharedPreferenceChangeListener(this)
        preferences = prefs
        prefs.registerOnSharedPreferenceChangeListener(this)
        applyPreference(prefs)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == MiscPreferences.CRASH_REPORTING_ENABLED.key) {
            applyPreference(sharedPreferences)
        }
    }

    private fun applyPreference(sharedPreferences: SharedPreferences) {
        val allowReports = Preferences.getBoolean(
                sharedPreferences,
                MiscPreferences.CRASH_REPORTING_ENABLED
        )
        enabled = allowReports

        FirebaseCrashlytics.getInstance().apply {
            // Never opt into automatic collection here. This explicit false also replaces any
            // persisted override left by an older build that used the SDK's automatic mode.
            setCrashlyticsCollectionEnabled(false)
            if (allowReports) {
                // Fatal reports are finalized after the next process start. Send only after the
                // current preference has been read, so startup never bypasses the privacy gate.
                sendUnsentReports()
            } else {
                // An explicit opt-out removes reports queued while consent was enabled; they must
                // not be uploaded if the user changes their mind again later.
                deleteUnsentReports()
            }
        }
    }
}
