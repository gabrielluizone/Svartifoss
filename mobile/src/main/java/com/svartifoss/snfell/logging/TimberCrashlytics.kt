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
 *
 * Every call that cannot reach Crashlytics - no throwable, or crash reporting switched off - is
 * turned away before Timber's base class formats it. That formatting runs for every tree before
 * [log] is consulted, so this tree used to format each of the app's log lines only to ignore it.
 */
class TimberCrashlytics : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String?, t: Throwable?) {
        if (t != null && CrashReporting.enabled) {
            val crashlytics = FirebaseCrashlytics.getInstance()
            message?.let { crashlytics.log(it) }
            if (priority >= Log.ERROR && t !is CancellationException) {
                crashlytics.recordException(t)
            }
        }
    }

    // No throwable, nothing to forward.
    override fun v(message: String?, vararg args: Any?) = Unit
    override fun d(message: String?, vararg args: Any?) = Unit
    override fun i(message: String?, vararg args: Any?) = Unit
    override fun w(message: String?, vararg args: Any?) = Unit
    override fun e(message: String?, vararg args: Any?) = Unit
    override fun wtf(message: String?, vararg args: Any?) = Unit

    // A throwable, but only worth formatting while reports may be sent. This tree has no tag to
    // derive, so calling on to the base class costs nothing but the formatting it came for.
    override fun v(t: Throwable?, message: String?, vararg args: Any?) {
        if (CrashReporting.enabled) super.v(t, message, *args)
    }

    override fun d(t: Throwable?, message: String?, vararg args: Any?) {
        if (CrashReporting.enabled) super.d(t, message, *args)
    }

    override fun i(t: Throwable?, message: String?, vararg args: Any?) {
        if (CrashReporting.enabled) super.i(t, message, *args)
    }

    override fun w(t: Throwable?, message: String?, vararg args: Any?) {
        if (CrashReporting.enabled) super.w(t, message, *args)
    }

    override fun e(t: Throwable?, message: String?, vararg args: Any?) {
        if (CrashReporting.enabled) super.e(t, message, *args)
    }

    override fun wtf(t: Throwable?, message: String?, vararg args: Any?) {
        if (CrashReporting.enabled) super.wtf(t, message, *args)
    }
}
