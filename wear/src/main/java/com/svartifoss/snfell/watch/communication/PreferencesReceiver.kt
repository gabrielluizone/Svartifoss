package com.svartifoss.snfell.watch.communication

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.matejdro.wearutils.preferencesync.PreferencePusher
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.WatchPreferenceSyncProtocol
import timber.log.Timber

/** Durable settings share the fast message path's ordering guard and atomic preference commit. */
class PreferencesReceiver : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val snapshots = dataEvents.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED ||
                    event.dataItem.uri.path != CommPaths.PREFERENCES_PREFIX) return@mapNotNull null
            try {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val sharedSequence = dataMap.get(WatchPreferenceSyncProtocol.SEQUENCE_KEY) as? Long
                require(!dataMap.containsKey(WatchPreferenceSyncProtocol.SEQUENCE_KEY) || sharedSequence != null) {
                    "Invalid preference snapshot sequence"
                }
                val values = dataMap.keySet().mapNotNull entry@ { key ->
                    if (key == PreferencePusher.SYNC_KEYS_KEY || key == PreferencePusher.SYNC_REVISION_KEY ||
                            !WatchPreferenceReceiver.isPreferenceKey(key)) return@entry null
                    val value: Any = when (val raw = dataMap.get<Any>(key)) {
                        is Int, is Long, is Boolean, is Float, is String -> raw
                        is ArrayList<*> -> {
                            require(raw.all { it is String }) { "Unsupported preference list for $key" }
                            raw.filterIsInstance<String>().toSet()
                        }
                        else -> error("Unsupported preference type for $key")
                    }
                    key to value
                }.toMap()
                ReceivedPreferenceSnapshot(values, sharedSequence ?: Long.MIN_VALUE,
                        durable = true, sharedProtocol = sharedSequence != null,
                        legacyRevision = dataMap.getLong(PreferencePusher.SYNC_REVISION_KEY, Long.MIN_VALUE))
            } catch (e: Exception) {
                Timber.w(e, "Could not decode preference DataItem")
                null
            }
        }
        // Collapse a delivery batch before any disk writes or UI notifications. Across batches,
        // the shared guard also rejects old durable copies after a newer fast message landed.
        snapshots.maxWithOrNull(compareBy<ReceivedPreferenceSnapshot> { it.sharedProtocol }
                .thenBy { if (it.sharedProtocol) it.sequence else it.legacyRevision })
                ?.let { WatchPreferenceReceiver.receive(this, it) }
    }
}
