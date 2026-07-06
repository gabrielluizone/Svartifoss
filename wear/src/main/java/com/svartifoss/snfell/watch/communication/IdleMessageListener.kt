package com.svartifoss.snfell.watch.communication

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.watch.view.MainActivity
import timber.log.Timber

class IdleMessageListener : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            CommPaths.MESSAGE_OPEN_APP -> {
                launchMainActivity()
            }
            CommPaths.MESSAGE_START_SERVICE -> {
                // WatchMusicService is a foreground service that calls startForeground() right
                // away, so it must be started with startForegroundService() - a bare startService()
                // from a background WearableListenerService throws IllegalStateException on API 26+.
                // The start can still be refused on API 31+ if the OS considers us fully
                // backgrounded (ForegroundServiceStartNotAllowedException, an IllegalStateException);
                // swallow that rather than crash - the phone re-sends when playback next changes.
                try {
                    ContextCompat.startForegroundService(
                            this, Intent(this, WatchMusicService::class.java))
                } catch (e: IllegalStateException) {
                    Timber.w(e, "Could not start WatchMusicService from background")
                }
            }
            CommPaths.MESSAGE_OPEN_VOICE_SEARCH -> {
                launchMainActivity {
                    it.putExtra(MainActivity.EXTRA_OPEN_VOICE_SEARCH, true)
                }
            }
        }
    }

    /**
     * Launches the watch player from this background listener. Background activity starts are
     * restricted on Wear OS 3+ (Android 11+) and can be refused; guard so a refusal doesn't crash
     * the listener. (If silent refusal is ever seen on a device, a full-screen-intent notification
     * would be the sanctioned way to bring the UI up from the background.)
     */
    private fun launchMainActivity(extras: (Intent) -> Unit = {}) {
        val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .also(extras)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Timber.w(e, "Could not open the watch app from background")
        }
    }
}
