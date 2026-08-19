package com.svartifoss.snfell.actions.playback

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
}
