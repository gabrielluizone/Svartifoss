package com.svartifoss.snfell.logging

import android.os.Build
import com.google.android.gms.wearable.DataMap
import com.google.firebase.crashlytics.FirebaseCrashlytics

import pl.tajchert.exceptionwear.ExceptionWearHandler
import timber.log.Timber

class CrashlyticsExceptionWearHandler : ExceptionWearHandler {
    override fun handleException(throwable: Throwable, map: DataMap) {
        Timber.d("HandleException %s", throwable)

        if (!CrashReporting.enabled) {
            return
        }

        // The watch forwards its uncaught exceptions here wrapped in a WatchException, so we match
        // on the message text. Routine coroutine cancellation and "phone not currently connected"
        // Data Layer failures are expected background noise, not faults - drop them.
        val message = throwable.message.orEmpty()
        if (message.contains("CancellationException") || message.contains("TARGET_NODE_NOT_CONNECTED")) {
            return
        }

        FirebaseCrashlytics.getInstance().setCustomKey("wear_exception", true)
        FirebaseCrashlytics.getInstance().setCustomKey("board", map.getString("board")!!)
        FirebaseCrashlytics.getInstance()
            .setCustomKey("fingerprint", map.getString("fingerprint")!!)
        FirebaseCrashlytics.getInstance().setCustomKey("model", map.getString("model")!!)
        FirebaseCrashlytics.getInstance()
            .setCustomKey("manufacturer", map.getString("manufacturer")!!)
        FirebaseCrashlytics.getInstance().setCustomKey("product", map.getString("product")!!)
        FirebaseCrashlytics.getInstance().setCustomKey(
            "api_level",
            map.getString("api_level")!!
        )

        // WatchException.create puts the watch's frames on the exception from Android N on; below
        // that they travel inside the message text, and the stack is the phone's own.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            throwable.stackTrace = WatchCrashFrames.mark(throwable.stackTrace)
        }

        FirebaseCrashlytics.getInstance().recordException(throwable)
    }
}
