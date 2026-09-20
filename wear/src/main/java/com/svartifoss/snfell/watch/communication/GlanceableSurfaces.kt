package com.svartifoss.snfell.watch.communication

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.svartifoss.snfell.watch.complication.AlbumArtComplicationDataSourceService
import com.svartifoss.snfell.watch.tile.MediaTileService
import com.svartifoss.snfell.watch.tile.ShortcutsTileService
import timber.log.Timber

/**
 * Requests refreshes of the glanceable surfaces - both Tiles and the
 * [AlbumArtComplicationDataSourceService] album-art complication - swallowing any failure so a
 * library-side error can never crash the caller. The shortcuts Tile is included because its
 * app-opening action follows the current album accent too.
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
        requestTileUpdate(context, MediaTileService::class.java, "media")
        requestTileUpdate(context, ShortcutsTileService::class.java, "shortcuts")
    }

    private fun requestTileUpdate(
        context: Context,
        service: Class<out TileService>,
        label: String
    ) {
        try {
            TileService.getUpdater(context).requestUpdate(service)
        } catch (e: SecurityException) {
            Timber.w(
                "Could not request %s Tile update due to API 35+ settings restrictions: %s",
                label,
                e.message
            )
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not request %s Tile update", label)
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
