package com.svartifoss.snfell.watch.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import java.io.File
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.view.MainActivity
import com.matejdro.wearutils.messages.getByteArrayAsset
import com.matejdro.wearutils.miscutils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Watch-face complication that shows the current album cover and opens Svartifoss when tapped.
 *
 * Round slots get SMALL_IMAGE/PHOTO_IMAGE (just the cover). Wide slots get LONG_TEXT - cover
 * plus track title and artist - because a bare SMALL_IMAGE in a wide slot renders as a lone
 * circle floating in the middle of the area. SHORT_TEXT stays deliberately unsupported: whether
 * a face renders the cover attached to a short-text complication is up to its own renderer
 * (most show just the text), which made small text slots look broken.
 *
 * Reads the same music-state DataItem the phone already publishes (like [MediaTileService]) so it
 * works even when the player UI is closed.
 */
class AlbumArtComplicationDataSourceService : SuspendingComplicationDataSourceService() {

    // SuspendingComplicationDataSourceService invokes this on the main dispatcher - the same
    // main thread the app's UI runs on (single process). All the bitmap decode/encode and file
    // I/O below therefore hops to a background dispatcher: rendering the complication used to
    // visibly stall the open app right as a track changed.
    override suspend fun onComplicationRequest(
        request: ComplicationRequest
    ): ComplicationData? = withContext(Dispatchers.IO) {
        Timber.d("Complication request: type=%s id=%d", request.complicationType, request.complicationInstanceId)

        // Cache policy: only a *definitive* "no music" answer (the phone explicitly published an
        // error state, or never published state at all) may clear the cached cover. A transient
        // Data Layer failure (Bluetooth hiccup, asset fetch timing out, request firing at boot
        // before Play Services is ready) used to fall into the same "no music" branch, nuking the
        // cache and flashing the placeholder until the next fully successful fetch - the main
        // "cover appears only when it wants to" symptom. Now those failures reuse the last cover.
        val albumArt: Bitmap?
        val state: MusicState?
        when (val result = readNowPlaying()) {
            is NowPlayingResult.Playing -> {
                state = result.state
                // The album-art asset can lag behind the state DataItem; when it is not readable
                // (yet), reuse the last cover we successfully rendered instead of flashing the
                // placeholder. Fresh art overwrites the cache.
                albumArt = result.albumArt?.also { cacheAlbumArt(it) } ?: readCachedAlbumArt()
            }
            is NowPlayingResult.NoMusic -> {
                state = null
                albumArt = null
                clearCachedAlbumArt()
            }
            is NowPlayingResult.Unavailable -> {
                state = null
                albumArt = readCachedAlbumArt()
            }
        }

        buildComplicationData(request.complicationType, state, albumArt)
    }

    private sealed class NowPlayingResult {
        /** The phone published a valid, non-error music state. */
        class Playing(val state: MusicState, val albumArt: Bitmap?) : NowPlayingResult()

        /** Definitive: the phone says there is no music (error state) or never published state. */
        object NoMusic : NowPlayingResult()

        /** Transient: the Data Layer could not be read right now - not proof that music stopped. */
        object Unavailable : NowPlayingResult()
    }

    private val albumArtCacheFile: File
        get() = File(cacheDir, "complication_album_art.png")

    private fun cacheAlbumArt(bitmap: Bitmap) {
        try {
            val tmp = File(cacheDir, "complication_album_art.tmp")
            // JPEG, not PNG: covers have no alpha, and a max-quality PNG encode of a full-screen
            // bitmap took hundreds of ms on watch hardware for zero visible gain. (The file keeps
            // its .png name; BitmapFactory sniffs content, not extension.)
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            tmp.renameTo(albumArtCacheFile)
        } catch (e: Exception) {
            Timber.w(e, "Complication: failed caching album art")
        }
    }

    private fun readCachedAlbumArt(): Bitmap? = try {
        albumArtCacheFile.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    } catch (e: Exception) {
        Timber.w(e, "Complication: failed reading cached album art")
        null
    }

    private fun clearCachedAlbumArt() {
        runCatching { albumArtCacheFile.delete() }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        // Preview must always render - a blank preview makes the complication look broken in the
        // watch face editor.
        return buildComplicationData(type, previewMusicState(), null)
    }

    private fun buildComplicationData(
        type: ComplicationType,
        state: MusicState?,
        albumArt: Bitmap?
    ): ComplicationData? {
        val hasMusic = state != null && !state.error
        val description = when {
            hasMusic && state!!.title.isNotBlank() && state.artist.isNotBlank() ->
                "${state.title} — ${state.artist}"
            hasMusic && state!!.title.isNotBlank() -> state.title
            else -> getString(R.string.complication_no_music)
        }
        val contentDescription = PlainComplicationText.Builder(description).build()
        val tapAction = openAppPendingIntent()

        val imageIcon = when {
            albumArt != null -> Icon.createWithBitmap(albumArt)
            else -> Icon.createWithResource(this, R.drawable.ic_complication_media)
        }

        return when (type) {
            ComplicationType.SMALL_IMAGE -> {
                SmallImageComplicationData.Builder(
                    smallImage = SmallImage.Builder(imageIcon, SmallImageType.PHOTO).build(),
                    contentDescription = contentDescription
                )
                    .setTapAction(tapAction)
                    .build()
            }
            ComplicationType.PHOTO_IMAGE -> {
                PhotoImageComplicationData.Builder(
                    photoImage = imageIcon,
                    contentDescription = contentDescription
                )
                    .setTapAction(tapAction)
                    .build()
            }
            ComplicationType.LONG_TEXT -> {
                // Wide slots: cover on the side, title as the main line, artist as the
                // secondary one (faces render title/text stacked next to the small image).
                val mainText = when {
                    hasMusic && state!!.title.isNotBlank() -> state.title
                    else -> getString(R.string.complication_no_music)
                }
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(mainText).build(),
                    contentDescription = contentDescription
                )
                    .setSmallImage(SmallImage.Builder(imageIcon, SmallImageType.PHOTO).build())
                    .setTapAction(tapAction)
                    .apply {
                        if (hasMusic && state!!.artist.isNotBlank()) {
                            setTitle(PlainComplicationText.Builder(state.artist).build())
                        }
                    }
                    .build()
            }
            else -> null
        }
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private suspend fun readNowPlaying(): NowPlayingResult {
        val dataClient = Wearable.getDataClient(this)

        val item = try {
            val buffer = dataClient.getDataItems(
                    Uri.parse("wear://*${CommPaths.DATA_MUSIC_STATE}"),
                    DataClient.FILTER_LITERAL
            ).await()
            try {
                buffer.firstOrNull()?.freeze()
            } finally {
                buffer.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Complication: failed reading now-playing state from the Data Layer")
            return NowPlayingResult.Unavailable
        }

        if (item == null) {
            Timber.w("Complication: no %s DataItem yet - phone hasn't published music state", CommPaths.DATA_MUSIC_STATE)
            return NowPlayingResult.NoMusic
        }

        val state = try {
            MusicState.parseFrom(item.data)
        } catch (e: Exception) {
            Timber.e(e, "Complication: could not parse the music state DataItem")
            return NowPlayingResult.Unavailable
        }
        if (state.error) {
            Timber.w("Complication: music state DataItem reports an error: %s", state.title)
            return NowPlayingResult.NoMusic
        }

        // The asset fetch gets its own failure domain: a valid state whose cover cannot be read
        // right now must not degrade into "no music" - the caller falls back to the cached cover.
        val albumArt = try {
            val albumArtAsset = item.assets[CommPaths.ASSET_ALBUM_ART]
            if (albumArtAsset == null) {
                Timber.d("Complication: music state has no %s asset attached", CommPaths.ASSET_ALBUM_ART)
            }
            val albumArtBytes = albumArtAsset?.let { dataClient.getByteArrayAsset(it) }
            BitmapUtils.deserialize(albumArtBytes)
        } catch (e: Exception) {
            Timber.w(e, "Complication: failed fetching the album-art asset")
            null
        }

        return NowPlayingResult.Playing(state, albumArt)
    }

    private fun previewMusicState(): MusicState {
        return MusicState.newBuilder()
            .setTitle("Preview Track")
            .setArtist("Preview Artist")
            .setPlaying(true)
            .build()
    }
}
