package com.svartifoss.snfell.view.settings.dev

import android.content.Context
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.common.HandGestureAvailability
import com.svartifoss.snfell.config.WatchInfoWithIcons
import com.svartifoss.snfell.util.WearableAvailability
import kotlinx.coroutines.tasks.await

/**
 * Dumps the raw Data Layer state behind the "No watch connected" banner: which nodes Play
 * Services considers paired, and the last [com.svartifoss.snfell.proto.WatchInfo] the watch app
 * actually answered with. The two can legitimately disagree - a node connected with no WatchInfo
 * ever arriving is the exact shape of an `applicationId`/signing mismatch, which otherwise reports
 * as an identical "No watch connected" banner on both a mismatched build and a genuinely absent
 * watch.
 */
internal suspend fun buildDataLayerReport(
        context: Context,
        watchInfo: WatchInfoWithIcons?
): String {
    val apiAvailable = WearableAvailability.isAvailable(context)
    val nodes: List<Node>? = if (!apiAvailable) {
        null
    } else {
        try {
            Wearable.getNodeClient(context).connectedNodes.await()
        } catch (e: Exception) {
            null
        }
    }

    return buildString {
        appendLine("Wearable Data Layer API available on this device: $apiAvailable")
        appendLine()

        appendLine("Connected nodes:")
        when {
            !apiAvailable -> appendLine("  (Data Layer unavailable - see above)")
            nodes == null -> appendLine("  (could not query connected nodes)")
            nodes.isEmpty() -> appendLine("  (none)")
            else -> for (node in nodes) {
                appendLine("  - ${node.displayName} (id ${node.id}, " +
                        "${if (node.isNearby) "nearby" else "not nearby"})")
            }
        }
        appendLine()

        appendLine("Last WatchInfo received:")
        val info = watchInfo?.watchInfo
        if (info == null) {
            appendLine("  (none - the watch app has never answered, or this phone build has " +
                    "never heard from it. If a node is listed above, check that both apps share " +
                    "the same applicationId and signing key.)")
        } else {
            appendLine("  Round display: ${info.roundWatch}")
            appendLine("  Display: ${info.displayWidth}x${info.displayHeight} @ " +
                    "${info.displayDensity} density")
            if (info.hasTime()) appendLine("  Reported at: ${java.util.Date(info.time)}")
            appendLine("  Watch app version: " +
                    if (info.hasAppVersionCode()) {
                        "${info.appVersionName} (${info.appVersionCode})"
                    } else {
                        "unknown (watch build predates this field)"
                    })
            appendLine("  One-handed gesture: " +
                    if (info.hasHandGesture()) {
                        HandGestureAvailability.fromCode(info.handGesture).name
                    } else {
                        "unknown (watch build predates this field)"
                    })
            appendLine("  Physical buttons reported: ${info.buttonsCount}")
            for (button in info.buttonsList) {
                appendLine("    - \"${button.label}\" code=${
                    if (button.hasCode()) button.code.toString() else "?"
                } longPress=${button.supportsLongPress}")
            }
        }
    }
}
