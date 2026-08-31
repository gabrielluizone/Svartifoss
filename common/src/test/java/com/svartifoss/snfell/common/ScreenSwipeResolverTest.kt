package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenSwipeResolverTest {

    @Test
    fun `dominant configured directions resolve across the full surface`() {
        assertEquals(ScreenSwipeDirection.UP, ScreenSwipeResolver.resolve(20f, -700f, 400f))
        assertEquals(ScreenSwipeDirection.DOWN, ScreenSwipeResolver.resolve(-30f, 700f, 400f))
        assertEquals(ScreenSwipeDirection.LEFT, ScreenSwipeResolver.resolve(-700f, 20f, 400f))
    }

    @Test
    fun `right swipe remains reserved for system dismiss and slow motion is ignored`() {
        assertNull(ScreenSwipeResolver.resolve(700f, 10f, 400f))
        assertNull(ScreenSwipeResolver.resolve(-300f, 100f, 400f))
        assertNull(ScreenSwipeResolver.resolve(100f, 300f, 400f))
    }
}
