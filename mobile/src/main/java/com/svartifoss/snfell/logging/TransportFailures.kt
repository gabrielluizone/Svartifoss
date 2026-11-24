package com.svartifoss.snfell.logging

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.ChannelIOException
import com.svartifoss.snfell.common.logging.RoutineTransportFailure

/**
 * [RoutineTransportFailure] with this app's Play services types filled in. True for a Data Layer
 * failure caused by the device's link (no Wearable API, watch out of range, channel dropped), which
 * [TimberCrashlytics] keeps as a breadcrumb instead of filing as an issue.
 */
internal fun isRoutineTransportFailure(failure: Throwable): Boolean =
        RoutineTransportFailure.isRoutine(
                failure,
                statusCodeOf = { (it as? ApiException)?.statusCode },
                isLinkDrop = { it is ChannelIOException })
