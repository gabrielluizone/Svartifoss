package com.svartifoss.snfell.watch.view.face

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * No awake face may draw the play or pause mark from a drawable of its own choosing.
 *
 * The marks are a setting (`wear_play_icon` / `wear_pause_icon`), resolved once per face by
 * `TransportGlyphs` and handed to the face as `state.playGlyph` / `state.pauseGlyph`. A face that
 * paints `action_play_filled` directly still compiles and still looks right - and quietly ignores
 * the setting, which is the worst place for it to go unnoticed because the screen built to show
 * what a setting does would be the only one it appeared to work on.
 *
 * Source sweep for the same reason `ArtistLineTypographyTest` is one: what goes wrong is never the
 * drawing, it is a call site opting out without saying so.
 *
 * [ALLOWED] names every composable that may still name those drawables and why. The ambient
 * variants are the whole list apart from the shared helper's own defaults: AOD is styled by
 * `WEAR_AOD_*` and lights no pixels it does not have to, so it draws its own static glyph.
 */
class TransportGlyphUsageTest {

    private companion object {
        val ALLOWED = mapOf(
                "PlayPauseIcon" to "the shared helper's default parameters for a caller that passes none",
                "AmbientCookie" to "Expressive's AOD variant",
                "CuratedAmbientFace" to "the curated faces' AOD variants")

        val DRAWABLES = Regex("""action_(play_filled|pause_filled|pause_expressive)""")
    }

    @Test
    fun noAwakeFaceNamesAPlayOrPauseDrawableDirectly() {
        val offenders = mutableListOf<String>()
        var inspected = 0

        faceSources().forEach { file ->
            val lines = file.readText().split("\n")
            val functions = lines.mapIndexedNotNull { index, line ->
                Regex("""^(?:internal |private |public )?fun (?:[A-Za-z0-9_.]+\.)?([A-Za-z0-9_]+)""")
                        .find(line.trim())?.let { (index + 1) to it.groupValues[1] }
            }
            lines.forEachIndexed { index, line ->
                if (line.trim().startsWith("import") || !DRAWABLES.containsMatchIn(line)) return@forEachIndexed
                inspected++
                val owner = functions.lastOrNull { it.first <= index + 1 }?.second ?: "<file scope>"
                if (owner !in ALLOWED) offenders += "${file.name}:${index + 1} in $owner"
            }
        }

        assertTrue("Expected to find the ambient and default sites to inspect", inspected >= 4)
        if (offenders.isNotEmpty()) {
            fail("Faces drawing the play/pause mark from a fixed drawable - the Play icon and " +
                    "Pause icon settings will not reach these:\n  " + offenders.joinToString("\n  ") +
                    "\nDraw state.playIconRes()/pauseIconRes() instead, or add the composable to " +
                    "ALLOWED with the reason it must not follow the setting.")
        }
    }

    @Test
    fun theAwakeFacesThatDrawAMarkReadTheResolvedGlyph() {
        val all = faceSources().joinToString("\n") { it.readText() }
        // The five places an awake mark is drawn: Expressive's cookie, Material's centre, the shared
        // glyph of the seven curated transport faces, and the tap confirmation of Frame and Ribbon.
        assertTrue(Regex("""playIcon = state\.playIconRes\(\)""").findAll(all).count() >= 2)
        assertTrue(all.contains("state.pauseIconRes()") && all.contains("state.playIconRes()"))
        assertTrue(all.contains("if (enteringPlay) state.playIconRes() else state.pauseIconRes()"))
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
}
