package com.svartifoss.snfell

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.matejdro.wearutils.preferencesync.PreferencePusher
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private var debounceJob: Job? = null
    private var syncJob: Job? = null
    private var syncRequestedWhileRunning = false
    private var retryJob: Job? = null
    private var retryDelayMs = INITIAL_RETRY_MS
    private var started = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (shouldSyncWatchPreference(key)) requestSync(CHANGE_DEBOUNCE_MS)
    }

    fun start() {
        if (started) return
        started = true
        preferences.registerOnSharedPreferenceChangeListener(listener)

        // Re-publish once per phone process. PreferencePusher's transport revision guarantees this
        // reaches a watch whose local store is stale even when Play Services cached the same data.
        requestSync(delayMs = 0L)
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
            Timber.w(e, "Could not synchronize watch preferences; retrying")
            scheduleRetry()
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
