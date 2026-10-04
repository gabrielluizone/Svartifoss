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
 * Plays the user's Spotify "Liked Songs" collection in one tap from the watch. `collection:tracks`
 * is Spotify's fixed URI for every account's Liked Songs, mirroring [PlayLikedSongsAction] for
 * YouTube Music. Like every streaming shortcut this goes through MusicService.playDeepLink, which
 * asks Spotify's media session to play the URI - so it starts in the background when Spotify allows
 * it, and falls back to opening the app otherwise (Spotify is stricter than YT Music about
 * background media-browser clients).
 */
class PlaySpotifyLikedSongsAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String =
            context.getString(R.string.action_play_spotify_liked_songs)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_liked_songs)!!
    override val remoteUri: String
        get() = PlayPlaylistShortcutAction(context, title, LIKED_SONGS_URI).remoteUri

    /** A playlist like any saved one, so picked from a list it opens with Play and Shuffle. */
    override val streamingShortcut: StreamingShortcutDescription
        get() = PlaylistShortcutStorage.describeForWatch(context, title, LIKED_SONGS_URI)

    /** Listed with its collection's cover rather than the glyph its buttons keep. */
    override val listCover: Drawable
        get() = BitmapDrawable(context.resources,
                ShortcutCovers.forCollection(context, StreamingCollection.SPOTIFY_LIKED))

    override val streamingCollection: StreamingCollection
        get() = StreamingCollection.SPOTIFY_LIKED

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<PlaySpotifyLikedSongsAction> {
        override suspend fun handleAction(action: PlaySpotifyLikedSongsAction) {
            service.playDeepLink(LIKED_SONGS_URI)
        }
    }

    companion object {
        private const val LIKED_SONGS_URI = "spotify:collection:tracks"
    }
}
