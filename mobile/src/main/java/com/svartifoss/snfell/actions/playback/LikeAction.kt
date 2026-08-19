package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.media.session.PlaybackState
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

// "heart"/"save"/"collection"/"library" cover Spotify, whose save-to-Liked-Songs custom action
// reads "Save to Your Library" / heart rather than the word "like".
private val LIKE_NAME_HINTS = listOf(
        "like", "thumb", "favorite", "favourite", "love",
        "heart", "save", "collection", "library")
private val ALREADY_LIKED_HINTS = listOf(
        "unlike", "remove", "undo", "unfavorite", "unfavourite",
        "unsave", "saved", "unheart", "in_library", "in library")

/** Whether a like/save action's own label reads as "remove/undo" (already liked) rather than
 *  "add" (not liked yet). A top-level pure predicate, not a class member, so a JVM test can
 *  exercise it without touching any of the Android types the rest of this file needs. Also reused
 *  by [com.svartifoss.snfell.notifications.MediaNotificationActions] for apps - SoundCloud among
 *  them - that expose "like" only as a `Notification.Action`, never a MediaSession custom action,
 *  which is the one case [LikeAction.isCurrentlyLiked] can't see at all: it only inspects
 *  [PlaybackState.customActions]. Not all apps expose enough information to tell either way. */
internal fun likeLabelIndicatesAlreadyLiked(vararg labels: CharSequence?): Boolean =
        labels.any { label ->
            label != null && ALREADY_LIKED_HINTS.any { hint -> label.contains(hint, ignoreCase = true) }
        }

/**
 * Toggles a "like"/"favorite" custom action exposed by the currently playing app's
 * MediaSession, e.g. YouTube Music's or Retro Music's thumbs-up/favorite button.
 *
 * There is no standardized MediaSession API for "like" - it is surfaced as one of the
 * app-defined [android.media.session.PlaybackState.CustomAction]s, so the best a generic
 * remote like this can do is look for a custom action whose name/id looks like a like button,
 * and guess whether it's currently active from whether its label reads like "like" or "unlike".
 * This is inherently best-effort and may not work identically on every app.
 */
class LikeAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_like)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_like)!!

    companion object {
        fun findLikeCustomAction(playbackState: PlaybackState): PlaybackState.CustomAction? {
            return playbackState.customActions.orEmpty().firstOrNull { customAction ->
                LIKE_NAME_HINTS.any { hint ->
                    customAction.action.contains(hint, ignoreCase = true) ||
                            customAction.name.toString().contains(hint, ignoreCase = true)
                }
            }
        }

        /** Best-effort guess at whether the track is *currently* liked, based on whether the
         *  matched custom action's label/id reads like "remove/undo like" (already liked) rather
         *  than "like" (not liked yet). Not all apps expose enough information to tell. */
        fun isCurrentlyLiked(playbackState: PlaybackState): Boolean {
            val action = findLikeCustomAction(playbackState) ?: return false
            return likeLabelIndicatesAlreadyLiked(action.action, action.name)
        }
    }

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<LikeAction> {
        override suspend fun handleAction(action: LikeAction) {
            val playbackState = service.currentMediaController?.playbackState ?: return
            val likeAction = findLikeCustomAction(playbackState) ?: return

            service.currentMediaController?.transportControls
                    ?.sendCustomAction(likeAction.action, likeAction.extras)

            // Some apps don't immediately re-publish their playback state after toggling the like
            // (so onPlaybackStateChanged never fires). Schedule a forced re-read so the watch
            // button shows the correct liked/unliked state within ~500 ms.
            service.scheduleStateRefresh()
        }
    }
}
