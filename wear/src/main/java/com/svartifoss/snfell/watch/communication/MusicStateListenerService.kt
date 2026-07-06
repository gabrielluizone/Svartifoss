package com.svartifoss.snfell.watch.communication

import android.content.ComponentName
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.complication.AlbumArtComplicationDataSourceService
import com.svartifoss.snfell.watch.tile.MediaTileService
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

        ComplicationDataSourceUpdateRequester.create(
                this,
                ComponentName(this, AlbumArtComplicationDataSourceService::class.java)
        ).requestUpdateAll()

        TileService.getUpdater(this).requestUpdate(MediaTileService::class.java)

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
