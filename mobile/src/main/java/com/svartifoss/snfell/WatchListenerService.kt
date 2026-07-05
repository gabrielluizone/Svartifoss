package com.svartifoss.snfell

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.music.MusicService

class WatchListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event == null ||
                event.path == CommPaths.MESSAGE_WATCH_CLOSED ||
                event.path == CommPaths.MESSAGE_ACK ||
                event.path == CommPaths.MESSAGE_WATCH_CLOSED_MANUALLY) {

            return
        }

        val musicServiceIntent = Intent(this, MusicService::class.java)
        musicServiceIntent.action = MusicService.ACTION_START_FROM_WATCH
        // MusicService calls startForeground() shortly after being started, and this listener
        // fires from a background trigger (a wearable message, no foreground UI) - startService()
        // alone can throw ForegroundServiceStartNotAllowedException in that case on API 31+.
        ContextCompat.startForegroundService(this, musicServiceIntent)
    }
}
