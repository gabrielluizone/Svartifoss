package com.svartifoss.snfell.notifications

import android.content.Context
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

    /** Its own small file: the default one is several hundred kilobytes and rewritten whole. */
    private const val STATE_PREFS = "announcement_subscription"
    private const val KEY_SUBSCRIBED = "subscribed"
    private const val KEY_APPLIED_AT = "applied_at"

    private var preferences: SharedPreferences? = null
    private var state: SharedPreferences? = null

    fun initialize(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        state = context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        preferences?.unregisterOnSharedPreferenceChangeListener(this)
        preferences = prefs
        prefs.registerOnSharedPreferenceChangeListener(this)
        applyPreference(prefs, force = false)
    }

    /**
     * A new FCM token has no topic subscriptions - they belong to the token, not to the install - so
     * whatever was recorded as applied no longer is.
     */
    fun onTokenChanged(context: Context) {
        val appContext = context.applicationContext
        applyPreference(PreferenceManager.getDefaultSharedPreferences(appContext), force = true,
                state = appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE))
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == MiscPreferences.ANNOUNCEMENTS_ENABLED.key) {
            applyPreference(sharedPreferences, force = false)
        }
    }

    private fun applyPreference(
            sharedPreferences: SharedPreferences,
            force: Boolean,
            state: SharedPreferences? = this.state
    ) {
        val enabled = Preferences.getBoolean(sharedPreferences, MiscPreferences.ANNOUNCEMENTS_ENABLED)
        val recorded = state?.takeIf { it.contains(KEY_SUBSCRIBED) }?.getBoolean(KEY_SUBSCRIBED, false)
        val appliedAt = state?.getLong(KEY_APPLIED_AT, 0L) ?: 0L
        val now = System.currentTimeMillis()
        if (!force && !AnnouncementSubscriptionPolicy.needsApplying(enabled, recorded, appliedAt, now)) {
            return
        }

        val messaging = FirebaseMessaging.getInstance()
        val task = if (enabled) messaging.subscribeToTopic(TOPIC) else messaging.unsubscribeFromTopic(TOPIC)
        task.addOnSuccessListener {
            state?.edit()
                    ?.putBoolean(KEY_SUBSCRIBED, enabled)
                    ?.putLong(KEY_APPLIED_AT, now)
                    ?.apply()
        }
        task.addOnFailureListener { e ->
            Timber.w(e, "Could not update announcement topic subscription (enabled=$enabled)")
        }
    }
}

/**
 * Whether the announcement topic subscription has to be sent to FCM again.
 *
 * It used to be sent on every process start, and the phone process starts often - a watch message,
 * the notification listener rebinding, the music service coming back. FCM does not compare the
 * request against the subscription the token already has, so each start was one more network round
 * trip to say what the server already knew. Now it is sent when the choice differs from the last one
 * FCM confirmed, when nothing has been confirmed yet, and otherwise once every [REFRESH_INTERVAL_MS]
 * as a repair for a subscription lost on the server's side. A token change clears it separately
 * (see [AnnouncementNotifications.onTokenChanged]).
 */
internal object AnnouncementSubscriptionPolicy {
    const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

    fun needsApplying(enabled: Boolean, confirmed: Boolean?, confirmedAtMs: Long, nowMs: Long): Boolean {
        if (confirmed == null || confirmed != enabled) return true
        // A clock set backwards makes the age negative; re-apply rather than wait for it to catch up.
        val age = nowMs - confirmedAtMs
        return age < 0 || age >= REFRESH_INTERVAL_MS
    }
}
