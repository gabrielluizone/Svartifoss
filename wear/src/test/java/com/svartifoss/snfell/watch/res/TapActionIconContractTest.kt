package com.svartifoss.snfell.watch.res

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tap ripple names the action it just ran, and does so independently of the quadrant hints.
 *
 * That independence is the entire feature. Permanently visible hints are four glyphs on top of an
 * album cover, which is a reasonable thing to switch off, and switching them off used to leave no
 * confirmation at all of which of four actions a corner tap had fired. The answer shipped twice
 * before in weaker forms - brightening a hint that was hidden anyway, then simply enlarging the
 * ripple - and both times a later change quietly removed it, because nothing said it was load
 * bearing. This says so.
 */
class TapActionIconContractTest {

    private val host by lazy {
        repoFile("wear/src/main/java/com/svartifoss/snfell/watch/view/MainActivity.kt").readText()
    }

    @Test
    fun `the glyph is revealed from the tap, not from touch-down`() {
        val singleTap = functionBody(host, "onSingleTap")
        assertTrue("the single tap must reveal the action's glyph",
                singleTap.contains("revealQuadrantActionIcon(quadrant)"))

        // Touch-down has not classified the gesture yet, so a press that becomes a swipe would
        // announce an action that never ran.
        assertFalse("touch-down must not reveal a glyph",
                functionBody(host, "onTouchDown").contains("revealIcon") ||
                        functionBody(host, "onTouchDown").contains("revealQuadrantActionIcon"))
    }

    /**
     * A double tap and a long press on the same quadrant run *different* actions, and the glyph
     * reused here is the single-tap one. Showing it for either would be worse than showing nothing.
     */
    @Test
    fun `only the single tap claims to name the action`() {
        for (gesture in listOf("onDoubleTap", "onLongTap")) {
            assertFalse("$gesture must not reveal the single-tap action's glyph",
                    functionBody(host, gesture).contains("revealQuadrantActionIcon"))
        }
    }

    @Test
    fun `the glyph does not depend on the hints being drawn`() {
        val reveal = functionBody(host, "revealQuadrantActionIcon")
        assertTrue("it must honour its own setting", reveal.contains("quadrantTapFlashEnabled"))
        assertTrue("and stay out of ambient", reveal.contains("isAmbient"))
        assertFalse(
                "it must not check whether the persistent hint is visible - a user who hid the " +
                        "hints is exactly who this is for",
                reveal.contains("visibility") || reveal.contains("playerControlsVisible"))
    }

    /** The hint's own bounce is a location cue and does need a hint; the two must not be conflated. */
    @Test
    fun `the hint bounce still requires a visible hint`() {
        val bounce = functionBody(host, "pulseQuadrantIcon")
        assertTrue(bounce.contains("visibility != View.VISIBLE"))
    }

    private fun functionBody(source: String, name: String): String {
        val start = Regex("""fun $name\(""").find(source)?.range?.first
                ?: throw AssertionError("$name is gone from MainActivity")
        val open = source.indexOf('{', start)
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, i + 1)
                }
            }
        }
        throw AssertionError("unbalanced body for $name")
    }

    private fun repoFile(relative: String): File =
            listOf(File("../$relative"), File(relative))
                    .firstOrNull { it.exists() }
                    ?: throw AssertionError("Could not locate $relative")
}
