package com.svartifoss.snfell.watch.communication

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.WatchPreferenceMessage
import com.svartifoss.snfell.watch.config.PreferencesBus
import timber.log.Timber

/**
 * Applies a preference snapshot delivered over the low-latency MessageClient (see
 * [WatchPreferenceMessage] on the phone). This is the fast path: unlike the `/Settings` DataItem
 * handled by [PreferencesReceiver], a MessageClient message is delivered promptly whenever the two
 * devices are connected, so a setting changed on the phone lands here immediately instead of
 * waiting for the DataItem to sync out of doze.
 *
 * The DataItem remains the durable source of truth; this only accelerates delivery. A monotonic
 * sequence, persisted locally, drops an out-of-order (older) message so a delayed snapshot can
 * never clobber a newer one. Application is additive - key removals are left to the DataItem path.
 */
class PreferenceMessageReceiver : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != CommPaths.MESSAGE_APPLY_PREFERENCES) return
        val snapshot = WatchPreferenceMessage.decode(event.data) ?: return

        val seqPrefs = getSharedPreferences(SEQUENCE_PREFS, Context.MODE_PRIVATE)
        val lastSequence = seqPrefs.getLong(KEY_LAST_SEQUENCE, Long.MIN_VALUE)
        if (snapshot.sequence <= lastSequence) {
            // A stale (out-of-order) message; the newer one already applied.
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = prefs.edit()
        WatchPreferenceMessage.applyTo(editor, snapshot.values)
        // commit(), not apply(): a WearableListenerService can be torn down right after this
        // callback, so the write must be durable before the sequence is advanced and the bus fired.
        if (!editor.commit()) {
            Timber.w("Could not commit preference message snapshot")
            return
        }
        seqPrefs.edit().putLong(KEY_LAST_SEQUENCE, snapshot.sequence).commit()

        // Same bus the DataItem receiver posts to: an open now-playing screen re-applies at once.
        PreferencesBus.postValue(prefs)
    }

    private companion object {
        const val SEQUENCE_PREFS = "preference_message_sequence"
        const val KEY_LAST_SEQUENCE = "last_sequence"
    }
}
