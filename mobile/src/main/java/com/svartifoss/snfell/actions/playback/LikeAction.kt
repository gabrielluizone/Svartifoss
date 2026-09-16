package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.media.Rating
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
 * Whether a session's rating style can carry a "like" at all.
 *
 * `setRating` is the only like the media framework itself defines, and it is the route asked for
 * by users whose player is not one of the ones the custom-action and notification hints happen to
 * recognise. It only means "like" for two of the five rating styles: a heart is one, and a
 * thumbs-up/down pair is the one every "thumb" custom action is modelled on. The three star scales
 * are a *rating*, not a like - deciding that four stars means liked and three does not would be
 * inventing the user's opinion, and `RATING_NONE` says outright that the session does not rate.
 *
 * A top-level predicate over an int, like [likeLabelIndicatesAlreadyLiked] above and for the same
 * reason: the constants inline, so a JVM test reaches it without any of the Android types the
 * ratings themselves need.
 */
internal fun ratingTypeExpressesLike(ratingType: Int): Boolean =
        ratingType == Rating.RATING_HEART || ratingType == Rating.RATING_THUMB_UP_DOWN

/**
 * Likes the current track, through whichever of three routes the playing app actually offers.
 *
 * In order, and the order is the point: the app's own MediaSession custom action (YouTube Music's
 * and Retro Music's thumbs-up, Spotify's save-to-library), then its like/save *notification*
 * action, then the session's user rating. `MusicService.executeLikeCommand` is the ladder itself.
 *
 * The first two are matched by what their id and label look like, because a custom action is
 * app-defined and nothing about it is standard - so they work on the players whose wording is
 * recognised and silently on nobody else, which is what "the like button only works in some apps"
 * meant. The rating is the standard one: `setRating` with a heart or a thumbs-up is the only like
 * the framework itself defines. It is last because it is also the one most often declared and not
 * implemented, and because an app that ships its own button is telling you which control it
 * honours - but it is the reason a player that does none of the app-specific things can still be
 * liked from the watch. See [ratingTypeExpressesLike] for which sessions it can reach.
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

        /**
         * Whether the session's own user rating says this track is liked, or null when the
         * session's rating style cannot say either way.
         *
         * Null rather than false on purpose: this is the last of three sources, and "this session
         * does not do hearts" has to stay distinguishable from "not liked", or it would overrule
         * the two better-informed ones above it.
         */
        fun ratingLikedState(ratingType: Int, rating: Rating?): Boolean? {
            if (!ratingTypeExpressesLike(ratingType)) return null
            // Unrated is a real answer here, and it is "not liked".
            if (rating == null || !rating.isRated) return false
            return when (ratingType) {
                Rating.RATING_HEART -> rating.hasHeart()
                else -> rating.isThumbUp
            }
        }

        /** The rating to send for [liked], or null for a style that cannot express one. */
        fun likeRating(ratingType: Int, liked: Boolean): Rating? = when (ratingType) {
            Rating.RATING_HEART -> Rating.newHeartRating(liked)
            Rating.RATING_THUMB_UP_DOWN -> Rating.newThumbRating(liked)
            else -> null
        }
    }

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<LikeAction> {
        override suspend fun handleAction(action: LikeAction) {
            // The same complete ladder as the panel's built-in Like button - see the class doc.
            service.executeLikeCommand()
        }
    }
}
