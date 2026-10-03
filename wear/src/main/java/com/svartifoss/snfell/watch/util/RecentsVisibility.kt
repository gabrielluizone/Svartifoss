package com.svartifoss.snfell.watch.util

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import androidx.preference.PreferenceManager
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.common.MiscPreferences
import timber.log.Timber

/**
 * Applies [MiscPreferences.WEAR_HIDE_FROM_RECENTS] to the task this activity runs in.
 *
 * At runtime through [ActivityManager.AppTask.setExcludeFromRecents] rather than with
 * `android:excludeFromRecents` in the manifest, because it is a choice the user can turn on and
 * off: the manifest attribute is fixed at install. The flag belongs to the *task*, so applying it
 * from `MainActivity` - the root every screen of the app is opened on top of - covers the queue,
 * the menu, the lyrics and the rest too.
 *
 * Only this activity's own task is touched. The app has another one: `ShortcutLaunchActivity`, the
 * Tile's invisible trampoline, runs in a task of its own and is excluded from recents in the
 * manifest. Clearing the flag on every app task would bring that empty task back into the list.
 *
 * Re-applied rather than set once, like [applyKeepScreenOnPreference]: the preference is owned by
 * the phone and arrives over the Data Layer, so it can flip while the player is open, and the flag
 * is cleared as well as set so that switching it back off takes effect too.
 *
 * What the platform does with it is worth knowing, because it is not "never listed": Android keeps
 * an excluded task in recents while it is the *most recently used* one, so after pressing the
 * button back to the watch face the app can still sit at the front of the list. It drops out once
 * another app has been used, and a task whose activities have all finished (the app swiped closed)
 * is not shown at all. That is the platform's rule for every app that hides itself from recents,
 * not something this can change. Its process, the background service, the ongoing-activity chip
 * and the Now Bar entry are all untouched either way.
 */
fun Activity.applyRecentsVisibilityPreference() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val hide = Preferences.getBoolean(prefs, MiscPreferences.WEAR_HIDE_FROM_RECENTS)
    val activityManager = getSystemService(ActivityManager::class.java) ?: return

    try {
        activityManager.appTasks
                .firstOrNull { it.taskInfo.idOfTask() == taskId }
                ?.setExcludeFromRecents(hide)
    } catch (e: RuntimeException) {
        // A task the system has just removed throws rather than answering. Nothing to recover:
        // the next start applies it to whatever task the app is in by then.
        Timber.w(e, "Could not change whether the app shows in recent apps")
    }
}

/** The task's id as [Activity.getTaskId] reports it, on every supported API level. */
private fun ActivityManager.RecentTaskInfo.idOfTask(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            taskId
        } else {
            @Suppress("DEPRECATION")
            persistentId
        }
