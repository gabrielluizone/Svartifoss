package com.svartifoss.snfell.watch.communication

import com.svartifoss.snfell.watch.communication.PreferenceSnapshotReconciler.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceSnapshotReconcilerTest {
    @Test
    fun delayedDurableThemeCannotUndoNewerMessageTheme() {
        val watch = Watch()
        watch.receive(message(1, "theme" to "first"))
        watch.receive(message(2, "theme" to "latest"))

        assertEquals(Result.IGNORED, watch.receive(data(1, "theme" to "first")))
        assertEquals(Result.UNCHANGED, watch.receive(data(2, "theme" to "latest")))
        assertEquals("latest", watch.values["theme"])
        assertEquals(2, watch.refreshes)
    }

    @Test
    fun delayedMessageCannotUndoNewerDurableTheme() {
        val watch = Watch()
        watch.receive(data(2, "theme" to "latest"))
        assertEquals(Result.IGNORED, watch.receive(message(1, "theme" to "first")))
        assertEquals(Result.IGNORED, watch.receive(message(2, "theme" to "latest")))
        assertEquals(1, watch.refreshes)
    }

    @Test
    fun durableCopyWithSameSequenceStillRemovesAbsentKeys() {
        val watch = Watch()
        watch.receive(data(1, "theme" to "first", "custom" to "old"))
        watch.receive(message(2, "theme" to "latest"))
        assertEquals("old", watch.values["custom"])

        assertEquals(Result.CHANGED, watch.receive(data(2, "theme" to "latest")))
        assertFalse(watch.values.containsKey("custom"))
        assertEquals(Result.IGNORED, watch.receive(data(2, "theme" to "latest")))
    }

    @Test
    fun messageOnlyKeysAreOwnedAndRemovedByLaterDurableSnapshot() {
        val watch = Watch(values = mapOf("watch_local" to 19))
        watch.receive(message(1, "ephemeral" to "value"))
        watch.receive(message(2, "theme" to "latest"))
        watch.receive(data(2, "theme" to "latest"))

        assertEquals(mapOf("watch_local" to 19, "theme" to "latest"), watch.values)
        assertEquals(setOf("theme"), watch.persistedState.syncedKeys)
    }

    @Test
    fun emptySnapshotClearsAllPhoneOwnedKeysAndKeepsWatchLocalKeys() {
        val watch = Watch(values = mapOf("watch_local" to true))
        watch.receive(message(1, "theme" to "first"))
        assertEquals(Result.UNCHANGED, watch.receive(message(2)))
        assertEquals("first", watch.values["theme"])
        assertEquals(Result.CHANGED, watch.receive(data(2)))
        assertEquals(mapOf("watch_local" to true), watch.values)
        assertTrue(watch.persistedState.syncedKeys.isEmpty())
    }

    @Test
    fun restartRetainsCrossTransportOrderingAndPendingRemovals() {
        val original = Watch()
        original.receive(data(1, "custom" to "old"))
        original.receive(message(3, "theme" to "latest"))
        val restarted = Watch(original.persistedState, original.values)

        assertEquals(Result.IGNORED, restarted.receive(data(2, "theme" to "intermediate")))
        assertEquals(Result.CHANGED, restarted.receive(data(3, "theme" to "latest")))
        assertEquals(mapOf("theme" to "latest"), restarted.values)
    }

    @Test
    fun equalValuesWithNewSequenceDoNotRefreshUi() {
        val watch = Watch()
        watch.receive(message(1, "theme" to "same", "blacklist" to setOf("a", "b")))
        assertEquals(Result.UNCHANGED,
                watch.receive(message(2, "theme" to "same", "blacklist" to setOf("b", "a"))))
        assertEquals(Result.UNCHANGED,
                watch.receive(data(2, "theme" to "same", "blacklist" to setOf("a", "b"))))
        assertEquals(1, watch.refreshes)
        assertEquals(2L, watch.persistedState.durableSequence)
    }

    @Test
    fun failedCommitDoesNotAdvanceAcceptedRevisionAndRetryPublishesChangedMemory() {
        val watch = Watch()
        watch.failNextCommit = true
        assertEquals(Result.FAILED, watch.receive(message(2, "theme" to "latest")))
        assertEquals("latest", watch.values["theme"])
        assertEquals(Long.MIN_VALUE, watch.reconciler.state.sequence)
        assertEquals(Long.MIN_VALUE, watch.persistedState.sequence)
        assertEquals(0, watch.refreshes)

        assertEquals(Result.CHANGED, watch.receive(message(2, "theme" to "latest")))
        assertEquals(2L, watch.persistedState.sequence)
        assertEquals(1, watch.refreshes)
    }

    @Test
    fun newerSnapshotCanRepairFailedCommitAndPublishOnlyFinalState() {
        val watch = Watch()
        watch.failNextCommit = true
        watch.receive(message(1, "theme" to "first"))
        assertEquals(Result.CHANGED, watch.receive(data(2, "theme" to "latest")))
        assertEquals(mapOf("theme" to "latest"), watch.values)
        assertEquals(1, watch.refreshes)
    }

    @Test
    fun failedMessageDoesNotLeaveAnUnownedKeyBehind() {
        val watch = Watch(values = mapOf("watch_local" to true))
        watch.failNextCommit = true
        watch.receive(message(1, "removed_theme_setting" to "old"))
        watch.receive(message(2, "theme" to "latest"))
        watch.receive(data(2, "theme" to "latest"))
        assertEquals(mapOf("watch_local" to true, "theme" to "latest"), watch.values)
    }

    @Test
    fun failedRemovalRetriesAndPublishesEvenWhenKeyIsAlreadyAbsentInMemory() {
        val watch = Watch()
        watch.receive(data(1, "theme" to "first"))
        watch.failNextCommit = true
        assertEquals(Result.FAILED, watch.receive(data(2)))
        assertTrue(watch.values.isEmpty())
        assertEquals(Result.CHANGED, watch.receive(data(2)))
        assertEquals(2, watch.refreshes)
    }

    @Test
    fun legacyPhoneKeepsIndependentOrderingAndDurableRemovalsBeforeUpgrade() {
        val watch = Watch(PreferenceSyncState(sequence = 100, legacyRevision = 900,
                syncedKeys = setOf("old")), mapOf("old" to "value"))
        assertEquals(Result.IGNORED,
                watch.receive(message(99, "theme" to "stale").copy(sharedProtocol = false)))
        watch.receive(message(101, "theme" to "current").copy(sharedProtocol = false))
        assertEquals(Result.IGNORED, watch.receive(legacyData(899, "theme" to "stale")))
        assertEquals(Result.CHANGED, watch.receive(legacyData(901, "theme" to "current")))
        assertEquals(mapOf("theme" to "current"), watch.values)
        assertFalse(watch.persistedState.sharedProtocolSeen)
    }

    @Test
    fun firstMarkedMessageStopsOldProtocolFromOverwritingIt() {
        val watch = Watch()
        watch.receive(message(10, "theme" to "latest"))
        assertEquals(Result.IGNORED, watch.receive(legacyData(999, "theme" to "old")))
        assertEquals(Result.IGNORED,
                watch.receive(message(999, "theme" to "old").copy(sharedProtocol = false)))
        assertEquals("latest", watch.values["theme"])
    }

    @Test
    fun firstMarkedDataItemStopsLegacyReplayEvenIfNewerMessageAlreadyApplied() {
        val watch = Watch(PreferenceSyncState(sequence = 12), mapOf("theme" to "latest"))
        assertEquals(Result.UNCHANGED, watch.receive(data(11, "theme" to "old")))
        assertEquals(Result.IGNORED, watch.receive(legacyData(999, "theme" to "old")))
        assertEquals("latest", watch.values["theme"])
        assertTrue(watch.persistedState.sharedProtocolSeen)
        assertEquals(0, watch.refreshes)
    }

    @Test
    fun legacySnapshotWithoutRevisionRemainsReadableBeforeUpgrade() {
        val watch = Watch()
        assertEquals(Result.CHANGED, watch.receive(legacyData(Long.MIN_VALUE, "theme" to "old")))
        assertEquals(Result.UNCHANGED, watch.receive(legacyData(Long.MIN_VALUE, "theme" to "old")))
        assertEquals(1, watch.refreshes)
    }

    private fun message(sequence: Long, vararg values: Pair<String, Any>) =
            ReceivedPreferenceSnapshot(mapOf(*values), sequence, durable = false, sharedProtocol = true)

    private fun data(sequence: Long, vararg values: Pair<String, Any>) =
            message(sequence, *values).copy(durable = true)

    private fun legacyData(revision: Long, vararg values: Pair<String, Any>) =
            data(Long.MIN_VALUE, *values).copy(sharedProtocol = false, legacyRevision = revision)

    private class Watch(
            initialState: PreferenceSyncState = PreferenceSyncState(),
            values: Map<String, Any> = emptyMap()
    ) {
        val values = values.toMutableMap()
        var persistedState = initialState
        val reconciler = PreferenceSnapshotReconciler(initialState)
        var failNextCommit = false
        var refreshes = 0

        fun receive(snapshot: ReceivedPreferenceSnapshot): Result {
            val result = reconciler.receive(snapshot, values) { change ->
                // Model Android commit(false): memory changes before disk reports failure.
                change.removals.forEach(values::remove)
                values.putAll(change.writes)
                if (failNextCommit) {
                    failNextCommit = false
                    false
                } else {
                    persistedState = change.state
                    true
                }
            }
            if (result == Result.CHANGED) refreshes++
            return result
        }
    }
}
