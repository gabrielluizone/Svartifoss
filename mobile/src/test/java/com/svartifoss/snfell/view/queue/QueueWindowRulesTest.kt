package com.svartifoss.snfell.view.queue

import com.svartifoss.snfell.notifications.MediaActionSemantics as S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueWindowRulesTest {

    @Test
    fun `search matches every word in title or artist, ignoring case and accents`() {
        assertTrue(QueueWindowRules.matches("muri evo", "ムリムリ進化論 - MURI MURI EVOLUTION", "NANAOAKARI"))
        assertTrue(QueueWindowRules.matches("nanaoakari", "Song", "NANAOAKARI"))
        assertTrue(QueueWindowRules.matches("cancao", "Canção", null))
        assertTrue(QueueWindowRules.matches("進化", "ムリムリ進化論", null))
        assertTrue(QueueWindowRules.matches("  ", "Anything", null))
        assertFalse(QueueWindowRules.matches("muri spica", "MURI MURI EVOLUTION", "NANAOAKARI"))
    }

    private fun x(name: String, semantic: String) = QueueWindowRules.Extra(name, semantic)

    @Test
    fun `retro music gets shuffle from its session beside the heart from its notification`() {
        // Notification: favourite, previous, play, next. Session: repeat, shuffle, favourite.
        val placed = QueueWindowRules.placeExtras(
                fromPlayer = listOf(x("favorite", S.LIKE), x("prev", S.PREVIOUS), x("play", S.PLAY), x("next", S.NEXT)),
                fromSession = listOf(x("repeat", S.REPEAT), x("shuffle", S.SHUFFLE), x("session-favorite", S.LIKE)))
        assertEquals(listOf("shuffle"), placed.left)
        assertEquals(listOf("favorite"), placed.right)
    }

    @Test
    fun `the notification's own buttons win and nothing appears twice`() {
        val placed = QueueWindowRules.placeExtras(
                fromPlayer = listOf(x("n-shuffle", S.SHUFFLE), x("n-like", S.LIKE)),
                fromSession = listOf(x("s-like", S.LIKE), x("s-shuffle", S.SHUFFLE), x("s-repeat", S.REPEAT)))
        assertEquals(listOf("n-shuffle"), placed.left)
        assertEquals(listOf("n-like"), placed.right)
    }

    @Test
    fun `shuffle goes left and repeat right when both are chosen`() {
        val placed = QueueWindowRules.placeExtras(emptyList(),
                listOf(x("repeat", S.REPEAT), x("shuffle", S.SHUFFLE)))
        assertEquals(listOf("shuffle"), placed.left)
        assertEquals(listOf("repeat"), placed.right)
    }

    @Test
    fun `dislike and transport never take a place, and there are at most two`() {
        val placed = QueueWindowRules.placeExtras(
                fromPlayer = listOf(x("dislike", S.DISLIKE), x("next", S.NEXT)),
                fromSession = listOf(x("like", S.LIKE), x("a", S.UNKNOWN), x("b", S.UNKNOWN)))
        assertEquals(listOf("like"), placed.left)
        assertEquals(listOf("a"), placed.right)
    }

    @Test
    fun `a lone heart stays on the right`() {
        val placed = QueueWindowRules.placeExtras(listOf(x("like", S.LIKE)), emptyList())
        assertEquals(emptyList<String>(), placed.left)
        assertEquals(listOf("like"), placed.right)
    }
}
