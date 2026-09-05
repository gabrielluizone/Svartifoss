package com.svartifoss.snfell.watch.view.face

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The always-on controls must reach every always-on face.
 *
 * There are three of them for artwork - show it, how to treat it, how visible to make it - and one
 * for the track information, and each is a switch on the phone that a user expects to do something
 * on the wrist. What makes them easy to drop is that a face receives everything it draws as one
 * state object: reading the wrong field, or forgetting to read one at all, compiles, renders and
 * looks entirely correct. The setting simply does nothing, on that one face, and only in ambient -
 * where nobody is looking at it closely.
 *
 * Both failures had already happened. Carousel's ambient card read [NowPlayingFaceState.albumArt],
 * the *awake* cover, so it kept showing a picture the user had turned off, under the interactive
 * photo filter rather than the ambient one, at a hardcoded opacity. Metadata drew its identity and
 * rows unconditionally, so "Show track info" emptied the screen on ten styles and did nothing on
 * the eleventh.
 *
 * A source sweep rather than a rendering assertion, for the same reason
 * [ArtistLineTypographyTest] is one: what goes wrong is never the drawing, it is a face quietly
 * not consulting a control.
 */
class AmbientFaceContractTest {

    private companion object {
        /** `state.albumArt` itself, not the sibling flags (`albumArtBlurRadiusPx` and friends),
         *  which an ambient face may legitimately read. */
        val AWAKE_COVER = Regex("""state\.albumArt(?![A-Za-z0-9_])""")

        /**
         * Every always-on artwork control, as the face sees it.
         *
         * Named together because they only work together: a card that honours visibility but not
         * the treatment shows the right picture wrongly, and one that honours both but not the
         * opacity ignores the slider that sits directly under them on the phone.
         */
        val AMBIENT_ARTWORK_CONTROLS = listOf(
                "state.ambientAlbumArt",
                "state.ambientAlbumArtBlurred",
                "state.ambientAlbumArtAlpha")
    }

    /**
     * An ambient face that wants the cover takes the ambient one.
     *
     * [NowPlayingFaceState.albumArt] carries the awake treatment - the frosted rim, the interactive
     * photo filter - and is published whether the always-on artwork controls asked for a cover or
     * not. It is the right field for exactly one mode, and ambient is the other one.
     */
    @Test
    fun noAmbientFaceReadsTheAwakeCover() {
        val offenders = ambientFaceBodies()
                .filterValues { AWAKE_COVER.containsMatchIn(it) }
                .keys
        if (offenders.isNotEmpty()) {
            fail("Always-on faces reading the awake cover:\n  " +
                    offenders.sorted().joinToString("\n  ") +
                    "\nUse state.ambientAlbumArt: it is null exactly when the user turned " +
                    "always-on artwork off, and carries the ambient treatment rather than the " +
                    "interactive one.")
        }
    }

    /** Whichever faces draw an ambient cover, each must carry all three artwork controls. */
    @Test
    fun everyAmbientCoverCarriesAllThreeArtworkControls() {
        val drawing = ambientFaceBodies()
                .filterValues { it.contains(AMBIENT_ARTWORK_CONTROLS.first()) }
        assertTrue(
                "No ambient face draws a cover any more - if that is deliberate, delete this test " +
                        "and NowPlayingFaceState's ambientAlbumArt with it",
                drawing.isNotEmpty())

        val missing = drawing.flatMap { (name, body) ->
            AMBIENT_ARTWORK_CONTROLS.filterNot { body.contains(it) }.map { "$name: $it" }
        }
        if (missing.isNotEmpty()) {
            fail("Always-on artwork controls an ambient cover does not consult:\n  " +
                    missing.joinToString("\n  "))
        }
    }

    /**
     * Every ambient face consults the track-information switch.
     *
     * Not "draws no text when it is off" - a face may keep a purely structural element, as Split
     * keeps its seam - but it has to *read* the flag. A face that never mentions it cannot
     * possibly be honouring it.
     */
    @Test
    fun everyAmbientFaceHonoursTheTrackInfoSwitch() {
        val offenders = ambientFaceBodies()
                .filterValues { !it.contains("ambientShowTrackInfo") }
                .keys
        if (offenders.isNotEmpty()) {
            fail("Always-on faces that never read ambientShowTrackInfo - the phone's " +
                    "\"Show track info\" switch does nothing on these:\n  " +
                    offenders.sorted().joinToString("\n  "))
        }
    }

    // ----------------------------------------------------------------- source

    /**
     * Each always-on face composable, by "File.kt:Name", mapped to its body.
     *
     * Matched on the name rather than on an annotation so the ambient *helpers* stay out of it:
     * `AmbientUpNextPill` and `AmbientSourceIconGlyph` are pieces an ambient face puts on screen,
     * gated by their own controls at the call site, not faces that owe these answers themselves.
     */
    private fun ambientFaceBodies(): Map<String, String> {
        val declaration = Regex(
                """^(?:private |internal |public )?fun (\w+Ambient(?:Face)?)\(""",
                RegexOption.MULTILINE)
        val bodies = mutableMapOf<String, String>()
        faceSources().forEach { file ->
            val text = withoutComments(file.readText())
            declaration.findAll(text).forEach { match ->
                bodies["${file.name}:${match.groupValues[1]}"] =
                        balancedBlock(text, text.indexOf('{', match.range.last))
            }
        }
        assertTrue(
                "Expected to find the always-on faces; found ${bodies.keys}",
                bodies.size >= 10)
        return bodies
    }

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
     * Comments removed, so a line explaining the rule is not read as a breach of it.
     *
     * The prose beside a fix routinely names the field it replaced - "state.ambientAlbumArt, never
     * state.albumArt" is exactly the comment worth leaving at the site - and a sweep that matched
     * that would make documenting the trap the one thing you cannot do. Block comments go first;
     * a `//` starts a comment only outside a string literal, so a "content://" URL survives.
     */
    private fun withoutComments(source: String): String =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
                    .lineSequence()
                    .joinToString("\n") { line ->
                        var quotes = 0
                        line.forEachIndexed { index, ch ->
                            if (ch == '"' && (index == 0 || line[index - 1] != '\\')) quotes++
                            if (ch == '/' && index > 0 && line[index - 1] == '/' &&
                                    quotes % 2 == 0) {
                                return@joinToString line.substring(0, index - 1)
                            }
                        }
                        line
                    }

    /** The text between [open] and its matching brace. */
    private fun balancedBlock(source: String, open: Int): String {
        var depth = 0
        var index = open
        while (index < source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                }
            }
            index++
        }
        throw AssertionError("Unbalanced braces from offset $open")
    }
}
