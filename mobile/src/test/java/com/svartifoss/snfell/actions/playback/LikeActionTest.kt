package com.svartifoss.snfell.actions.playback

import android.media.Rating
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LikeActionTest {
    @Test
    fun removalWordingReadsAsAlreadyLiked() {
        assertTrue(likeLabelIndicatesAlreadyLiked("Unlike"))
        assertTrue(likeLabelIndicatesAlreadyLiked("Remove from Your Library"))
        assertTrue(likeLabelIndicatesAlreadyLiked("Saved"))
    }

    @Test
    fun additiveWordingReadsAsNotYetLiked() {
        assertFalse(likeLabelIndicatesAlreadyLiked("Like"))
        assertFalse(likeLabelIndicatesAlreadyLiked("Save to Your Library"))
        assertFalse(likeLabelIndicatesAlreadyLiked("Curtir"))
    }

    @Test
    fun blankOrAbsentLabelsReadAsNotLiked() {
        assertFalse(likeLabelIndicatesAlreadyLiked())
        assertFalse(likeLabelIndicatesAlreadyLiked(null, ""))
    }

    @Test
    fun anyMatchingLabelAmongSeveralWins() {
        assertTrue(likeLabelIndicatesAlreadyLiked("Like", "com.app.action.UNLIKE"))
    }

    /**
     * The third rung of the like ladder reaches a session through `setRating`, which is the only
     * like the framework defines - but only two of the five rating styles mean "like" at all.
     */
    @Test
    fun onlyHeartAndThumbsCanCarryALike() {
        assertTrue(ratingTypeExpressesLike(Rating.RATING_HEART))
        assertTrue(ratingTypeExpressesLike(Rating.RATING_THUMB_UP_DOWN))
    }

    @Test
    fun aStarScaleIsARatingAndNotALike() {
        // Choosing a star count to mean "liked" would be inventing the user's opinion, so these
        // sessions fall through the rung rather than being given one.
        assertFalse(ratingTypeExpressesLike(Rating.RATING_3_STARS))
        assertFalse(ratingTypeExpressesLike(Rating.RATING_4_STARS))
        assertFalse(ratingTypeExpressesLike(Rating.RATING_5_STARS))
        assertFalse(ratingTypeExpressesLike(Rating.RATING_PERCENTAGE))
    }

    @Test
    fun aSessionThatDoesNotRateIsNotOfferedTheRung() {
        assertFalse(ratingTypeExpressesLike(Rating.RATING_NONE))
        // Whatever a future platform adds, an unrecognised style is not assumed to mean "like".
        assertFalse(ratingTypeExpressesLike(99))
    }
}
