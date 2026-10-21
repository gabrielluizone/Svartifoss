package com.svartifoss.snfell.watch.view.shortcut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.max

/**
 * Pins the shortcut screen's placements on a round display, over the sizes real watches come in
 * (a 40mm watch is about 180dp across, a TicWatch Pro 5 233dp): the buttons clear the narrowing
 * bottom of the circle, "Open on phone" sits whole on the glass where the screen is big enough to
 * hold it, and the backdrop's masks are the shape the screen relies on.
 */
class ShortcutDetailGeometryTest {

    private val screens = listOf(180f, 192f, 210f, 233f, 260f)

    @Test
    fun `the page reads top to bottom without overlap`() {
        screens.forEach { screen ->
            val title = ShortcutDetailGeometry.titleTop(screen)
            val buttons = ShortcutDetailGeometry.buttonsTop(screen)
            val buttonsBottom = buttons + ShortcutDetailGeometry.buttonSize(screen)
            val pill = ShortcutDetailGeometry.pillTop(screen)
            assertTrue("$screen: title above the buttons", title < buttons)
            assertTrue("$screen: buttons above the pill", buttonsBottom < pill)
        }
    }

    @Test
    fun `the title block and the pill gap fill exactly the space the layout leaves`() {
        val spacing = 8f
        screens.forEach { screen ->
            // title top + its block + a gap = the buttons' top, however tall the text is
            assertEquals(ShortcutDetailGeometry.buttonsTop(screen),
                    ShortcutDetailGeometry.titleTop(screen) +
                            ShortcutDetailGeometry.titleBlockMinHeight(screen, spacing) + spacing, 1e-3f)
            // buttons' bottom + a gap + the pill gap = the pill's top
            assertEquals(ShortcutDetailGeometry.pillTop(screen),
                    ShortcutDetailGeometry.buttonsTop(screen) + ShortcutDetailGeometry.buttonSize(screen) +
                            spacing + ShortcutDetailGeometry.pillTopGap(screen, spacing), 1e-3f)
        }
    }

    @Test
    fun `both buttons sit well inside the glass`() {
        screens.forEach { screen ->
            val size = ShortcutDetailGeometry.buttonSize(screen)
            val offsetX = (size + ShortcutDetailGeometry.buttonGap(screen)) / 2f
            val centreY = ShortcutDetailGeometry.buttonsTop(screen) + size / 2f
            val reach = hypot(offsetX, centreY - screen / 2f) + size / 2f
            assertTrue("$screen: the pair reaches $reach of ${screen / 2f}", reach <= screen / 2f - 6f)
        }
    }

    @Test
    fun `a touch target never gets smaller than the minimum nor grows into a poster`() {
        screens.forEach { screen ->
            assertTrue(ShortcutDetailGeometry.buttonSize(screen) in 48f..64f)
        }
        assertEquals(48f, ShortcutDetailGeometry.buttonSize(100f), 0f)
        assertEquals(64f, ShortcutDetailGeometry.buttonSize(400f), 0f)
    }

    /** Whether a capsule [width] wide, [height] tall, top edge at [top], is inside a round screen. */
    private fun capsuleOnGlass(screen: Float, top: Float, width: Float, height: Float): Boolean {
        val radius = screen / 2f
        val endRadius = height / 2f
        val endCentreX = max(0f, width / 2f - endRadius)
        val centreY = top + endRadius - radius
        return hypot(endCentreX, centreY) + endRadius <= radius + 1e-3f
    }

    @Test
    fun `the pill is whole on the glass wherever it is wider than the minimum`() {
        screens.forEach { screen ->
            val width = ShortcutDetailGeometry.pillMaxWidth(screen)
            assertTrue("$screen: never below the minimum", width >= ShortcutDetailGeometry.MIN_PILL_WIDTH_DP)
            if (width > ShortcutDetailGeometry.MIN_PILL_WIDTH_DP) {
                assertTrue("$screen: a $width dp pill leaves the glass", capsuleOnGlass(
                        screen, ShortcutDetailGeometry.pillTop(screen), width,
                        ShortcutDetailGeometry.PILL_HEIGHT_DP))
            }
        }
    }

    @Test
    fun `on a large watch the whole label fits at rest`() {
        // "Abrir no celular" with its mark and padding is about 140dp at 12.5sp; on a TicWatch-sized
        // screen it must not be ellipsized or run into the bezel before anyone scrolls.
        assertTrue(ShortcutDetailGeometry.pillMaxWidth(233f) >= 140f)
        assertTrue(ShortcutDetailGeometry.pillMaxWidth(260f) >= 140f)
    }

    @Test
    fun `a mask is linear between its stops and flat beyond them`() {
        val stops = listOf(.2f to .0f, .6f to 1f)
        assertEquals(0f, ShortcutDetailGeometry.maskAt(stops, 0f), 0f)
        assertEquals(.5f, ShortcutDetailGeometry.maskAt(stops, .4f), 1e-6f)
        assertEquals(1f, ShortcutDetailGeometry.maskAt(stops, 2f), 0f)
    }

    @Test
    fun `the backdrop is lit between the name and the buttons and black under them`() {
        val vertical = ShortcutDetailGeometry.VERTICAL_MASK
        // The text and the top of the buttons see the cover as it is.
        listOf(.25f, .35f, .45f).forEach {
            assertEquals("at $it", 0f, ShortcutDetailGeometry.maskAt(vertical, it), 0f)
        }
        // Darker towards the clock, and wholly black from where the pill sits.
        assertTrue(ShortcutDetailGeometry.maskAt(vertical, .02f) > .3f)
        assertEquals(1f, ShortcutDetailGeometry.maskAt(vertical, .85f), 0f)
        // A long ramp between: no step steeper than this per tenth of the screen.
        (48..82).forEach { percent ->
            val step = ShortcutDetailGeometry.maskAt(vertical, (percent + 1) / 100f) -
                    ShortcutDetailGeometry.maskAt(vertical, percent / 100f)
            assertTrue("steep at $percent%: $step", step in 0f..0.04f)
        }
        val radial = ShortcutDetailGeometry.RADIAL_MASK
        assertTrue("the middle is clear", ShortcutDetailGeometry.maskAt(radial, 0f) == 0f)
        assertTrue("rising outwards", radial.zipWithNext().all { (a, b) -> b.second >= a.second })
        assertEquals("black at its reach", 1f, ShortcutDetailGeometry.maskAt(radial, 1f), 0f)
    }

    @Test
    fun `the chord is the circle`() {
        assertEquals(0f, ShortcutDetailGeometry.chordHalfWidth(200f, 0f), 1e-3f)
        assertEquals(100f, ShortcutDetailGeometry.chordHalfWidth(200f, 100f), 1e-3f)
        assertEquals(0f, ShortcutDetailGeometry.chordHalfWidth(200f, 200f), 1e-3f)
        assertEquals(ShortcutDetailGeometry.chordHalfWidth(200f, 60f),
                ShortcutDetailGeometry.chordHalfWidth(200f, 140f), 1e-3f)
        // Outside the screen it is clamped rather than becoming a NaN.
        assertEquals(0f, ShortcutDetailGeometry.chordHalfWidth(200f, 500f), 1e-3f)
    }
}
