package com.svartifoss.snfell.watch.view.face

import com.svartifoss.snfell.common.FaceElements
import com.svartifoss.snfell.common.ThemeAppearance
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FaceElements] must say what the faces actually draw.
 *
 * The phone hides the app-mark switch and the track-time mode on the faces that registry names as
 * lacking them. Get it wrong one way and the rows come back where they move nothing; the other way
 * and a face that does draw the element loses its controls. Both are silent, so the registry is
 * checked against the source: from each face's entry point, through every function of its own file
 * it calls (its awake composition only - the always-on variants are styled by their own controls).
 *
 * Classic and Matejdro are the View faces; their artist row and time line live in `MainActivity`'s
 * layout, not here, and the registry lists both for them.
 */
class FaceElementsContractTest {

    private companion object {
        /** Each Compose face, as the file and the function its awake composition starts from. */
        val ENTRY_POINTS = mapOf(
                "expressive" to ("ExpressiveFace.kt" to "ExpressiveFace"),
                "carousel" to ("CarouselFace.kt" to "CarouselFace"),
                "chat" to ("ChatFace.kt" to "ChatFace"),
                "split" to ("SplitFace.kt" to "SplitFace"),
                "note" to ("NoteFace.kt" to "NoteFace"),
                "verse" to ("VerseFace.kt" to "VerseFace"),
                "metadata" to ("MetadataFace.kt" to "MetadataFace"),
                "artist" to ("ArtistFace.kt" to "ArtistFace"),
                "frame" to ("FrameFace.kt" to "FrameFace"),
                "ribbon" to ("RibbonFace.kt" to "RibbonFace"),
                "vinyl" to ("CuratedPlayerFaces.kt" to "VinylComposition"),
                "poster" to ("CuratedPlayerFaces.kt" to "PosterComposition"),
                "studio" to ("CuratedPlayerFaces.kt" to "StudioComposition"),
                "halo" to ("CuratedPlayerFaces.kt" to "HaloComposition"),
                "aurora" to ("CuratedPlayerFaces.kt" to "AuroraComposition"),
                "eclipse" to ("CuratedPlayerFaces.kt" to "EclipseComposition"),
                "spectrum" to ("CuratedPlayerFaces.kt" to "SpectrumComposition"),
                "material" to ("CuratedPlayerFaces.kt" to "MaterialComposition"),
                "immersive" to ("CuratedPlayerFaces.kt" to "ImmersiveComposition"),
                "depth" to ("CuratedPlayerFaces.kt" to "DepthComposition"))

        val VIEW_FACES = setOf("classic", "matejdro")

        val SOURCE_ICON = Regex("""(?<![A-Za-z])SourceIconGlyph\(|state\.sourceIcon(?![A-Za-z])""")
        val TRACK_TIME = Regex("""(?<![A-Za-z])TrackTimeText\(|state\.showTrackTime(?![A-Za-z])""")
        val DECLARATION = Regex(
                """^(?:@Composable\s+)?(?:private |internal |public )?fun (?:[\w.<>]+\.)?(\w+)\(""",
                RegexOption.MULTILINE)
        val CALL = Regex("""(?<![\w.])(\w+)\(""")
    }

    @Test
    fun everyFaceIsCovered() {
        assertEquals(
                "Add the new face to ENTRY_POINTS or VIEW_FACES",
                ThemeAppearance.ALLOWED_BASE_FACES,
                ENTRY_POINTS.keys + VIEW_FACES)
    }

    @Test
    fun theRegistryMatchesTheFacesThatDrawTheAppMark() {
        ENTRY_POINTS.forEach { (face, entry) ->
            assertEquals(
                    "Does $face draw the source app's mark? FaceElements says " +
                            FaceElements.drawsSourceIcon(face),
                    FaceElements.drawsSourceIcon(face),
                    SOURCE_ICON.containsMatchIn(awakeComposition(entry.first, entry.second)))
        }
    }

    @Test
    fun theRegistryMatchesTheFacesThatDrawTheTrackTime() {
        ENTRY_POINTS.forEach { (face, entry) ->
            assertEquals(
                    "Does $face draw the track time? FaceElements says " +
                            FaceElements.drawsTrackTime(face),
                    FaceElements.drawsTrackTime(face),
                    TRACK_TIME.containsMatchIn(awakeComposition(entry.first, entry.second)))
        }
    }

    @Test
    fun theViewFacesDrawBoth() {
        VIEW_FACES.forEach {
            assertTrue(FaceElements.drawsSourceIcon(it))
            assertTrue(FaceElements.drawsTrackTime(it))
        }
    }

    /**
     * [root]'s body and the bodies of every function of [fileName] it reaches, minus the always-on
     * variants. A function's body is taken as everything up to the next declaration, which is
     * generous but only ever over-includes a face's own code.
     */
    private fun awakeComposition(fileName: String, root: String): String {
        val text = withoutComments(sourceFile(fileName).readText())
        val declarations = DECLARATION.findAll(text).toList()
        val bodies = declarations.mapIndexed { index, match ->
            val end = declarations.getOrNull(index + 1)?.range?.first ?: text.length
            match.groupValues[1] to text.substring(match.range.first, end)
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.joinToString("\n") }
        assertTrue("$root not found in $fileName", root in bodies)
        val reached = linkedSetOf(root)
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val body = bodies.getValue(queue.removeFirst())
            CALL.findAll(body).map { it.groupValues[1] }
                    .filter { it in bodies && "Ambient" !in it && it !in reached }
                    .forEach { reached += it; queue += it }
        }
        return reached.joinToString("\n") { bodies.getValue(it) }
    }

    private fun sourceFile(name: String): File =
            listOf(
                    File("src/main/java/com/svartifoss/snfell/watch/view/face"),
                    File("wear/src/main/java/com/svartifoss/snfell/watch/view/face"))
                    .firstOrNull { it.isDirectory }
                    ?.resolve(name)
                    ?.takeIf { it.isFile }
                    ?: throw AssertionError("Could not locate $name from ${File(".").absolutePath}")

    /** Comments removed, so prose naming a helper is not read as a call to it. */
    private fun withoutComments(source: String): String =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
                    .lineSequence()
                    .joinToString("\n") { line ->
                        val index = line.indexOf("//")
                        if (index < 0 || line.substring(0, index).count { it == '"' } % 2 == 1) {
                            line
                        } else {
                            line.substring(0, index)
                        }
                    }
}
