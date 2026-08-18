package com.svartifoss.snfell.watch.communication

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.MusicState
import timber.log.Timber

/**
 * Manifest-registered listener (filtered to the music-state DataItem path) that pokes the
 * glanceable surfaces - the album-art complication and the media Tile - whenever the phone
 * publishes new music state.
 *
 * WatchMusicService requests the same updates while it's running, but it shuts itself down after
 * an idle timeout; without this listener the surfaces silently froze on the last track from
 * before the service died (classic symptom: the complication cover "worked once and then never
 * changed again"). A manifest-registered WearableListenerService is started by Play Services for
 * each matching event, so this fires no matter what else is running.
 *
 * It also revives WatchMusicService itself when state says music is playing but the service is
 * dead. The service owns the proxy MediaSession that makes recents/media-controls show the
 * current track; the phone only sends MESSAGE_START_SERVICE on a playback-*start* edge while its
 * own MusicService is inactive, so a watch service that died mid-playback (idle timeout after a
 * long pause, system kill, FGS start refusal) otherwise stayed dead - and the track name silently
 * vanished from recents - until the phone-side service also died and playback restarted.
 */
class MusicStateListenerService : WearableListenerService() {

    private companion object {
        /** Last state fingerprint a refresh was requested for. Held on the companion because the
         *  system creates and tears this listener down around delivery, so an instance field would
         *  reset between the very events it is meant to deduplicate. */
        @Volatile
        var lastGlanceableFingerprint: String? = null
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        val latestState = dataEvents
                .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == CommPaths.DATA_MUSIC_STATE }
                .lastOrNull()
                ?.let { event ->
                    try {
                        MusicState.parseFrom(event.dataItem.data)
                    } catch (e: Exception) {
                        Timber.w(e, "Could not parse received music state")
                        null
                    }
                }

        // Only when a field the Tile/complication actually renders changed - the same guard
        // WatchMusicService already applies. The phone re-publishes music state on every position
        // tick, so this ran roughly once a second, and each run makes the Tiles library bind and
        // unbind a system service. That library (SysUiTileUpdateRequester) unbinds twice under
        // load and throws "Service not registered" from its own background thread, where no
        // try/catch of ours can reach it - so the only lever we have is asking far less often.
        val fingerprint = latestState?.let {
            "${it.title}|${it.artist}|${it.playing}|${it.error}"
        }
        if (fingerprint != lastGlanceableFingerprint) {
            lastGlanceableFingerprint = fingerprint
            GlanceableSurfaces.requestUpdate(this)
        }

        if (latestState?.playing == true && !latestState.error && !WatchMusicService.active) {
            // Same contract as IdleMessageListener: the service promotes itself to foreground, so
            // it must be started with startForegroundService(), and the start can still be
            // refused on API 31+ when the OS considers us fully backgrounded - swallow that
            // rather than crash; the next state change retries.
            try {
                ContextCompat.startForegroundService(
                        this, Intent(this, WatchMusicService::class.java))
            } catch (e: IllegalStateException) {
                Timber.w(e, "Could not revive WatchMusicService from background")
            }
        }
    }
}
