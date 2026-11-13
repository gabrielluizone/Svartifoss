package com.svartifoss.snfell.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import com.google.android.gms.wearable.MessageEvent
import com.svartifoss.snfell.music.WatchCommandDelivery
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Feeds a watch command into the app as if the Wearable Data Layer had delivered it.
 *
 * An emulator's phone image has no paired watch (the Wearable API answers `ApiException 17`), so
 * nothing there could ever start a shortcut, a skip or a queue request - and those are exactly the
 * paths whose behaviour depends on the Android version the emulator is there to provide. The
 * command enters at [WatchCommandDelivery.receive], the same call the manifest listener makes, so
 * everything after it (the service start, the direct path, the shortcut ladder, the reply) is the
 * real code. Replies to the "watch" fail to send and are logged, which is the outcome to read.
 *
 * ```
 * adb shell am broadcast -n com.svartifoss.snfell/.debug.SimulatedWatchCommandReceiver \
 *     --es path /Messages/CustomListItemSelected --es data <base64 of the protobuf payload>
 * ```
 *
 * Exists in debug builds only (`src/debug`), and its manifest entry requires `DUMP`.
 */
class SimulatedWatchCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            Timber.w("Simulated watch command without a path")
            return
        }
        val payload = intent.getStringExtra(EXTRA_DATA)?.let {
            try {
                Base64.decode(it, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Simulated watch command with unreadable data")
                return
            }
        } ?: ByteArray(0)
        val id = requestIds.incrementAndGet()
        Timber.d("Simulated watch command %s (%d bytes, id %d)", path, payload.size, id)
        WatchCommandDelivery.receive(context, object : MessageEvent {
            override fun getData(): ByteArray = payload
            override fun getPath(): String = path
            override fun getRequestId(): Int = id
            override fun getSourceNodeId(): String = SIMULATED_NODE
        })
    }

    private companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_DATA = "data"
        const val SIMULATED_NODE = "simulated-watch"

        /** Distinct per delivery: the inbox drops a repeated (node, request id) as a duplicate. */
        val requestIds = AtomicInteger(1_000_000)
    }
}
