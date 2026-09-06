package com.svartifoss.snfell.watch.communication

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.WatchPreferenceMessage
import com.svartifoss.snfell.common.WatchPreferenceSyncProtocol

/** Fast additive delivery; [PreferencesReceiver] reconciles deletions at the same revision. */
class PreferenceMessageReceiver : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != CommPaths.MESSAGE_APPLY_PREFERENCES) return
        val snapshot = WatchPreferenceMessage.decode(event.data) ?: return

        val markedSequence = snapshot.values[WatchPreferenceSyncProtocol.SEQUENCE_KEY]
        if (markedSequence != null && markedSequence != snapshot.sequence) return
        WatchPreferenceReceiver.receive(this, ReceivedPreferenceSnapshot(
                snapshot.values, snapshot.sequence, durable = false, sharedProtocol = markedSequence != null))
    }
}
