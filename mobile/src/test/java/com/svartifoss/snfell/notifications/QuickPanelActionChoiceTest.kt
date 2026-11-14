package com.svartifoss.snfell.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPanelActionChoiceTest {
    private val like = MediaActionSemantics.LIKE
    private val shuffle = MediaActionSemantics.SHUFFLE
    private val repeat = MediaActionSemantics.REPEAT
    private val previous = MediaActionSemantics.PREVIOUS
    private val play = MediaActionSemantics.PLAY
    private val next = MediaActionSemantics.NEXT

    @Test
    fun youtubeMusicTransportOnlyNotificationGivesWayToItsCustomActions() {
        // Pixel 3, Android 10, YouTube Music 8.30: compact notification Previous/Play/Next, custom
        // actions Undo like/Shuffle off/Repeat off (Dislike already discarded).
        assertEquals(
                QuickActionSource.CUSTOM_ACTIONS,
                chooseQuickActionSource(listOf(previous, play, next), listOf(like, shuffle, repeat))
        )
    }

    @Test
    fun aNotificationWithAnythingBesidesTransportStillWins() {
        assertEquals(
                QuickActionSource.NOTIFICATION,
                chooseQuickActionSource(listOf(like, previous, next), listOf(shuffle, repeat))
        )
        assertEquals(
                QuickActionSource.NOTIFICATION,
                chooseQuickActionSource(listOf(MediaActionSemantics.UNKNOWN), listOf(shuffle))
        )
    }

    @Test
    fun transportOnlyEverywhereStillFillsThePanelFromTheNotification() {
        assertEquals(
                QuickActionSource.NOTIFICATION,
                chooseQuickActionSource(listOf(previous, play, next), emptyList())
        )
        assertEquals(
                QuickActionSource.NOTIFICATION,
                chooseQuickActionSource(listOf(previous, play, next), listOf(MediaActionSemantics.STOP))
        )
    }

    @Test
    fun customActionsAreTheFallbackWhenTheNotificationHasNothing() {
        assertEquals(
                QuickActionSource.CUSTOM_ACTIONS,
                chooseQuickActionSource(emptyList(), listOf(shuffle))
        )
        assertEquals(
                QuickActionSource.CUSTOM_ACTIONS,
                chooseQuickActionSource(emptyList(), listOf(next))
        )
        assertEquals(QuickActionSource.NONE, chooseQuickActionSource(emptyList(), emptyList()))
    }

    @Test
    fun theCapNoLongerDropsALikeListedAfterTransport() {
        assertEquals(
                listOf(like, previous, play),
                rankQuickActions(listOf(previous, play, next, like), 3) { it }
        )
    }

    @Test
    fun rankingKeepsTheAppsOrderWithinEachGroup() {
        assertEquals(
                listOf(repeat, shuffle, next, previous),
                rankQuickActions(listOf(next, repeat, previous, shuffle), 4) { it }
        )
        assertEquals(
                listOf(like, shuffle, repeat),
                rankQuickActions(listOf(like, shuffle, repeat), 3) { it }
        )
    }
}
