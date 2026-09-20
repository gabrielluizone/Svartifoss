package com.svartifoss.snfell.watch.view.face

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Every face that draws the playing track's cover must draw it through `FaceCoverImage`.
 *
 * The helper carries the one transition the whole app shares - the host's full-screen artwork, the
 * phone's preview and each face's own cover window - and the failure it exists to stop is silent in
 * the usual way: a call site that writes a bare `Image` renders perfectly, ignores
 * `wear_album_art_fade`, and swaps the picture outright while everything around it eases. That was
 * the shipped state: the switch reached the host backdrop and Vinyl's mini disc and did nothing on
 * Split, Note, Chat, Metadata, Ribbon, Frame or Depth, so whether a track change animated depended
 * on which face the author happened to be looking at.
 *
 * A source sweep rather than a rendering assertion, for the reason
 * [ArtistLineTypographyTest] records: what goes wrong is never the drawing, it is a call site
 * opting out of the shared behaviour without saying so.
 */
class FaceCoverTransitionTest {

    private companion object {

        /**
         * Composables allowed to draw a cover bitmap straight into an `Image`.
         *
         * Keyed by the composable's simple name, and every entry names why the shared helper does
         * not fit. There are only two reasons, and neither is "it looked fine": the picture is not
         * the playing track's cover, or it is an always-on variant, which is styled by the
         * `WEAR_AOD_*` controls and must not read the awake transition at all.
         */
        val ALLOWED_BARE_COVER = mapOf(
                // The rail's cards - the hero among them. Carousel deliberately routes everything
                // that changes with the track through one AnimatedContent so the cover, the title
                // and the neighbouring cards move as one frame; a second transition inside it
                // would animate the cover against the card carrying it.
                "CarouselCardImage" to "Carousel animates its whole frame as one AnimatedContent",
                // Two tracks behind and two ahead, from the queue. Not this track's cover.
                "RibbonQueueRails" to "queue rail cards, not the playing cover",
                // --- Always-on variants.
                "CarouselAmbient" to "AOD variant"
        )

        /**
         * Faces that read `state.albumArt` without calling the helper, and why.
         *
         * Deliberately separate from [ALLOWED_BARE_COVER]: that one exempts a *drawing*, this one
         * exempts a whole file, which is the larger claim and should stay rare.
         */
        val FILES_WITHOUT_THE_HELPER = mapOf(
                "CarouselFace.kt" to "one AnimatedContent for the whole frame - see CarouselCardImage"
        )
    }

    @Test
    fun everyFaceThatDrawsTheCoverGoesThroughTheSharedHelper() {
        val offenders = mutableListOf<String>()
        var inspected = 0

        faceSources().forEach { file ->
            val text = file.readText()
            if (!text.contains("state.albumArt")) return@forEach
            inspected++
            if (text.contains("FaceCoverImage(")) return@forEach
            if (file.name in FILES_WITHOUT_THE_HELPER) return@forEach
            offenders += file.name
        }

        assertTrue("Expected to find faces drawing the cover", inspected >= 5)

        if (offenders.isNotEmpty()) {
            fail("Faces reading the playing cover without FaceCoverImage - the cover will snap " +
                    "there while every other surface eases, and wear_album_art_fade will not " +
                    "reach them:\n  " + offenders.joinToString("\n  ") +
                    "\nRoute the cover through FaceCoverImage, or add the file to " +
                    "FILES_WITHOUT_THE_HELPER with the reason.")
        }
    }

    @Test
    fun noCoverIsDrawnStraightIntoAnImage() {
        val offenders = mutableListOf<String>()

        faceSources().forEach { file ->
            val lines = file.readText().split("\n")
            val functions = functionStarts(lines)
            lines.forEachIndexed { index, line ->
                val bare = line.contains("BitmapPainter(art)") ||
                        Regex("""bitmap = art\b""").containsMatchIn(line)
                if (!bare) return@forEachIndexed
                val owner = enclosingFunction(functions, index + 1)
                if (owner.substringAfterLast('.') !in ALLOWED_BARE_COVER) {
                    offenders += "${file.name}:${index + 1} in $owner"
                }
            }
        }

        if (offenders.isNotEmpty()) {
            fail("Cover bitmaps drawn without the shared transition:\n  " +
                    offenders.joinToString("\n  ") +
                    "\nUse FaceCoverImage, or add the composable to ALLOWED_BARE_COVER with the " +
                    "reason the helper does not fit.")
        }
    }

    /** A stale exemption would let a real regression back in unnoticed. */
    @Test
    fun everyExemptionStillExists() {
        val allSource = faceSources().joinToString("\n") { it.readText() }
        ALLOWED_BARE_COVER.forEach { (name, reason) ->
            assertTrue(
                    "$name is exempted (\"$reason\") but no longer exists - remove the entry",
                    allSource.contains("fun $name(") || allSource.contains(".$name("))
        }
        FILES_WITHOUT_THE_HELPER.forEach { (name, reason) ->
            assertTrue(
                    "$name is exempted (\"$reason\") but no longer exists - remove the entry",
                    faceSources().any { it.name == name })
        }
    }

    // ----------------------------------------------------------------- source

    private fun faceSources(): List<File> =
            listOf(
                    File("src/main/java/com/svartifoss/snfell/watch/view/face"),
                    File("wear/src/main/java/com/svartifoss/snfell/watch/view/face"))
                    .firstOrNull { it.isDirectory }
                    ?.listFiles { file -> file.extension == "kt" }
                    ?.sortedBy { it.name }
                    ?: throw AssertionError(
                            "Could not locate the face sources from ${File(".").absolutePath}")

    /**
     * Top-level declarations only - a local `fun` nested inside a composable is indented, and
     * counting it would attribute a drawing to whatever helper happened to be declared above it
     * (Ribbon's `cardAt`, which is a queue lookup and draws nothing).
     */
    private fun functionStarts(lines: List<String>): List<Pair<Int, String>> =
            lines.mapIndexedNotNull { index, line ->
                Regex("""^(?:internal |private |public )?fun ([A-Za-z0-9_.]+)""")
                        .find(line)
                        ?.let { (index + 1) to it.groupValues[1] }
            }

    private fun enclosingFunction(functions: List<Pair<Int, String>>, line: Int): String =
            functions.lastOrNull { it.first <= line }?.second ?: "<file scope>"
}
