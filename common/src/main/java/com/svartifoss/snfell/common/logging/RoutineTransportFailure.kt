package com.svartifoss.snfell.common.logging

/**
 * Whether a logged failure is the device's Play services or Wear OS link being unavailable, rather
 * than a fault in this app. Such failures stay in the logs as breadcrumbs but never become their
 * own crash-report issue.
 *
 * Both apps log a failed Data Layer call at error level in places where they cannot tell why it
 * failed (`launchWithPlayServicesErrorHandling`, the phone's auto-start message, the watch's
 * coroutine helper). Most of the time the reason is the setting, not the code: a phone with no
 * Wear OS app has no Wearable API at all (status 17), a watch out of range is not connected
 * (4000), and a Bluetooth drop closes a channel mid-stream. 4.3 and 4.3.1 filed about twenty such
 * reports a month under three issues, each naming only Play services' own threads, so nothing in
 * them could be fixed.
 *
 * The status codes are the numeric values of `CommonStatusCodes` and `WearableStatusCodes`. This
 * module has no Play services dependency, and the phone's `RoutineTransportFailureTest` pins each
 * value against the real constant. Codes that can point at a mistake of ours are deliberately
 * absent: `DEVELOPER_ERROR` (10) is a misconfiguration, and `ERROR` (13) is too generic to dismiss.
 */
object RoutineTransportFailure {
    val STATUS_CODES: Set<Int> = setOf(
            2,    // SERVICE_VERSION_UPDATE_REQUIRED
            3,    // SERVICE_DISABLED
            7,    // NETWORK_ERROR
            8,    // INTERNAL_ERROR - Play services' own failure
            14,   // INTERRUPTED
            15,   // TIMEOUT
            16,   // CANCELED
            17,   // API_NOT_CONNECTED - no Wearable API on this device
            19,   // REMOTE_EXCEPTION
            20,   // CONNECTION_SUSPENDED_DURING_CALL
            21,   // RECONNECTION_TIMED_OUT_DURING_UPDATE
            22,   // RECONNECTION_TIMED_OUT
            4000, // WearableStatusCodes.TARGET_NODE_NOT_CONNECTED
    )

    /** How deep a cause chain is followed; real ones are two or three links. */
    private const val MAX_CAUSE_DEPTH = 8

    /**
     * Walks [failure] and its causes. The first link that carries a status code decides
     * ([statusCodeOf]: the `ApiException`'s code, or null when the link is not one). A link that
     * [isLinkDrop] recognises as a closed Wear OS channel is routine too.
     */
    fun isRoutine(
            failure: Throwable,
            statusCodeOf: (Throwable) -> Int?,
            isLinkDrop: (Throwable) -> Boolean
    ): Boolean {
        var current: Throwable? = failure
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            val code = statusCodeOf(current)
            if (code != null) return code in STATUS_CODES
            if (isLinkDrop(current)) return true
            current = current.cause
            depth++
        }
        return false
    }
}
