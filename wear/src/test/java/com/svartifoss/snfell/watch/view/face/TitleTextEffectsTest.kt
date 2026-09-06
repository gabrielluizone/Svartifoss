package com.svartifoss.snfell.watch.view.face

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The title's shadow, outline and backdrop must reach every awake face from the host's answer.
 *
 * This is the sibling of [ArtistLineTypographyTest], and it exists because the title half failed in
 * a quieter way than the artist half did. The host resolved all three effects, put them on
 * `NowPlayingFaceState`, and [AdaptiveTitleText] accepted them as parameters that defaulted to
 * null - so every one of the fifteen call sites had to opt *in*, and not one of them did. The
 * settings therefore worked on the classic View face and in the phone's own preview (both of which
 * read the specs directly) and did nothing at all on any Compose face, which is the worst possible
 * arrangement: the screen built to show what the setting does was the one place it appeared to
 * work.
 *
 * Two rules keep that shut. The effects default off the state inside the helper, so a call site
 * cannot forget them; and a face's title has to go through a helper at all rather than being
 * styled as a bare `Text`.
 */
class TitleTextEffectsTest {

    private companion object {

        /** The state fields the helper must default from, and the parameter each backs. */
        val EFFECT_DEFAULTS = mapOf(
                "shadow: Shadow? = state.titleShadow" to "wear_title_shadow_style",
                "outline: TextOutlinePaint? = state.titleOutline" to "wear_title_outline_style",
                "backdrop: Color? = state.titleBackdrop" to "wear_title_text_bg_style")

        /**
         * Composables allowed to hand `state.titleFont` to something other than the two helpers.
         *
         * Keyed by the composable's simple name, as in [ArtistLineTypographyTest]. Every entry is
         * either an always-on variant - AOD is styled by `WEAR_AOD_*` and must not read the awake
         * title effects, which would be the bug in the other direction - or a piece of chrome that
         * borrows the title's family for text that is not the title.
         */
        val ALLOWED_BARE_TITLE_FONT = mapOf(
                "ArtistAmbient" to "AOD variant",
                "CarouselAmbient" to "AOD variant",
                "ChatAmbient" to "AOD variant",
                "ChronoAmbientFace" to "AOD variant",
                "CuratedAmbientFace" to "AOD variant",
                "ExpressiveAmbientFace" to "AOD variant",
                "MetadataAmbient" to "AOD variant",
                "NoteAmbient" to "AOD variant",
                "SplitAmbient" to "AOD variant",
                "VerseAmbient" to "AOD variant",

                // The Up Next pills name the *next* track, not the line the title controls style.
                "AmbientUpNextPill" to "next track, not the title line",
                "AwakeUpNextPill" to "next track, not the title line",

                // Note's sentence is one AnnotatedString whose title span is built here and then
                // handed to AdaptiveTitleText's AnnotatedString overload.
                "NoteLine" to "builds the span AdaptiveTitleText then renders",

                // The Artist face hands its authored family to TrackTimeText, whose own font key
                // falls back to it while set to `follow` - the same shape as the other track-time
                // sites, and not a title being drawn.
                "ArtistTextBlock" to "the face's authored family for TrackTimeText",

                // The helpers themselves.
                "TitleLineTextPass" to "TitleLineText's own drawing pass")

        /** Calls that are the shared helpers (or feed one) rather than a face drawing text itself. */
        val HELPER_CALLS = setOf("AdaptiveTitleText", "TitleLineText", "SpanStyle")
    }

    @Test
    fun theTitleEffectsDefaultOffTheFaceState() {
        val chrome = faceSources().first { it.name == "FaceChrome.kt" }.readText()
        EFFECT_DEFAULTS.forEach { (declaration, preference) ->
            assertTrue(
                    "AdaptiveTitleText must default its effect off NowPlayingFaceState - " +
                            "expected \"$declaration\". Left at null, $preference resolves on the " +
                            "phone, rides the preference sync, reaches the watch and then styles " +
                            "nothing, because no face passes it.",
                    chrome.contains(declaration))
        }
    }

    @Test
    fun everyAwakeFaceDrawsItsTitleThroughASharedHelper() {
        val offenders = mutableListOf<String>()
        var inspected = 0

        faceSources().forEach { file ->
            val lines = file.readText().split("\n")
            val functions = functionStarts(lines)
            lines.forEachIndexed { index, line ->
                if (!line.contains("state.titleFont")) return@forEachIndexed
                inspected++
                if (enclosingCall(lines, index) in HELPER_CALLS) return@forEachIndexed
                val owner = enclosingFunction(functions, index + 1).substringAfterLast('.')
                if (owner !in ALLOWED_BARE_TITLE_FONT) {
                    offenders += "${file.name}:${index + 1} in $owner"
                }
            }
        }

        assertTrue("Expected to find title-font call sites to inspect", inspected > 10)

        if (offenders.isNotEmpty()) {
            fail("Titles bypassing AdaptiveTitleText/TitleLineText - the Text tab's title " +
                    "shadow, outline, backdrop, weight, size, tracking and case will not reach " +
                    "these:\n  " + offenders.joinToString("\n  ") +
                    "\nRoute them through a helper, or add the composable to " +
                    "ALLOWED_BARE_TITLE_FONT with the reason neither one fits.")
        }
    }

    /**
     * A stale exemption would let a real regression back in unnoticed.
     *
     * Matched on the simple name with or without a receiver, as [ArtistLineTypographyTest] does: a
     * Compose extension function carries its receiver in the declaration
     * (`BoxScope.ArtistTextBlock`), and requiring the bare spelling would report a live exemption
     * as stale the moment the composable gained one - which is a failure that says the opposite of
     * what is true.
     */
    @Test
    fun everyExemptedComposableStillExists() {
        val allSource = faceSources().joinToString("\n") { it.readText() }
        ALLOWED_BARE_TITLE_FONT.forEach { (name, reason) ->
            assertTrue(
                    "$name is exempted (\"$reason\") but no longer exists - remove the entry",
                    allSource.contains("fun $name(") || allSource.contains(".$name("))
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
     * The call this argument line belongs to.
     *
     * Walks back to the nearest line indented less than the argument itself and reads the
     * identifier it opens - which is what separates `fontFamily = state.titleFont` sitting inside
     * an `AdaptiveTitleText(...)` argument list from the same line inside a bare `Text(...)`.
     */
    private fun enclosingCall(lines: List<String>, index: Int): String? {
        val indent = lines[index].indentWidth()
        for (i in index downTo 0) {
            val line = lines[i]
            if (line.isBlank()) continue
            if (i != index && line.indentWidth() >= indent) continue
            // First, not last: a call opener may carry an argument that is itself a call
            // (`AdaptiveTitleText(state.title.uppercase(), ...`), and the name being resolved is
            // the one the argument list below belongs to.
            val call = Regex("""([A-Za-z_][A-Za-z0-9_]*)\(""").find(line)?.groupValues?.get(1)
            if (call != null) return call
        }
        return null
    }

    private fun String.indentWidth(): Int = length - trimStart().length

    private fun functionStarts(lines: List<String>): List<Pair<Int, String>> =
            lines.mapIndexedNotNull { index, line ->
                Regex("""^(?:internal |private |public )?fun ([A-Za-z0-9_.]+)""")
                        .find(line.trim())
                        ?.let { (index + 1) to it.groupValues[1] }
            }

    private fun enclosingFunction(functions: List<Pair<Int, String>>, line: Int): String =
            functions.lastOrNull { it.first <= line }?.second ?: "<file scope>"
}
