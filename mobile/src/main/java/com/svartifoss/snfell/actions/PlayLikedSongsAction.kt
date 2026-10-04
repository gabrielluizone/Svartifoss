package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import com.svartifoss.snfell.music.ShortcutCovers
import com.svartifoss.snfell.music.StreamingCollection
import com.svartifoss.snfell.music.StreamingShortcutDescription
import javax.inject.Inject

/**
 * Plays the user's YouTube Music "Liked Music" playlist in one tap from the watch. `LM` is the
 * fixed playlist id YouTube Music assigns to every account's liked songs, and the `watch` (not
 * `playlist`) endpoint starts playback immediately instead of just opening the playlist page.
 * There is no official YouTube Music API - the deep link is the same path a shared playlist
 * link takes, handled by MusicService.playDeepLink.
 */
class PlayLikedSongsAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_play_liked_songs)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_liked_songs)!!
    override val remoteUri: String
        get() = PlayPlaylistShortcutAction(context, title, LIKED_SONGS_LINK).remoteUri

    /** A playlist like any saved one, so picked from a list it opens with Play and Shuffle. */
    override val streamingShortcut: StreamingShortcutDescription
        get() = PlaylistShortcutStorage.describeForWatch(context, title, LIKED_SONGS_LINK)

    /** Listed with its collection's cover rather than the glyph its buttons keep. */
    override val listCover: Drawable
        get() = BitmapDrawable(context.resources,
                ShortcutCovers.forCollection(context, StreamingCollection.YOUTUBE_MUSIC_LIKED))

    override val streamingCollection: StreamingCollection
        get() = StreamingCollection.YOUTUBE_MUSIC_LIKED

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<PlayLikedSongsAction> {
        override suspend fun handleAction(action: PlayLikedSongsAction) {
            service.playDeepLink(LIKED_SONGS_LINK)
        }
    }

    companion object {
        private const val LIKED_SONGS_LINK = "https://music.youtube.com/watch?list=LM"
    }
}
