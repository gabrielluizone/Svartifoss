package com.svartifoss.snfell.watch.communication

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.google.android.gms.wearable.DataEventBuffer
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.watch.config.PreferencesBus
import com.matejdro.wearutils.preferencesync.PreferenceReceiverService

class PreferencesReceiver : PreferenceReceiverService(CommPaths.PREFERENCES_PREFIX) {
    @SuppressLint("CommitPrefEdits")
    override fun getDestinationPreferences(): SharedPreferences.Editor =
            PreferenceManager.getDefaultSharedPreferences(this).edit()

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        super.onDataChanged(dataEventBuffer)

        PreferencesBus.postValue(PreferenceManager.getDefaultSharedPreferences(this))
    }
}
