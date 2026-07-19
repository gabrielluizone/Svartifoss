package com.svartifoss.snfell.watch.communication

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.watch.config.PreferencesBus
import com.matejdro.wearutils.preferencesync.PreferenceReceiverService

class PreferencesReceiver : PreferenceReceiverService(CommPaths.PREFERENCES_PREFIX) {
    @SuppressLint("CommitPrefEdits")
    override fun getDestinationPreferences(): SharedPreferences.Editor =
            PreferenceManager.getDefaultSharedPreferences(this).edit()

    override fun onPreferencesCommitted() {
        // Publish only after PreferenceReceiverService has durably committed the complete
        // snapshot. MainActivity observes this bus and re-applies the active face immediately,
        // even when no touch/media event occurs on the watch.
        PreferencesBus.postValue(PreferenceManager.getDefaultSharedPreferences(this))
    }
}
