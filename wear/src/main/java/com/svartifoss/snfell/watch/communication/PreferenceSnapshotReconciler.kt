package com.svartifoss.snfell.watch.communication

internal data class PreferenceSyncState(
        val sequence: Long = Long.MIN_VALUE,
        val durableSequence: Long = Long.MIN_VALUE,
        val legacyRevision: Long = Long.MIN_VALUE,
        val sharedProtocolSeen: Boolean = false,
        val syncedKeys: Set<String> = emptySet()
)

internal data class ReceivedPreferenceSnapshot(
        val values: Map<String, Any>,
        val sequence: Long,
        val durable: Boolean,
        val sharedProtocol: Boolean,
        val legacyRevision: Long = Long.MIN_VALUE
)

internal data class PreferenceSnapshotChange(
        val writes: Map<String, Any>,
        val removals: Set<String>,
        val state: PreferenceSyncState
) {
    val valuesChanged: Boolean get() = writes.isNotEmpty() || removals.isNotEmpty()
}

/**
 * One ordering and ownership policy for both transports. Messages remain additive; the matching
 * durable snapshot can still delete absent phone-owned keys after its values arrived by message.
 * The owner serializes calls and commits values and [PreferenceSyncState] in one transaction.
 */
internal class PreferenceSnapshotReconciler(initialState: PreferenceSyncState) {
    var state: PreferenceSyncState = initialState
        private set
    private var unpublishedChanges = false
    private var uncommittedKeys: Set<String> = emptySet()

    enum class Result { IGNORED, UNCHANGED, CHANGED, FAILED }

    fun receive(
            snapshot: ReceivedPreferenceSnapshot,
            currentValues: Map<String, *>,
            commit: (PreferenceSnapshotChange) -> Boolean
    ): Result {
        if (state.sharedProtocolSeen && !snapshot.sharedProtocol) return Result.IGNORED

        val stale = if (!snapshot.sharedProtocol && snapshot.durable) {
            snapshot.legacyRevision != Long.MIN_VALUE && snapshot.legacyRevision <= state.legacyRevision
        } else {
            snapshot.sequence < state.sequence ||
                    (snapshot.sequence == state.sequence &&
                            (!snapshot.durable || snapshot.sequence <= state.durableSequence))
        }
        if (stale) {
            // A delayed marked DataItem can be our first evidence that the phone was upgraded.
            // Record that fact even if a newer message already supplied the current values.
            if (snapshot.sharedProtocol && !state.sharedProtocolSeen) {
                return commitChange(PreferenceSnapshotChange(emptyMap(), emptySet(),
                        state.copy(sharedProtocolSeen = true)), commit)
            }
            return Result.IGNORED
        }

        val latestKeys = snapshot.values.keys
        val removals = if (snapshot.durable) {
            (state.syncedKeys + uncommittedKeys - latestKeys)
                    .filterTo(mutableSetOf()) { it in currentValues }
        } else {
            emptySet()
        }
        val writes = snapshot.values.filter { (key, value) -> currentValues[key] != value }
        val nextState = state.copy(
                sequence = if (snapshot.sharedProtocol || !snapshot.durable) snapshot.sequence else state.sequence,
                durableSequence = if (snapshot.sharedProtocol && snapshot.durable) {
                    snapshot.sequence
                } else state.durableSequence,
                legacyRevision = if (!snapshot.sharedProtocol && snapshot.durable &&
                        snapshot.legacyRevision != Long.MIN_VALUE) snapshot.legacyRevision else state.legacyRevision,
                sharedProtocolSeen = state.sharedProtocolSeen || snapshot.sharedProtocol,
                // A message can introduce keys that never reach a DataItem before the next edit.
                // Remember ownership now so a future durable snapshot can remove those too.
                syncedKeys = if (snapshot.durable) latestKeys.toSet()
                        else state.syncedKeys + uncommittedKeys + latestKeys
        )
        return commitChange(PreferenceSnapshotChange(writes, removals, nextState), commit)
    }

    private fun commitChange(
            change: PreferenceSnapshotChange,
            commit: (PreferenceSnapshotChange) -> Boolean
    ): Result {
        // commit(false) may still update SharedPreferences' in-memory map. Keep accepted ordering
        // here until disk succeeds, so a retry is not rejected by metadata from a failed commit.
        unpublishedChanges = unpublishedChanges || change.valuesChanged
        if (!commit(change)) {
            // A failed write may also introduce keys in memory. Keep their ownership until a
            // successful snapshot can reconcile them, without acknowledging the failed revision.
            uncommittedKeys = uncommittedKeys + change.state.syncedKeys
            return Result.FAILED
        }
        state = change.state
        uncommittedKeys = emptySet()
        val result = if (unpublishedChanges) Result.CHANGED else Result.UNCHANGED
        unpublishedChanges = false
        return result
    }
}
