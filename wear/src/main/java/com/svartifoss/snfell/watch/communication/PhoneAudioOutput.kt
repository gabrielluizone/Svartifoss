package com.svartifoss.snfell.watch.communication

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.svartifoss.snfell.common.actions.StandardActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Opens the phone's audio-output switcher on the phone - the watch's half of
 * `StandardActions.ACTION_OPEN_PHONE_AUDIO_OUTPUT`.
 *
 * Through `RemoteActivityHelper`, which the companion app carries out on the phone: the one way a
 * screen can appear on a phone whose app is in the background, and the same route a streaming
 * shortcut's visible open takes (see [PhoneUriOpener]). Process-scoped because the screen that
 * asked usually closes in the same gesture.
 */
object PhoneAudioOutput {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Asks the phone to show its switcher; [onOpened] runs on the main thread if it accepted. */
    fun open(context: Context, onOpened: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            val opened = try {
                RemoteActivityHelper(app, ContextCompat.getMainExecutor(app)).startRemoteActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(StandardActions.PHONE_AUDIO_OUTPUT_URI))
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                .setPackage(app.packageName)).await()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable, as in PhoneUriOpener: with no phone connected the platform fails the
                // helper's future with a bare Throwable.
                Timber.w(e, "Could not open the phone's audio output switcher")
                false
            }
            if (opened) onOpened()
        }
    }
}
