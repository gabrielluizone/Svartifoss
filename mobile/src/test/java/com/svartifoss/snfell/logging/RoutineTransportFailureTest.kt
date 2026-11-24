package com.svartifoss.snfell.logging

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.wearable.ChannelIOException
import com.google.android.gms.wearable.WearableStatusCodes
import com.svartifoss.snfell.common.logging.RoutineTransportFailure
import java.io.IOException
import java.util.concurrent.ExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTransportFailureTest {

    private fun api(code: Int) = ApiException(Status(code))

    @Test
    fun `the status codes are the real Play services constants`() {
        // common has no Play services dependency, so it writes the numbers out; they must not drift.
        assertEquals(setOf(
                CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED,
                CommonStatusCodes.SERVICE_DISABLED,
                CommonStatusCodes.NETWORK_ERROR,
                CommonStatusCodes.INTERNAL_ERROR,
                CommonStatusCodes.INTERRUPTED,
                CommonStatusCodes.TIMEOUT,
                CommonStatusCodes.CANCELED,
                CommonStatusCodes.API_NOT_CONNECTED,
                CommonStatusCodes.REMOTE_EXCEPTION,
                CommonStatusCodes.CONNECTION_SUSPENDED_DURING_CALL,
                CommonStatusCodes.RECONNECTION_TIMED_OUT_DURING_UPDATE,
                CommonStatusCodes.RECONNECTION_TIMED_OUT,
                WearableStatusCodes.TARGET_NODE_NOT_CONNECTED,
        ), RoutineTransportFailure.STATUS_CODES)
    }

    @Test
    fun `a phone without the Wearable API is routine`() {
        // The 4.3.1 reports: status 17, then 8 and 20, from Play services' own threads.
        assertTrue(isRoutineTransportFailure(api(CommonStatusCodes.API_NOT_CONNECTED)))
        assertTrue(isRoutineTransportFailure(api(CommonStatusCodes.INTERNAL_ERROR)))
        assertTrue(isRoutineTransportFailure(api(CommonStatusCodes.CONNECTION_SUSPENDED_DURING_CALL)))
    }

    @Test
    fun `a wrapped failure is judged by its cause`() {
        assertTrue(isRoutineTransportFailure(ExecutionException(api(WearableStatusCodes.TARGET_NODE_NOT_CONNECTED))))
        assertTrue(isRoutineTransportFailure(IOException(ChannelIOException("closed", 0, 0))))
    }

    @Test
    fun `a failure that can be our own mistake is still reported`() {
        assertFalse(isRoutineTransportFailure(api(CommonStatusCodes.DEVELOPER_ERROR)))
        assertFalse(isRoutineTransportFailure(api(CommonStatusCodes.ERROR)))
        assertFalse(isRoutineTransportFailure(IllegalStateException("boom")))
        // The first status code in the chain decides, so a routine cause behind a developer error
        // does not hide it.
        assertFalse(isRoutineTransportFailure(
                ApiException(Status(CommonStatusCodes.DEVELOPER_ERROR)).also {
                    it.initCause(api(CommonStatusCodes.API_NOT_CONNECTED))
                }))
    }
}
