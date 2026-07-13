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
 * On Wear OS 5, `androidx.wear.tiles`' `SysUiTileUpdateRequester` reads the `Settings.Global`
 * `clockwork_sysui_package` key, which the platform now restricts to apps targeting API <= 34;
 * with our `targetSdk 35` that read throws [SecurityException]. Both callers request the update
 * synchronously on the main thread (from a `LiveData` observer in [WatchMusicService] and from
 * [MusicStateListenerService.onDataChanged]), so an unguarded throw took the whole watch app down.
 * These updates are best-effort hints anyway - dropping one just leaves a surface briefly stale
 * until the next state change.
 */
object GlanceableSurfaces {
    fun requestUpdate(context: Context) {
        requestTileUpdate(context)
        requestComplicationUpdate(context)
    }

    fun requestTileUpdate(context: Context) {
        try {
            TileService.getUpdater(context).requestUpdate(MediaTileService::class.java)
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
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not request complication update")
        }
    }
}
