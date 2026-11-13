package com.svartifoss.snfell.watch.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * The one of this app's screens the user is looking at, if any.
 *
 * For the few things that have to be shown from code with no screen of its own - a reply from the
 * phone arriving in a listener service, seconds after the screen that asked for it closed itself.
 * Starting an activity from the application context there would need a new task, which on a watch
 * means either a refusal (nothing of this app is visible) or, worse, the player's task being
 * brought forward over whatever the user had moved on to. Starting it from the screen that is
 * actually in front does neither: it lands in that screen's task, and when there is none, nothing
 * is shown.
 *
 * Process-wide and main-thread only, like the callbacks that feed it.
 */
object FrontActivity : Application.ActivityLifecycleCallbacks {
    private var resumed: WeakReference<Activity>? = null

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    /** Null while none of this app's screens is in front, or the one that was is closing. */
    fun current(): Activity? = resumed?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
