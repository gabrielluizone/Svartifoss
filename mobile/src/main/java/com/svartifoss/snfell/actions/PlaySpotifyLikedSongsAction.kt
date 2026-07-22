package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.MusicService
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
