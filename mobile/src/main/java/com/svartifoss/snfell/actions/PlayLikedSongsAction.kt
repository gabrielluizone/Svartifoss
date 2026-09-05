package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.MusicService
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

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<PlayLikedSongsAction> {
        override suspend fun handleAction(action: PlayLikedSongsAction) {
            service.playDeepLink(LIKED_SONGS_LINK)
        }
    }

    companion object {
        private const val LIKED_SONGS_LINK = "https://music.youtube.com/watch?list=LM"
    }
}
