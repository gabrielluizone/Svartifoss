package com.svartifoss.snfell.watch.view.compose

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scrolling line must take its room from measure, never from the draw scope's size.
 *
 * `trackMarquee` is one node that is both a layout node and a draw node, and Compose draws such a
 * node with the coordinator *below* it: inside `draw()`, `size` is the text's own size - the whole
 * title, not the room it was given. The first version read the room from `size.width`, so every
 * title "fit": nothing ever scrolled or was clipped, and a long title in the scrolling mode ran
 * straight off the round screen, on every Compose face and in the queue. Nothing about that fails
 * loudly - the line is simply drawn whole and standing still - so this pins the one rule that keeps
 * it working.
 */
class TrackMarqueeContractTest {

    private companion object {
        const val SOURCE_PATH =
                "src/main/java/com/svartifoss/snfell/watch/view/compose/TrackMarquee.kt"
        const val DRAW_SIGNATURE = "override fun ContentDrawScope.draw()"
    }

    @Test
    fun theDrawUsesTheRoomFromMeasure() {
        val draw = drawBody()
        assertFalse(
                "TrackMarquee.draw() reads size.width. In a node that both measures and draws, " +
                        "size is the text's own width, so every line looks as if it fits and " +
                        "never scrolls; use the width stored in measure.",
                draw.contains("size.width"))
        assertTrue(
                "TrackMarquee.draw() no longer reads viewportWidth, the room stored in measure.",
                draw.contains("viewportWidth"))
    }

    /**
     * The other half of the same trap: a brush is sized by the draw scope, not by the rect it
     * fills, so a fade gradient left at its default `endX` spans the whole title and puts the
     * right-hand dissolve past the end of the room - that end then never fades.
     */
    @Test
    fun theFadeIsBoundedToTheRoom() {
        val gradient = drawBody().substringAfter("Brush.horizontalGradient(", missingDelimiterValue = "")
                .substringBefore("blendMode")
        assertTrue("Could not find the fade gradient in TrackMarquee.draw().", gradient.isNotEmpty())
        assertTrue(
                "The fade gradient in TrackMarquee.draw() no longer gives its own endX; the draw " +
                        "scope is the whole title, so the right-hand fade would fall outside the room.",
                gradient.contains("endX = viewport"))
    }

    @Test
    fun measureStoresTheRoom() {
        val code = codeOf(source())
        val measure = code.substringAfter("MeasureScope.measure(", missingDelimiterValue = "")
                .substringBefore("IntrinsicMeasureScope")
        assertTrue(
                "TrackMarquee's measure no longer records the room the line was given.",
                measure.contains("viewportWidth ="))
    }

    private fun drawBody(): String {
        val code = codeOf(source())
        assertTrue("Could not find $DRAW_SIGNATURE in TrackMarquee.kt", code.contains(DRAW_SIGNATURE))
        return code.substringAfter(DRAW_SIGNATURE)
    }

    private fun source(): File = listOf(File("."), File("wear"))
            .map { File(it, SOURCE_PATH) }
            .firstOrNull { it.isFile }
            ?: throw AssertionError("Could not locate TrackMarquee.kt from ${File(".").absolutePath}")

    /** The source without comments, so a rule is matched only against code. */
    private fun codeOf(file: File): String = file.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { line ->
                val index = line.indexOf("//")
                if (index >= 0) line.substring(0, index) else line
            }
}
