package com.svartifoss.snfell.actions.symfonium

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.common.R as commonR
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.SymfoniumPlayback
import com.svartifoss.snfell.view.actionconfigs.ActionConfigFragment
import com.svartifoss.snfell.view.actionconfigs.SymfoniumMediaStartConfigFragment
import javax.inject.Inject

/** Starts a configured song, collection, mix or radio in Symfonium. */
class SymfoniumMediaStartAction : SelectableAction {
    companion object {
        const val KEY_MEDIA_TYPE = "MEDIA_TYPE"
        const val KEY_NAME = "NAME"
        const val KEY_ARTIST = "ARTIST"
        const val KEY_ALBUM = "ALBUM"
        const val KEY_ID = "ID"
        const val KEY_RESUME = "RESUME"
        const val KEY_SHUFFLE = "SHUFFLE"
        const val KEY_QUEUE = "QUEUE"

        const val TYPE_PLAYLIST = "playlist"
        const val TYPE_ARTIST = "artist"
        const val TYPE_ALBUM = "album"
        const val TYPE_GENRE = "genre"
        const val TYPE_SONG = "song"
        const val TYPE_SONG_MIX = "song_mix"
        const val TYPE_ALBUM_MIX = "album_mix"
        const val TYPE_INTERNET_RADIO = "internet_radio"

        const val QUEUE_START = 0
        const val QUEUE_NEXT = SymfoniumPlayback.QUEUE_NEXT
        const val QUEUE_AT_END = SymfoniumPlayback.QUEUE_AT_END
    }

    var mediaType: String
    var name: String
    var artist: String
    var album: String
    var id: Long?
    var resume: Boolean
    var shuffle: Boolean
    var queue: Int

    constructor(context: Context) : super(context) {
        mediaType = TYPE_SONG
        name = ""
        artist = ""
        album = ""
        id = null
        resume = true
        shuffle = false
        queue = QUEUE_START
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        mediaType = bundle.getString(KEY_MEDIA_TYPE, TYPE_SONG)
        name = bundle.getString(KEY_NAME).orEmpty()
        artist = bundle.getString(KEY_ARTIST).orEmpty()
        album = bundle.getString(KEY_ALBUM).orEmpty()
        id = bundle.getLong(KEY_ID).takeIf { bundle.containsKey(KEY_ID) }
        resume = bundle.getBoolean(KEY_RESUME, true)
        shuffle = bundle.getBoolean(KEY_SHUFFLE, false)
        queue = bundle.getInt(KEY_QUEUE, QUEUE_START)
    }

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)
        bundle.putString(KEY_MEDIA_TYPE, mediaType)
        bundle.putString(KEY_NAME, name)
        bundle.putString(KEY_ARTIST, artist)
        bundle.putString(KEY_ALBUM, album)
        id?.let { bundle.putLong(KEY_ID, it) } ?: bundle.remove(KEY_ID)
        bundle.putBoolean(KEY_RESUME, resume)
        bundle.putBoolean(KEY_SHUFFLE, shuffle)
        bundle.putInt(KEY_QUEUE, queue)
    }

    override fun retrieveTitle(): String = name.trim().takeIf { it.isNotEmpty() }?.let {
        context.getString(R.string.action_symfonium_start_named, it)
    } ?: context.getString(R.string.action_symfonium_start)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, commonR.drawable.action_play)!!

    override val configFragment: Class<out ActionConfigFragment<out PhoneAction>>
        get() = SymfoniumMediaStartConfigFragment::class.java

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as SymfoniumMediaStartAction
        return super.isEqualToAction(other) &&
                mediaType == other.mediaType && name == other.name && artist == other.artist &&
                album == other.album && id == other.id && resume == other.resume &&
                shuffle == other.shuffle && queue == other.queue
    }

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<SymfoniumMediaStartAction> {
        override suspend fun handleAction(action: SymfoniumMediaStartAction) {
            val sent = SymfoniumPlayback.startMedia(service,
                    SymfoniumPlayback.MediaStartRequest(
                            mediaType = action.mediaType,
                            name = action.name,
                            artist = action.artist,
                            album = action.album,
                            id = action.id,
                            resume = action.resume,
                            shuffle = action.shuffle,
                            queue = action.queue.takeIf { it != QUEUE_START }))
            if (!sent) {
                timber.log.Timber.w("Could not start Symfonium media '%s'", action.name)
            }
        }
    }
}
