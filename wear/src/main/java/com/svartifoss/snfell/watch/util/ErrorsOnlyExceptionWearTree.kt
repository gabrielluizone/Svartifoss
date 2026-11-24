package com.svartifoss.snfell.watch.util

import android.app.Application
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.ChannelIOException
import com.matejdro.wearutils.logging.TimberExceptionWear
import com.svartifoss.snfell.common.logging.RoutineTransportFailure

/**
 * [TimberExceptionWear], without formatting the lines it has no use for.
 *
 * It forwards a throwable logged at ERROR or above to the phone's crash reporting and ignores
 * everything else - but the bundled Timber formats every message in the base class before a tree
 * gets to decide, so each of the watch's log lines was formatted once more here only to be dropped.
 * Only error and assert calls that carry a throwable can reach it, so those are all that is let
 * through.
 *
 * A failure that only says the phone link is down or absent ([RoutineTransportFailure]: the phone
 * out of range, a channel closed mid-transfer) is not forwarded either. It is the setting, not a
 * fault, and it used to reach the phone's crash reports as its own issue every time the Bluetooth
 * link dropped during an update or a font transfer.
 */
class ErrorsOnlyExceptionWearTree(application: Application) : TimberExceptionWear(application) {
    override fun log(priority: Int, tag: String?, message: String?, t: Throwable?) {
        if (t != null && RoutineTransportFailure.isRoutine(
                        t,
                        statusCodeOf = { (it as? ApiException)?.statusCode },
                        isLinkDrop = { it is ChannelIOException })) {
            return
        }
        super.log(priority, tag, message, t)
    }

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
