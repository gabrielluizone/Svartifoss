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
        try {
            PreferencePusher.pushPreferences(
                    appContext,
                    preferences,
                    CommPaths.PREFERENCES_PREFIX,
                    urgent = true)
            // Also deliver the snapshot over the low-latency MessageClient so a connected watch
            // applies it immediately instead of waiting for the DataItem to sync out of doze. The
            // DataItem above stays the durable source of truth; this is a best-effort accelerant,
            // so its failure is swallowed and never affects the DataItem's retry state.
            sendImmediatePreferenceMessage()
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
     * Sends the watch-synced preference values over MessageClient for immediate application. Only
     * the keys the watch actually consumes ([shouldSyncWatchPreference]) are included, keeping the
     * message small. A monotonic sequence lets the watch reject a delayed older message.
     */
    private suspend fun sendImmediatePreferenceMessage() {
        try {
            val synced = preferences.all.filterKeys { shouldSyncWatchPreference(it) }
            if (synced.isEmpty()) return
            val bytes = WatchPreferenceMessage.encode(nextPreferenceSequence(), synced)
            messageClient.sendMessageToNearestClient(nodeClient, CommPaths.MESSAGE_APPLY_PREFERENCES, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The DataItem already carried the change durably; a failed accelerant is harmless.
            Timber.d(e, "Immediate preference message not delivered (DataItem still applies)")
        }
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
