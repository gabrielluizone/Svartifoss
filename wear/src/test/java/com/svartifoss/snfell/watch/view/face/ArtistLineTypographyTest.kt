package com.svartifoss.snfell.watch.view.face

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Every awake face must draw its artist line through `ArtistLineText`.
 *
 * The helper exists because the artist controls are six separate settings - family, weight, slant,
 * size, tracking and opacity - and a face that styles a bare `Text` reads whichever of them it
 * happens to name and silently drops the rest. That is not a hypothetical: `ArtistLineText` was
 * called from two places in `CuratedPlayerFaces` while twelve bare `Text`s passed nothing but
 * `state.artistFont`, so on most of the collection the Text tab's artist weight, size, tracking and
 * opacity moved the title and left the artist exactly where it was. Nothing failed; the controls
 * simply had no effect on the wrist, which is the hardest kind of bug to notice from the phone.
 *
 * The check is a source sweep rather than a rendering assertion because what went wrong was never
 * the drawing - it was a call site opting out of the shared merge without saying so.
 *
 * [ALLOWED_BARE_ARTIST_FONT] is the escape hatch and every entry names why the helper genuinely
 * does not fit. Ambient is the largest group and the least negotiable: AOD has its own `WEAR_AOD_*`
 * controls by design, so an always-on variant reading the awake artist spec would be a bug in the
 * other direction.
 */
class ArtistLineTypographyTest {

    private companion object {

        /**
         * Composables allowed to pass `state.artistFont` to something other than `ArtistLineText`.
         *
         * Keyed by the composable's simple name - a Compose extension function carries its
         * receiver in the declaration (`BoxWithConstraintsScope.ElapsedTime`), and pinning that
         * spelling would make the exemption break when the receiver changes rather than when the
         * rule does. Adding an entry means naming the composable that opted out.
         */
        val ALLOWED_BARE_ARTIST_FONT = mapOf(
                // --- Always-on variants: AOD is styled by WEAR_AOD_* and must not read the awake
                // artist spec at all.
                "CarouselAmbient" to "AOD variant",
                "ChatAmbient" to "AOD variant",
                "CuratedAmbientFace" to "AOD variant",
                "ExpressiveAmbientFace" to "AOD variant",
                "ChronoAmbientFace" to "AOD variant",
                "MetadataAmbient" to "AOD variant",
                "SplitAmbient" to "AOD variant",

                // --- Playback time, not the artist. These pass the artist family as the family
                // *that face authored* for its time readout; TrackTimeText then lets
                // WEAR_TRACK_TIME_FONT override it, which is the whole point of that control.
                "ImmersiveComposition" to "TrackTimeText's authored family",
                "PosterComposition" to "TrackFooter's authored family",
                "StudioComposition" to "TrackFooter's authored family",
                "VoiceBubble" to "TrackTimeText's authored family",
                "NoteFace" to "TrackTimeText's authored family",
                "ElapsedTime" to "TrackTimeText's authored family",
                // Frame's header row holds the chip *and* the time. The chip's artist line goes
                // through the helper (FrameArtistChip); the artist family here is the readout's
                // authored one.
                "FrameHeaderRow" to "TrackTimeText's authored family",

                // --- The Metadata face's table body borrows the artist family for its rows. Those
                // are data, not the artist line; the face's own artist line is separate and does
                // go through the helper.
                "MetadataTable" to "table body font",
                "MetadataRow" to "table body font",

                // --- Two artist lines the helper cannot express, which apply the spec by hand
                // instead. Both are checked separately below.
                "CurrentMessageBubble" to "start-aligned bubble; applies the spec inline",
                "NoteLine" to "one sentence as an AnnotatedString; applies the spec to its span",

                // --- The helper itself.
                "ArtistLineText" to "the shared helper"
        )

        /** The two inline sites, and every property each has to carry to stand in for the helper. */
        val INLINE_ARTIST_SITES = mapOf(
                "ChatFace.kt" to listOf(
                        "state.artistFont",
                        "state.artistFontStyle",
                        "state.artistFontWeight",
                        "state.artistLetterSpacing",
                        "artistSpec.scaled",
                        "artistSpec.alpha",
                        "artistSpec.case"),
                "NoteFace.kt" to listOf(
                        "state.artistFont",
                        "state.artistFontStyle",
                        "state.artistLetterSpacing",
                        "artistSpec.weight",
                        "artistSpec.alpha",
                        "artistSpec.case")
        )
    }

    @Test
    fun everyAwakeFaceDrawsItsArtistThroughTheSharedHelper() {
        val offenders = mutableListOf<String>()
        var inspected = 0

        faceSources().forEach { file ->
            val text = file.readText()
            val lines = text.split("\n")
            val functions = functionStarts(lines)
            lines.forEachIndexed { index, line ->
                if (!line.contains("fontFamily = state.artistFont")) return@forEachIndexed
                inspected++
                val owner = enclosingFunction(functions, index + 1)
                if (owner.substringAfterLast('.') !in ALLOWED_BARE_ARTIST_FONT) {
                    offenders += "${file.name}:${index + 1} in $owner"
                }
            }
        }

        assertTrue("Expected to find artist-font call sites to inspect", inspected > 10)

        if (offenders.isNotEmpty()) {
            fail("Artist lines bypassing ArtistLineText - the Text tab's artist weight, size, " +
                    "tracking and opacity will not reach these:\n  " +
                    offenders.joinToString("\n  ") +
                    "\nRoute them through ArtistLineText, or add the composable to " +
                    "ALLOWED_BARE_ARTIST_FONT with the reason the helper does not fit.")
        }
    }

    /**
     * The two hand-rolled sites have to keep carrying the whole spec.
     *
     * They are the ones most likely to rot, because they look finished while missing a property -
     * Chat's bubble already read the family, slant and size and dropped the weight, tracking and
     * opacity, and looked entirely reasonable doing it.
     */
    @Test
    fun theInlineArtistSitesStillApplyTheWholeSpec() {
        val missing = mutableListOf<String>()
        INLINE_ARTIST_SITES.forEach { (fileName, required) ->
            val text = faceSources().first { it.name == fileName }.readText()
            required.filterNot { text.contains(it) }.forEach { missing += "$fileName: $it" }
        }
        if (missing.isNotEmpty()) {
            fail("Artist typography properties dropped from a hand-rolled artist line:\n  " +
                    missing.joinToString("\n  "))
        }
    }

    /** A stale exemption would let a real regression back in unnoticed. */
    @Test
    fun everyExemptedComposableStillExists() {
        val allSource = faceSources().joinToString("\n") { it.readText() }
        ALLOWED_BARE_ARTIST_FONT.forEach { (name, reason) ->
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

    private fun functionStarts(lines: List<String>): List<Pair<Int, String>> =
            lines.mapIndexedNotNull { index, line ->
                Regex("""^(?:internal |private |public )?fun ([A-Za-z0-9_.]+)""")
                        .find(line.trim())
                        ?.let { (index + 1) to it.groupValues[1] }
            }

    private fun enclosingFunction(functions: List<Pair<Int, String>>, line: Int): String =
            functions.lastOrNull { it.first <= line }?.second ?: "<file scope>"
}
