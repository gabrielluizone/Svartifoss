package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CenterLongPressActionTest {

    @Test
    fun `explicit values are honoured`() {
        assertEquals(CenterLongPressAction.QUEUE, CenterLongPressAction.resolve("queue"))
        assertEquals(CenterLongPressAction.FACES, CenterLongPressAction.resolve("faces"))
        assertEquals(CenterLongPressAction.NONE, CenterLongPressAction.resolve("none"))
    }

    /**
     * The gesture was inert for almost everyone before this existed (its predecessor boolean was
     * off by default), so the picker is free to claim the unset state.
     */
    @Test
    fun `an unset value opens the face picker`() {
        assertEquals(CenterLongPressAction.FACES, CenterLongPressAction.resolve(null))
        assertEquals(CenterLongPressAction.FACES, CenterLongPressAction.resolve(""))
    }

    /**
     * A value from a newer build (or a corrupt one) must not disable the gesture - performing the
     * default action is recoverable, a dead gesture reads as a broken app.
     */
    @Test
    fun `an unparseable value falls back to the default action, not to none`() {
        assertEquals(CenterLongPressAction.FACES, CenterLongPressAction.resolve("something_new"))
    }

    @Test
    fun `values are case and whitespace tolerant, as an imported backup can carry either`() {
        assertEquals(CenterLongPressAction.QUEUE, CenterLongPressAction.resolve("  QUEUE "))
    }
}
