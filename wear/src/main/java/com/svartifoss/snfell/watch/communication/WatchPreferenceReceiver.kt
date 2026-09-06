package com.svartifoss.snfell.watch.communication

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.WatchPreferenceMessage
import com.svartifoss.snfell.common.WatchPreferenceSyncProtocol
import com.svartifoss.snfell.watch.config.PreferencesBus
import timber.log.Timber

/** Process-wide serialization for the two independent WearableListenerService callback threads. */
internal object WatchPreferenceReceiver {
    private const val STATE_PREFIX = "__svartifoss_preference_receiver_"
    private const val KEY_SEQUENCE = STATE_PREFIX + "sequence"
    private const val KEY_DURABLE_SEQUENCE = STATE_PREFIX + "durable_sequence"
    private const val KEY_LEGACY_REVISION = STATE_PREFIX + "legacy_revision"
    private const val KEY_PROTOCOL_SEEN = STATE_PREFIX + "shared_protocol"
    private const val KEY_SYNCED_KEYS = STATE_PREFIX + "synced_keys"

    private var reconciler: PreferenceSnapshotReconciler? = null

    fun isPreferenceKey(key: String): Boolean =
            key != WatchPreferenceSyncProtocol.SEQUENCE_KEY && !key.startsWith(STATE_PREFIX)

    @Synchronized
    fun receive(context: Context, snapshot: ReceivedPreferenceSnapshot) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val receiver = reconciler ?: PreferenceSnapshotReconciler(readState(context, prefs)).also {
            reconciler = it
        }
        val safeSnapshot = snapshot.copy(values = snapshot.values.filterKeys(::isPreferenceKey))
        val result = receiver.receive(safeSnapshot, prefs.all) { change ->
            val editor = prefs.edit()
            change.removals.forEach(editor::remove)
            WatchPreferenceMessage.applyTo(editor, change.writes)
            editor.putLong(KEY_SEQUENCE, change.state.sequence)
                    .putLong(KEY_DURABLE_SEQUENCE, change.state.durableSequence)
                    .putLong(KEY_LEGACY_REVISION, change.state.legacyRevision)
                    .putBoolean(KEY_PROTOCOL_SEEN, change.state.sharedProtocolSeen)
                    .putStringSet(KEY_SYNCED_KEYS, change.state.syncedKeys)
                    .commit()
        }
        when (result) {
            PreferenceSnapshotReconciler.Result.CHANGED -> PreferencesBus.postValue(prefs)
            PreferenceSnapshotReconciler.Result.FAILED -> Timber.w("Could not commit received preference snapshot")
            else -> Unit
        }
    }

    private fun readState(context: Context, prefs: SharedPreferences): PreferenceSyncState {
        val oldDataState = context.getSharedPreferences("wearutils.preference_sync_state", Context.MODE_PRIVATE)
        val oldMessageState = context.getSharedPreferences("preference_message_sequence", Context.MODE_PRIVATE)
        return PreferenceSyncState(
                sequence = prefs.getLong(KEY_SEQUENCE, oldMessageState.getLong("last_sequence", Long.MIN_VALUE)),
                durableSequence = prefs.getLong(KEY_DURABLE_SEQUENCE, Long.MIN_VALUE),
                legacyRevision = prefs.getLong(KEY_LEGACY_REVISION,
                        oldDataState.getLong(CommPaths.PREFERENCES_PREFIX + "::revision", Long.MIN_VALUE)),
                sharedProtocolSeen = prefs.getBoolean(KEY_PROTOCOL_SEEN, false),
                syncedKeys = prefs.getStringSet(KEY_SYNCED_KEYS,
                        oldDataState.getStringSet(CommPaths.PREFERENCES_PREFIX, emptySet()))
                        .orEmpty().filterTo(mutableSetOf(), ::isPreferenceKey)
        )
    }
}
