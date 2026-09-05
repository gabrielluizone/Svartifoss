package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

/**
 * Plays the user's SoundCloud "Likes" in one tap from the watch, mirroring [PlayLikedSongsAction]
 * (YouTube Music) and [PlaySpotifyLikedSongsAction].
 *
 * `/you/likes` is the fixed path SoundCloud gives every signed-in account's liked tracks, and the
 * SoundCloud app registers an intent filter for its own `soundcloud.com` URLs, so this resolves
 * into the app rather than a browser whenever it is installed.
 *
 * Expect less of this one than of the YouTube Music shortcut, for a reason worth knowing before
 * filing it as broken: SoundCloud exposes no *playable* URI for a personal collection. So the
 * `playFromUri` and MediaBrowser steps of `MusicService.playDeepLink` are very likely to decline,
 * and what actually runs is the visible fallback - open Likes, then nudge the media session to
 * start. That works, but it needs the app to come to the foreground, unlike the YouTube Music
 * shortcut which plays with the screen off. Spotify's shortcut already lives with the same
 * limitation whenever Spotify refuses the browser connection.
 */
class PlaySoundCloudLikesAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String =
            context.getString(R.string.action_play_soundcloud_likes)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_liked_songs)!!
    override val remoteUri: String
        get() = PlayPlaylistShortcutAction(context, title, LIKES_LINK).remoteUri

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<PlaySoundCloudLikesAction> {
        override suspend fun handleAction(action: PlaySoundCloudLikesAction) {
            service.playDeepLink(LIKES_LINK)
        }
    }

    companion object {
        private const val LIKES_LINK = "https://soundcloud.com/you/likes"
    }
}
