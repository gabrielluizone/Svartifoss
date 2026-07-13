package com.svartifoss.snfell.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import java.util.concurrent.CancellationException

/**
 * Forwards logged throwables to Crashlytics, but records a *non-fatal issue* only at [Log.ERROR]
 * or higher. Lower-priority throwables - the handled/expected cases our guards log at WARN or
 * below (e.g. a cross-version config blob that failed to decode and fell back to defaults) - are
 * kept only as a breadcrumb on the next real crash rather than raising their own Crashlytics
 * issue. [CancellationException] is always skipped: routine coroutine cancellation is not a fault.
 */
class TimberCrashlytics : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String?, t: Throwable?) {
        if (t != null) {
            val crashlytics = FirebaseCrashlytics.getInstance()
            message?.let { crashlytics.log(it) }
            if (priority >= Log.ERROR && t !is CancellationException) {
                crashlytics.recordException(t)
            }
        }
    }
}
