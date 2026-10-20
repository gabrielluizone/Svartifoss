package com.svartifoss.snfell.watch.communication

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.complication.AlbumArtComplicationDataSourceService
import com.svartifoss.snfell.watch.complication.PlaybackComplicationDataSourceService
import com.svartifoss.snfell.watch.complication.PlaybackComplicationMath
import com.svartifoss.snfell.watch.complication.PlaybackComplicationToggleReceiver
import com.svartifoss.snfell.watch.tile.MediaTileService
import com.svartifoss.snfell.watch.tile.RecentTracksTileService
import com.svartifoss.snfell.watch.tile.ShortcutsTileService
import timber.log.Timber

/**
 * Requests refreshes of the glanceable surfaces - both Tiles, the
 * [AlbumArtComplicationDataSourceService] album-art complication and the
 * [PlaybackComplicationDataSourceService] playback one - swallowing any failure so a
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

    /** Only the Recently played Tile - for its own list changing, which nothing else draws. */
    fun requestRecentTracksTileUpdate(context: Context) {
        requestTileUpdate(context, RecentTracksTileService::class.java, "recent")
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
        requestComplicationUpdate(context, AlbumArtComplicationDataSourceService::class.java)
        requestComplicationUpdate(context, PlaybackComplicationDataSourceService::class.java)
    }

    /** Only the playback complication - for a change nothing else draws, such as a seek. */
    fun requestPlaybackComplicationUpdate(context: Context) {
        requestComplicationUpdate(context, PlaybackComplicationDataSourceService::class.java)
    }

    private fun requestComplicationUpdate(context: Context, source: Class<*>) {
        try {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, source))
                    .requestUpdateAll()
        } catch (e: SecurityException) {
            Timber.w("Could not request complication update: %s", e.message)
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not request complication update")
        }
    }

    /** What the playback complication last drew from - see [onMusicState]. */
    private data class PlaybackReading(
            val playing: Boolean,
            val error: Boolean,
            val title: String,
            val durationMs: Long,
            val speed: Float,
            val trackStartEpochMs: Long
    )

    @Volatile
    private var lastPlaybackReading: PlaybackReading? = null

    /**
     * Tells the playback complication about a new music state, asking for a redraw only when what
     * it counts from moved.
     *
     * The other surfaces refresh on a fingerprint that leaves the position out on purpose (see the
     * callers), which is right for them and wrong for this one: after a seek it would go on
     * counting from where the track *was*. Comparing the moment the track started instead of the
     * position itself keeps steady playback - every republish for a volume step lands on the same
     * start - from asking at all, while a seek moves it by however far it jumped.
     *
     * Called from both the manifest listener and the running service; the shared reading makes the
     * second call for one state a no-op.
     */
    fun onMusicState(context: Context, state: MusicState?) {
        val reading = state?.let {
            PlaybackReading(
                    playing = it.playing,
                    error = it.error,
                    title = it.title,
                    durationMs = it.durationMs,
                    speed = it.playbackSpeed,
                    trackStartEpochMs = PlaybackComplicationMath(
                            it.positionMs, it.durationMs, it.playing, it.playbackSpeed,
                            it.positionUpdateTime).trackStartEpochMs(System.currentTimeMillis()))
        }
        val previous = lastPlaybackReading
        if (previous == reading) return
        val sameApartFromStart = previous != null && reading != null &&
                previous.copy(trackStartEpochMs = 0L) == reading.copy(trackStartEpochMs = 0L)
        if (sameApartFromStart && (!reading!!.playing ||
                        kotlin.math.abs(previous!!.trackStartEpochMs - reading.trackStartEpochMs) <
                        PLAYBACK_START_TOLERANCE_MS)) {
            return
        }
        lastPlaybackReading = reading
        if (previous?.playing != reading?.playing) PlaybackComplicationToggleReceiver.onStateArrived()
        requestPlaybackComplicationUpdate(context)
    }

    /** How far the counted start may wander (sample jitter, a second of rounding) before a redraw. */
    private const val PLAYBACK_START_TOLERANCE_MS = 2_500L
}
