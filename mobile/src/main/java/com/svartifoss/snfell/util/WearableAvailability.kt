package com.svartifoss.snfell.util

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Whether this device supports the Wearable Data Layer API at all.
 *
 * Not every device with Play Services has it - tablets, some OEM and regional builds, and
 * Play-Services-less ROMs all report `API_UNAVAILABLE`. On those, every Data Layer call fails
 * forever, which previously showed up as a crash reported from the Play Services handler thread
 * with no app frames in it, and as retry loops that could never succeed.
 *
 * This is a *permanent* property of the device, unlike "no watch is currently connected" - so
 * callers should stop retrying rather than back off, and say something accurate to the user
 * instead of implying a watch just needs to be paired.
 */
object WearableAvailability {

    @Volatile
    private var cached: Boolean? = null

    /**
     * True when the Data Layer can be used here. The result is cached for the process: the answer
     * cannot change without Play Services itself being installed or removed, which restarts the app.
     */
    suspend fun isAvailable(context: Context): Boolean {
        cached?.let { return it }
        val available = try {
            GoogleApiAvailability.getInstance()
                    .checkApiAvailability(Wearable.getDataClient(context.applicationContext))
                    .await()
            true
        } catch (e: ApiException) {
            Timber.w(e, "Wearable Data Layer API is unavailable on this device")
            false
        } catch (e: Exception) {
            // An unexpected failure is not proof of absence - assume available so a transient
            // problem cannot permanently disable syncing.
            Timber.w(e, "Could not determine Wearable API availability; assuming present")
            true
        }
        cached = available
        return available
    }

    /**
     * Whether [throwable] is the Data-Layer-not-on-this-device failure, as opposed to an ordinary
     * transient error.
     *
     * Both codes are accepted because the two layers disagree: the connection result carries
     * [ConnectionResult.API_UNAVAILABLE] (16) while the [ApiException] surfaced to callers reports
     * [CommonStatusCodes.API_NOT_CONNECTED] (17) - which is the one the crash reports actually show.
     */
    fun isApiUnavailable(throwable: Throwable): Boolean {
        val status = (throwable as? ApiException)?.statusCode ?: return false
        return status == CommonStatusCodes.API_NOT_CONNECTED ||
                status == ConnectionResult.API_UNAVAILABLE
    }
}
