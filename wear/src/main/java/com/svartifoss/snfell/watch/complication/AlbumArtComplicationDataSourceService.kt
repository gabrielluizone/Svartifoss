package com.svartifoss.snfell.watch.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import java.io.File
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.view.MainActivity
import com.matejdro.wearutils.messages.getByteArrayAsset
import com.matejdro.wearutils.miscutils.BitmapUtils
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Watch-face complication that shows the current album cover and opens Svartifoss when tapped.
 *
 * Image types only, on purpose: text-type slots were dropped because whether a face renders the
 * cover attached to a text complication is up to the face's own renderer (most just show the
 * text), which made the complication look broken. Supporting only SMALL_IMAGE/PHOTO_IMAGE means
 * the picker offers this source solely for slots that will actually draw the cover.
 *
 * Reads the same music-state DataItem the phone already publishes (like [MediaTileService]) so it
 * works even when the player UI is closed.
 */
class AlbumArtComplicationDataSourceService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Timber.d("Complication request: type=%s id=%d", request.complicationType, request.complicationInstanceId)
        val (state, freshArt) = readNowPlaying()
        val hasMusic = state != null && !state.error

        // The Data Layer streams the album-art asset separately from the state DataItem, so a
        // request that fires the instant new state arrives often reads the state before the bitmap
        // finished transferring. Rather than flashing the placeholder (the old "cover appears only
        // when it wants to" symptom), reuse the last cover we successfully rendered until the new
        // one is available. Fresh art overwrites the cache; when there's no music we clear it.
        val albumArt = when {
            freshArt != null -> { cacheAlbumArt(freshArt); freshArt }
            hasMusic -> readCachedAlbumArt()
            else -> { clearCachedAlbumArt(); null }
        }

        return buildComplicationData(request.complicationType, state, albumArt)
    }

    private val albumArtCacheFile: File
        get() = File(cacheDir, "complication_album_art.png")

    private fun cacheAlbumArt(bitmap: Bitmap) {
        try {
            val tmp = File(cacheDir, "complication_album_art.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    private suspend fun readNowPlaying(): Pair<MusicState?, Bitmap?> {
        return try {
            val dataClient = Wearable.getDataClient(this)
            val buffer = dataClient.dataItems.await()
            try {
                val item = buffer.firstOrNull { it.uri.path == CommPaths.DATA_MUSIC_STATE }
                if (item == null) {
                    Timber.w("Complication: no %s DataItem yet - phone hasn't published music state", CommPaths.DATA_MUSIC_STATE)
                    return null to null
                }
                val state = MusicState.parseFrom(item.data)
                if (state.error) {
                    Timber.w("Complication: music state DataItem reports an error: %s", state.title)
                    return null to null
                }
                val albumArtAsset = item.assets[CommPaths.ASSET_ALBUM_ART]
                if (albumArtAsset == null) {
                    Timber.d("Complication: music state has no %s asset attached", CommPaths.ASSET_ALBUM_ART)
                }
                val albumArtBytes = albumArtAsset?.let { dataClient.getByteArrayAsset(it) }
                val albumArt = BitmapUtils.deserialize(albumArtBytes)
                state to albumArt
            } finally {
                buffer.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Complication: failed reading now-playing state from the Data Layer")
            null to null
        }
    }

    private fun previewMusicState(): MusicState {
        return MusicState.newBuilder()
            .setTitle("Preview Track")
            .setArtist("Preview Artist")
            .setPlaying(true)
            .build()
    }
}
