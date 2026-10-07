package com.google.android.wearable.intent

import android.content.Context
import android.content.Intent
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * The one method of the legacy Wear Support Library's `RemoteIntent` that wearutils'
 * `PhoneAppNoticeActivity` calls, written over [RemoteActivityHelper].
 *
 * The watch build excludes `com.google.android.support:wearable` (its `android.support.wearable`
 * classes duplicate the platform's `com.google.android.wearable` shared library, which this module
 * compiles against), and the shared library has no `intent` package, so the real class is simply not
 * in the APK. Everything that touches it was therefore a latent crash: tapping *Open on phone* on the
 * "install the phone app" screen died with `NoClassDefFoundError` the moment it was reached. It went
 * unnoticed because on a small round watch the screen's button used to be cut off by the screen's
 * edge and nobody could tap it; making the button reachable is what exposed this.
 *
 * The calling code lives in a submodule, so the fix is to be the class it already names rather than to
 * edit it. The signature and the two result codes are the real ones (`RESULT_OK = 0`,
 * `RESULT_FAILED = 1`; both are compile-time constants, inlined into the caller). Like the original it
 * wants an `ACTION_VIEW` intent with a data URI and the `BROWSABLE` category, which is also what
 * [RemoteActivityHelper] requires. Nothing in the app should call this directly: opening something on
 * the phone from our own code goes through `PhoneUriOpener`.
 */
object RemoteIntent {
    const val RESULT_OK = 0
    const val RESULT_FAILED = 1

    /**
     * Longer than a connected phone ever needs, shorter than a person waits on a spinner: the caller
     * shows an indeterminate progress bar until the receiver answers, so a phone that is paired but out
     * of reach has to end in a failure message and not in a screen that never moves on.
     */
    private const val OPEN_TIMEOUT_MS = 10_000L

    // Process-scoped: the caller finishes its screen from the receiver, and nothing about the request
    // should depend on that screen still being there to be cancelled with it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @JvmStatic
    fun startRemoteActivity(context: Context, intent: Intent, resultReceiver: ResultReceiver?) {
        val appContext = context.applicationContext
        scope.launch {
            val opened = try {
                withTimeoutOrNull(OPEN_TIMEOUT_MS) {
                    RemoteActivityHelper(appContext, ContextCompat.getMainExecutor(appContext))
                            .startRemoteActivity(intent).await()
                    true
                } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable, not Exception, on purpose: with no companion node the platform's
                // remote-interactions service fails the helper's future with a bare Throwable, which
                // `catch (e: Exception)` lets through to the coroutine and so to the process (found on
                // a watch with no phone paired, which is what a reviewer's watch is). No paired phone,
                // a phone out of reach, an intent the helper refuses: all of them are the same answer
                // to the person looking at the watch.
                Timber.w(e, "Could not open the link on the phone")
                false
            }
            resultReceiver?.send(if (opened) RESULT_OK else RESULT_FAILED, null)
        }
    }
}
