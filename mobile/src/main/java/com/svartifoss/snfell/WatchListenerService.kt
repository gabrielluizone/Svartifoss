package com.svartifoss.snfell

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.music.WatchCommandDelivery

class WatchListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        // Keep the event that wakes the service: registering its live MessageClient listener
        // after startup cannot recover a command that was already delivered here.
        WatchCommandDelivery.receive(this, event)
    }
}
