package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

/**
 * Plays "Liked Music" shuffled. A dedicated action rather than relying on the watch/session
 * Shuffle toggle after [PlayLikedSongsAction] starts playing: YouTube Music serves liked songs
 * as an auto-generated mix, and doesn't reliably honor `MediaSession.setShuffleMode` toggled
 * from outside the app for that kind of queue. `&shuffle=true` is the same query param YT
 * Music's own web/app "Shuffle" button appends to a `watch?list=` link, so it's honored at
 * playback start instead.
 */
class PlayLikedSongsShuffledAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_play_liked_songs_shuffled)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_liked_songs)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<PlayLikedSongsShuffledAction> {
        override suspend fun handleAction(action: PlayLikedSongsShuffledAction) {
            service.playDeepLink(LIKED_SONGS_SHUFFLED_LINK)
        }
    }

    companion object {
        private const val LIKED_SONGS_SHUFFLED_LINK = "https://music.youtube.com/watch?list=LM&shuffle=true"
    }
}
