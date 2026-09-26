package com.svartifoss.snfell.watch.util

import android.app.Application
import com.matejdro.wearutils.logging.TimberExceptionWear

/**
 * [TimberExceptionWear], without formatting the lines it has no use for.
 *
 * It forwards a throwable logged at ERROR or above to the phone's crash reporting and ignores
 * everything else - but the bundled Timber formats every message in the base class before a tree
 * gets to decide, so each of the watch's log lines was formatted once more here only to be dropped.
 * Only error and assert calls that carry a throwable can reach it, so those are all that is let
 * through.
 */
class ErrorsOnlyExceptionWearTree(application: Application) : TimberExceptionWear(application) {
    override fun v(message: String?, vararg args: Any?) = Unit
    override fun v(t: Throwable?, message: String?, vararg args: Any?) = Unit
    override fun d(message: String?, vararg args: Any?) = Unit
    override fun d(t: Throwable?, message: String?, vararg args: Any?) = Unit
    override fun i(message: String?, vararg args: Any?) = Unit
    override fun i(t: Throwable?, message: String?, vararg args: Any?) = Unit
    override fun w(message: String?, vararg args: Any?) = Unit
    override fun w(t: Throwable?, message: String?, vararg args: Any?) = Unit
    override fun e(message: String?, vararg args: Any?) = Unit
    override fun wtf(message: String?, vararg args: Any?) = Unit
}
