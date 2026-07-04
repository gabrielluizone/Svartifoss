package com.svartifoss.snfell.watch.communication

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.watch.complication.AlbumArtComplicationDataSourceService
import com.svartifoss.snfell.watch.tile.MediaTileService

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
 */
class MusicStateListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        ComplicationDataSourceUpdateRequester.create(
                this,
                ComponentName(this, AlbumArtComplicationDataSourceService::class.java)
        ).requestUpdateAll()

        TileService.getUpdater(this).requestUpdate(MediaTileService::class.java)
    }
}
