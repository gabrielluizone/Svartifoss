package com.svartifoss.snfell

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.matejdro.wearutils.preferencesync.PreferencePusher
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
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
/** Reported well before the cap; gallery installs use the same conservative boundary. */
internal const val WATCH_SNAPSHOT_GUARD_BYTES = 70 * 1024

/**
 * Point at which [selectWatchPreferenceSnapshot] stops packing *inactive* face scopes.
 *
 * Deliberately below [WATCH_SNAPSHOT_GUARD_BYTES], which is itself below the transport's hard cap:
 * the estimate is approximate, and the two margins mean a mis-estimate costs one skipped inactive
 * scope rather than a rejected put. Nothing the watch is actually rendering is ever measured
 * against this - see the function's own note on what may and may not be dropped.
 */
internal const val WATCH_SNAPSHOT_SCOPE_BUDGET_BYTES = 56 * 1024

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
 * Conservative common budget for the DataItem and immediate-message preference snapshots.
 *
 * Key names travel twice (as DataMap keys and in PreferencePusher's key inventory), and values
 * are counted as UTF-8 because a character count would understate a public text value containing
 * non-ASCII characters. This intentionally remains an estimate; callers use the 70 KiB guard to
 * leave headroom below the transport's 100 KiB hard cap.
 */
internal fun estimateWatchPreferenceSnapshotBytes(snapshot: Map<String, Any?>): Int {
    val bytes = snapshot.entries.sumOf { (key, value) ->
        (2L * key.toByteArray(Charsets.UTF_8).size) +
                estimateValueBytes(value) +
                VALUE_OVERHEAD_BYTES
    }
    return bytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Only text carries a variable payload. A string set (the auto-start blacklist is the one in
 *  [MiscPreferences.EXPORTABLE]) travels as a string list and grows with the number of packages
 *  the user picked, so it is measured too - counting it as zero understated exactly the entry a
 *  heavy user is most likely to have made large. */
private fun estimateValueBytes(value: Any?): Long = when (value) {
    is String -> value.toByteArray(Charsets.UTF_8).size.toLong()
    is Set<*> -> value.sumOf {
        ((it as? String)?.toByteArray(Charsets.UTF_8)?.size ?: 0).toLong() + VALUE_OVERHEAD_BYTES
    }
    else -> 0L
}

/**
 * The one appearance scope the watch can actually read right now.
 *
 * Every watch-side read - the player, the AOD, the panels, the tile, the queue and menu - resolves
 * through `ThemeAppearance.resolve(prefs)`, so this is not an optimisation over what the watch
 * prefers: it is the complete set of scoped values the watch has any way of reaching.
 *
 * Resolved from the already-read snapshot rather than from [SharedPreferences] again, so the scope
 * and the values sent for it are provably the same moment: a face change landing between two reads
 * would otherwise pair one face's key with another face's values.
 */
internal fun activeWatchAppearanceScope(snapshot: Map<String, Any?>): String {
    val context = ThemeAppearance.resolve(
            baseFace = snapshot[MiscPreferences.WEAR_SCREEN_FACE.key] as? String,
            customThemeId = snapshot[MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key] as? String,
            customComplete = snapshot[MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key] as? Boolean
                    ?: MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.defaultValue,
            customSchema = snapshot.storedInt(
                    MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.key,
                    MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.defaultValue),
            customRevision = snapshot.storedInt(
                    MiscPreferences.WEAR_CUSTOM_THEME_REVISION.key,
                    MiscPreferences.WEAR_CUSTOM_THEME_REVISION.defaultValue))
    return when (context) {
        is AppearanceContext.Custom -> ThemeAppearance.CUSTOM_SCOPE
        is AppearanceContext.BuiltIn -> context.baseFace
    }
}

/** Integer appearance metadata is persisted as a string (the wearutils convention), but a raw int
 *  from an older or debug path stays readable - mirroring `ThemeAppearance.resolve`'s own
 *  tolerance, since this reads the very keys that function would have read. */
private fun Map<String, Any?>.storedInt(key: String, defaultValue: Int): Int =
        when (val value = this[key]) {
            is String -> value.toIntOrNull() ?: defaultValue
            is Int -> value
            else -> defaultValue
        }

/** What [selectWatchPreferenceSnapshot] chose to send, and which scopes it had to leave behind. */
internal data class WatchPreferenceSnapshot(
        val values: Map<String, Any?>,
        val droppedScopes: List<String>
)

/**
 * Chooses which watch-facing preferences travel to the watch.
 *
 * Face scoping multiplies one appearance setting by twenty-one scopes (twenty built-in faces plus
 * the active custom-theme snapshot), and the phone used to ship all of them. That is roughly 295 KB
 * fully materialised against a 100 KB per-item transport cap, so a user who customised more than
 * about four faces could not sync at all - which surfaced as a community theme refusing to install
 * with "your watch settings are too large", and as an instruction to delete their own work.
 *
 * The watch reads exactly one scope (see [activeWatchAppearanceScope]), so that scope and the
 * unscoped behaviour keys are **mandatory** and never measured against a budget: whatever is on the
 * wrist is always transmitted in full, no matter how large the phone's library has grown. Every
 * other scope is a *cache* - useful only for the moment between picking a different face on the
 * wrist and the phone publishing that face's scope back - so those are packed in a stable order
 * while [budgetBytes] lasts and skipped afterwards.
 *
 * A skipped scope costs a fraction of a second of default styling on a face changed from the watch,
 * and it repairs itself: the picker's `MESSAGE_SET_SCREEN_FACE` makes the phone write the face and
 * re-publish, and the newly-chosen face is then the mandatory scope. Ordering is
 * [ThemeAppearance.ALLOWED_BASE_FACES] (the face picker's own order), then any scope this build
 * does not recognise, then a *stale* custom-theme snapshot last - once a theme is not active, its
 * scope is the one group in the map that nothing can read.
 */
internal fun selectWatchPreferenceSnapshot(
        all: Map<String, Any?>,
        activeScope: String,
        budgetBytes: Int = WATCH_SNAPSHOT_SCOPE_BUDGET_BYTES,
        faceOrder: List<String> = ThemeAppearance.ALLOWED_BASE_FACES.toList()
): WatchPreferenceSnapshot {
    val mandatory = LinkedHashMap<String, Any?>()
    val inactiveScopes = LinkedHashMap<String, MutableMap<String, Any?>>()
    for ((key, value) in all) {
        if (!shouldSyncWatchPreference(key)) continue
        val separator = key.indexOf(FaceScopedPreferences.SCOPE_SEPARATOR)
        if (separator < 0) {
            mandatory[key] = value
            continue
        }
        val scope = key.substring(separator + 1)
        if (scope == activeScope) {
            mandatory[key] = value
        } else {
            inactiveScopes.getOrPut(scope) { LinkedHashMap() }[key] = value
        }
    }

    val known = faceOrder.filter { it != activeScope && inactiveScopes.containsKey(it) }
    val unrecognized = inactiveScopes.keys
            .filter { it != ThemeAppearance.CUSTOM_SCOPE && it !in faceOrder }
            .sorted()
    val stale = if (activeScope != ThemeAppearance.CUSTOM_SCOPE &&
            inactiveScopes.containsKey(ThemeAppearance.CUSTOM_SCOPE)) {
        listOf(ThemeAppearance.CUSTOM_SCOPE)
    } else {
        emptyList()
    }

    val selected = LinkedHashMap<String, Any?>(mandatory)
    var usedBytes = estimateWatchPreferenceSnapshotBytes(mandatory)
    val dropped = mutableListOf<String>()
    for (scope in known + unrecognized + stale) {
        val group = inactiveScopes.getValue(scope)
        val cost = estimateWatchPreferenceSnapshotBytes(group)
        // Skip rather than stop: a later, smaller scope still fits, and the order is fixed, so the
        // result stays deterministic for a given preference file.
        if (usedBytes + cost <= budgetBytes) {
            selected.putAll(group)
            usedBytes += cost
        } else {
            dropped += scope
        }
    }
    return WatchPreferenceSnapshot(selected, dropped)
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
    /** Latched for the same reason as [oversizedSnapshotReported]. */
    private var droppedScopesReported = false

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
        val all = preferences.all
        val selection = selectWatchPreferenceSnapshot(all, activeWatchAppearanceScope(all))
        val snapshot = selection.values
        reportDroppedScopes(selection)
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
     * Logs which inactive face scopes did not fit, and stays quiet once nothing is being dropped.
     *
     * Not an error and not shown to the user: the wrist is rendering the active scope, which is
     * always transmitted whole. This exists because the one visible symptom - a face changed from
     * the watch showing its defaults for a moment - happens on the *other* device from the code
     * that caused it, so without this there is nothing anywhere naming a cause.
     */
    private fun reportDroppedScopes(selection: WatchPreferenceSnapshot) {
        if (selection.droppedScopes.isEmpty()) {
            droppedScopesReported = false
            return
        }
        if (droppedScopesReported) return
        droppedScopesReported = true
        Timber.i("Watch preference snapshot left %d inactive face scope(s) behind (%s); the " +
                "active face is unaffected and a face picked on the watch re-syncs on selection.",
                selection.droppedScopes.size, selection.droppedScopes.joinToString())
    }

    /**
     * Logs once when the snapshot approaches the Data Layer's per-item limit.
     *
     * Both transports cap at 100 KB and neither says so in a way a user could ever see: the put
     * simply throws and the watch silently keeps the values it already had. Since
     * [selectWatchPreferenceSnapshot] drops inactive scopes to stay inside a budget, reaching this
     * now means the *mandatory* half - behaviour keys plus the one scope on the wrist - is itself
     * near the cap, which no ordinary preference file gets to. Advisory only; the push is still
     * attempted, since the estimate is approximate and the real encoder is the authority.
     */
    private fun warnIfSnapshotIsOversized(snapshot: Map<String, Any?>) {
        // Key names are carried twice - once as DataMap keys, once in PreferencePusher's synced-key
        // inventory - so they are counted twice here.
        val estimatedBytes = estimateWatchPreferenceSnapshotBytes(snapshot)
        if (estimatedBytes < WATCH_SNAPSHOT_GUARD_BYTES) {
            oversizedSnapshotReported = false
            return
        }
        if (oversizedSnapshotReported) return
        oversizedSnapshotReported = true
        Timber.w("Watch preference snapshot is %d entries / ~%d bytes, close to the Data Layer's " +
                "%d byte item limit, after inactive face scopes were already dropped.",
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
