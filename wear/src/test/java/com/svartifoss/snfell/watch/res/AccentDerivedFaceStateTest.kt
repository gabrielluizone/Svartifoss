package com.svartifoss.snfell.watch.res

import java.io.File
import org.junit.Assert.fail
import org.junit.Test

/**
 * Album colour arrives asynchronously, so `applyAccentColor` is the single place every value
 * derived from it is recomputed. Anything resolved *beside* that function instead of inside it
 * renders with the previous track's colour until an unrelated preference change refreshes it.
 *
 * That has now happened three times - the awake clock, the classic text shadows, and the
 * background stack - and it is invisible in all three: nothing throws, the phone's own preview is
 * honest about a watch that is itself wrong, and the value is correct again by the time anybody
 * looks for it. The first two are recorded in comments at their call sites. This pins the third,
 * and the shape they share.
 *
 * The stack is the sharp one because it had a *half* fix. `applyPlayerBackground` re-resolves the
 * layers for Classic's native drawable, so the one face rendered from a View updated correctly
 * while every Compose face - reading the same layers off `NowPlayingFaceState` - kept the old
 * album's accent floor. Checking the two together is the point: a future edit that drops either
 * leaves the other still working, which is exactly what made this hard to see.
 */
class AccentDerivedFaceStateTest {

    @Test
    fun `applyAccentColor republishes every accent-derived background value`() {
        val body = functionBody("applyAccentColor")

        val required = mapOf(
                "backgroundLayers = resolvedBackgroundLayers()" to
                        "the resolved stack the Compose faces draw their backdrop, shading and " +
                        "accent floor from",
                "backdropShadingColor = resolvedShadingColor()" to
                        "the legacy single shading tint",
                "applyPlayerBackground()" to
                        "Classic's native background drawable")

        val missing = required.filterKeys { !body.contains(it) }
        if (missing.isNotEmpty()) {
            fail("applyAccentColor no longer refreshes:\n  " +
                    missing.entries.joinToString("\n  ") { "${it.key}  -- ${it.value}" } +
                    "\nA value derived from the album accent has to be recomputed inside " +
                    "applyAccentColor, which is the one place currentAccentColor is assigned. " +
                    "Resolved anywhere else it renders one track behind.")
        }
    }

    /**
     * The stack's colours are re-resolved on a track change; its *structure* must not be. Reading
     * the preference again here would rebuild the layer list from whatever face is current, which
     * is work a track change has no reason to do and a second place for the two to disagree.
     */
    @Test
    fun `applyAccentColor re-resolves the stack without re-reading the preference`() {
        val body = functionBody("applyAccentColor")
        if (body.contains("readBackgroundLayers()")) {
            fail("applyAccentColor calls readBackgroundLayers(). A track change alters which " +
                    "colours the layers resolve to, never which layers there are; the structure " +
                    "is read where the appearance preferences are.")
        }
    }

    /**
     * Extracts one function's body by brace balance. Crude on purpose: a real parser is a lot of
     * machinery for a check whose whole job is to notice a deleted line.
     */
    private fun functionBody(name: String): String {
        val source = activity().readText()
        val start = source.indexOf("private fun $name(")
        if (start < 0) fail("MainActivity no longer declares $name")
        val open = source.indexOf('{', start)
        if (open < 0) fail("$name has no body")
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                }
            }
        }
        fail("$name has an unbalanced body")
        return ""
    }

    private fun activity(): File = listOf(
            File("src/main/java/com/svartifoss/snfell/watch/view/MainActivity.kt"),
            File("wear/src/main/java/com/svartifoss/snfell/watch/view/MainActivity.kt"))
            .first(File::isFile)
}
