package com.svartifoss.snfell.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationActionOrderTest {
    @Test
    fun compactNotificationButtonsAreMirroredWithoutExpandedFallbacks() {
        assertEquals(
                listOf(2, 0, 3),
                orderedNotificationActionIndices(5, intArrayOf(2, 0, 3))
        )
    }

    @Test
    fun malformedCompactIndicesAreIgnoredAndDuplicatesAreRemoved() {
        assertEquals(
                listOf(1),
                orderedNotificationActionIndices(3, intArrayOf(-1, 1, 1, 9))
        )
        assertEquals(listOf(0, 1, 2), orderedNotificationActionIndices(3, intArrayOf(-1, 9)))
        assertEquals(emptyList<Int>(), orderedNotificationActionIndices(0, intArrayOf(0)))
    }

    @Test
    fun dislikeIsDiscardedButEveryOtherActionKeepsItsOrder() {
        val ordered = discardDislikeActions(
                listOf("next", "dislike", "shuffle", "like")
        ) { it }
        assertEquals(listOf("next", "shuffle", "like"), ordered)
    }

    @Test
    fun youtubeMusicStyleFourthDislikeNeverCreatesAnotherButton() {
        val ordered = discardDislikeActions(
                listOf("previous", "next", "like", "dislike")
        ) { it }.take(3)
        assertEquals(listOf("previous", "next", "like"), ordered)
    }

    @Test
    fun noActionsAreLostWhenNoneAreDislike() {
        val actions = listOf("previous", "next", "shuffle", "like")
        assertEquals(actions, discardDislikeActions(actions) { it })
    }

    @Test
    fun semanticInferenceUnderstandsPlayerLabelsWithoutSubstringFalsePositives() {
        assertEquals(MediaActionSemantics.LIKE, inferMediaActionSemantic("Curtir"))
        assertEquals(MediaActionSemantics.SHUFFLE, inferMediaActionSemantic("Modo aleatório"))
        assertEquals(MediaActionSemantics.REPEAT, inferMediaActionSemantic("Repetição"))
        assertEquals(MediaActionSemantics.NEXT, inferMediaActionSemantic("Próxima"))
        assertEquals(MediaActionSemantics.PAUSE, inferMediaActionSemantic("Pausar"))
        assertEquals(
                MediaActionSemantics.UNKNOWN,
                inferMediaActionSemantic("com.example.action.DISPLAY_PANEL")
        )
    }

    @Test
    fun stableMatchFollowsExecutionTargetAcrossReordering() {
        data class Candidate(
                val target: String,
                val identity: MediaActionIdentity
        )

        val like = mediaActionIdentity(
                sourceId = null,
                label = "Like",
                semantic = MediaActionSemantics.LIKE,
                iconPng = byteArrayOf(1, 2, 3)
        )
        val shuffle = mediaActionIdentity(
                sourceId = null,
                label = "Shuffle",
                semantic = MediaActionSemantics.SHUFFLE,
                iconPng = byteArrayOf(4, 5, 6)
        )
        val previous = listOf(
                Candidate("shuffle-intent", shuffle),
                Candidate("like-intent", like)
        )

        val match = findStableActionMatch(
                previous = previous,
                currentIdentity = like,
                identityOf = Candidate::identity,
                hasSameExecutionTarget = { it.target == "like-intent" }
        )

        assertEquals(1, match?.index)
    }

    @Test
    fun stableMatchNeverBorrowsAnIconFromAnotherExecutionTarget() {
        data class Candidate(
                val target: String,
                val identity: MediaActionIdentity
        )

        val oldIdentity = mediaActionIdentity(
                sourceId = null,
                label = "Like",
                semantic = MediaActionSemantics.LIKE,
                iconPng = byteArrayOf(1)
        )
        val changedIcon = mediaActionIdentity(
                sourceId = null,
                label = "Like",
                semantic = MediaActionSemantics.LIKE,
                iconPng = byteArrayOf(2)
        )
        val previous = listOf(Candidate("old-intent", oldIdentity))

        assertNull(findStableActionMatch(
                previous = previous,
                currentIdentity = oldIdentity,
                identityOf = Candidate::identity,
                hasSameExecutionTarget = { it.target == "new-intent" }
        ))
        assertNull(findStableActionMatch(
                previous = previous,
                currentIdentity = changedIcon,
                identityOf = Candidate::identity,
                hasSameExecutionTarget = { it.target == "old-intent" }
        ))
    }

    @Test
    fun customActionSnapshotIsStableButChangesWithVisibleOrExecutableIdentity() {
        val original = customActionSnapshotId(
                sourceId = "player.like",
                label = "Like",
                semantic = MediaActionSemantics.LIKE,
                iconResourceId = 42
        )
        assertEquals(original, customActionSnapshotId(
                sourceId = "player.like",
                label = "LIKE",
                semantic = MediaActionSemantics.LIKE,
                iconResourceId = 42
        ))
        assertNotEquals(original, customActionSnapshotId(
                sourceId = "player.like",
                label = "Unlike",
                semantic = MediaActionSemantics.DISLIKE,
                iconResourceId = 43
        ))
        assertNotEquals(original, customActionSnapshotId(
                sourceId = "player.shuffle",
                label = "Like",
                semantic = MediaActionSemantics.LIKE,
                iconResourceId = 42
        ))
    }
}
