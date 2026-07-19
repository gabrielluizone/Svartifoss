package com.svartifoss.snfell.watch.communication

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.svartifoss.snfell.watch.complication.AlbumArtComplicationDataSourceService
import com.svartifoss.snfell.watch.tile.MediaTileService
import timber.log.Timber

/**
 * Requests refreshes of the glanceable surfaces - the media [MediaTileService] Tile and the
 * [AlbumArtComplicationDataSourceService] album-art complication - swallowing any failure so a
 * library-side error can never crash the caller.
 *
 * Both callers request the update synchronously on the main thread (from a `LiveData` observer in
 * [WatchMusicService] and from [MusicStateListenerService.onDataChanged]). The runtime guard stays
 * in place because these are best-effort hints: a vendor renderer failure must never crash the
 * player, and the Tile still has a one-minute freshness fallback.
 */
object GlanceableSurfaces {
    fun requestUpdate(context: Context) {
        requestTileUpdate(context)
        requestComplicationUpdate(context)
    }

    fun requestTileUpdate(context: Context) {
        try {
            TileService.getUpdater(context).requestUpdate(MediaTileService::class.java)
        } catch (e: SecurityException) {
            Timber.w("Could not request media Tile update due to API 35+ settings restrictions: %s", e.message)
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not request media Tile update")
        }
    }

    fun requestComplicationUpdate(context: Context) {
        try {
            ComplicationDataSourceUpdateRequester.create(
                    context,
                    ComponentName(context, AlbumArtComplicationDataSourceService::class.java)
            ).requestUpdateAll()
        } catch (e: SecurityException) {
            Timber.w("Could not request complication update: %s", e.message)
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not request complication update")
        }
    }
}
