package com.svartifoss.snfell.notifications

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessaging
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.common.MiscPreferences
import timber.log.Timber

/**
 * Single privacy gate for developer announcement push notifications (Firebase Cloud Messaging).
 *
 * There is no backend server, so "notify everyone" means one FCM topic every install subscribes
 * to; a message composed as a Firebase Console campaign targeting that topic reaches every
 * subscribed device without Svartifoss ever holding a list of users or tokens itself. Mirrors
 * [com.svartifoss.snfell.logging.CrashReporting]'s shape: a single object that reads the
 * preference at startup and on every change, so there is exactly one place that can leave the
 * subscription state inconsistent with the user's choice.
 */
object AnnouncementNotifications : SharedPreferences.OnSharedPreferenceChangeListener {
    /** Every install shares this topic; there is no per-user or per-segment targeting. */
    private const val TOPIC = "announcements"

    private var preferences: SharedPreferences? = null

    fun initialize(context: android.content.Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        preferences?.unregisterOnSharedPreferenceChangeListener(this)
        preferences = prefs
        prefs.registerOnSharedPreferenceChangeListener(this)
        applyPreference(prefs)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == MiscPreferences.ANNOUNCEMENTS_ENABLED.key) {
            applyPreference(sharedPreferences)
        }
    }

    private fun applyPreference(sharedPreferences: SharedPreferences) {
        val enabled = Preferences.getBoolean(sharedPreferences, MiscPreferences.ANNOUNCEMENTS_ENABLED)
        val messaging = FirebaseMessaging.getInstance()
        val task = if (enabled) messaging.subscribeToTopic(TOPIC) else messaging.unsubscribeFromTopic(TOPIC)
        task.addOnFailureListener { e ->
            Timber.w(e, "Could not update announcement topic subscription (enabled=$enabled)")
        }
    }
}
