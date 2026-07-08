package com.svartifoss.snfell.watch.communication

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.common.CommPaths
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Manifest-registered listener for the button-config and action-menu DataItems (the "settings"
 * edited on the phone). Play Services starts it for each matching change regardless of whether the
 * watch UI or WatchMusicService is running.
 *
 * These paths have no other always-on delivery: unlike watch preferences (PreferencesReceiver) and
 * music state (MusicStateListenerService), a config edited on the phone otherwise reaches the watch
 * only through PhoneConnection's *runtime* DataClient listener, which is registered only while the
 * connection is active (a UI activity started or WatchMusicService running, plus a 15s grace). With
 * the watch idle - screen off, music paused, service idle-timed-out - the new config just sat in the
 * Data Layer store until the next activation re-read it, so a mapping changed on the phone appeared
 * to take effect only after the user woke/tapped the watch.
 *
 * This posts the frozen DataItem straight into the @Singleton PhoneConnection's config LiveData, the
 * same sink PhoneConnection.onDataChanged writes to. The config providers observe those LiveData with
 * observeForever (tied to MusicViewModel's lifetime, not the connection's), so an open now-playing
 * screen re-decodes and applies the new mapping immediately - no tap needed. When the app process is
 * dead this simply primes the singleton that the next launch reads anyway, which is harmless.
 *
 * Paths must match CommPaths.DATA_ACTION_CONFIG_PREFIX (/Actions/{Playback,Stopped}) and
 * DATA_LIST_ITEMS (/ActionList) - see the manifest intent-filter.
 */
@AndroidEntryPoint
class ConfigListenerService : WearableListenerService() {
    @Inject
    internal lateinit var phoneConnection: PhoneConnection

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        dataEvents
                .filter { it.type == DataEvent.TYPE_CHANGED }
                .forEach { event ->
                    val item = event.dataItem.freeze()
                    when (item.uri.path) {
                        CommPaths.DATA_PLAYING_ACTION_CONFIG -> phoneConnection.rawPlaybackConfig.postValue(item)
                        CommPaths.DATA_STOPPING_ACTION_CONFIG -> phoneConnection.rawStoppedConfig.postValue(item)
                        CommPaths.DATA_LIST_ITEMS -> phoneConnection.rawActionMenuConfig.postValue(item)
                    }
                }
    }
}
