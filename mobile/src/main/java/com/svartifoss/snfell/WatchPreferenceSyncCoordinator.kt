package com.svartifoss.snfell

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.matejdro.wearutils.preferencesync.PreferencePusher
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.WatchPreferenceMessage
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.svartifoss.snfell.util.WearableAvailability
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.min

private const val CHANGE_DEBOUNCE_MS = 120L
private const val INITIAL_RETRY_MS = 1_000L
private const val MAX_RETRY_MS = 60_000L

/** Play Services' hard cap on one DataItem and on one message payload. */
private const val DATA_ITEM_LIMIT_BYTES = 100 * 1024
/** Reported well before the cap: the estimate is rough and a warning after the fact is useless. */
private const val SNAPSHOT_WARN_BYTES = 70 * 1024
/** Type tag plus length prefix plus DataMap bookkeeping, per entry. */
private const val VALUE_OVERHEAD_BYTES = 12

/** Base keys whose values affect watch behavior or appearance. Scoped appearance writes append
 * `@face`, so [shouldSyncWatchPreference] normalizes them before checking this registry. */
private val WATCH_SYNC_BASE_KEYS: Set<String> by lazy {
    MiscPreferences.EXPORTABLE.mapTo(mutableSetOf()) { it.key }.apply {
        // Diagnostics are intentionally excluded from backup/export, but are still phone-owned
        // watch settings and therefore must use the same immediate delivery path.
        add(MiscPreferences.WEAR_DEV_SHOW_LAYOUT_BOUNDS.key)
        add(MiscPreferences.WEAR_DEV_SHOW_PLAYER_INFO.key)
    }
}

internal fun shouldSyncWatchPreference(key: String?): Boolean {
    val baseKey = key?.substringBefore(FaceScopedPreferences.SCOPE_SEPARATOR) ?: return false
    return baseKey in WATCH_SYNC_BASE_KEYS
}

/**
 * Process-lifetime owner of phone -> watch preference synchronization.
 *
 * The old listeners lived in individual Settings/Watch fragments. Leaving a screen, opening a
 * dialog/activity, or a lifecycle cancellation could therefore strand the latest edit until a
 * later watch-side event caused state to be read again. This coordinator is registered once from
 * [WearMusicCenter], coalesces bulk edits, and uses an application-owned coroutine so delivery no
 * longer depends on which mobile screen happens to be STARTED.
 */
internal class WatchPreferenceSyncCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    // Seeded from wall-clock time (like PreferencePusher's revision) so it stays monotonic across
    // process restarts without needing to persist it on the phone.
    private val preferenceSequence = AtomicLong(System.currentTimeMillis())
    private fun nextPreferenceSequence(): Long =
            preferenceSequence.updateAndGet { max(it + 1L, System.currentTimeMillis()) }

    private var debounceJob: Job? = null
    private var syncJob: Job? = null
    private var syncRequestedWhileRunning = false
    private var retryJob: Job? = null
    private var retryDelayMs = INITIAL_RETRY_MS
    private var started = false
    /** Latched so a snapshot that stays large logs once, not on every edit that follows. */
    private var oversizedSnapshotReported = false

    /** Set once the device is known to have no Data Layer at all; [start] then becomes a no-op. */
    private var disabled = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (shouldSyncWatchPreference(key)) requestSync(CHANGE_DEBOUNCE_MS)
    }

    fun start() {
        if (started || disabled) return
        started = true
        preferences.registerOnSharedPreferenceChangeListener(listener)

        // Re-publish once per phone process. PreferencePusher's transport revision guarantees this
        // reaches a watch whose local store is stale even when Play Services cached the same data.
        requestSync(delayMs = 0L)
    }

    /**
     * Permanently stops syncing for this process. Used when the Data Layer turns out not to exist
     * on the device at all - a condition that cannot resolve itself while the app is running, so
     * this is deliberately not restartable by a later [start].
     */
    fun stop() {
        if (!started) return
        started = false
        disabled = true
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        debounceJob?.cancel()
        debounceJob = null
        retryJob?.cancel()
        retryJob = null
    }

    private fun requestSync(delayMs: Long) {
        retryJob?.cancel()
        retryJob = null
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            debounceJob = null
            enqueueSync()
        }
    }

    /** Never cancel a DataClient put that has started: cancelling await() does not guarantee the
     * underlying Play Services task is cancelled, so a stale put could otherwise finish last.
     * Conflate edits that arrive in flight and send one fresh snapshot after the current put. */
    private fun enqueueSync() {
        if (syncJob?.isActive == true) {
            syncRequestedWhileRunning = true
            return
        }
        syncJob = scope.launch {
            do {
                syncRequestedWhileRunning = false
                pushLatestSnapshot()
            } while (syncRequestedWhileRunning)
        }
    }

    private suspend fun pushLatestSnapshot() {
        // Read the phone's preferences exactly once and drive both transports from that one map,
        // so the DataItem and the message can never describe two different moments.
        val snapshot = preferences.all.filterKeys { shouldSyncWatchPreference(it) }
        warnIfSnapshotIsOversized(snapshot)

        // The accelerant runs first and unconditionally. It used to run *after* the DataItem put
        // and inside its try block, which made one failing put take the whole channel down: the
        // put is retried with the identical payload, so a payload the Data Layer rejects (a
        // snapshot over its 100 KB item limit is the realistic way to get there) fails forever and
        // the message that would still have worked was never reached. The watch then keeps
        // whatever it last received - the previous theme, the previous language - with nothing on
        // either device saying why.
        sendImmediatePreferenceMessage(snapshot)

        try {
            PreferencePusher.pushPreferences(
                    appContext,
                    SnapshotPreferences(snapshot),
                    CommPaths.PREFERENCES_PREFIX,
                    urgent = true)
            retryDelayMs = INITIAL_RETRY_MS
            retryJob?.cancel()
            retryJob = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: GooglePlayServicesRepairableException) {
            GoogleApiAvailability.getInstance()
                    .showErrorNotification(appContext, e.connectionStatusCode)
            Timber.w(e, "Watch preference sync requires Play Services repair")
            scheduleRetry()
        } catch (e: Exception) {
            if (WearableAvailability.isApiUnavailable(e)) {
                // The Data Layer does not exist on this device, so retrying can only ever fail
                // again. Backing off forever burnt battery and kept a doomed job alive; stopping
                // is the honest response, and the settings banner explains it to the user.
                Timber.w(e, "Wearable API unavailable on this device; stopping preference sync")
                stop()
                return
            }
            Timber.w(e, "Could not synchronize watch preferences; retrying")
            scheduleRetry()
        }
    }

    /**
     * Sends the watch-synced preference values over MessageClient for immediate application. A
     * monotonic sequence lets the watch reject a delayed older message.
     */
    private suspend fun sendImmediatePreferenceMessage(snapshot: Map<String, Any?>) {
        try {
            if (snapshot.isEmpty()) return
            val bytes = WatchPreferenceMessage.encode(nextPreferenceSequence(), snapshot)
            messageClient.sendMessageToNearestClient(nodeClient, CommPaths.MESSAGE_APPLY_PREFERENCES, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The DataItem carries the same change durably; a failed accelerant is not fatal.
            Timber.d(e, "Immediate preference message not delivered (DataItem still applies)")
        }
    }

    /**
     * Logs once when the snapshot approaches the Data Layer's per-item limit.
     *
     * Both transports cap at 100 KB and neither says so in a way a user could ever see: the put
     * simply throws and the watch silently keeps the values it already had. The snapshot grows
     * with the number of *explicitly set* face-scoped keys - "Apply this look to all faces" writes
     * every one of them onto all eighteen faces in a single tap - so it is reachable by ordinary
     * use, and the symptom (a setting that will not cross to the watch) points nowhere near the
     * cause. Advisory only; the push is still attempted, since the estimate is approximate and the
     * real encoder is the authority.
     */
    private fun warnIfSnapshotIsOversized(snapshot: Map<String, Any?>) {
        // Key names are carried twice - once as DataMap keys, once in PreferencePusher's synced-key
        // inventory - so they are counted twice here.
        val estimatedBytes = snapshot.entries.sumOf { (key, value) ->
            2 * key.length + (value as? String)?.length.let { it ?: 0 } + VALUE_OVERHEAD_BYTES
        }
        if (estimatedBytes < SNAPSHOT_WARN_BYTES) {
            oversizedSnapshotReported = false
            return
        }
        if (oversizedSnapshotReported) return
        oversizedSnapshotReported = true
        Timber.w("Watch preference snapshot is %d entries / ~%d bytes, close to the Data Layer's " +
                "%d byte item limit. Resetting unused faces shrinks it.",
                snapshot.size, estimatedBytes, DATA_ITEM_LIMIT_BYTES)
    }

    /**
     * Read-only [SharedPreferences] view over one already-filtered snapshot.
     *
     * [PreferencePusher] takes a `SharedPreferences` and reads `all` from it, which meant the push
     * carried the phone's *entire* default preference file to the watch: the saved streaming
     * shortcuts, the search history and the track history all live in that file as JSON blobs, as
     * does every phone-only setting. None of it is read on the watch, and all of it was spending
     * the same 100 KB budget the watch-facing keys need. This hands the pusher exactly the keys
     * [shouldSyncWatchPreference] accepts and nothing else.
     */
    private class SnapshotPreferences(
            private val values: Map<String, Any?>
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
                values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
                (values[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
                values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
                values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        /** Nothing may write through a snapshot; failing loudly beats silently dropping an edit. */
        override fun edit(): SharedPreferences.Editor =
                throw UnsupportedOperationException("Watch preference snapshot is read-only")

        override fun registerOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private fun scheduleRetry() {
        if (!started || retryJob?.isActive == true) return
        val waitMs = retryDelayMs
        retryDelayMs = min(retryDelayMs * 2L, MAX_RETRY_MS)
        retryJob = scope.launch {
            delay(waitMs)
            retryJob = null
            enqueueSync()
        }
    }
}
