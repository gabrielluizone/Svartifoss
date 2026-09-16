package com.svartifoss.snfell.music

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PinchCalibrationTransfer
import timber.log.Timber

/** Handles explicit calibration saves without starting playback or a foreground service. */
internal object PinchCalibrationReceiver {
    // Manifest and live listeners may both receive the same request. A retry is acknowledged
    // again, but must not overwrite a newer profile. All entries hold only small profile strings.
    private val completed = LinkedHashMap<String, ByteArray>()

    @Synchronized
    fun receive(context: Context, event: MessageEvent) {
        val request = PinchCalibrationTransfer.decode(event.data) ?: return
        val key = "${event.sourceNodeId}/${request.requestId}"
        val bytes = request.encode()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val previous = completed[key]
        if (previous != null && !previous.contentEquals(bytes)) return
        if (previous != null && prefs.getString(MiscPreferences.WEAR_PINCH_CALIBRATION.key, "") !=
                request.profile.encode()) return
        if (previous == null) {
            if (!prefs.edit().putString(MiscPreferences.WEAR_PINCH_CALIBRATION.key,
                            request.profile.encode()).commit()) return
            // The existing coordinator observes this phone-owned key and synchronizes it.
            completed[key] = bytes
            while (completed.size > 32) completed.remove(completed.keys.first())
        }
        Wearable.getMessageClient(context).sendMessage(event.sourceNodeId,
                CommPaths.MESSAGE_PINCH_CALIBRATION_ACK, bytes).addOnFailureListener {
            Timber.w(it, "Could not acknowledge pinch calibration")
        }
    }
}
